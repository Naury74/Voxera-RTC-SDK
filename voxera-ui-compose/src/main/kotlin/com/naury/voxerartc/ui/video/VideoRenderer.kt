package com.naury.voxerartc.ui.video

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import com.naury.voxerartc.api.VideoEffect
import com.naury.voxerartc.api.VideoSource
import com.naury.voxerartc.api.VideoTrackHandle
import com.naury.voxerartc.ui.component.avatarColors
import com.naury.voxerartc.ui.room.applyEffect
import com.naury.voxerartc.ui.theme.VoxeraTheme

/**
 * [VideoTrackHandle]을 화면에 그리는 어댑터. RTC provider 모듈(예: voxera-livekit)이 구현해
 * [LocalVoxeraVideoRenderer]로 넘긴다. 구현체는 Composable이 사라질 때 렌더러, Surface, 트랙 리스너를 해제해야 한다.
 */
public fun interface VoxeraVideoRenderer {
    @Composable
    public fun Render(track: VideoTrackHandle, mirror: Boolean, modifier: Modifier)
}

/** 실제 영상 렌더러. `null`이면 카메라가 켜진 참가자도 아바타로 보인다. */
public val LocalVoxeraVideoRenderer: androidx.compose.runtime.ProvidableCompositionLocal<VoxeraVideoRenderer?> =
    staticCompositionLocalOf { null }

/** 데모 내 영상에 적용할 효과. 데모 세션일 때 [com.naury.voxerartc.ui.room.VoxeraRoomScreen]이 넣는다. */
internal val LocalSimulatedEffect = staticCompositionLocalOf { VideoEffect.None }

/**
 * 오프라인 데모용 가짜 영상. 인물 사진 대신 참가자별 차분한 단색 위에 사람 윤곽(머리·어깨)을 그린다.
 * 말하는 것처럼 윤곽이 아주 조금 숨 쉬듯 움직인다. 내 영상(mirror)에는 [LocalSimulatedEffect]를 적용한다.
 */
internal val SimulatedVideoRenderer = VoxeraVideoRenderer { track, mirror, modifier ->
    if (track.source == VideoSource.SCREEN_SHARE) {
        SimulatedScreen(modifier)
        return@VoxeraVideoRenderer
    }
    val colors = VoxeraTheme.colors
    val (fill, hue) = avatarColors(track.participantId, colors)
    val effect = if (mirror) LocalSimulatedEffect.current else VideoEffect.None
    val breath by rememberInfiniteTransition(label = "demo-video").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_200, easing = LinearEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val background = applyEffect(lerp(colors.canvas, fill, 0.7f), effect)
    val figure = applyEffect(lerp(background, hue, 0.22f), effect)
    Canvas(modifier) {
        drawRect(background)
        val unit = minOf(size.width, size.height)
        val cx = size.width / 2f
        val head = unit * 0.13f
        val headY = size.height * 0.5f - unit * 0.12f - unit * 0.015f * breath
        drawCircle(figure, radius = head, center = Offset(cx, headY))
        // 어깨는 넓고 낮은 곡선, 그 아래 몸통은 타일 끝까지 곧게 내려 상반신처럼 보이게 한다.
        val shoulderTop = headY + head * 1.3f
        val shoulderW = unit * 0.66f
        val shoulderH = unit * 0.28f
        drawOval(figure, topLeft = Offset(cx - shoulderW / 2f, shoulderTop), size = Size(shoulderW, shoulderH))
        drawRect(figure, topLeft = Offset(cx - shoulderW / 2f, shoulderTop + shoulderH / 2f), size = Size(shoulderW, size.height))
    }
}

/** 데모 화면 공유: 문서 면에 제목 막대·본문 줄·막대그래프를 그린다. 실제 화면처럼 보이지 않는 단순한 도형만 쓴다. */
@Composable
private fun SimulatedScreen(modifier: Modifier) {
    val colors = VoxeraTheme.colors
    Canvas(modifier) {
        val page = colors.surface
        val ink = colors.foregroundSecondary.copy(alpha = 0.35f)
        drawRect(colors.canvas)
        val margin = size.minDimension * 0.06f
        val w = size.width - margin * 2
        val h = size.height - margin * 2
        drawRect(page, topLeft = Offset(margin, margin), size = Size(w, h))
        drawRect(colors.secondary, topLeft = Offset(margin * 2, margin * 2), size = Size(w * 0.45f, h * 0.06f))
        repeat(4) { line ->
            drawRect(ink, topLeft = Offset(margin * 2, margin * 2 + h * (0.14f + line * 0.07f)), size = Size(w * (0.8f - line * 0.1f), h * 0.025f))
        }
        val bars = listOf(0.3f, 0.45f, 0.4f, 0.6f, 0.75f)
        val barWidth = w * 0.1f
        bars.forEachIndexed { index, ratio ->
            val barHeight = h * 0.35f * ratio
            drawRect(
                if (index == bars.lastIndex) colors.primary else colors.secondary.copy(alpha = 0.6f),
                topLeft = Offset(margin * 2 + index * barWidth * 1.4f, margin + h - margin - barHeight),
                size = Size(barWidth, barHeight),
            )
        }
    }
}
