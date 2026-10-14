package com.naury.voxerartc.core

import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.ConfigurationProblem
import com.naury.voxerartc.api.ConnectOptions
import com.naury.voxerartc.api.ConnectOutcome
import com.naury.voxerartc.api.CredentialReason
import com.naury.voxerartc.api.CredentialRequest
import com.naury.voxerartc.api.CredentialsUnavailableException
import com.naury.voxerartc.api.EndReason
import com.naury.voxerartc.api.InputChecks
import com.naury.voxerartc.api.JoinResult
import com.naury.voxerartc.api.LeaveReason
import com.naury.voxerartc.api.LocalMediaState
import com.naury.voxerartc.api.MediaActionResult
import com.naury.voxerartc.api.MediaToggle
import com.naury.voxerartc.api.MonotonicClock
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.api.QualitySnapshot
import com.naury.voxerartc.api.RtcCredentials
import com.naury.voxerartc.api.RtcEngine
import com.naury.voxerartc.api.RtcEngineEvent
import com.naury.voxerartc.api.RtcEngineFactory
import com.naury.voxerartc.api.RtcEngineKind
import com.naury.voxerartc.api.ScreenShareConsent
import com.naury.voxerartc.api.VideoEffect
import com.naury.voxerartc.api.ServerUrlCheck
import com.naury.voxerartc.api.SessionState
import com.naury.voxerartc.api.VoxeraError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [VoxeraSession] 구현. 상태 전이는 모두 [SessionReducer]를 거치고, 전이가 받아들여졌을 때만 부수 효과를 실행한다.
 *
 * 연결 하나는 [Connection]으로 묶여 있으며 세대(generation)가 다르면 엔진 이벤트·타이머·늦은 결과를 버린다.
 */
internal class DefaultVoxeraSession(
    private val config: VoxeraSessionConfig,
    private val engineFactory: RtcEngineFactory,
    private val callLock: ActiveCallLock,
    override val clock: MonotonicClock,
    dispatcher: CoroutineDispatcher,
) : VoxeraSession {

    private class Connection(val generation: Long, val engine: RtcEngine, val events: Job) {
        var reconnectDeadline: Job? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private val mediaMutex = Mutex()

    // lock으로 보호한다.
    private var machine = SessionMachine()
    private var connection: Connection? = null
    private var pendingJoin: Deferred<JoinResult>? = null

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    private val _participants = MutableStateFlow<List<Participant>>(emptyList())
    private val _localMedia = MutableStateFlow(LocalMediaState())
    private val _diagnostics = MutableSharedFlow<QualitySnapshot>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val engineKind: RtcEngineKind get() = engineFactory.kind
    override val state: StateFlow<SessionState> = _state.asStateFlow()
    override val participants: StateFlow<List<Participant>> = _participants.asStateFlow()
    override val localMedia: StateFlow<LocalMediaState> = _localMedia.asStateFlow()
    override val diagnostics: Flow<QualitySnapshot> = _diagnostics.asSharedFlow()

    private fun dispatch(action: SessionAction): Transition = synchronized(lock) {
        val transition = SessionReducer.reduce(machine, action)
        if (transition.accepted) {
            machine = transition.machine
            _state.value = transition.machine.state
        }
        transition
    }

    override suspend fun join(roomId: String, mode: CallMode, media: MediaPreferences?): JoinResult {
        if (closed.get()) return JoinResult.Rejected(VoxeraError.InternalFailure("SESSION_CLOSED"))
        InputChecks.checkRoomName(roomId)?.let { return JoinResult.Rejected(VoxeraError.InvalidConfiguration(it)) }
        if (!callLock.tryAcquire(this)) return JoinResult.Rejected(VoxeraError.ConcurrentSession)
        val transition = dispatch(SessionAction.JoinRequested(roomId.trim(), mode))
        if (!transition.accepted) return JoinResult.Rejected(VoxeraError.ConcurrentSession)
        val generation = transition.machine.generation
        val preferences = media ?: config.media
        // leave()가 바로 취소할 수 있도록 작업을 등록한 뒤에 시작한다.
        val work = scope.async(start = CoroutineStart.LAZY) { runJoin(generation, roomId.trim(), mode, preferences) }
        synchronized(lock) { pendingJoin = work }
        work.start()
        return try {
            work.await()
        } catch (cancelled: CancellationException) {
            if (currentCoroutineContext().isActive) {
                // 호출자는 살아 있고 leave()가 입장 작업을 취소한 경우.
                JoinResult.Rejected(VoxeraError.UserCancelled)
            } else {
                work.cancel()
                scope.launch { leave(LeaveReason.USER) }
                throw cancelled
            }
        } finally {
            synchronized(lock) { if (pendingJoin === work) pendingJoin = null }
        }
    }

    private suspend fun runJoin(generation: Long, roomId: String, mode: CallMode, media: MediaPreferences): JoinResult {
        var credentials = when (val fetched = fetchCredentials(roomId, CredentialReason.INITIAL_JOIN)) {
            is CredentialFetch.Ready -> fetched.credentials
            is CredentialFetch.Error -> return fail(generation, fetched.error)
        }
        if (!dispatch(SessionAction.CredentialsReady(generation)).accepted) {
            return JoinResult.Rejected(VoxeraError.UserCancelled)
        }
        val engine = engineFactory.create()
        val events = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            engine.events.collect { onEngineEvent(generation, it) }
        }
        val attached = synchronized(lock) {
            if (machine.generation == generation && machine.state is SessionState.Connecting) {
                connection = Connection(generation, engine, events)
                true
            } else false
        }
        if (!attached) {
            events.cancel()
            withContext(NonCancellable) { engine.release() }
            return JoinResult.Rejected(VoxeraError.UserCancelled)
        }
        val options = ConnectOptions(
            roomId = roomId,
            mode = mode,
            startWithMicrophone = media.startWithMicrophone,
            startWithCamera = mode == CallMode.VIDEO && media.startWithCamera,
        )
        var outcome = connect(engine, credentials, options)
        if (outcome is ConnectOutcome.Failed && outcome.error == VoxeraError.AuthenticationExpired) {
            // 만료된 토큰은 새 토큰으로 한 번만 다시 시도한다.
            credentials = when (val fetched = fetchCredentials(roomId, CredentialReason.REAUTHENTICATE)) {
                is CredentialFetch.Ready -> fetched.credentials
                is CredentialFetch.Error -> return fail(generation, fetched.error)
            }
            outcome = connect(engine, credentials, options)
        }
        return when (outcome) {
            is ConnectOutcome.Failed -> fail(generation, outcome.error)
            is ConnectOutcome.Connected -> {
                if (!dispatch(SessionAction.Connected(generation, clock.nowMs())).accepted) {
                    // 종료 중에 도착한 성공. 정리는 leave()가 맡는다.
                    return JoinResult.Rejected(VoxeraError.UserCancelled)
                }
                val local = outcome.local
                upsertParticipant(local)
                _localMedia.update {
                    it.copy(
                        microphone = MediaToggle(local.isMicrophoneEnabled, local.isMicrophoneEnabled),
                        camera = MediaToggle(local.camera != null, local.camera != null),
                    )
                }
                JoinResult.Connected(UUID.randomUUID().toString())
            }
        }
    }

    private suspend fun connect(engine: RtcEngine, credentials: RtcCredentials, options: ConnectOptions): ConnectOutcome =
        withTimeoutOrNull(config.policy.connectTimeoutMs) { engine.connect(credentials, options) }
            ?: ConnectOutcome.Failed(VoxeraError.ServerUnreachable)

    private sealed interface CredentialFetch {
        data class Ready(val credentials: RtcCredentials) : CredentialFetch
        data class Error(val error: VoxeraError) : CredentialFetch
    }

    private suspend fun fetchCredentials(roomId: String, reason: CredentialReason): CredentialFetch {
        val policy = config.policy
        var attempt = 0
        while (true) {
            attempt++
            val credentials = try {
                config.credentials.getCredentials(CredentialRequest(roomId, reason))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: CredentialsUnavailableException) {
                when (failure.kind) {
                    CredentialsUnavailableException.Kind.UNAUTHORIZED -> return CredentialFetch.Error(VoxeraError.HostUnauthorized)
                    CredentialsUnavailableException.Kind.REJECTED ->
                        return CredentialFetch.Error(VoxeraError.InvalidConfiguration(ConfigurationProblem.TOKEN_SOURCE_REJECTED))
                    CredentialsUnavailableException.Kind.TRANSIENT -> {
                        if (attempt >= policy.credentialAttempts) return CredentialFetch.Error(VoxeraError.ServerUnreachable)
                        delay(policy.credentialRetryDelayMs shl (attempt - 1))
                        continue
                    }
                }
            } catch (_: Exception) {
                return CredentialFetch.Error(VoxeraError.InternalFailure("CREDENTIAL_PROVIDER_ERROR"))
            }
            return validate(credentials)
        }
    }

    private fun validate(credentials: RtcCredentials): CredentialFetch {
        val url = when (val check = config.serverUrlPolicy.check(credentials.serverUrl)) {
            is ServerUrlCheck.Invalid -> return CredentialFetch.Error(VoxeraError.InvalidConfiguration(check.problem))
            is ServerUrlCheck.Valid -> check.normalized
        }
        if (credentials.accessToken.isBlank()) {
            return CredentialFetch.Error(VoxeraError.InvalidConfiguration(ConfigurationProblem.TOKEN_BLANK))
        }
        return CredentialFetch.Ready(RtcCredentials(url, credentials.accessToken.trim()))
    }

    /** 자원을 먼저 정리하고 실패 상태로 옮긴다. 이미 나가는 중이면 leave()가 마무리한다. */
    private suspend fun fail(generation: Long, error: VoxeraError): JoinResult {
        releaseConnection(generation)
        releaseCallLock(generation)
        return if (dispatch(SessionAction.Failed(generation, error, clock.nowMs())).accepted) {
            JoinResult.Rejected(error)
        } else {
            JoinResult.Rejected(VoxeraError.UserCancelled)
        }
    }

    /**
     * 통화 잠금을 푼다. 미디어를 이미 해제한 뒤에 부르며, 그사이 새 세대가 시작되었으면 그 통화의 잠금이므로 풀지 않는다.
     */
    private fun releaseCallLock(generation: Long) {
        synchronized(lock) {
            if (machine.generation == generation) callLock.release(this)
        }
    }

    private fun onEngineEvent(generation: Long, event: RtcEngineEvent) {
        if (synchronized(lock) { machine.generation != generation || connection?.generation != generation }) return
        when (event) {
            is RtcEngineEvent.ParticipantUpserted -> upsertParticipant(event.participant)
            is RtcEngineEvent.ParticipantLeft -> _participants.update { list -> list.filterNot { it.id == event.participantId } }
            is RtcEngineEvent.LocalMediaChanged -> _localMedia.update { current ->
                event.state.copy(
                    microphone = current.microphone.settle(event.state.microphone.actual),
                    camera = current.camera.settle(event.state.camera.actual),
                    screenShare = current.screenShare.settle(event.state.screenShare.actual),
                )
            }
            is RtcEngineEvent.QualitySampled -> _diagnostics.tryEmit(event.snapshot)
            RtcEngineEvent.Reconnecting -> onReconnecting(generation)
            RtcEngineEvent.Reconnected -> if (dispatch(SessionAction.Reconnected(generation)).accepted) {
                synchronized(lock) { connection?.reconnectDeadline?.cancel() }
            }
            is RtcEngineEvent.Disconnected -> scope.launch { onEngineDisconnected(generation, event.error) }
        }
    }

    private fun onReconnecting(generation: Long) {
        val now = clock.nowMs()
        val deadline = now + config.policy.reconnectDeadlineMs
        if (!dispatch(SessionAction.Reconnecting(generation, now, deadline)).accepted) return
        val timer = scope.launch {
            delay(config.policy.reconnectDeadlineMs)
            if (dispatch(SessionAction.ReconnectDeadlinePassed(generation, clock.nowMs())).accepted) {
                // 타이머 자신이 releaseConnection에서 취소되므로 정리는 별도 코루틴에서 한다.
                scope.launch {
                    withContext(NonCancellable) {
                        releaseConnection(generation)
                        releaseCallLock(generation)
                        dispatch(SessionAction.Failed(generation, VoxeraError.NetworkUnavailable, clock.nowMs()))
                    }
                }
            }
        }
        synchronized(lock) {
            val current = connection
            if (current?.generation == generation) {
                current.reconnectDeadline?.cancel()
                current.reconnectDeadline = timer
            } else timer.cancel()
        }
    }

    private suspend fun onEngineDisconnected(generation: Long, error: VoxeraError?) {
        val now = clock.nowMs()
        if (error == null) {
            if (!dispatch(SessionAction.RemoteEnded(generation, now)).accepted) return
            releaseConnection(generation)
            releaseCallLock(generation)
            dispatch(SessionAction.Released(generation, clock.nowMs()))
        } else {
            val active = synchronized(lock) {
                machine.generation == generation && when (machine.state) {
                    is SessionState.Connected, is SessionState.Reconnecting -> true
                    else -> false
                }
            }
            if (!active) return
            releaseConnection(generation)
            releaseCallLock(generation)
            dispatch(SessionAction.Failed(generation, error, clock.nowMs()))
        }
    }

    private fun upsertParticipant(participant: Participant) {
        _participants.update { list ->
            val index = list.indexOfFirst { it.id == participant.id }
            val next = if (index >= 0) list.toMutableList().apply { set(index, participant) } else list + participant
            next.sortedByDescending { it.isLocal }
        }
    }

    /** 해당 세대의 연결을 한 번만 정리한다. 다른 세대거나 이미 정리했으면 아무것도 하지 않는다. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun releaseConnection(generation: Long) {
        val released = synchronized(lock) {
            connection?.takeIf { it.generation == generation }?.also { connection = null }
        } ?: return
        released.events.cancel()
        released.reconnectDeadline?.cancel()
        withContext(NonCancellable) { released.engine.release() }
        _participants.value = emptyList()
        _localMedia.value = LocalMediaState()
        _diagnostics.resetReplayCache()
    }

    override suspend fun setMicrophoneEnabled(enabled: Boolean): MediaActionResult =
        applyToggle(MediaKind.MICROPHONE, enabled)

    override suspend fun setCameraEnabled(enabled: Boolean): MediaActionResult =
        applyToggle(MediaKind.CAMERA, enabled)

    private enum class MediaKind { MICROPHONE, CAMERA }

    private fun LocalMediaState.toggle(kind: MediaKind): MediaToggle = when (kind) {
        MediaKind.MICROPHONE -> microphone
        MediaKind.CAMERA -> camera
    }

    private fun LocalMediaState.with(kind: MediaKind, toggle: MediaToggle): LocalMediaState = when (kind) {
        MediaKind.MICROPHONE -> copy(microphone = toggle)
        MediaKind.CAMERA -> copy(camera = toggle)
    }

    /**
     * 사용자의 의도를 먼저 기록하고, 엔진에는 한 번에 하나씩 가장 최근 의도만 반영한다. 빠르게 여러 번 눌러도
     * 마지막 의도와 엔진 ack가 일치한다.
     */
    private suspend fun applyToggle(kind: MediaKind, enabled: Boolean): MediaActionResult {
        val engine = connectedEngine() ?: return MediaActionResult.Failed(VoxeraError.NotConnected)
        _localMedia.update { it.with(kind, it.toggle(kind).copy(desired = enabled, lastError = null)) }
        return mediaMutex.withLock {
            val target = _localMedia.value.toggle(kind).desired
            if (_localMedia.value.toggle(kind).actual == target) return@withLock MediaActionResult.Applied
            val result = when (kind) {
                MediaKind.MICROPHONE -> engine.setMicrophoneEnabled(target)
                MediaKind.CAMERA -> engine.setCameraEnabled(target)
            }
            _localMedia.update { media ->
                val current = media.toggle(kind)
                val next = when (result) {
                    MediaActionResult.Applied -> current.copy(actual = target)
                    is MediaActionResult.Failed -> MediaToggle(current.actual, current.actual, result.error)
                    MediaActionResult.Unsupported -> MediaToggle(current.actual, current.actual)
                }
                media.with(kind, next)
            }
            result
        }
    }

    override suspend fun switchCamera(): MediaActionResult {
        val engine = connectedEngine() ?: return MediaActionResult.Failed(VoxeraError.NotConnected)
        return mediaMutex.withLock { engine.switchCamera() }
    }

    override suspend fun startScreenShare(consent: ScreenShareConsent): MediaActionResult {
        val engine = connectedEngine() ?: return MediaActionResult.Failed(VoxeraError.NotConnected)
        _localMedia.update { it.copy(screenShare = it.screenShare.copy(desired = true, lastError = null)) }
        return mediaMutex.withLock { engine.startScreenShare(consent) }.also { settleScreenShare(it, target = true) }
    }

    override suspend fun stopScreenShare(): MediaActionResult {
        val engine = connectedEngine() ?: return MediaActionResult.Failed(VoxeraError.NotConnected)
        _localMedia.update { it.copy(screenShare = it.screenShare.copy(desired = false, lastError = null)) }
        return mediaMutex.withLock { engine.stopScreenShare() }.also { settleScreenShare(it, target = false) }
    }

    private fun settleScreenShare(result: MediaActionResult, target: Boolean) {
        _localMedia.update { media ->
            val current = media.screenShare
            media.copy(
                screenShare = when (result) {
                    MediaActionResult.Applied -> current.copy(actual = target)
                    is MediaActionResult.Failed -> MediaToggle(current.actual, current.actual, result.error)
                    MediaActionResult.Unsupported -> MediaToggle(current.actual, current.actual)
                },
            )
        }
    }

    override suspend fun setVideoEffect(effect: VideoEffect): MediaActionResult {
        val engine = connectedEngine() ?: return MediaActionResult.Failed(VoxeraError.NotConnected)
        return mediaMutex.withLock { engine.setVideoEffect(effect) }.also { result ->
            if (result == MediaActionResult.Applied) _localMedia.update { it.copy(videoEffect = effect) }
        }
    }

    override suspend fun selectAudioRoute(routeId: String): MediaActionResult {
        val engine = connectedEngine() ?: return MediaActionResult.Failed(VoxeraError.NotConnected)
        if (_localMedia.value.audioRoutes.none { it.id == routeId && it.isSelectable }) return MediaActionResult.Unsupported
        return mediaMutex.withLock {
            engine.selectAudioRoute(routeId).also { result ->
                if (result == MediaActionResult.Applied) _localMedia.update { it.copy(selectedAudioRouteId = routeId) }
            }
        }
    }

    private fun connectedEngine(): RtcEngine? = synchronized(lock) {
        val state = machine.state
        if (state is SessionState.Connected || state is SessionState.Reconnecting) connection?.engine else null
    }

    override suspend fun leave(reason: LeaveReason) {
        val endReason = when (reason) {
            LeaveReason.USER -> EndReason.USER_LEFT
            LeaveReason.HOST -> EndReason.HOST_LEFT
        }
        val transition = dispatch(SessionAction.LeaveRequested(endReason, clock.nowMs()))
        if (!transition.accepted) return
        val generation = transition.machine.generation
        // Failed에서 나가면 자원은 이미 정리되어 있다.
        if (transition.machine.state is SessionState.Ended) return
        synchronized(lock) { pendingJoin }?.cancel()
        withContext(NonCancellable) {
            releaseConnection(generation)
            releaseCallLock(generation)
            dispatch(SessionAction.Released(generation, clock.nowMs()))
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        scope.launch {
            try {
                leave(LeaveReason.HOST)
            } finally {
                scope.cancel()
            }
        }
    }
}

/** 엔진이 알려 준 실제 값으로 맞춘다. 사용자의 다른 의도가 반영 중이면 그 의도는 유지한다. */
private fun MediaToggle.settle(actual: Boolean): MediaToggle =
    if (isPending) copy(actual = actual) else MediaToggle(actual, actual, lastError)
