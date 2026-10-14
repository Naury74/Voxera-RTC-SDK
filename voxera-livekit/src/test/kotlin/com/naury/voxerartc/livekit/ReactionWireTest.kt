package com.naury.voxerartc.livekit

import com.google.common.truth.Truth.assertThat
import com.naury.voxerartc.api.Reaction
import org.junit.Test

class ReactionWireTest {

    @Test
    fun `모든 반응은 보낸 그대로 되읽힌다`() {
        Reaction.entries.forEach { reaction ->
            assertThat(ReactionWire.decode(ReactionWire.TOPIC, ReactionWire.encode(reaction))).isEqualTo(reaction)
        }
    }

    @Test
    fun `다른 주제, 모르는 버전·이름, 너무 긴 내용은 버린다`() {
        val heart = ReactionWire.encode(Reaction.HEART)
        assertThat(ReactionWire.decode("chat", heart)).isNull()
        assertThat(ReactionWire.decode(null, heart)).isNull()
        assertThat(ReactionWire.decode(ReactionWire.TOPIC, "2:heart".toByteArray())).isNull()
        assertThat(ReactionWire.decode(ReactionWire.TOPIC, "1:<script>".toByteArray())).isNull()
        assertThat(ReactionWire.decode(ReactionWire.TOPIC, "heart".toByteArray())).isNull()
        assertThat(ReactionWire.decode(ReactionWire.TOPIC, ByteArray(65) { 'a'.code.toByte() })).isNull()
        assertThat(ReactionWire.decode(ReactionWire.TOPIC, ByteArray(0))).isNull()
    }
}
