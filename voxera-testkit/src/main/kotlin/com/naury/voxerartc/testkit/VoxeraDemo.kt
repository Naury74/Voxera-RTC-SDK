package com.naury.voxerartc.testkit

import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.ConnectOptions
import com.naury.voxerartc.api.ConnectOutcome
import com.naury.voxerartc.api.ConnectionQuality
import com.naury.voxerartc.api.MetricDirection
import com.naury.voxerartc.api.MetricKind
import com.naury.voxerartc.api.MetricSource
import com.naury.voxerartc.api.MetricUnit
import com.naury.voxerartc.api.MonotonicClock
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.api.QualityMetric
import com.naury.voxerartc.api.QualitySnapshot
import com.naury.voxerartc.api.RtcCredentials
import com.naury.voxerartc.api.RtcCredentialsProvider
import com.naury.voxerartc.api.RtcEngine
import com.naury.voxerartc.api.RtcEngineFactory
import com.naury.voxerartc.api.RtcEngineKind
import com.naury.voxerartc.api.VideoSource
import com.naury.voxerartc.api.VideoTrackHandle
import com.naury.voxerartc.core.Voxera
import com.naury.voxerartc.core.VoxeraSdk
import com.naury.voxerartc.core.VoxeraSession
import com.naury.voxerartc.core.VoxeraSessionConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 오프라인 데모 상황. 서버·계정 없이 화면과 상태 변화를 보여 주며 실제 통화가 아니다.
 *
 * @property mode 이 상황을 시작할 때 권장하는 모드.
 * @property remoteNames 들어오는 원격 참가자 표시 이름. 인물 사진 대신 이니셜 아바타로 보인다.
 */
public enum class DemoScenario(public val mode: CallMode, public val remoteNames: List<String>) {
    VOICE_ONE_ON_ONE(CallMode.VOICE, listOf("Alex Morgan")),
    VIDEO_ONE_ON_ONE(CallMode.VIDEO, listOf("Alex Morgan")),
    GROUP_FOUR(CallMode.VIDEO, listOf("Alex", "Taylor", "Jamie")),
    GROUP_NINE(CallMode.VIDEO, listOf("Alex", "Taylor", "Jamie", "Morgan", "Riley", "Casey", "Jordan", "Sam")),

    /** 연결 뒤 잠시 끊겼다가 복구되는 망. 재연결 화면을 보여 준다. */
    UNSTABLE_NETWORK(CallMode.VOICE, listOf("Alex Morgan")),
}

/** 오프라인 데모 진입점. 만든 세션의 [VoxeraSession.engineKind]는 항상 [RtcEngineKind.SIMULATED]다. */
public object VoxeraDemo {

    /** 데모 룸 이름. 실제 서버로 전송되지 않는다. */
    public const val ROOM_ID: String = "offline-demo"

    /** [scenario]를 재생하는 SDK. 연결할 때마다 시나리오가 처음부터 다시 시작된다. */
    public fun createSdk(scenario: DemoScenario): VoxeraSdk =
        Voxera.create(DemoRtcEngineFactory(scenario, MonotonicClock.System, Dispatchers.Default))

    /** [scenario]를 재생하는 세션. `join(ROOM_ID, scenario.mode)`로 시작한다. */
    public fun createFakeSession(scenario: DemoScenario): VoxeraSession =
        createSdk(scenario).createSession(VoxeraSessionConfig(credentials = OfflineCredentials))

    /** 예약된 `.invalid` 도메인이라 실수로 네트워크에 연결되지 않는다. */
    private val OfflineCredentials = RtcCredentialsProvider { RtcCredentials("wss://offline.invalid", "offline-demo") }
}

internal class DemoRtcEngineFactory(
    private val scenario: DemoScenario,
    private val clock: MonotonicClock,
    private val dispatcher: CoroutineDispatcher,
) : RtcEngineFactory {
    override val kind: RtcEngineKind = RtcEngineKind.SIMULATED

    override fun create(): RtcEngine = DemoRtcEngine(scenario, clock, dispatcher)
}

/**
 * 시나리오 대본을 [FakeRtcEngine] 위에서 재생한다. 참가자 입장·발화자 교대·품질 표본·재연결을 시간에 맞춰 보낸다.
 * 품질 값은 [MetricSource.SIMULATED]로 표시한다.
 */
internal class DemoRtcEngine(
    private val scenario: DemoScenario,
    private val clock: MonotonicClock,
    dispatcher: CoroutineDispatcher,
    private val fake: FakeRtcEngine = FakeRtcEngine(
        local = Participant(id = "local", displayName = "You", isLocal = true),
        connectDelayMs = 900,
        mediaAckDelayMs = 120,
    ),
) : RtcEngine by fake {

    private val script = CoroutineScope(SupervisorJob() + dispatcher)
    private val random = Random(scenario.ordinal)
    private val remotes = scenario.remoteNames.mapIndexed { index, name ->
        Participant(
            id = "demo-${index + 1}",
            displayName = name,
            isLocal = false,
            isMicrophoneEnabled = index % 4 != 3,
            camera = if (scenario.mode == CallMode.VIDEO) VideoTrackHandle("demo-${index + 1}", "demo-camera-${index + 1}", VideoSource.CAMERA) else null,
            connectionQuality = ConnectionQuality.GOOD,
        )
    }.toMutableList()

    override suspend fun connect(credentials: RtcCredentials, options: ConnectOptions): ConnectOutcome {
        val outcome = fake.connect(credentials, options)
        if (outcome is ConnectOutcome.Connected) script.launch { play() }
        return outcome
    }

    override suspend fun release() {
        script.cancel()
        fake.release()
    }

    private suspend fun play() {
        remotes.forEachIndexed { index, participant ->
            delay(if (index == 0) 300 else 250)
            fake.upsertRemote(participant)
        }
        var tick = 0
        while (script.isActive) {
            delay(TICK_MS)
            tick++
            rotateSpeaker(tick)
            if (tick % 2 == 0) fake.emitQuality(sample())
            if (scenario == DemoScenario.UNSTABLE_NETWORK) when (tick % 14) {
                6 -> fake.simulateReconnecting()
                9 -> fake.simulateReconnected()
            }
        }
    }

    private fun rotateSpeaker(tick: Int) {
        val speaking = remotes.indices.filter { remotes[it].isMicrophoneEnabled }.let { if (it.isEmpty()) -1 else it[(tick / 3) % it.size] }
        remotes.indices.forEach { index ->
            val before = remotes[index]
            val talking = index == speaking
            val level = if (talking) 0.35f + random.nextFloat() * 0.6f else 0f
            val after = before.copy(isSpeaking = talking, audioLevel = level)
            if (after != before) {
                remotes[index] = after
                fake.upsertRemote(after)
            }
        }
    }

    private fun sample(): QualitySnapshot {
        val video = scenario.mode == CallMode.VIDEO
        fun metric(kind: MetricKind, direction: MetricDirection, value: Double?, unit: MetricUnit) = QualityMetric(kind, direction, value, unit)
        return QualitySnapshot(
            timestampMs = clock.nowMs(),
            source = MetricSource.SIMULATED,
            metrics = listOf(
                metric(MetricKind.ROUND_TRIP_TIME, MetricDirection.NONE, 38.0 + random.nextInt(25), MetricUnit.MILLISECONDS),
                metric(MetricKind.JITTER, MetricDirection.RECEIVE, 3.0 + random.nextInt(6), MetricUnit.MILLISECONDS),
                metric(MetricKind.PACKET_LOSS, MetricDirection.RECEIVE, random.nextInt(4) / 10.0, MetricUnit.PERCENT),
                metric(MetricKind.BITRATE, MetricDirection.SEND, if (video) 900_000.0 + random.nextInt(300_000) else 32_000.0, MetricUnit.BITS_PER_SECOND),
                metric(MetricKind.BITRATE, MetricDirection.RECEIVE, if (video) 1_500_000.0 + random.nextInt(500_000) else 32_000.0, MetricUnit.BITS_PER_SECOND),
                // 음성 통화에는 영상 프레임이 없으므로 측정 불가로 둔다.
                metric(MetricKind.FRAME_RATE, MetricDirection.RECEIVE, if (video) 28.0 + random.nextInt(3) else null, MetricUnit.FRAMES_PER_SECOND),
            ),
        )
    }

    private companion object {
        const val TICK_MS = 1_000L
    }
}
