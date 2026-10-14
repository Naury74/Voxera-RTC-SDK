package com.naury.voxerartc.testkit

import com.naury.voxerartc.api.AudioRoute
import com.naury.voxerartc.api.AudioRouteType
import com.naury.voxerartc.api.CameraFacing
import com.naury.voxerartc.api.ConnectOptions
import com.naury.voxerartc.api.ConnectOutcome
import com.naury.voxerartc.api.LocalMediaState
import com.naury.voxerartc.api.MediaActionResult
import com.naury.voxerartc.api.MediaToggle
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.api.QualitySnapshot
import com.naury.voxerartc.api.RtcCredentials
import com.naury.voxerartc.api.RtcEngine
import com.naury.voxerartc.api.RtcEngineEvent
import com.naury.voxerartc.api.RtcEngineFactory
import com.naury.voxerartc.api.RtcEngineKind
import com.naury.voxerartc.api.ScreenShareConsent
import com.naury.voxerartc.api.EffectsAvailability
import com.naury.voxerartc.api.Reaction
import com.naury.voxerartc.api.VideoEffect
import com.naury.voxerartc.api.VideoSource
import com.naury.voxerartc.api.VideoTrackHandle
import com.naury.voxerartc.api.VoxeraError
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 네트워크 없이 동작하는 결정적 [RtcEngine]. 테스트에서 연결 결과·지연·미디어 실패를 정하고, 참가자·재연결 이벤트를
 * 직접 보낼 수 있다. 지연은 `delay`를 쓰므로 `kotlinx-coroutines-test`의 가상 시간으로 제어된다.
 *
 * 실제 미디어를 다루지 않으며 [kind]는 항상 [RtcEngineKind.SIMULATED]다.
 *
 * @param connectDelayMs [connect]가 결과를 돌려주기까지의 지연(ms).
 * @param mediaAckDelayMs 마이크·카메라·장치 조작이 반영되기까지의 지연(ms).
 * @param cameras 기기에 있는 카메라. 2개 미만이면 전환은 [MediaActionResult.Unsupported].
 * @param audioRoutes 선택할 수 있는 오디오 경로. 첫 번째가 기본 선택이다.
 */
public class FakeRtcEngine(
    private val local: Participant = Participant(id = "local", displayName = "You", isLocal = true),
    public var connectDelayMs: Long = 0,
    public var mediaAckDelayMs: Long = 0,
    private val cameras: List<CameraFacing> = listOf(CameraFacing.FRONT, CameraFacing.BACK),
    private val audioRoutes: List<AudioRoute> = DefaultAudioRoutes,
    private val effects: EffectsAvailability = EffectsAvailability.AVAILABLE,
) : RtcEngine {

    override val kind: RtcEngineKind = RtcEngineKind.SIMULATED

    private val _events = MutableSharedFlow<RtcEngineEvent>(extraBufferCapacity = 256)
    override val events: Flow<RtcEngineEvent> = _events.asSharedFlow()

    /** 차례로 꺼내 쓰는 연결 결과. `null`이면 성공, 비어 있으면 성공이다. */
    public val connectResults: ArrayDeque<VoxeraError?> = ArrayDeque()

    /** `true`면 [connect]가 끝나지 않고 취소될 때까지 기다린다. */
    public var hangOnConnect: Boolean = false

    /** `true`면 입장할 때 마이크를 열지 못한 것처럼 마이크 없이 연결된다. */
    public var failMicrophoneOnConnect: Boolean = false

    private val pendingMediaFailures = ArrayDeque<VoxeraError>()
    private val _receivedCredentials = mutableListOf<RtcCredentials>()
    private val _connectRequests = mutableListOf<ConnectOptions>()
    private val _microphoneRequests = mutableListOf<Boolean>()
    private val _cameraRequests = mutableListOf<Boolean>()
    private var cameraTrackSerial = 0

    private var localState = local
    private var media = LocalMediaState(
        cameraFacing = cameras.firstOrNull() ?: CameraFacing.FRONT,
        canSwitchCamera = cameras.size > 1,
        audioRoutes = audioRoutes,
        selectedAudioRouteId = audioRoutes.firstOrNull()?.id,
        effectsAvailability = effects,
    )

    /** [connect]에 전달된 자격 증명. */
    public val receivedCredentials: List<RtcCredentials> get() = _receivedCredentials.toList()
    public val connectRequests: List<ConnectOptions> get() = _connectRequests.toList()

    /** 엔진에 실제로 전달된 마이크 요청 순서. */
    public val microphoneRequests: List<Boolean> get() = _microphoneRequests.toList()
    public val cameraRequests: List<Boolean> get() = _cameraRequests.toList()

    public var releaseCount: Int = 0
        private set

    public val isReleased: Boolean get() = releaseCount > 0

    /** 다음 미디어 조작 하나를 [error]로 실패시킨다. */
    public fun failNextMediaAction(error: VoxeraError) {
        pendingMediaFailures.addLast(error)
    }

    override suspend fun connect(credentials: RtcCredentials, options: ConnectOptions): ConnectOutcome {
        _receivedCredentials += credentials
        _connectRequests += options
        if (hangOnConnect) delay(Long.MAX_VALUE)
        delay(connectDelayMs)
        connectResults.removeFirstOrNull()?.let { return ConnectOutcome.Failed(it) }
        val microphone = options.startWithMicrophone && !failMicrophoneOnConnect
        localState = localState.copy(
            isMicrophoneEnabled = microphone,
            camera = if (options.startWithCamera) nextCameraHandle() else null,
        )
        media = media.copy(
            microphone = MediaToggle(microphone, microphone),
            camera = MediaToggle(options.startWithCamera, options.startWithCamera),
        )
        _events.tryEmit(RtcEngineEvent.LocalMediaChanged(media))
        return ConnectOutcome.Connected(localState)
    }

    override suspend fun setMicrophoneEnabled(enabled: Boolean): MediaActionResult {
        _microphoneRequests += enabled
        delay(mediaAckDelayMs)
        pendingMediaFailures.removeFirstOrNull()?.let { return MediaActionResult.Failed(it) }
        localState = localState.copy(isMicrophoneEnabled = enabled, isSpeaking = localState.isSpeaking && enabled)
        media = media.copy(microphone = MediaToggle(enabled, enabled))
        _events.tryEmit(RtcEngineEvent.ParticipantUpserted(localState))
        return MediaActionResult.Applied
    }

    override suspend fun setCameraEnabled(enabled: Boolean): MediaActionResult {
        _cameraRequests += enabled
        delay(mediaAckDelayMs)
        pendingMediaFailures.removeFirstOrNull()?.let { return MediaActionResult.Failed(it) }
        if (enabled && cameras.isEmpty()) return MediaActionResult.Unsupported
        localState = localState.copy(camera = if (enabled) nextCameraHandle() else null)
        media = media.copy(camera = MediaToggle(enabled, enabled))
        _events.tryEmit(RtcEngineEvent.ParticipantUpserted(localState))
        return MediaActionResult.Applied
    }

    override suspend fun switchCamera(): MediaActionResult {
        if (cameras.size < 2) return MediaActionResult.Unsupported
        delay(mediaAckDelayMs)
        pendingMediaFailures.removeFirstOrNull()?.let { return MediaActionResult.Failed(it) }
        val next = cameras[(cameras.indexOf(media.cameraFacing) + 1) % cameras.size]
        media = media.copy(cameraFacing = next)
        _events.tryEmit(RtcEngineEvent.LocalMediaChanged(media))
        return MediaActionResult.Applied
    }

    override suspend fun selectAudioRoute(routeId: String): MediaActionResult {
        if (audioRoutes.none { it.id == routeId && it.isSelectable }) return MediaActionResult.Unsupported
        delay(mediaAckDelayMs)
        pendingMediaFailures.removeFirstOrNull()?.let { return MediaActionResult.Failed(it) }
        media = media.copy(selectedAudioRouteId = routeId)
        _events.tryEmit(RtcEngineEvent.LocalMediaChanged(media))
        return MediaActionResult.Applied
    }

    override suspend fun startScreenShare(consent: ScreenShareConsent): MediaActionResult {
        delay(mediaAckDelayMs)
        pendingMediaFailures.removeFirstOrNull()?.let { return MediaActionResult.Failed(it) }
        localState = localState.copy(screenShare = VideoTrackHandle(localState.id, "local-screen-${++cameraTrackSerial}", VideoSource.SCREEN_SHARE))
        media = media.copy(screenShare = MediaToggle(desired = true, actual = true))
        _events.tryEmit(RtcEngineEvent.ParticipantUpserted(localState))
        _events.tryEmit(RtcEngineEvent.LocalMediaChanged(media))
        return MediaActionResult.Applied
    }

    override suspend fun stopScreenShare(): MediaActionResult {
        delay(mediaAckDelayMs)
        simulateScreenShareStopped()
        return MediaActionResult.Applied
    }

    override suspend fun setVideoEffect(effect: VideoEffect): MediaActionResult {
        if (media.effectsAvailability != EffectsAvailability.AVAILABLE) return MediaActionResult.Unsupported
        delay(mediaAckDelayMs)
        media = media.copy(videoEffect = effect)
        _events.tryEmit(RtcEngineEvent.LocalMediaChanged(media))
        return MediaActionResult.Applied
    }

    /** 처리 시간 초과로 엔진이 효과를 끈 상황을 보낸다. */
    private val _sentReactions = mutableListOf<Reaction>()

    /** [sendReaction]으로 보낸 반응 순서. */
    public val sentReactions: List<Reaction> get() = synchronized(_sentReactions) { _sentReactions.toList() }

    override suspend fun sendReaction(reaction: Reaction): MediaActionResult {
        synchronized(_sentReactions) { _sentReactions += reaction }
        return MediaActionResult.Applied
    }

    /** 원격 참가자 [participantId]가 반응을 보낸 것처럼 이벤트를 낸다. */
    public fun simulateReaction(participantId: String, reaction: Reaction) {
        _events.tryEmit(RtcEngineEvent.ReactionReceived(participantId, reaction))
    }

    public fun simulateEffectsThrottled() {
        media = media.copy(videoEffect = VideoEffect.None, effectsAvailability = EffectsAvailability.DISABLED_FOR_PERFORMANCE)
        _events.tryEmit(RtcEngineEvent.LocalMediaChanged(media))
    }

    /** 사용자가 시스템 알림에서 공유를 멈춘 것처럼 엔진 쪽에서 공유를 끝낸다. */
    public fun simulateScreenShareStopped() {
        localState = localState.copy(screenShare = null)
        media = media.copy(screenShare = MediaToggle.Off)
        _events.tryEmit(RtcEngineEvent.ParticipantUpserted(localState))
        _events.tryEmit(RtcEngineEvent.LocalMediaChanged(media))
    }

    override suspend fun release() {
        releaseCount++
    }

    /** 원격 참가자를 들여보내거나 상태를 바꾼다. */
    public fun upsertRemote(participant: Participant) {
        _events.tryEmit(RtcEngineEvent.ParticipantUpserted(participant.copy(isLocal = false)))
    }

    public fun removeRemote(participantId: String) {
        _events.tryEmit(RtcEngineEvent.ParticipantLeft(participantId))
    }

    public fun simulateReconnecting() {
        _events.tryEmit(RtcEngineEvent.Reconnecting)
    }

    public fun simulateReconnected() {
        _events.tryEmit(RtcEngineEvent.Reconnected)
    }

    /** 엔진 쪽 종료. [error]가 `null`이면 상대·서버의 정상 종료다. */
    public fun simulateDisconnect(error: VoxeraError?) {
        _events.tryEmit(RtcEngineEvent.Disconnected(error))
    }

    public fun emitQuality(snapshot: QualitySnapshot) {
        _events.tryEmit(RtcEngineEvent.QualitySampled(snapshot))
    }

    /** 시스템이 카메라를 끊은 경우처럼 엔진 쪽에서 로컬 미디어가 바뀐 상황을 보낸다. */
    public fun simulateLocalMedia(transform: (LocalMediaState) -> LocalMediaState) {
        media = transform(media)
        _events.tryEmit(RtcEngineEvent.LocalMediaChanged(media))
    }

    private fun nextCameraHandle() = VideoTrackHandle(localState.id, "local-camera-${++cameraTrackSerial}", VideoSource.CAMERA)

    public companion object {
        /** 이어피스·스피커·블루투스가 있는 기본 경로 목록. */
        public val DefaultAudioRoutes: List<AudioRoute> = listOf(
            AudioRoute("earpiece", "Phone", AudioRouteType.EARPIECE),
            AudioRoute("speaker", "Speaker", AudioRouteType.SPEAKER),
            AudioRoute("bluetooth", "Bluetooth headset", AudioRouteType.BLUETOOTH),
        )
    }
}

/** 연결마다 [newEngine]으로 새 [FakeRtcEngine]을 만들고 [created]에 기록한다. */
public class FakeRtcEngineFactory(
    private val newEngine: () -> FakeRtcEngine = { FakeRtcEngine() },
) : RtcEngineFactory {
    private val _created = mutableListOf<FakeRtcEngine>()

    override val kind: RtcEngineKind = RtcEngineKind.SIMULATED

    public val created: List<FakeRtcEngine> get() = synchronized(_created) { _created.toList() }

    /** 마지막으로 만든 엔진. 아직 없으면 예외. */
    public val last: FakeRtcEngine get() = created.last()

    override fun create(): FakeRtcEngine = newEngine().also { synchronized(_created) { _created += it } }
}
