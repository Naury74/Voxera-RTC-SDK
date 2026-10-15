package com.naury.voxerartc.livekit

import com.google.common.truth.Truth.assertThat
import com.naury.voxerartc.api.ChatMessage
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SocialWireTest {

    @Test
    fun `채팅 스트림 조각을 이어 붙이고 상한을 넘으면 버린다`() = runBlocking {
        assertThat(ChatStream.read(flowOf("안녕", "하세요 😀"), declaredBytes = null)).isEqualTo("안녕하세요 😀")
        assertThat(ChatStream.read(flowOf("가".repeat(ChatMessage.MAX_LENGTH)), declaredBytes = null)).hasLength(ChatMessage.MAX_LENGTH)
        // 한 조각씩 받다 넘으면 멈춘다.
        assertThat(ChatStream.read(flowOf("12345", "67890"), declaredBytes = null, maxBytes = 8)).isNull()
        // 보낸 쪽이 미리 알린 크기가 크면 읽지 않는다.
        assertThat(ChatStream.read(flowOf("hi"), declaredBytes = ChatStream.MAX_BYTES + 1L)).isNull()
    }

    @Test
    fun `손들기는 올림과 내림만 알아본다`() {
        assertThat(HandWire.decode(HandWire.TOPIC, HandWire.encode(true))).isTrue()
        assertThat(HandWire.decode(HandWire.TOPIC, HandWire.encode(false))).isFalse()
        assertThat(HandWire.decode(HandWire.TOPIC, "1:maybe".toByteArray())).isNull()
        assertThat(HandWire.decode(ReactionWire.TOPIC, HandWire.encode(true))).isNull()
    }
}
