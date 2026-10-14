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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import com.naury.voxerartc.api.VideoTrackHandle
import com.naury.voxerartc.ui.component.avatarColors
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

/**
 * 오프라인 데모용 가짜 영상. 인물 이미지 없이 참가자별 색으로 천천히 움직이는 추상 화면을 그린다.
 * 실제 카메라 영상처럼 보이지 않도록 단순한 그라디언트만 쓴다.
 */
internal val SimulatedVideoRenderer = VoxeraVideoRenderer { track, _, modifier ->
    val (start, end) = avatarColors(track.participantId, VoxeraTheme.colors)
    val canvas = VoxeraTheme.colors.canvas
    val phase by rememberInfiniteTransition(label = "demo-video").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "phase",
    )
    Canvas(modifier) {
        val a = lerp(canvas, start, 0.55f)
        val b = lerp(canvas, end, 0.45f)
        drawRect(
            Brush.linearGradient(
                listOf(a, b),
                start = Offset(size.width * phase, 0f),
                end = Offset(size.width * (1f - phase), size.height),
            ),
        )
    }
}
