package com.naury.voxerartc.invitation

import com.google.common.truth.Truth.assertThat
import com.naury.voxerartc.api.CallMode
import org.junit.Test

class InvitationPayloadsTest {

    private val invited = mapOf(
        "event_id" to "e1", "call_id" to "c1", "revision" to "3", "type" to "INVITED",
        "occurred_at" to "2026-10-14T10:00:00Z", "room_id" to "r1", "mode" to "video", "caller_id" to "u1", "caller_name" to "Alex",
    )

    @Test
    fun `스키마 키로 INVITED 이벤트를 만든다`() {
        val parsed = InvitationPayloads.parse(invited) as PayloadParseResult.Parsed
        assertThat(parsed.event.revision).isEqualTo(3)
        assertThat(parsed.event.call).isEqualTo(CallInfo("c1", "r1", CallMode.VIDEO, "u1", "Alex"))
    }

    @Test
    fun `종료 이벤트는 통화 정보 없이 받는다`() {
        val parsed = InvitationPayloads.parse(mapOf("event_id" to "e2", "call_id" to "c1", "revision" to "4", "type" to "CANCELLED", "occurred_at" to "t"))
        assertThat((parsed as PayloadParseResult.Parsed).event.type).isEqualTo(InvitationEventType.CANCELLED)
    }

    @Test
    fun `필수 키가 없거나 잘못되면 그 키를 알린다`() {
        assertThat(InvitationPayloads.parse(invited - "revision")).isEqualTo(PayloadParseResult.Invalid("revision"))
        assertThat(InvitationPayloads.parse(invited + ("revision" to "-1"))).isEqualTo(PayloadParseResult.Invalid("revision"))
        assertThat(InvitationPayloads.parse(invited + ("type" to "RINGING"))).isEqualTo(PayloadParseResult.Invalid("type"))
        assertThat(InvitationPayloads.parse(invited - "room_id")).isEqualTo(PayloadParseResult.Invalid("room_id"))
    }

    @Test
    fun `토큰이나 비밀 값 키가 있으면 값을 읽지 않고 거부한다`() {
        assertThat(InvitationPayloads.parse(invited + ("access_token" to "eyJ...")))
            .isEqualTo(PayloadParseResult.ContainsSecret("access_token"))
        assertThat(InvitationPayloads.parse(invited + ("livekitApiSecret" to "x")))
            .isEqualTo(PayloadParseResult.ContainsSecret("livekitApiSecret"))
    }
}
