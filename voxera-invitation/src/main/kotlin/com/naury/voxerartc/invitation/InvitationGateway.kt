package com.naury.voxerartc.invitation

import com.naury.voxerartc.api.CallMode
import kotlinx.coroutines.flow.Flow

/**
 * 호스트가 구현하는 초대 신호 인터페이스. 발신, 수신, 벨, 부재중 처리는 호스트 서버와 푸시(FCM 등)가 맡고, Voxera는 따로 서버를 두지 않는다.
 *
 * 모든 결과는 호스트 서버가 정한 `callId`와 `revision`을 기준으로 한다. 푸시 payload에는 접근 토큰이나 API Secret을 넣지 않는다.
 * 룸 토큰은 수락한 뒤 세션의 `RtcCredentialsProvider`가 따로 받아 온다.
 */
public interface InvitationGateway {
    /** 발신한다. 중복 발신을 막기 위해 서버는 [CallInviteRequest.clientRequestId]가 같은 요청에 같은 초대를 돌려줘야 한다. */
    public suspend fun initiate(request: CallInviteRequest): InviteResult

    public suspend fun accept(callId: String): InvitationDecision

    public suspend fun decline(callId: String, reason: DeclineReason): InvitationDecision

    /** 상대가 받기 전에 발신자가 취소한다. */
    public suspend fun cancel(callId: String): InvitationDecision

    /** 연결된 통화를 끝내거나, 수락 뒤 미디어 연결에 실패했음을 알린다. */
    public suspend fun hangup(callId: String): InvitationDecision

    /** 서버나 푸시로 받은 초대 이벤트. 중복되거나 순서가 바뀌어 올 수 있으며, [CallCoordinator]가 revision으로 정리한다. */
    public fun observeInvitations(): Flow<InvitationEvent>
}

/**
 * @property recipientIds 호스트 서비스의 사용자 id. SDK는 해석하지 않는다.
 * @property clientRequestId 중복 발신을 막는 UUID.
 */
public data class CallInviteRequest(
    val recipientIds: List<String>,
    val mode: CallMode,
    val clientRequestId: String,
)

public sealed interface InviteResult {
    /** 서버가 초대를 만들었다. [CallInfo.roomId]는 상대가 수락하면 들어갈 룸이다. */
    public data class Created(val call: CallInfo, val revision: Long) : InviteResult

    public data class Rejected(val reason: InviteFailure) : InviteResult
}

public enum class InviteFailure {
    /** 상대가 이미 다른 통화 중. */
    BUSY,

    /** 상대를 찾을 수 없거나 호출할 권한이 없음. */
    NOT_ALLOWED,

    /** 호스트 서버·망 문제. */
    UNAVAILABLE,
}

/** 수락, 거절, 취소, 종료 요청의 결과. */
public sealed interface InvitationDecision {
    /** 서버가 요청을 반영했다. */
    public data class Applied(val revision: Long) : InvitationDecision

    /** 서버의 최종 상태가 이미 달라 반영하지 않았다(예: 수락하려는데 이미 취소됨). */
    public data class Conflict(val current: InvitationEventType, val revision: Long) : InvitationDecision

    public data object Unavailable : InvitationDecision
}

public enum class DeclineReason { USER, BUSY, TIMEOUT }

/** 통화 정보. 이름은 호스트가 정한 미리보기용 값이며 비밀 값은 담지 않는다. */
public data class CallInfo(
    val callId: String,
    val roomId: String,
    val mode: CallMode,
    val peerId: String,
    val peerDisplayName: String,
)

/** `contracts/invitation-events.schema.json`의 `type`. */
public enum class InvitationEventType { INVITED, ACCEPTED, DECLINED, CANCELLED, EXPIRED, ENDED, BUSY }

/**
 * 호스트 서버가 보낸 초대 이벤트. [revision]은 같은 [callId] 안에서 서버가 단조 증가시키는 값이다.
 *
 * @property occurredAt 서버 시각(ISO-8601). 순서는 이 값이 아니라 [revision]으로만 판단한다.
 * @property call 이 이벤트로 알 수 있는 통화 정보. `INVITED`에는 반드시 있어야 한다.
 */
public data class InvitationEvent(
    val eventId: String,
    val callId: String,
    val revision: Long,
    val type: InvitationEventType,
    val occurredAt: String,
    val call: CallInfo? = null,
)
