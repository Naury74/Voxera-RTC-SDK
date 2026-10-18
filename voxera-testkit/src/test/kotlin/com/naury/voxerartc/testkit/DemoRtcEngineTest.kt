package com.naury.voxerartc.testkit

import com.google.common.truth.Truth.assertThat
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.ConnectOptions
import com.naury.voxerartc.api.ConnectOutcome
import com.naury.voxerartc.api.MetricDirection
import com.naury.voxerartc.api.MetricKind
import com.naury.voxerartc.api.MetricSource
import com.naury.voxerartc.api.MonotonicClock
import com.naury.voxerartc.api.RtcCredentials
import com.naury.voxerartc.api.RtcEngineEvent
import com.naury.voxerartc.api.RtcEngineKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoRtcEngineTest {

    private val credentials = RtcCredentials("wss://offline.invalid", "offline-demo")

    private fun options(scenario: DemoScenario) = ConnectOptions(VoxeraDemo.ROOM_ID, scenario.mode, startWithMicrophone = true, startWithCamera = scenario.mode == CallMode.VIDEO)

    private fun remoteCountAfterJoin(scenario: DemoScenario): Int {
        var count = 0
        runTest {
            val engine = DemoRtcEngine(scenario, MonotonicClock { testScheduler.currentTime }, StandardTestDispatcher(testScheduler))
            val ids = mutableSetOf<String>()
            val collector = launch {
                engine.events.collect { if (it is RtcEngineEvent.ParticipantUpserted && !it.participant.isLocal) ids += it.participant.id }
            }
            runCurrent()
            assertThat(engine.connect(credentials, options(scenario))).isInstanceOf(ConnectOutcome.Connected::class.java)
            advanceTimeBy(5_000)
            runCurrent()
            count = ids.size
            engine.release()
            collector.cancel()
        }
        return count
    }

    @Test
    fun `시나리오마다 1, 3, 8명의 원격 참가자를 들여보낸다`() {
        assertThat(remoteCountAfterJoin(DemoScenario.VOICE_ONE_ON_ONE)).isEqualTo(1)
        assertThat(remoteCountAfterJoin(DemoScenario.GROUP_FOUR)).isEqualTo(3)
        assertThat(remoteCountAfterJoin(DemoScenario.GROUP_NINE)).isEqualTo(8)
        assertThat(remoteCountAfterJoin(DemoScenario.GROUP_TWELVE)).isEqualTo(11)
    }

    @Test
    fun `품질 값은 SIMULATED로 표시하고 음성에서는 FPS를 측정 불가로 둔다`() = runTest {
        val engine = DemoRtcEngine(DemoScenario.VOICE_ONE_ON_ONE, MonotonicClock { testScheduler.currentTime }, StandardTestDispatcher(testScheduler))
        val samples = mutableListOf<RtcEngineEvent.QualitySampled>()
        val collector = launch { engine.events.collect { if (it is RtcEngineEvent.QualitySampled) samples += it } }
        runCurrent()
        engine.connect(credentials, options(DemoScenario.VOICE_ONE_ON_ONE))
        advanceTimeBy(5_000)
        runCurrent()
        engine.release()
        collector.cancel()

        assertThat(engine.kind).isEqualTo(RtcEngineKind.SIMULATED)
        assertThat(samples).isNotEmpty()
        val snapshot = samples.first().snapshot
        assertThat(snapshot.source).isEqualTo(MetricSource.SIMULATED)
        assertThat(snapshot.valueOf(MetricKind.FRAME_RATE, MetricDirection.RECEIVE)).isNull()
        assertThat(snapshot.valueOf(MetricKind.ROUND_TRIP_TIME, MetricDirection.NONE)).isNotNull()
    }

    @Test
    fun `불안정한 망 시나리오는 끊겼다가 복구된다`() = runTest {
        val engine = DemoRtcEngine(DemoScenario.UNSTABLE_NETWORK, MonotonicClock { testScheduler.currentTime }, StandardTestDispatcher(testScheduler))
        val events = mutableListOf<RtcEngineEvent>()
        val collector = launch { engine.events.collect { if (it == RtcEngineEvent.Reconnecting || it == RtcEngineEvent.Reconnected) events += it } }
        runCurrent()
        engine.connect(credentials, options(DemoScenario.UNSTABLE_NETWORK))
        advanceTimeBy(12_000)
        runCurrent()
        engine.release()
        collector.cancel()
        assertThat(events).containsExactly(RtcEngineEvent.Reconnecting, RtcEngineEvent.Reconnected).inOrder()
    }

    @Test
    fun `release 뒤에는 더 이상 이벤트를 보내지 않는다`() = runTest {
        val engine = DemoRtcEngine(DemoScenario.GROUP_FOUR, MonotonicClock { testScheduler.currentTime }, StandardTestDispatcher(testScheduler))
        var count = 0
        val collector = launch { engine.events.collect { count++ } }
        runCurrent()
        engine.connect(credentials, options(DemoScenario.GROUP_FOUR))
        advanceTimeBy(3_000)
        runCurrent()
        engine.release()
        val before = count
        advanceTimeBy(10_000)
        runCurrent()
        collector.cancel()
        assertThat(count).isEqualTo(before)
    }
}
