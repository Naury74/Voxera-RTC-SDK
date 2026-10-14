package com.naury.voxerartc.invitation

import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.JoinResult
import com.naury.voxerartc.api.LeaveReason
import com.naury.voxerartc.api.MonotonicClock
import com.naury.voxerartc.api.SessionState
import com.naury.voxerartc.core.VoxeraSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

public enum class CallDirection { INCOMING, OUTGOING }

/** 통화가 끝난 이유. */
public enum class CallEndReason {
    /** 연결된 뒤 정상 종료. */
    COMPLETED,
    DECLINED,

    /** 수신자가 받지 않음(벨이 울리다 끝남, 발신자 취소 포함). */
    MISSED,

    /** 상대가 이미 통화 중. */
    BUSY,

    /** 발신자가 취소. */
    CANCELLED,

    /** 서버나 로컬 제한 시간이 지남. */
    EXPIRED,

    /** 같은 계정의 다른 기기에서 받음. */
    ANSWERED_ELSEWHERE,

    /** 서버 오류 또는 수락 뒤 미디어 연결 실패. */
    FAILED,
}

/**
 * Call Mode 상태. 문서의 `Draft → Dialing → Ringing → Accepted → ConnectingMedia → Active → Ended`를 따르며,
 * Declined·Missed·Busy·Cancelled·Expired·Failed는 [Ended]의 [CallEndReason]으로 나타낸다.
 */
public sealed interface CallState {
    public data object Idle : CallState

    /** 서버에 초대를 만드는 중. */
    public data class Dialing(val clientRequestId: String, val mode: CallMode) : CallState

    /** 벨이 울리는 중. [deadlineMs]가 지나면 로컬에서도 끝낸다([MonotonicClock] 기준). */
    public data class Ringing(val call: CallInfo, val direction: CallDirection, val deadlineMs: Long) : CallState

    /** 수락되어 룸 미디어에 연결하는 중. */
    public data class ConnectingMedia(val call: CallInfo, val direction: CallDirection) : CallState

    /** 미디어가 연결됨. 화면은 [VoxeraSession] 상태로 그린다. */
    public data class Active(val call: CallInfo, val direction: CallDirection) : CallState

    public data class Ended(val call: CallInfo?, val reason: CallEndReason) : CallState
}

/** 조작 결과. */
public enum class CallActionResult { APPLIED, NOT_ALLOWED_IN_STATE }

/**
 * @property ringTimeoutMs 서버의 EXPIRED가 오지 않을 때를 대비한 로컬 벨 제한 시간(ms, 1 이상).
 */
public data class CallPolicy(val ringTimeoutMs: Long = 45_000) {
    init {
        require(ringTimeoutMs > 0) { "ringTimeoutMs must be positive" }
    }
}

/**
 * 초대 신호([InvitationGateway])와 룸 미디어([VoxeraSession])를 묶는다. Room Mode만 쓰는 앱은 필요 없다.
 *
 * - 같은 `callId`의 이벤트는 revision이 커질 때만 반영하므로 중복·역순 푸시가 무해하다.
 * - 수락·취소가 겹치면 서버의 더 큰 revision을 따르고, 최종 상태가 종료면 룸에서 나간다.
 * - 이미 통화 중에 새 초대가 오면 `BUSY`로 거절한다.
 * - 수락 뒤 미디어 연결이 실패하면 `hangup`으로 상대에게 알린다.
 *
 * [start]를 불러야 이벤트를 받기 시작하고, 다 쓰면 [close]한다. [close]는 세션을 닫지 않는다.
 */
public class CallCoordinator internal constructor(
    private val gateway: InvitationGateway,
    private val session: VoxeraSession,
    private val policy: CallPolicy,
    private val clock: MonotonicClock,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {

    public constructor(gateway: InvitationGateway, session: VoxeraSession, policy: CallPolicy = CallPolicy()) :
        this(gateway, session, policy, MonotonicClock.System, Dispatchers.Default)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutex = Mutex()
    private val revisions = HashMap<String, Long>()
    private val earlyEvents = HashMap<String, InvitationEvent>()
    private var acceptedHere = false
    private var ringTimer: Job? = null

    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    public val state: StateFlow<CallState> = _state.asStateFlow()

    /**
     * 초대 이벤트와 세션 종료를 관찰하기 시작한다. 한 번만 부른다. 돌아올 때 이미 구독되어 있으므로, 그 뒤에 들어온 초대는
     * 유실되지 않는다(호스트의 Flow가 구독 전 이벤트를 버리는 SharedFlow여도).
     */
    public fun start() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            gateway.observeInvitations().collect { event -> mutex.withLock { onEvent(event) } }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            session.state.drop(1).collect { sessionState ->
                if (sessionState is SessionState.Ended || sessionState is SessionState.Failed) onSessionFinished(sessionState)
            }
        }
    }

    /** 발신한다. 이미 통화 중이거나 벨이 울리는 중이면 [CallActionResult.NOT_ALLOWED_IN_STATE]. */
    public suspend fun call(recipientIds: List<String>, mode: CallMode): CallActionResult {
        val requestId = UUID.randomUUID().toString()
        mutex.withLock {
            if (!isIdle() || sessionBusy()) return CallActionResult.NOT_ALLOWED_IN_STATE
            earlyEvents.clear()
            _state.value = CallState.Dialing(requestId, mode)
        }
        val result = gateway.initiate(CallInviteRequest(recipientIds, mode, requestId))
        mutex.withLock {
            val dialing = _state.value as? CallState.Dialing
            if (dialing?.clientRequestId != requestId) return CallActionResult.APPLIED
            when (result) {
                is InviteResult.Rejected -> _state.value = CallState.Ended(
                    null,
                    if (result.reason == InviteFailure.BUSY) CallEndReason.BUSY else CallEndReason.FAILED,
                )
                is InviteResult.Created -> {
                    revisions[result.call.callId] = result.revision
                    ring(result.call, CallDirection.OUTGOING)
                    // initiate가 돌아오기 전에 도착한 수락·거절을 이어서 반영한다.
                    earlyEvents.remove(result.call.callId)?.let { onEvent(it) }
                }
            }
            earlyEvents.clear()
        }
        return CallActionResult.APPLIED
    }

    /** 걸려 온 통화를 받는다. */
    public suspend fun accept(): CallActionResult {
        val call = mutex.withLock {
            val ringing = _state.value as? CallState.Ringing
            if (ringing?.direction != CallDirection.INCOMING) return CallActionResult.NOT_ALLOWED_IN_STATE
            acceptedHere = true
            ringTimer?.cancel()
            _state.value = CallState.ConnectingMedia(ringing.call, CallDirection.INCOMING)
            ringing.call
        }
        when (val decision = gateway.accept(call.callId)) {
            is InvitationDecision.Applied -> mutex.withLock { revisions.merge(call.callId, decision.revision, ::maxOf) }
            is InvitationDecision.Conflict -> {
                mutex.withLock { if (isConnecting(call)) finish(call, endReasonFor(decision.current, CallDirection.INCOMING)) }
                return CallActionResult.APPLIED
            }
            InvitationDecision.Unavailable -> {
                mutex.withLock { if (isConnecting(call)) finish(call, CallEndReason.FAILED) }
                return CallActionResult.APPLIED
            }
        }
        joinMedia(call, CallDirection.INCOMING)
        return CallActionResult.APPLIED
    }

    /** 걸려 온 통화를 거절한다. */
    public suspend fun decline(): CallActionResult {
        val call = mutex.withLock {
            val ringing = _state.value as? CallState.Ringing
            if (ringing?.direction != CallDirection.INCOMING) return CallActionResult.NOT_ALLOWED_IN_STATE
            finish(ringing.call, CallEndReason.DECLINED)
            ringing.call
        }
        gateway.decline(call.callId, DeclineReason.USER)
        return CallActionResult.APPLIED
    }

    /** 상대가 받기 전에 발신을 취소한다. */
    public suspend fun cancel(): CallActionResult {
        val call = mutex.withLock {
            when (val current = _state.value) {
                is CallState.Dialing -> {
                    _state.value = CallState.Ended(null, CallEndReason.CANCELLED)
                    return CallActionResult.APPLIED
                }
                is CallState.Ringing -> if (current.direction == CallDirection.OUTGOING) {
                    finish(current.call, CallEndReason.CANCELLED)
                    current.call
                } else return CallActionResult.NOT_ALLOWED_IN_STATE
                else -> return CallActionResult.NOT_ALLOWED_IN_STATE
            }
        }
        gateway.cancel(call.callId)
        return CallActionResult.APPLIED
    }

    /** 연결 중이거나 연결된 통화를 끝낸다. */
    public suspend fun hangup(): CallActionResult {
        val call = mutex.withLock {
            val call = when (val current = _state.value) {
                is CallState.ConnectingMedia -> current.call
                is CallState.Active -> current.call
                else -> return CallActionResult.NOT_ALLOWED_IN_STATE
            }
            finish(call, CallEndReason.COMPLETED)
            call
        }
        session.leave(LeaveReason.USER)
        gateway.hangup(call.callId)
        return CallActionResult.APPLIED
    }

    private suspend fun joinMedia(call: CallInfo, direction: CallDirection) {
        val result = session.join(call.roomId, call.mode)
        val failed = mutex.withLock {
            val stillWanted = isConnecting(call)
            when {
                !stillWanted -> {
                    // 미디어 연결 중에 서버가 통화를 끝냄. 룸에 남지 않는다.
                    if (result is JoinResult.Connected) scope.launch { session.leave(LeaveReason.HOST) }
                    false
                }
                result is JoinResult.Connected -> {
                    _state.value = CallState.Active(call, direction)
                    false
                }
                else -> {
                    finish(call, CallEndReason.FAILED)
                    true
                }
            }
        }
        if (failed) gateway.hangup(call.callId)
    }

    /** lock 안에서 부른다. */
    private fun onEvent(event: InvitationEvent) {
        val last = revisions[event.callId]
        if (last != null && event.revision <= last) return
        val current = _state.value
        val currentCall = current.callOrNull()
        if (current is CallState.Dialing) {
            // 아직 callId를 모른다. initiate 결과가 오면 이어서 반영한다.
            if (event.type != InvitationEventType.INVITED) {
                earlyEvents.merge(event.callId, event) { a, b -> if (b.revision > a.revision) b else a }
                return
            }
        }
        revisions[event.callId] = event.revision

        if (currentCall == null || currentCall.callId != event.callId) {
            if (event.type == InvitationEventType.INVITED) onInvited(event)
            return
        }
        when (current) {
            is CallState.Ringing -> onRingingEvent(current, event.type)
            is CallState.ConnectingMedia, is CallState.Active -> if (event.type.isTerminal()) {
                val reason = if (current is CallState.Active) CallEndReason.COMPLETED else endReasonFor(event.type, current.direction())
                finish(currentCall, reason)
                scope.launch { session.leave(LeaveReason.HOST) }
            }
            else -> Unit
        }
    }

    private fun onInvited(event: InvitationEvent) {
        val call = event.call ?: return
        if (!isIdle() || sessionBusy()) {
            scope.launch { gateway.decline(call.callId, DeclineReason.BUSY) }
            return
        }
        acceptedHere = false
        ring(call, CallDirection.INCOMING)
    }

    private fun onRingingEvent(ringing: CallState.Ringing, type: InvitationEventType) {
        when (ringing.direction) {
            CallDirection.INCOMING -> when (type) {
                InvitationEventType.ACCEPTED -> if (!acceptedHere) finish(ringing.call, CallEndReason.ANSWERED_ELSEWHERE)
                InvitationEventType.INVITED -> Unit
                else -> finish(ringing.call, endReasonFor(type, CallDirection.INCOMING))
            }
            CallDirection.OUTGOING -> when (type) {
                InvitationEventType.ACCEPTED -> {
                    ringTimer?.cancel()
                    _state.value = CallState.ConnectingMedia(ringing.call, CallDirection.OUTGOING)
                    scope.launch { joinMedia(ringing.call, CallDirection.OUTGOING) }
                }
                InvitationEventType.INVITED -> Unit
                else -> finish(ringing.call, endReasonFor(type, CallDirection.OUTGOING))
            }
        }
    }

    private fun ring(call: CallInfo, direction: CallDirection) {
        val deadline = clock.nowMs() + policy.ringTimeoutMs
        _state.value = CallState.Ringing(call, direction, deadline)
        ringTimer?.cancel()
        ringTimer = scope.launch {
            delay(policy.ringTimeoutMs)
            val timedOut = mutex.withLock {
                val ringing = _state.value as? CallState.Ringing
                if (ringing?.call?.callId != call.callId) return@withLock false
                finish(call, if (direction == CallDirection.INCOMING) CallEndReason.MISSED else CallEndReason.EXPIRED)
                true
            }
            if (timedOut) {
                if (direction == CallDirection.INCOMING) gateway.decline(call.callId, DeclineReason.TIMEOUT) else gateway.cancel(call.callId)
            }
        }
    }

    private suspend fun onSessionFinished(sessionState: SessionState) {
        val call = mutex.withLock {
            val active = _state.value as? CallState.Active ?: return
            finish(active.call, if (sessionState is SessionState.Failed) CallEndReason.FAILED else CallEndReason.COMPLETED)
            active.call
        }
        gateway.hangup(call.callId)
    }

    /** lock 안에서 부른다. */
    private fun finish(call: CallInfo, reason: CallEndReason) {
        ringTimer?.cancel()
        _state.value = CallState.Ended(call, reason)
    }

    private fun isIdle(): Boolean = _state.value.let { it is CallState.Idle || it is CallState.Ended }

    private fun isConnecting(call: CallInfo): Boolean =
        (_state.value as? CallState.ConnectingMedia)?.call?.callId == call.callId

    private fun sessionBusy(): Boolean = when (session.state.value) {
        SessionState.Idle, is SessionState.Ended, is SessionState.Failed -> false
        else -> true
    }

    override fun close() {
        scope.cancel()
    }
}

private fun CallState.callOrNull(): CallInfo? = when (this) {
    is CallState.Ringing -> call
    is CallState.ConnectingMedia -> call
    is CallState.Active -> call
    else -> null
}

private fun CallState.direction(): CallDirection = when (this) {
    is CallState.Ringing -> direction
    is CallState.ConnectingMedia -> direction
    is CallState.Active -> direction
    else -> CallDirection.INCOMING
}

private fun InvitationEventType.isTerminal(): Boolean = this != InvitationEventType.INVITED && this != InvitationEventType.ACCEPTED

/** 서버 이벤트가 끝낸 통화의 이유. 받는 쪽에서는 발신자 취소·만료가 모두 부재중이다. */
internal fun endReasonFor(type: InvitationEventType, direction: CallDirection): CallEndReason = when (type) {
    InvitationEventType.DECLINED -> CallEndReason.DECLINED
    InvitationEventType.BUSY -> CallEndReason.BUSY
    InvitationEventType.CANCELLED -> if (direction == CallDirection.INCOMING) CallEndReason.MISSED else CallEndReason.CANCELLED
    InvitationEventType.EXPIRED -> if (direction == CallDirection.INCOMING) CallEndReason.MISSED else CallEndReason.EXPIRED
    InvitationEventType.ENDED -> if (direction == CallDirection.INCOMING) CallEndReason.MISSED else CallEndReason.CANCELLED
    InvitationEventType.ACCEPTED -> CallEndReason.ANSWERED_ELSEWHERE
    InvitationEventType.INVITED -> CallEndReason.FAILED
}
