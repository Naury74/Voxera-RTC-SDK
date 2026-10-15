package com.naury.voxerartc.ui.room

import com.google.common.truth.Truth.assertThat
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.api.VideoSource
import com.naury.voxerartc.api.VideoTrackHandle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import org.junit.Test

class StagePlanTest {

    private val area = TileRect(0f, 100f, 1000f, 1600f)
    private val full = TileRect(0f, 0f, 1000f, 2000f)

    private fun people(count: Int, speaking: Int? = null) = (0 until count).map {
        Participant(id = "p$it", displayName = "P$it", isLocal = it == 0, isSpeaking = it == speaking)
    }

    private fun plan(
        participants: List<Participant>,
        pinned: String? = null,
        view: StageView = StageView.GALLERY,
        last: String? = null,
        landscape: Boolean = false,
        corner: Corner = Corner.BOTTOM_END,
    ) = planStage(participants, pinned, view, last, area, full, landscape, gap = 10f, strip = 200f, floatingWidth = 300f, floatingMargin = 20f, corner = corner)

    @Test
    fun `격자는 영역 안에 겹치지 않게 놓이고 마지막 줄은 가운데로 모인다`() {
        val tiles = plan(people(5)).tiles
        assertThat(tiles).hasSize(5)
        tiles.forEach { t ->
            assertThat(t.rect.x).isAtLeast(area.x)
            assertThat(t.rect.right).isAtMost(area.right + 0.5f)
            assertThat(t.rect.bottom).isAtMost(area.bottom + 0.5f)
        }
        // 5명은 2열 3행. 마지막 한 칸은 가운데.
        val last = tiles.last().rect
        assertThat(last.x + last.width / 2).isWithin(1f).of(area.x + area.width / 2)
    }

    @Test
    fun `고정하면 그 사람이 크게, 나머지는 같은 key로 작은 줄에 남는다`() {
        val plan = plan(people(4), pinned = "p2")
        assertThat(plan.focusKey).isEqualTo("p2")
        val focus = plan.tiles.single { it.role == TileRole.FOCUS }
        assertThat(focus.rect.width).isEqualTo(area.width)
        assertThat(plan.tiles.filter { it.role == TileRole.THUMB }.map { it.key }).containsExactly("p0", "p1", "p3").inOrder()
    }

    @Test
    fun `발표자 보기는 화면이 정한 마지막 발화자를 크게 본다`() {
        assertThat(plan(people(4, speaking = 3), view = StageView.SPEAKER, last = "p1").focusKey).isEqualTo("p1")
        assertThat(plan(people(4, speaking = 3), view = StageView.SPEAKER).focusKey).isEqualTo("p3")
    }

    @Test
    fun `크게 보기의 작은 줄은 모두를 한 줄에 놓고 넘친 만큼 넘겨 볼 수 있다`() {
        val plan = plan(people(12, speaking = 11), pinned = "p1")
        val thumbs = plan.tiles.filter { it.role == TileRole.THUMB }
        assertThat(thumbs).hasSize(11)
        assertThat(thumbs.map { it.key }).isEqualTo((0 until 12).map { "p$it" }.filter { it != "p1" })
        val strip = plan.strip!!
        assertThat(strip.horizontal).isTrue()
        assertThat(strip.maxScroll).isGreaterThan(0f)
        // 처음에는 오른쪽 끝 사람이 안 보이고, 끝까지 넘기면 보인다.
        val last = thumbs.last().rect
        assertThat(strip.isNearlyVisible(last, 0f)).isFalse()
        assertThat(strip.isNearlyVisible(last, strip.maxScroll)).isTrue()
        assertThat(strip.shift(30f)).isEqualTo(IntOffset(-30, 0))
    }

    @Test
    fun `다 들어가면 넘기지 않는다`() {
        assertThat(plan(people(3), pinned = "p1").strip!!.maxScroll).isEqualTo(0f)
    }

    @Test
    fun `1대1은 상대가 화면 전체, 나는 고른 모서리, 나를 고정하면 서로 바뀐다`() {
        val two = people(2)
        val normal = plan(two, corner = Corner.TOP_START)
        assertThat(normal.tiles.single { it.role == TileRole.FULL }.key).isEqualTo("p1")
        val small = normal.tiles.single { it.role == TileRole.FLOATING }
        assertThat(small.key).isEqualTo("p0")
        assertThat(small.rect.x).isEqualTo(20f)
        assertThat(small.rect.y).isEqualTo(area.y)
        assertThat(plan(two, pinned = "p0").tiles.single { it.role == TileRole.FULL }.key).isEqualTo("p0")
    }

    @Test
    fun `화면 공유는 공유 화면이 크게, 공유한 사람도 작은 줄에 있다`() {
        val share = people(3).mapIndexed { i, p -> if (i == 2) p.copy(screenShare = VideoTrackHandle("p2", "s", VideoSource.SCREEN_SHARE)) else p }
        val plan = plan(share, landscape = true)
        assertThat(plan.focusKey).isEqualTo("screen:p2")
        assertThat(plan.tiles.filter { it.role == TileRole.THUMB }.map { it.key }).containsExactly("p0", "p1", "p2")
        // 가로 화면은 작은 줄이 오른쪽.
        val focus = plan.tiles.first().rect
        assertThat(plan.tiles[1].rect.x).isGreaterThan(focus.right)
    }

    @Test
    fun `10명 이상 갤러리는 쪽 넘김으로, 놓은 자리는 가까운 모서리로`() {
        assertThat(plan(people(10)).paged).isTrue()
        assertThat(nearestCorner(Offset(900f, 100f), full)).isEqualTo(Corner.TOP_END)
        assertThat(nearestCorner(Offset(100f, 1900f), full)).isEqualTo(Corner.BOTTOM_START)
    }
}
