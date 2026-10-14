package com.naury.voxerartc.invitation

import com.naury.voxerartc.api.CallMode

/** 푸시 payload 해석 결과. */
public sealed interface PayloadParseResult {
    public data class Parsed(val event: InvitationEvent) : PayloadParseResult

    /** 필수 키가 없거나 값이 맞지 않음. [field]는 문제 키 이름이다. */
    public data class Invalid(val field: String) : PayloadParseResult

    /** 토큰·비밀 값으로 보이는 키가 들어 있음. 호스트 서버가 payload에서 빼야 한다. 값은 읽지 않는다. */
    public data class ContainsSecret(val field: String) : PayloadParseResult
}

/**
 * FCM 같은 푸시의 문자열 맵을 [InvitationEvent]로 바꾼다. 키는 `contracts/invitation-events.schema.json`을 따른다
 * (`event_id`, `call_id`, `revision`, `type`, `occurred_at`, 선택 `room_id`, `recipient_id`, `mode`, `caller_id`, `caller_name`).
 *
 * payload에는 접근 토큰이나 API Secret을 넣지 않는다. 그런 키가 보이면 [PayloadParseResult.ContainsSecret]로 거부한다.
 */
public object InvitationPayloads {

    private val forbidden = listOf("token", "secret", "password", "api_key", "apikey", "authorization", "jwt")

    public fun parse(data: Map<String, String>): PayloadParseResult {
        data.keys.firstOrNull { key -> forbidden.any { it in key.lowercase() } }?.let { return PayloadParseResult.ContainsSecret(it) }
        fun required(key: String): String? = data[key]?.trim()?.takeIf { it.isNotEmpty() }
        val eventId = required("event_id") ?: return PayloadParseResult.Invalid("event_id")
        val callId = required("call_id") ?: return PayloadParseResult.Invalid("call_id")
        val revision = required("revision")?.toLongOrNull()?.takeIf { it >= 0 } ?: return PayloadParseResult.Invalid("revision")
        val type = required("type")?.let { raw -> InvitationEventType.entries.firstOrNull { it.name == raw } }
            ?: return PayloadParseResult.Invalid("type")
        val occurredAt = required("occurred_at") ?: return PayloadParseResult.Invalid("occurred_at")
        val call = if (type == InvitationEventType.INVITED) {
            val roomId = required("room_id") ?: return PayloadParseResult.Invalid("room_id")
            val mode = when (data["mode"]?.uppercase()) {
                null, "VOICE" -> CallMode.VOICE
                "VIDEO" -> CallMode.VIDEO
                else -> return PayloadParseResult.Invalid("mode")
            }
            val callerId = required("caller_id") ?: return PayloadParseResult.Invalid("caller_id")
            CallInfo(callId, roomId, mode, callerId, required("caller_name") ?: callerId)
        } else null
        return PayloadParseResult.Parsed(InvitationEvent(eventId, callId, revision, type, occurredAt, call))
    }
}
