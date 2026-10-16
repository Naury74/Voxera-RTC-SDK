package com.naury.voxerartc.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.glowBorder
import com.naury.voxerartc.ui.theme.VoxeraTheme
import kotlin.math.roundToInt

/**
 * 말하는 사람 빛 테두리를 영상 무대 밖 위층에 그리기 위한 자리 목록.
 *
 * 무대는 유리(도크·상단 바)가 비추는 배경으로 기록된다. 그 안에서 테두리가 매 프레임 돌면 배경 기록이 매 프레임 바뀌어 유리 블러를
 * 매 프레임 다시 한다. 그래서 통화 화면에서는 타일이 자기 자리만 여기에 알리고, 테두리는 [SpeakingGlowLayer]가 무대 위층에 그린다.
 * 그러면 블러는 영상이 바뀔 때만 다시 한다.
 */
@Stable
internal class SpeakingGlows {
    internal val spots = mutableStateMapOf<Any, GlowSpot>()
}

/**
 * 타일 하나의 테두리 자리. [bounds]는 타일 전체, [visible]은 스크롤 줄 끝에 걸려 잘린 경우의 보이는 부분이다(둘 다 화면 루트 기준).
 * [level]은 그릴 때 읽는 세기(0..1)라 켜지고 꺼지는 동안 이 목록을 바꾸지 않는다.
 */
internal data class GlowSpot(val bounds: Rect, val visible: Rect, val shape: Shape, val level: () -> Float)

/** 통화 화면이 넣어 준다. 없으면 타일이 테두리를 직접 그린다. */
internal val LocalSpeakingGlows = staticCompositionLocalOf<SpeakingGlows?> { null }

/** [glows]에 모인 자리마다 빛 테두리를 그리는 층. 무대와 같은 크기·위치로, 무대 위·조작 막대 아래에 둔다. 터치는 받지 않는다. */
@Composable
internal fun SpeakingGlowLayer(glows: SpeakingGlows, modifier: Modifier = Modifier) {
    val colors = VoxeraTheme.colors
    val glowColors = remember(colors.primary, colors.secondary) { listOf(colors.primary, colors.secondary, colors.primary.copy(alpha = 0.5f)) }
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        glows.spots.forEach { (id, spot) ->
            key(id) {
                // 스크롤 줄 끝에 걸린 타일은 보이는 부분 밖으로 빛이 새지 않게 그 부분만 남긴다.
                val clipped = spot.visible != spot.bounds
                val frame = if (clipped) spot.visible else spot.bounds
                Box(
                    Modifier
                        .offset { IntOffset((frame.left - origin.x).roundToInt(), (frame.top - origin.y).roundToInt()) }
                        .size(with(density) { frame.width.toDp() }, with(density) { frame.height.toDp() })
                        .then(if (clipped) Modifier.clipToBounds() else Modifier),
                ) {
                    Box(
                        Modifier
                            .offset { IntOffset((spot.bounds.left - frame.left).roundToInt(), (spot.bounds.top - frame.top).roundToInt()) }
                            .size(with(density) { spot.bounds.width.toDp() }, with(density) { spot.bounds.height.toDp() })
                            .glowBorder(spot.level, spot.shape, glowColors, width = 3.dp, glow = 6.dp, periodMs = GLOW_PERIOD_MS),
                    )
                }
            }
        }
    }
}

internal const val GLOW_PERIOD_MS = 2_400
