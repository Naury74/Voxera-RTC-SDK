package com.naury.voxerartc.core

import com.google.common.truth.Truth.assertThat
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.EndReason
import com.naury.voxerartc.api.JoinResult
import com.naury.voxerartc.api.MonotonicClock
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.api.RtcCredentials
import com.naury.voxerartc.api.RtcCredentialsProvider
import com.naury.voxerartc.api.SessionState
import com.naury.voxerartc.testkit.FakeRtcEngine
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

/** 반복 입퇴장·토글에서 자원이 새거나 상태가 어긋나지 않는지 본다(09_QA 장기 soak의 JVM 판). */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionSoakTest {

    private val provider = RtcCredentialsProvider { RtcCredentials("wss://test.livekit.cloud", "t") }

    private fun TestScope.sdk(factory: FakeRtcEngineFactory): VoxeraSdk =
        Voxera.create(factory, MonotonicClock { testScheduler.currentTime }, StandardTestDispatcher(testScheduler))

    @Test
    fun `입퇴장 100회 뒤에도 엔진은 모두 한 번씩 해제되고 상태가 깨끗하다`() = runTest {
        val factory = FakeRtcEngineFactory { FakeRtcEngine(connectDelayMs = 50) }
        val session = sdk(factory).createSession(VoxeraSessionConfig(credentials = provider))
        repeat(100) { round ->
            val mode = if (round % 2 == 0) CallMode.VIDEO else CallMode.VOICE
            val join = async { session.join("room-$round", mode) }
            advanceUntilIdle()
            assertThat(join.await()).isInstanceOf(JoinResult.Connected::class.java)
            factory.last.upsertRemote(Participant("p$round", "P$round", isLocal = false))
            advanceTimeBy(1_000)
            session.leave()
            advanceUntilIdle()
            assertThat(session.state.value).isInstanceOf(SessionState.Ended::class.java)
            assertThat(session.participants.value).isEmpty()
        }
        assertThat(factory.created).hasSize(100)
        assertThat(factory.created.map { it.releaseCount }.distinct()).containsExactly(1)
        session.close()
    }

    @Test
    fun `연결 중 취소를 50번 섞어도 늦은 성공이 통화 상태로 남지 않는다`() = runTest {
        val factory = FakeRtcEngineFactory { FakeRtcEngine(connectDelayMs = 300) }
        val session = sdk(factory).createSession(VoxeraSessionConfig(credentials = provider))
        repeat(50) { round ->
            val join = async { session.join("room-$round", CallMode.VOICE) }
            runCurrent()
            advanceTimeBy(100)
            session.leave()
            advanceUntilIdle()
            assertThat(join.await()).isNotInstanceOf(JoinResult.Connected::class.java)
            assertThat(session.state.value).isEqualTo(SessionState.Ended(EndReason.CANCELLED, null))
        }
        assertThat(factory.created.filter { it.releaseCount != 1 }).isEmpty()
        session.close()
    }

    @Test
    fun `카메라를 100번 켜고 꺼도 마지막 의도와 엔진 상태가 같다`() = runTest {
        val factory = FakeRtcEngineFactory()
        val session = sdk(factory).createSession(VoxeraSessionConfig(credentials = provider))
        val join = async { session.join("room", CallMode.VIDEO) }
        advanceUntilIdle()
        assertThat(join.await()).isInstanceOf(JoinResult.Connected::class.java)
        repeat(100) { index -> launch { session.setCameraEnabled(index % 2 == 0) } }
        advanceUntilIdle()
        val camera = session.localMedia.value.camera
        assertThat(camera.desired).isFalse()
        assertThat(camera.actual).isEqualTo(camera.desired)
        session.leave()
        advanceUntilIdle()
        session.close()
    }
}
