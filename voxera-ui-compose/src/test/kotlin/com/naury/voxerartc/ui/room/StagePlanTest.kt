package com.naury.voxerartc.ui.room

import com.google.common.truth.Truth.assertThat
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.api.VideoSource
import com.naury.voxerartc.api.VideoTrackHandle
import org.junit.Test

class StagePlanTest {

    private fun people(count: Int, speaking: Int? = null) = (0 until count).map {
        Participant(id = "p$it", displayName = "P$it", isLocal = it == 0, isSpeaking = it == speaking)
    }

    private fun model(participants: List<Participant>, pinned: String? = null, view: StageView = StageView.GALLERY, last: String? = null) =
        stageModel(participants, pinned, view, last)

    @Test
    fun `아무도 고정하지 않은 갤러리는 크게 보는 사람 없이 격자다`() {
        val model = model(people(5))
        assertThat(model.focusKey).isNull()
        assertThat(model.items.map { it.key }).containsExactly("p0", "p1", "p2", "p3", "p4").inOrder()
    }

    @Test
    fun `고정하면 그 사람이 크게, 나머지는 같은 key로 남는다`() {
        val model = model(people(4), pinned = "p2")
        assertThat(model.focusKey).isEqualTo("p2")
        assertThat(model.items.map { it.key }).containsExactly("p0", "p1", "p2", "p3").inOrder()
    }

    @Test
    fun `발표자 보기는 화면이 정한 마지막 발화자를 크게 본다`() {
        assertThat(model(people(4, speaking = 3), view = StageView.SPEAKER, last = "p1").focusKey).isEqualTo("p1")
        assertThat(model(people(4, speaking = 3), view = StageView.SPEAKER).focusKey).isEqualTo("p3")
        // 아무도 말하지 않으면 첫 상대.
        assertThat(model(people(4), view = StageView.SPEAKER).focusKey).isEqualTo("p1")
        assertThat(loudestRemote(people(4, speaking = 3))).isEqualTo("p3")
        assertThat(loudestRemote(people(4, speaking = 0))).isNull()
    }

    @Test
    fun `1대1은 내가 작은 화면이고, 나를 고정하면 상대가 작은 화면이 된다`() {
        val two = people(2)
        val normal = model(two)
        assertThat(normal.focusKey).isNull()
        assertThat(normal.selfKey).isEqualTo("p0")
        val swapped = model(two, pinned = "p0")
        assertThat(swapped.focusKey).isNull()
        assertThat(swapped.selfKey).isEqualTo("p1")
    }

    @Test
    fun `화면 공유는 공유 화면이 크게, 공유한 사람의 카메라도 남는다`() {
        val share = people(3).mapIndexed { i, p -> if (i == 2) p.copy(screenShare = VideoTrackHandle("p2", "s", VideoSource.SCREEN_SHARE)) else p }
        val model = model(share, pinned = "p1")
        assertThat(model.focusKey).isEqualTo("screen:p2")
        assertThat(model.items.map { it.key }).containsExactly("screen:p2", "p0", "p1", "p2").inOrder()
        assertThat(model.items.first().screenShare).isTrue()
    }

    @Test
    fun `내가 공유하는 화면은 무대에 크게 띄우지 않는다`() {
        val mine = people(3).mapIndexed { i, p -> if (i == 0) p.copy(screenShare = VideoTrackHandle("p0", "s", VideoSource.SCREEN_SHARE)) else p }
        assertThat(model(mine).focusKey).isNull()
    }
}
