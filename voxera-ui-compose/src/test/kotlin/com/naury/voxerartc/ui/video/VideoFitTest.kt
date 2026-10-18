package com.naury.voxerartc.ui.video

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VideoFitTest {

    private val portrait = 9f / 16f
    private val landscape = 16f / 9f

    @Test
    fun `비율이 비슷하면 채우고 많이 잘리면 전체를 보여 준다`() {
        // 세로 휴대전화 영상을 세로로 긴 1:1 전체 화면(1080×2400)에: 80% 보임 → 채움
        assertThat(videoFit(portrait, 1080f / 2400f, VideoSpot.Large, screenShare = false)).isEqualTo(VideoFit.Cover)
        // 가로 노트북 영상을 같은 자리에: 25%만 보임 → 전체
        assertThat(videoFit(landscape, 1080f / 2400f, VideoSpot.Large, screenShare = false)).isEqualTo(VideoFit.Contain)
    }

    @Test
    fun `작은 칸은 절반까지는 채우고 내 작은 창은 늘 채운다`() {
        // 4:3 영상을 정사각형 칸에: 75% 보임 → 큰 자리에서는 채움 경계, 작은 칸은 채움
        assertThat(videoFit(4f / 3f, 1f, VideoSpot.Small, screenShare = false)).isEqualTo(VideoFit.Cover)
        // 가로 영상을 세로 칸(0.6)에: 34% 보임 → 작은 칸도 전체
        assertThat(videoFit(landscape, 0.6f, VideoSpot.Small, screenShare = false)).isEqualTo(VideoFit.Contain)
        assertThat(videoFit(landscape, 0.6f, VideoSpot.Self, screenShare = false)).isEqualTo(VideoFit.Cover)
    }

    @Test
    fun `화면 공유는 늘 전체를 보여 주고 크기를 모르면 채운다`() {
        assertThat(videoFit(landscape, landscape, VideoSpot.Small, screenShare = true)).isEqualTo(VideoFit.Contain)
        assertThat(videoFit(null, 0.6f, VideoSpot.Large, screenShare = false)).isEqualTo(VideoFit.Cover)
    }

    @Test
    fun `영상 View 크기는 채우면 자리를 덮고 전체면 자리 안에 든다`() {
        // 가로 16:9 영상, 세로 자리 100×200
        assertThat(videoBox(100.dp, 200.dp, landscape, VideoFit.Contain)).isEqualTo(100.dp to (100f / landscape).dp)
        val (w, h) = videoBox(100.dp, 200.dp, landscape, VideoFit.Cover)
        assertThat(h).isEqualTo(200.dp)
        assertThat(w.value).isWithin(0.01f).of(200f * landscape)
        // 비율을 모르면 자리 그대로
        assertThat(videoBox(100.dp, 200.dp, null, VideoFit.Cover)).isEqualTo(100.dp to 200.dp)
    }
}
