package com.naury.voxerartc.invitation

import com.naury.voxerartc.api.CallMode
import kotlinx.coroutines.flow.Flow

/**
 * 호스트가 구현하는 초대 신호 계약. 발신·수신·벨·부재중은 호스트 서버와 푸시(FCM 등)가 처리하며 Voxera는 서버를 두지 않는다.
 *
 * 모든 결과는 호스트 서버가 정한 `callId`와 `revision`을 기준으로 한다. 푸시 payload에 접근 토큰·API Secret을 넣지 않는다.
 * 룸 토큰은 수락 뒤 세션의 `RtcCredentialsProvider`가 따로 받는다.
 */
public interface InvitationGateway {
    /** 발신. [CallInviteRequest.clientRequestId]가 같으면 서버는 같은 초대를 돌려줘야 한다(중복 발신 방지). */
    public suspend fun initiate(request: CallInviteRequest): InviteResult

    public suspend fun accept(callId: String): InvitationDecision

    public suspend fun decline(callId: String, reason: DeclineReason): InvitationDecision

    /** 발신자가 상대가 받기 전에 취소. */
    public suspend fun cancel(callId: String): InvitationDecision

    /** 연결된 통화를 끝내거나, 수락 뒤 미디어 연결에 실패했음을 알린다. */
    public suspend fun hangup(callId: String): InvitationDecision

    /** 서버·푸시로 받은 초대 이벤트. 중복·역순 도착을 가정하며 [CallCoordinator]가 revision으로 정리한다. */
    public fun observeInvitations(): Flow<InvitationEvent>
}

/**
 * @property recipientIds 호스트 서비스의 사용자 id. SDK는 해석하지 않는다.
 * @property clientRequestId 발신 중복 방지용 UUID.
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

/** 수락·거절·취소·종료 요청의 결과. */
public sealed interface InvitationDecision {
    /** 서버가 요청을 반영했다. */
    public data class Applied(val revision: Long) : InvitationDecision

    /** 서버의 최종 상태가 이미 달라 반영하지 않았다(예: 수락하려는데 이미 취소됨). */
    public data class Conflict(val current: InvitationEventType, val revision: Long) : InvitationDecision

    public data object Unavailable : InvitationDecision
}

public enum class DeclineReason { USER, BUSY, TIMEOUT }

/** 통화 정보. 이름은 호스트가 정한 미리보기이며 비밀 값을 담지 않는다. */
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
 * 호스트 초대 이벤트. [revision]은 같은 [callId] 안에서 서버가 단조 증가시키는 값이다.
 *
 * @property occurredAt 서버 시각(ISO-8601). 순서 판단에는 쓰지 않고 [revision]만 쓴다.
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
