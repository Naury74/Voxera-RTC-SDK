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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import com.naury.voxerartc.api.VideoEffect
import com.naury.voxerartc.api.VideoSource
import com.naury.voxerartc.api.VideoTrackHandle
import com.naury.voxerartc.ui.component.avatarColors
import com.naury.voxerartc.ui.room.applyEffect
import com.naury.voxerartc.ui.theme.VoxeraTheme

/**
 * [VideoTrackHandle]을 화면에 그리는 어댑터. RTC provider 모듈(예: voxera-livekit)이 구현해
 * [LocalVoxeraVideoRenderer]로 넘긴다. 구현은 Composable이 사라질 때 렌더러·Surface·트랙 리스너를 해제해야 한다.
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
 * 오프라인 데모용 가짜 영상. 인물 이미지 없이 참가자별 색으로 천천히 움직이는 추상 화면을 그린다.
 * 실제 카메라 영상처럼 보이지 않도록 단순한 그라디언트만 쓴다. 내 영상(mirror)에는 [LocalSimulatedEffect]를 적용한다.
 */
internal val SimulatedVideoRenderer = VoxeraVideoRenderer { track, mirror, modifier ->
    if (track.source == VideoSource.SCREEN_SHARE) {
        SimulatedScreen(modifier)
        return@VoxeraVideoRenderer
    }
    val (start, end) = avatarColors(track.participantId, VoxeraTheme.colors)
    val canvas = VoxeraTheme.colors.canvas
    val phase by rememberInfiniteTransition(label = "demo-video").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "phase",
    )
    val effect = if (mirror) LocalSimulatedEffect.current else VideoEffect.None
    Canvas(modifier) {
        val a = applyEffect(lerp(canvas, start, 0.55f), effect)
        val b = applyEffect(lerp(canvas, end, 0.45f), effect)
        drawRect(
            Brush.linearGradient(
                listOf(a, b),
                start = Offset(size.width * phase, 0f),
                end = Offset(size.width * (1f - phase), size.height),
            ),
        )
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
