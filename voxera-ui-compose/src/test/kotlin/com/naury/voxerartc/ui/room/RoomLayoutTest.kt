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
    fun `참가자 수에 따른 격자`() {
        assertThat(gridFor(1, landscape = false)).isEqualTo(GridSpec(1, 1))
        assertThat(gridFor(2, landscape = false)).isEqualTo(GridSpec(1, 2))
        assertThat(gridFor(2, landscape = true)).isEqualTo(GridSpec(2, 1))
        assertThat(gridFor(4, landscape = false)).isEqualTo(GridSpec(2, 2))
        assertThat(gridFor(5, landscape = false)).isEqualTo(GridSpec(2, 3))
        assertThat(gridFor(6, landscape = true)).isEqualTo(GridSpec(3, 2))
        assertThat(gridFor(9, landscape = false)).isEqualTo(GridSpec(3, 3))
        assertThat(gridFor(20, landscape = false)).isEqualTo(GridSpec(3, 3))
    }

    @Test
    fun `9명까지는 모두 보이고 넘치면 로컬과 발화자를 먼저 보여 준다`() {
        assertThat(visibleTiles(people(9)).second).isEqualTo(0)
        val (shown, hidden) = visibleTiles(people(20, speaking = setOf(17)))
        assertThat(shown).hasSize(8)
        assertThat(hidden).isEqualTo(12)
        assertThat(shown.map { it.id }).containsAtLeast("p0", "p17")
        assertThat(shown.map { it.id }).isInOrder(compareBy<String> { it.removePrefix("p").toInt() })
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
