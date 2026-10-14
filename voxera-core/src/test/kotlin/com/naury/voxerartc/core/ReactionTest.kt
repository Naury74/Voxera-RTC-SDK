package com.naury.voxerartc.core

import com.google.common.truth.Truth.assertThat
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.JoinResult
import com.naury.voxerartc.api.MediaActionResult
import com.naury.voxerartc.api.MonotonicClock
import com.naury.voxerartc.api.Reaction
import com.naury.voxerartc.api.ReactionEvent
import com.naury.voxerartc.api.RtcCredentials
import com.naury.voxerartc.api.RtcCredentialsProvider
import com.naury.voxerartc.api.VoxeraError
import com.naury.voxerartc.testkit.FakeRtcEngineFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReactionTest {

    private val factory = FakeRtcEngineFactory()

    private fun TestScope.connected(): VoxeraSession {
        val session = Voxera.create(factory, MonotonicClock { testScheduler.currentTime }, StandardTestDispatcher(testScheduler))
            .createSession(VoxeraSessionConfig(credentials = RtcCredentialsProvider { RtcCredentials("wss://t.livekit.cloud", "t") }))
        val join = async { session.join("room", CallMode.VOICE) }
        advanceUntilIdle()
        assertThat(join.getCompleted()).isInstanceOf(JoinResult.Connected::class.java)
        return session
    }

    @Test
    fun `보낸 반응은 엔진에 가고 내 반응으로도 보인다`() = runTest {
        val session = connected()
        val seen = mutableListOf<ReactionEvent>()
        val job = launch { session.reactions.collect { seen += it } }
        runCurrent()
        assertThat(session.sendReaction(Reaction.HEART)).isEqualTo(MediaActionResult.Applied)
        factory.last.simulateReaction("alex", Reaction.CLAP)
        advanceUntilIdle()
        assertThat(factory.last.sentReactions).containsExactly(Reaction.HEART)
        assertThat(seen.map { it.participantId to it.reaction }).containsExactly("local" to Reaction.HEART, "alex" to Reaction.CLAP).inOrder()
        job.cancel()
        session.close()
    }

    @Test
    fun `한 참가자가 1초에 5개를 넘게 보내면 넘친 것은 버리고 1초 뒤에는 다시 받는다`() = runTest {
        val session = connected()
        val seen = mutableListOf<ReactionEvent>()
        val job = launch { session.reactions.collect { seen += it } }
        runCurrent()
        repeat(8) { factory.last.simulateReaction("alex", Reaction.LAUGH) }
        factory.last.simulateReaction("sam", Reaction.LAUGH)
        advanceUntilIdle()
        assertThat(seen.count { it.participantId == "alex" }).isEqualTo(VoxeraSession.REACTION_BURST)
        assertThat(seen.count { it.participantId == "sam" }).isEqualTo(1)
        advanceTimeBy(1_000)
        factory.last.simulateReaction("alex", Reaction.LAUGH)
        advanceUntilIdle()
        assertThat(seen.count { it.participantId == "alex" }).isEqualTo(VoxeraSession.REACTION_BURST + 1)
        job.cancel()
        session.close()
    }

    @Test
    fun `연결 전에는 보낼 수 없고 통화가 끝난 뒤 온 반응은 버린다`() = runTest {
        val idle = Voxera.create(FakeRtcEngineFactory(), MonotonicClock { testScheduler.currentTime }, StandardTestDispatcher(testScheduler))
            .createSession(VoxeraSessionConfig(credentials = RtcCredentialsProvider { RtcCredentials("wss://t.livekit.cloud", "t") }))
        assertThat(idle.sendReaction(Reaction.HEART)).isEqualTo(MediaActionResult.Failed(VoxeraError.NotConnected))
        idle.close()

        val session = connected()
        val seen = mutableListOf<ReactionEvent>()
        val job = launch { session.reactions.collect { seen += it } }
        val engine = factory.last
        session.leave()
        advanceUntilIdle()
        engine.simulateReaction("alex", Reaction.HEART)
        advanceUntilIdle()
        assertThat(seen).isEmpty()
        job.cancel()
        session.close()
    }
}
