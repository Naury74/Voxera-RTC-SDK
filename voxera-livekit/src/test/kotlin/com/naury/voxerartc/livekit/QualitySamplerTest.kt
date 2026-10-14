package com.naury.voxerartc.livekit

import com.google.common.truth.Truth.assertThat
import com.naury.voxerartc.api.MetricDirection
import com.naury.voxerartc.api.MetricKind
import com.naury.voxerartc.api.MetricSource
import org.junit.Test

class QualitySamplerTest {

    private fun pair(rttSeconds: Double) = StatEntry("CP1", "candidate-pair", mapOf("state" to "succeeded", "nominated" to true, "currentRoundTripTime" to rttSeconds))
    private fun outbound(bytes: Long) = StatEntry("OUT", "outbound-rtp", mapOf("kind" to "audio", "bytesSent" to bytes))
    private fun inbound(bytes: Long, received: Long, lost: Int, kind: String = "video", fps: Double? = 30.0) = StatEntry(
        "IN-$kind",
        "inbound-rtp",
        buildMap {
            put("kind", kind)
            put("bytesReceived", bytes)
            put("packetsReceived", received)
            put("packetsLost", lost)
            put("jitter", 0.006)
            if (fps != null) put("framesPerSecond", fps)
            put("frameHeight", 720L)
        },
    )

    @Test
    fun `첫 표본은 누적 값으로 비율을 낼 수 없어 N-A로 둔다`() {
        val first = QualitySampler().sample(0, listOf(pair(0.048), outbound(1_000)), listOf(inbound(5_000, 100, 0)))
        assertThat(first.source).isEqualTo(MetricSource.ENGINE)
        assertThat(first.valueOf(MetricKind.ROUND_TRIP_TIME, MetricDirection.NONE)).isWithin(1e-6).of(48.0)
        assertThat(first.valueOf(MetricKind.JITTER, MetricDirection.RECEIVE)).isWithin(1e-6).of(6.0)
        assertThat(first.valueOf(MetricKind.FRAME_RATE, MetricDirection.RECEIVE)).isEqualTo(30.0)
        assertThat(first.valueOf(MetricKind.BITRATE, MetricDirection.SEND)).isNull()
        assertThat(first.valueOf(MetricKind.PACKET_LOSS, MetricDirection.RECEIVE)).isNull()
    }

    @Test
    fun `두 번째 표본부터 비트레이트와 손실률을 차이로 계산한다`() {
        val sampler = QualitySampler()
        sampler.sample(0, listOf(outbound(1_000)), listOf(inbound(10_000, 100, 0)))
        val second = sampler.sample(2_000, listOf(outbound(9_000)), listOf(inbound(260_000, 298, 2)))
        assertThat(second.valueOf(MetricKind.BITRATE, MetricDirection.SEND)).isWithin(1e-6).of(32_000.0)
        assertThat(second.valueOf(MetricKind.BITRATE, MetricDirection.RECEIVE)).isWithin(1e-6).of(1_000_000.0)
        assertThat(second.valueOf(MetricKind.PACKET_LOSS, MetricDirection.RECEIVE)).isWithin(1e-6).of(1.0)
    }

    @Test
    fun `통계가 없으면 0이 아니라 N-A`() {
        val empty = QualitySampler().sample(0, emptyList(), emptyList())
        assertThat(empty.metrics.all { it.value == null }).isTrue()
    }

    @Test
    fun `음성만 받으면 프레임 레이트는 N-A`() {
        val audio = QualitySampler().sample(0, emptyList(), listOf(inbound(1_000, 10, 0, kind = "audio", fps = null)))
        assertThat(audio.valueOf(MetricKind.FRAME_RATE, MetricDirection.RECEIVE)).isNull()
    }

    @Test
    fun `카운터가 줄면(재연결) 음수 비트레이트 대신 N-A`() {
        val sampler = QualitySampler()
        sampler.sample(0, listOf(outbound(9_000)), emptyList())
        assertThat(sampler.sample(2_000, listOf(outbound(100)), emptyList()).valueOf(MetricKind.BITRATE, MetricDirection.SEND)).isNull()
    }
}
