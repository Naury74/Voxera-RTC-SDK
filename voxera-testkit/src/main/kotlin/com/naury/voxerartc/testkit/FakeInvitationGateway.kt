package com.naury.voxerartc.testkit

import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.invitation.CallInfo
import com.naury.voxerartc.invitation.CallInviteRequest
import com.naury.voxerartc.invitation.DeclineReason
import com.naury.voxerartc.invitation.InvitationDecision
import com.naury.voxerartc.invitation.InvitationEvent
import com.naury.voxerartc.invitation.InvitationEventType
import com.naury.voxerartc.invitation.InvitationGateway
import com.naury.voxerartc.invitation.InviteResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * 호스트 서버를 흉내 내는 [InvitationGateway]. 테스트와 오프라인 데모에서 초대 이벤트를 직접 보내고, 받은 요청을 기록한다.
 *
 * revision은 이 객체가 1씩 늘려 붙인다. 실제 서버처럼 중복·역순을 만들려면 [deliver]에 직접 이벤트를 넣는다.
 *
 * @param answer 발신 뒤 상대 응답을 흉내 낼 때 쓴다. `null`이면 아무 응답도 보내지 않는다.
 */
public class FakeInvitationGateway(
    private val scope: CoroutineScope? = null,
    public var answer: Answer? = null,
) : InvitationGateway {

    /** 발신 뒤 [delayMs] 지나 [type] 이벤트를 보낸다. */
    public data class Answer(val type: InvitationEventType, val delayMs: Long)

    private val events = MutableSharedFlow<InvitationEvent>(extraBufferCapacity = 64)
    private val revision = AtomicLong(0)
    private val eventSerial = AtomicLong(0)
    private val _requests = mutableListOf<String>()

    /** 받은 요청 기록. 예: `initiate`, `accept:call-1`, `decline:call-1:BUSY`, `hangup:call-1`. */
    public val requests: List<String> get() = synchronized(_requests) { _requests.toList() }

    /** 다음 [initiate]가 돌려줄 결과. 기본은 `call-1` 생성. */
    public var initiateResult: (CallInviteRequest) -> InviteResult = { request ->
        InviteResult.Created(
            CallInfo("call-1", "room-call-1", request.mode, request.recipientIds.first(), request.recipientIds.first()),
            nextRevision(),
        )
    }

    /** 수락·거절·취소·종료 결과. 기본은 모두 반영. */
    public var decision: (action: String, callId: String) -> InvitationDecision = { _, _ -> InvitationDecision.Applied(nextRevision()) }

    private fun record(entry: String) {
        synchronized(_requests) { _requests += entry }
    }

    public fun nextRevision(): Long = revision.incrementAndGet()

    override suspend fun initiate(request: CallInviteRequest): InviteResult {
        record("initiate")
        val result = initiateResult(request)
        val answer = answer
        if (result is InviteResult.Created && answer != null && scope != null) {
            scope.launch {
                delay(answer.delayMs)
                deliver(event(result.call.callId, answer.type))
            }
        }
        return result
    }

    override suspend fun accept(callId: String): InvitationDecision = decision("accept", callId).also { record("accept:$callId") }

    override suspend fun decline(callId: String, reason: DeclineReason): InvitationDecision =
        decision("decline", callId).also { record("decline:$callId:$reason") }

    override suspend fun cancel(callId: String): InvitationDecision = decision("cancel", callId).also { record("cancel:$callId") }

    override suspend fun hangup(callId: String): InvitationDecision = decision("hangup", callId).also { record("hangup:$callId") }

    override fun observeInvitations(): Flow<InvitationEvent> = events.asSharedFlow()

    /** 이벤트를 그대로 보낸다(중복·역순 시험용). */
    public fun deliver(event: InvitationEvent) {
        events.tryEmit(event)
    }

    /** 새 revision을 붙인 이벤트. */
    public fun event(callId: String, type: InvitationEventType, call: CallInfo? = null, revision: Long = nextRevision()): InvitationEvent =
        InvitationEvent("evt-${eventSerial.incrementAndGet()}", callId, revision, type, "2026-10-14T00:00:00Z", call)

    /** [callerName]에게서 전화가 걸려 온 것처럼 INVITED를 보낸다. */
    public fun ring(callId: String, callerName: String, mode: CallMode = CallMode.VOICE, roomId: String = "room-$callId"): CallInfo {
        val call = CallInfo(callId, roomId, mode, "user-$callId", callerName)
        deliver(event(callId, InvitationEventType.INVITED, call))
        return call
    }
}
