package com.naury.voxerartc.core

import com.google.common.truth.Truth.assertThat
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.ChatMessage
import com.naury.voxerartc.api.JoinResult
import com.naury.voxerartc.api.MediaActionResult
import com.naury.voxerartc.api.MonotonicClock
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.api.RtcCredentials
import com.naury.voxerartc.api.RtcCredentialsProvider
import com.naury.voxerartc.api.VoxeraError
import com.naury.voxerartc.testkit.FakeRtcEngineFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatAndHandTest {

    private val factory = FakeRtcEngineFactory()

    private fun TestScope.connected(): VoxeraSession {
        val session = Voxera.create(factory, MonotonicClock { testScheduler.currentTime }, StandardTestDispatcher(testScheduler))
            .createSession(VoxeraSessionConfig(credentials = RtcCredentialsProvider { RtcCredentials("wss://t.livekit.cloud", "t") }))
        val join = async { session.join("room", CallMode.VIDEO) }
        advanceUntilIdle()
        assertThat(join.getCompleted()).isInstanceOf(JoinResult.Connected::class.java)
        return session
    }

    @Test
    fun `보낸 채팅은 다듬어 엔진에 가고 받은 채팅과 함께 순서대로 쌓인다`() = runTest {
        val session = connected()
        assertThat(session.sendChatMessage("  안녕하세요 \u0007 ")).isEqualTo(MediaActionResult.Applied)
        factory.last.simulateChat("alex", "반가워요")
        advanceUntilIdle()
        assertThat(factory.last.sentChatMessages.map { it.second }).containsExactly("안녕하세요")
        assertThat(session.chat.value.map { it.participantId to it.text }).containsExactly("local" to "안녕하세요", "alex" to "반가워요").inOrder()
        assertThat(session.chat.value.map { it.isLocal }).containsExactly(true, false).inOrder()
        session.close()
    }

    @Test
    fun `빈 글이나 너무 긴 글은 보내지 않고 같은 ID로 다시 온 메시지는 한 번만 쌓인다`() = runTest {
        val session = connected()
        assertThat(session.sendChatMessage("   ")).isEqualTo(MediaActionResult.Unsupported)
        assertThat(session.sendChatMessage("가".repeat(ChatMessage.MAX_LENGTH + 1))).isEqualTo(MediaActionResult.Unsupported)
        factory.last.simulateChat("alex", "한 번", messageId = "m1")
        factory.last.simulateChat("alex", "한 번", messageId = "m1")
        factory.last.simulateChat("alex", "", messageId = "m2")
        factory.last.simulateChat("alex", "id가 너무 김", messageId = "x".repeat(ChatMessage.MAX_ID_LENGTH + 1))
        advanceUntilIdle()
        assertThat(factory.last.sentChatMessages).isEmpty()
        assertThat(session.chat.value.map { it.text }).containsExactly("한 번")
        session.close()
    }

    @Test
    fun `한 참가자가 5초에 5개를 넘게 보내면 넘친 것은 버린다`() = runTest {
        val session = connected()
        repeat(8) { factory.last.simulateChat("alex", "도배 $it") }
        advanceUntilIdle()
        assertThat(session.chat.value).hasSize(VoxeraSession.CHAT_BURST)
        advanceTimeBy(VoxeraSession.CHAT_WINDOW_MS)
        factory.last.simulateChat("alex", "다시")
        advanceUntilIdle()
        assertThat(session.chat.value.last().text).isEqualTo("다시")
        session.close()
    }

    @Test
    fun `통화가 끝나면 채팅과 손들기를 비우고 연결 전에는 보낼 수 없다`() = runTest {
        val idle = Voxera.create(FakeRtcEngineFactory(), MonotonicClock { testScheduler.currentTime }, StandardTestDispatcher(testScheduler))
            .createSession(VoxeraSessionConfig(credentials = RtcCredentialsProvider { RtcCredentials("wss://t.livekit.cloud", "t") }))
        assertThat(idle.sendChatMessage("hi")).isEqualTo(MediaActionResult.Failed(VoxeraError.NotConnected))
        assertThat(idle.setHandRaised(true)).isEqualTo(MediaActionResult.Failed(VoxeraError.NotConnected))
        idle.close()

        val session = connected()
        factory.last.simulateChat("alex", "hi")
        advanceUntilIdle()
        session.leave()
        advanceUntilIdle()
        assertThat(session.chat.value).isEmpty()
        session.close()
    }

    @Test
    fun `손들기는 참가자 상태에 시각으로 얹히고 엔진이 참가자를 갱신해도 유지된다`() = runTest {
        val session = connected()
        val engine = factory.last
        engine.upsertRemote(Participant("alex", "Alex", isLocal = false))
        advanceUntilIdle()
        advanceTimeBy(500)
        engine.simulateHandRaised("alex", true)
        assertThat(session.setHandRaised(true)).isEqualTo(MediaActionResult.Applied)
        advanceUntilIdle()
        assertThat(engine.localHandRaised).isTrue()
        val alex = session.participants.value.first { it.id == "alex" }
        val me = session.participants.value.first { it.isLocal }
        assertThat(alex.isHandRaised).isTrue()
        assertThat(me.isHandRaised).isTrue()
        assertThat(alex.handRaisedAtMs!!).isAtMost(me.handRaisedAtMs!!)

        // 엔진이 말하기 상태 등으로 참가자를 다시 보내도 손은 내려가지 않는다.
        engine.upsertRemote(Participant("alex", "Alex", isLocal = false, isSpeaking = true))
        advanceUntilIdle()
        assertThat(session.participants.value.first { it.id == "alex" }.isHandRaised).isTrue()

        engine.simulateHandRaised("alex", false)
        advanceUntilIdle()
        assertThat(session.participants.value.first { it.id == "alex" }.isHandRaised).isFalse()
        session.close()
    }
}
