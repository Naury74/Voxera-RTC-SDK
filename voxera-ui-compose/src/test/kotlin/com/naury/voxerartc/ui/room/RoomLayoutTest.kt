package com.naury.voxerartc.ui.room

import com.google.common.truth.Truth.assertThat
import com.naury.voxerartc.api.MetricKind
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.ui.component.initialsOf
import org.junit.Test

class RoomLayoutTest {

    private fun people(count: Int, speaking: Set<Int> = emptySet()) = (0 until count).map {
        Participant(id = "p$it", displayName = "P$it", isLocal = it == 0, isSpeaking = it in speaking)
    }

    @Test
    fun `통화 시간과 측정값 문구`() {
        assertThat(formatDuration(0)).isEqualTo("0:00")
        assertThat(formatDuration(65_400)).isEqualTo("1:05")
        assertThat(formatDuration(3_726_000)).isEqualTo("1:02:06")
        assertThat(formatDuration(-5)).isEqualTo("0:00")
        assertThat(formatMetric(MetricKind.BITRATE, 1_830_000.0)).isEqualTo("1.8 Mbps")
        assertThat(formatMetric(MetricKind.BITRATE, 32_000.0)).isEqualTo("32 kbps")
        assertThat(formatMetric(MetricKind.ROUND_TRIP_TIME, 48.4)).isEqualTo("48 ms")
        assertThat(formatMetric(MetricKind.FRAME_RATE, null)).isNull()
    }

    @Test
    fun `이니셜`() {
        assertThat(initialsOf("Alex Morgan")).isEqualTo("AM")
        assertThat(initialsOf("alex")).isEqualTo("A")
        assertThat(initialsOf("지연")).isEqualTo("지")
        assertThat(initialsOf("  ")).isEqualTo("?")
    }
}
