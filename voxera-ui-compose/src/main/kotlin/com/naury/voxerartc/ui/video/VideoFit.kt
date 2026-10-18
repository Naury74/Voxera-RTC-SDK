package com.naury.voxerartc.ui.video

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.naury.voxerartc.api.VideoTrackHandle
import com.naury.voxerartc.ui.theme.VoxeraTheme
import kotlin.math.min

/** 타일 안에 영상을 놓는 방법. [Cover]는 타일을 채우고 넘치는 가장자리를 자르고, [Contain]은 영상 전체를 보여 주고 남는 곳을 비운다. */
internal enum class VideoFit { Cover, Contain }

/**
 * 영상이 놓이는 자리. 자리마다 잘림을 얼마나 참을지가 다르다.
 *
 * @property minVisible 채웠을 때 보여야 하는 영상 넓이의 최소 비율. 이보다 많이 잘리면 전체를 보여 준다.
 */
internal enum class VideoSpot(val minVisible: Float) {
    /** 1:1 전체 화면, 크게 보기, PiP. 크게 보는 자리라 많이 자르지 않는다. */
    Large(0.75f),

    /** 격자와 아래 띠. 칸이 작아 꽉 차 보이는 쪽을 택하되, 영상 절반 넘게 잘리면 전체를 보여 준다. */
    Small(0.5f),

    /** 내 작은 창. 내 모습은 늘 채운다. */
    Self(0f),
}

/**
 * [videoAspect](가로/세로) 영상을 [boxAspect] 자리에 놓을 방법. 화면 공유는 글자가 잘리지 않게 늘 전체를 보여 주고,
 * 영상 크기를 아직 모르면 채운다.
 */
internal fun videoFit(videoAspect: Float?, boxAspect: Float, spot: VideoSpot, screenShare: Boolean): VideoFit {
    if (screenShare) return VideoFit.Contain
    if (videoAspect == null || videoAspect <= 0f || boxAspect <= 0f || !boxAspect.isFinite()) return VideoFit.Cover
    val visible = min(videoAspect / boxAspect, boxAspect / videoAspect)
    return if (visible >= spot.minVisible) VideoFit.Cover else VideoFit.Contain
}

/**
 * 영상을 비율에 맞춰 놓는다. 렌더러가 알려 준 프레임 크기로 [videoFit]을 정하고, 영상 View를 그 비율 그대로의 크기로 둔다.
 * 채울 때는 View가 자리보다 커지고 넘치는 곳은 잘리며, 전체를 보여 줄 때는 남는 곳에 어두운 띠가 생긴다.
 * 상대가 휴대전화를 돌리거나 자리 크기가 바뀌면 크기가 부드럽게 바뀐다.
 */
@Composable
internal fun FittedVideo(
    renderer: VoxeraVideoRenderer,
    track: VideoTrackHandle,
    mirror: Boolean,
    spot: VideoSpot,
    screenShare: Boolean,
    modifier: Modifier = Modifier,
) {
    var frame by remember(track) { mutableStateOf<IntSize?>(null) }
    val colors = VoxeraTheme.colors
    // 어두운 테마에서는 바탕과 같은 색이면 타일 경계가 사라지므로 바탕보다 한 단계 밝게 둔다.
    val letterbox = if (colors.isDark) colors.surfaceElevated else Letterbox
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val boxAspect = maxWidth / maxHeight
        val aspect = frame?.let { it.width.toFloat() / it.height }
        val fit = videoFit(aspect, boxAspect, spot, screenShare)
        val (width, height) = videoBox(maxWidth, maxHeight, aspect, fit)
        // 첫 프레임 크기를 알 때는 바로 맞추고, 그 뒤 비율이나 자리가 바뀔 때만 움직인다.
        val known = aspect != null
        val motion = if (known) spring<Dp>(stiffness = 400f) else snap()
        val w by animateDpAsState(width, motion, label = "video-w")
        val h by animateDpAsState(height, motion, label = "video-h")
        val bars by animateFloatAsState(if (fit == VideoFit.Contain && known) 1f else 0f, label = "video-bars")
        if (bars > 0f) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = bars }.background(letterbox))
        }
        renderer.Render(track, mirror, Modifier.requiredSize(w, h)) { frame = it }
    }
}

/** 자리 [width]×[height]에 [aspect] 영상을 [fit]으로 놓을 때 영상 View 크기. 비율을 모르면 자리 크기 그대로다. */
internal fun videoBox(width: Dp, height: Dp, aspect: Float?, fit: VideoFit): Pair<Dp, Dp> {
    if (aspect == null || width.value <= 0f || height.value <= 0f) return width to height
    val wider = aspect > width / height
    return when (fit) {
        VideoFit.Cover -> if (wider) height * aspect to height else width to width / aspect
        VideoFit.Contain -> if (wider) width to width / aspect else height * aspect to height
    }
}

/** 밝은 테마에서 전체를 보여 줄 때 남는 곳의 색. 영상 둘레는 어둡게 둔다. */
private val Letterbox = Color(0xFF0B0D10)
