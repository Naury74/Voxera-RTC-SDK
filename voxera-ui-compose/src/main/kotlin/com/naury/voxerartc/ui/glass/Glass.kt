package com.naury.voxerartc.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naury.voxerartc.ui.theme.VoxeraColors
import com.naury.voxerartc.ui.theme.VoxeraGlassQuality
import com.naury.voxerartc.ui.theme.VoxeraRadius
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme

/**
 * 반투명 표면 단계. 채움 불투명도: [Low] 0.92(설정 카드·대화상자), [Medium] 0.80(통화 조작 막대·영상 위 배지),
 * [High] 0.96(바텀 시트, 글자가 많아 뒤 화면이 비치지 않게 높였다). 글자가 잘 읽히지 않으면 흐림 대신 불투명도를 올린다.
 */
public enum class GlassLevel(internal val alpha: Float, internal val shadow: Dp) {
    Low(0.92f, 4.dp),
    Medium(0.80f, 8.dp),
    High(0.96f, 16.dp),
}

/** [level]과 [quality]에 맞는 채움 색. OFF면 불투명, REDUCED면 0.95 이상. */
internal fun glassFill(colors: VoxeraColors, level: GlassLevel, quality: VoxeraGlassQuality): Color {
    val alpha = when (quality) {
        VoxeraGlassQuality.OFF -> 1f
        VoxeraGlassQuality.REDUCED -> maxOf(level.alpha, 0.95f)
        VoxeraGlassQuality.FULL, VoxeraGlassQuality.AUTO -> level.alpha
    }
    return colors.glassTint.copy(alpha = alpha)
}

/**
 * Voxera 유리 질감. 층 순서는 그림자 → 반투명 채움 → 위쪽 반사광 → 1dp 테두리다.
 *
 * 뒤 배경을 흐리게 하지 않는다. `Modifier.blur`는 대상 자체를 흐리게 하므로 쓰지 않으며, 영상 Surface 위에서는
 * 이 반투명 채움이 대체 표현이다.
 */
@Composable
public fun Modifier.voxeraGlass(shape: Shape, level: GlassLevel = GlassLevel.Medium): Modifier {
    val colors = VoxeraTheme.colors
    val quality = VoxeraTheme.glassQuality
    val translucent = quality == VoxeraGlassQuality.FULL || quality == VoxeraGlassQuality.AUTO
    val sheen = Brush.verticalGradient(0f to colors.glassHighlight, 0.45f to Color.Transparent)
    return this
        .shadow(level.shadow, shape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f))
        .clip(shape)
        .background(glassFill(colors, level, quality))
        .then(if (translucent) Modifier.background(sheen) else Modifier)
        .border(1.dp, colors.glassStroke, shape)
}

/** 유리 질감 상자. */
@Composable
public fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(VoxeraRadius.lg),
    level: GlassLevel = GlassLevel.Low,
    contentPadding: PaddingValues = PaddingValues(VoxeraSpacing.md),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.voxeraGlass(shape, level).padding(contentPadding), content = content)
}

/** 통화 조작 막대. 모서리 28dp, [GlassLevel.Medium]. 위치는 화면이 하단 안전 영역 안에 고정한다. */
@Composable
public fun GlassDock(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .voxeraGlass(RoundedCornerShape(VoxeraRadius.dock), GlassLevel.Medium)
            .padding(horizontal = VoxeraSpacing.sm, vertical = VoxeraSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * 영상 위 이름·상태 알약. 흰 벽 같은 밝은 영상에서도 [VoxeraColors.onScrim] 글자가 읽히도록 불투명도를 0.75 이상으로 둔다.
 */
@Composable
public fun GlassPill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val scrim = VoxeraTheme.colors.scrim
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(scrim.copy(alpha = maxOf(scrim.alpha, PILL_MIN_ALPHA)))
            .padding(horizontal = VoxeraSpacing.sm, vertical = VoxeraSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

internal const val PILL_MIN_ALPHA = 0.75f
