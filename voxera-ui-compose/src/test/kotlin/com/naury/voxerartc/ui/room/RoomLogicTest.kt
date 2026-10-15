package com.naury.voxerartc.ui.room

import com.google.common.truth.Truth.assertThat
import com.naury.voxerartc.api.MetricDirection
import com.naury.voxerartc.api.MetricKind
import com.naury.voxerartc.api.MetricSource
import com.naury.voxerartc.api.MetricUnit
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.api.QualityMetric
import com.naury.voxerartc.api.QualitySnapshot
import com.naury.voxerartc.api.VideoEffect
import com.naury.voxerartc.api.VideoSource
import com.naury.voxerartc.api.VideoTrackHandle
import com.naury.voxerartc.ui.prejoin.levelOf
import androidx.compose.ui.graphics.Color
import org.junit.Test

class RoomLogicTest {

    private fun person(id: String, local: Boolean = false, speaking: Boolean = false, level: Float = 0f, camera: Boolean = false, share: Boolean = false) =
        Participant(
            id = id,
            displayName = id.uppercase(),
            isLocal = local,
            isSpeaking = speaking,
            audioLevel = level,
            camera = if (camera) VideoTrackHandle(id, "$id-cam", VideoSource.CAMERA) else null,
            screenShare = if (share) VideoTrackHandle(id, "$id-screen", VideoSource.SCREEN_SHARE) else null,
        )

    @Test
    fun `통화 상태는 가장 나쁜 값을 따르고 근거가 없으면 고르지 않는다`() {
        assertThat(healthOf(null, null, null)).isNull()
        assertThat(healthOf(40.0, 0.1, 5.0)).isEqualTo(CallHealth.STABLE)
        assertThat(healthOf(250.0, 0.1, 5.0)).isEqualTo(CallHealth.FAIR)
        assertThat(healthOf(40.0, 3.0, null)).isEqualTo(CallHealth.FAIR)
        assertThat(healthOf(40.0, 0.0, 60.0)).isEqualTo(CallHealth.POOR)
        assertThat(healthOf(null, 8.0, null)).isEqualTo(CallHealth.POOR)
    }

    @Test
    fun `품질 기록은 최근 30개만 두고 없는 값은 평균에서 뺀다`() {
        val history = QualityHistory()
        repeat(40) { index ->
            val rtt = if (index % 2 == 0) index.toDouble() else null
            history.add(
                QualitySnapshot(
                    timestampMs = index.toLong(),
                    source = MetricSource.ENGINE,
                    metrics = listOf(QualityMetric(MetricKind.ROUND_TRIP_TIME, MetricDirection.NONE, rtt, MetricUnit.MILLISECONDS)),
                ),
            )
        }
        assertThat(history.samples).hasSize(30)
        assertThat(history.series(MetricKind.ROUND_TRIP_TIME, MetricDirection.NONE)).hasSize(15)
        assertThat(history.average(MetricKind.ROUND_TRIP_TIME, MetricDirection.NONE)).isEqualTo((10..38 step 2).average())
        assertThat(history.average(MetricKind.JITTER, MetricDirection.RECEIVE)).isNull()
    }

    @Test
    fun `입퇴장 알림은 처음 목록과 로컬 참가자를 알리지 않는다`() {
        var id = 0L
        val first = listOf(person("me", local = true), person("a"))
        assertThat(noticesBetween(null, first) { ++id }).isEmpty()
        val next = listOf(person("me", local = true), person("b"))
        val notices = noticesBetween(first, next) { ++id }
        assertThat(notices.map { it.participantId to it.joined }).containsExactly("b" to true, "a" to false).inOrder()
        assertThat(noticesBetween(next, next.filterNot { it.isLocal }) { ++id }).isEmpty()
    }

    @Test
    fun `영상 격자는 9명씩 쪽을 나누고 첫 쪽 맨 앞은 나다`() {
        val people = (1..20).map { person("p$it") } + person("me", local = true)
        val pages = gridPages(people)
        assertThat(pages.map { it.size }).containsExactly(9, 9, 3).inOrder()
        assertThat(pages.first().first().isLocal).isTrue()
        assertThat(gridPages(emptyList())).containsExactly(emptyList<Participant>())
    }

    @Test
    fun `PiP는 공유 화면, 말하는 사람, 마지막 발화자 순으로 고른다`() {
        val me = person("me", local = true, camera = true)
        val a = person("a", camera = true)
        val b = person("b", speaking = true, level = 0.4f)
        val c = person("c", speaking = true, level = 0.9f)
        assertThat(pipFocus(listOf(me, a, b, c), null)?.id).isEqualTo("c")
        assertThat(pipFocus(listOf(me, a, person("b"), person("c")), lastSpeakerId = "c")?.id).isEqualTo("c")
        assertThat(pipFocus(listOf(me, a, person("b")), null)?.id).isEqualTo("a")
        assertThat(pipFocus(listOf(me, a, c, person("s", share = true)), null)?.id).isEqualTo("s")
        assertThat(pipFocus(listOf(me), null)?.id).isEqualTo("me")
        // 통화 화면에서 크게 보던 사람이 있으면 말하는 사람보다 먼저.
        assertThat(pipFocus(listOf(me, a, b, c), null, pinnedId = "a")?.id).isEqualTo("a")
    }

    @Test
    fun `마이크 레벨은 조용하면 0, 최대 음량이면 1에 가깝다`() {
        assertThat(levelOf(ShortArray(100), 100)).isEqualTo(0f)
        assertThat(levelOf(ShortArray(100) { Short.MAX_VALUE }, 100)).isWithin(0.01f).of(1f)
        val quiet = levelOf(ShortArray(100) { 30 }, 100)
        assertThat(quiet).isLessThan(0.2f)
        assertThat(levelOf(ShortArray(0), 0)).isEqualTo(0f)
    }

    @Test
    fun `효과 미리보기 색은 흑백이면 회색, 효과 없으면 원래 색`() {
        val color = Color(0.8f, 0.4f, 0.2f)
        val mono = applyEffect(color, VideoEffect(saturation = 0f))
        assertThat(mono.red).isWithin(0.001f).of(mono.green)
        assertThat(mono.green).isWithin(0.001f).of(mono.blue)
        val same = applyEffect(color, VideoEffect.None)
        assertThat(same.red).isWithin(0.001f).of(color.red)
        assertThat(same.blue).isWithin(0.001f).of(color.blue)
    }
}
