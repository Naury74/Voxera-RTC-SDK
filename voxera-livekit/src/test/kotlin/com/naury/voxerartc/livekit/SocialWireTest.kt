package com.naury.voxerartc.livekit

import com.google.common.truth.Truth.assertThat
import com.naury.voxerartc.api.ChatMessage
import org.junit.Test

class SocialWireTest {

    @Test
    fun `채팅은 콜론과 줄바꿈이 들어 있어도 보낸 그대로 되읽힌다`() {
        val text = "시간: 3시\n장소: 회의실 😀"
        assertThat(ChatWire.decode(ChatWire.TOPIC, ChatWire.encode("a1-b2", text))).isEqualTo("a1-b2" to text)
        val longest = "가".repeat(ChatMessage.MAX_LENGTH)
        assertThat(ChatWire.decode(ChatWire.TOPIC, ChatWire.encode("id", longest))?.second).isEqualTo(longest)
    }

    @Test
    fun `다른 주제, 모르는 버전, 잘못된 ID, 너무 큰 채팅은 버린다`() {
        val ok = ChatWire.encode("id", "hi")
        assertThat(ChatWire.decode(ReactionWire.TOPIC, ok)).isNull()
        assertThat(ChatWire.decode(null, ok)).isNull()
        assertThat(ChatWire.decode(ChatWire.TOPIC, "2:id:hi".toByteArray())).isNull()
        assertThat(ChatWire.decode(ChatWire.TOPIC, "1:i d:hi".toByteArray())).isNull()
        assertThat(ChatWire.decode(ChatWire.TOPIC, "1:hi".toByteArray())).isNull()
        assertThat(ChatWire.decode(ChatWire.TOPIC, ByteArray(0))).isNull()
        assertThat(ChatWire.decode(ChatWire.TOPIC, ChatWire.encode("id", "😀".repeat(ChatMessage.MAX_LENGTH + 100)))).isNull()
    }

    @Test
    fun `손들기는 올림과 내림만 알아본다`() {
        assertThat(HandWire.decode(HandWire.TOPIC, HandWire.encode(true))).isTrue()
        assertThat(HandWire.decode(HandWire.TOPIC, HandWire.encode(false))).isFalse()
        assertThat(HandWire.decode(HandWire.TOPIC, "1:maybe".toByteArray())).isNull()
        assertThat(HandWire.decode(ChatWire.TOPIC, HandWire.encode(true))).isNull()
    }
}
