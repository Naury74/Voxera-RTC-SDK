package com.naury.voxerartc.core.diagnostics

import com.google.common.truth.Truth.assertThat
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.MetricDirection
import com.naury.voxerartc.api.MetricKind
import com.naury.voxerartc.api.MetricSource
import com.naury.voxerartc.api.MetricUnit
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.api.QualityMetric
import com.naury.voxerartc.api.QualitySnapshot
import com.naury.voxerartc.api.RtcCredentials
import com.naury.voxerartc.api.RtcCredentialsProvider
import com.naury.voxerartc.api.SessionState
import com.naury.voxerartc.api.VoxeraError
import com.naury.voxerartc.core.Voxera
import com.naury.voxerartc.core.VoxeraSessionConfig
import com.naury.voxerartc.testkit.FakeRtcEngineFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class SessionDiagnosticsTest {

    private val context = DiagnosticContext(appVersion = "1.2.3", os = "Android 16 (API 36)", device = "Pixel 8")

    @Test
    fun `상태 전이·오류 코드·품질을 담고 토큰·서버·이름은 담지 않는다`() = runBlocking<Unit> {
        val engines = FakeRtcEngineFactory()
        val session = Voxera.create(engines).createSession(
            VoxeraSessionConfig(credentials = RtcCredentialsProvider { RtcCredentials("wss://secret-project.livekit.cloud", "eyJsecret-token") }),
        )
        val diagnostics = SessionDiagnostics(session).also { it.start() }
        session.join("private-room-name", CallMode.VOICE)
        engines.last.upsertRemote(Participant("user-42", "Kim Jiyeon", isLocal = false))
        engines.last.emitQuality(
            QualitySnapshot(
                timestampMs = session.clock.nowMs(),
                source = MetricSource.ENGINE,
                metrics = listOf(QualityMetric(MetricKind.ROUND_TRIP_TIME, MetricDirection.NONE, 48.0, MetricUnit.MILLISECONDS)),
            ),
        )
        delay(200)
        engines.last.simulateDisconnect(VoxeraError.NetworkUnavailable)
        withTimeout(5_000) { session.state.first { it is SessionState.Failed } }
        delay(100)

        val bundle = diagnostics.snapshot(context, sdkVersion = "0.1.0-alpha01")
        // StateFlow는 같은 순간의 전이(Connecting)를 합칠 수 있다.
        assertThat(bundle.events.map { it.state }).containsAtLeast("Idle", "Preparing", "Connected", "Failed").inOrder()
        assertThat(bundle.events.last().code).isEqualTo("VOX-NETWORK")
        assertThat(bundle.quality.single().rttMs).isEqualTo(48.0)
        assertThat(bundle.quality.single().jitterMs).isNull()
        assertThat(bundle.roomHash).hasLength(12)

        val json = bundle.toJson()
        listOf("eyJsecret-token", "secret-project", "private-room-name", "Kim Jiyeon", "user-42").forEach { secret ->
            assertThat(json).doesNotContain(secret)
        }
        assertThat(json).contains("\"code\": \"VOX-NETWORK\"")
        assertThat(json).contains("\"jitterMs\": null")
        diagnostics.close()
        session.close()
    }

    @Test
    fun `기록은 정한 개수까지만 남긴다`() = runBlocking<Unit> {
        val engines = FakeRtcEngineFactory()
        val session = Voxera.create(engines).createSession(VoxeraSessionConfig(credentials = RtcCredentialsProvider { RtcCredentials("wss://a.livekit.cloud", "t") }))
        val diagnostics = SessionDiagnostics(session, maxQuality = 3).also { it.start() }
        session.join("room", CallMode.VOICE)
        repeat(10) { index ->
            engines.last.emitQuality(QualitySnapshot(session.clock.nowMs() + index, MetricSource.SIMULATED, emptyList()))
            delay(10)
        }
        delay(200)
        assertThat(diagnostics.snapshot(context, "x").quality).hasSize(3)
        diagnostics.close()
        session.close()
    }

    @Test
    fun `JSON 문자열의 특수 문자를 이스케이프한다`() {
        val bundle = DiagnosticBundle("1", DiagnosticContext("a\"b", "line\nbreak", "tab\t"), null, emptyList(), emptyList())
        val json = bundle.toJson()
        assertThat(json).contains("\"appVersion\": \"a\\\"b\"")
        assertThat(json).contains("\"os\": \"line\\nbreak\"")
        assertThat(json).contains("\"roomHash\": null")
    }

    @Test
    fun `같은 룸은 같은 해시`() {
        assertThat(hashRoom("team")).isEqualTo(hashRoom("team"))
        assertThat(hashRoom("team")).isNotEqualTo(hashRoom("team2"))
    }
}
