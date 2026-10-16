package com.naury.voxerartc.ui.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.GlassStyle
import com.naury.kinetica.ui.glass
import com.naury.voxerartc.ui.theme.VoxeraColors
import com.naury.voxerartc.ui.theme.VoxeraGlassQuality
import com.naury.voxerartc.ui.theme.VoxeraRadius
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import com.naury.voxerartc.ui.theme.rememberReducedMotion

/**
 * 반투명 표면 단계. 채움 불투명도는 [Low] 0.92(설정 카드, 대화상자), [Medium] 0.80(통화 조작 막대, 영상 위 배지),
 * [High] 0.96(바텀 시트. 글자가 많아 뒤 화면이 비치지 않게 높였다)이다. 글자가 잘 읽히지 않으면 흐림 대신 불투명도를 올린다.
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
 * Voxera 유리 질감.
 *
 * 통화 화면처럼 배경을 기록하는 화면 안에서는 뒤에 있는 영상과 화면을 실제로 흐리게 비추고, 그 위에 [GlassLevel]만큼 옅은 색을 덮는다.
 * 기록하는 배경이 없거나 블러를 못 쓰는 기기(Android 11 이하)에서는 지금까지처럼 반투명 단색으로 칠한다.
 * 품질이 줄임(REDUCED)이나 불투명(OFF)이면 블러 없이 거의 불투명하게 칠하고, 불투명이면 반사광 없이 외곽선만 남긴다.
 */
@Composable
public fun Modifier.voxeraGlass(shape: Shape, level: GlassLevel = GlassLevel.Medium): Modifier {
    val colors = VoxeraTheme.colors
    val quality = VoxeraTheme.glassQuality
    val translucent = quality == VoxeraGlassQuality.FULL || quality == VoxeraGlassQuality.AUTO
    if (translucent) return this.glass(remember(colors, level) { colors.glassStyle(level) }, shape).clip(shape)
    val shadowColor = Color.Black.copy(alpha = if (colors.isDark) 0.35f else 0.14f)
    return this
        .shadow(level.shadow, shape, ambientColor = shadowColor, spotColor = shadowColor)
        .clip(shape)
        .background(glassFill(colors, level, quality))
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            onDrawWithContent {
                drawContent()
                drawOutline(outline, colors.glassStroke, style = Stroke(1.dp.toPx()))
            }
        }
}

/**
 * Voxera 색으로 만든 블러 유리. 흐린 배경 위에 덮는 색([GlassStyle.tint])은 블러가 글자 뒤를 가려 주므로 단색 채움보다 옅게 두고,
 * 블러를 못 쓸 때 칠하는 색([GlassStyle.fallback])은 지금까지의 반투명 채움과 같게 둔다.
 */
internal fun VoxeraColors.glassStyle(level: GlassLevel): GlassStyle = GlassStyle(
    blur = when (level) {
        GlassLevel.Low -> 20.dp
        GlassLevel.Medium -> 24.dp
        GlassLevel.High -> 32.dp
    },
    saturation = 1.5f,
    brightness = if (isDark) -0.04f else 0.02f,
    tint = glassTint.copy(alpha = level.blurTintAlpha),
    edgeLight = if (isDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.9f),
    edgeWidth = 1.dp,
    shadow = level.shadow,
    fallback = glassFill(this, level, VoxeraGlassQuality.FULL),
    onGlass = foreground,
)

/** 블러 위에 덮는 색의 불투명도. 시트는 글자가 많아 진하게 둔다. */
private val GlassLevel.blurTintAlpha: Float
    get() = when (this) {
        GlassLevel.Low -> 0.62f
        GlassLevel.Medium -> 0.48f
        GlassLevel.High -> 0.78f
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

/** 통화 조작 막대. 양 끝이 둥근 캡슐 모양이고 [GlassLevel.Medium]을 쓴다. 위치는 이 막대를 쓰는 화면이 하단 안전 영역 안에 고정한다. */
@Composable
public fun GlassDock(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .voxeraGlass(RoundedCornerShape(percent = 50), GlassLevel.Medium)
            .padding(horizontal = VoxeraSpacing.sm, vertical = VoxeraSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * 영상 위에 이름이나 상태를 띄우는 알약. 흰 벽처럼 밝은 영상 위에서도 [VoxeraColors.onScrim] 글자가 읽히도록 불투명도를 0.75 이상으로 둔다.
 */
@Composable
public fun GlassPill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val scrim = VoxeraTheme.colors.scrim
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(scrim.copy(alpha = maxOf(scrim.alpha, PILL_MIN_ALPHA)))
            .drawWithCache {
                val outline = CircleShape.createOutline(size, layoutDirection, this)
                val rim = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.06f)))
                onDrawWithContent {
                    drawContent()
                    drawOutline(outline, rim, style = Stroke(1.dp.toPx()))
                }
            }
            .padding(horizontal = VoxeraSpacing.sm, vertical = VoxeraSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

internal const val PILL_MIN_ALPHA = 0.75f

/**
 * 영상이 없는 화면의 배경. [VoxeraColors.canvas] 단색이다. 위에 올린 유리 표면은 외곽선과 그림자로 떠 보인다.
 *
 * @param emphasis 이전 버전 호환용으로 남겨 둔 값. 지금은 쓰지 않는다.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
public fun Modifier.voxeraBackdrop(emphasis: Float = 0f): Modifier = this.background(VoxeraTheme.colors.canvas)

/** 누르는 동안 살짝 작아졌다가 놓으면 튕기듯 돌아온다. 움직임 줄이기 설정이면 그대로 둔다. */
@Composable
public fun Modifier.pressScale(interaction: InteractionSource, pressed: Float = 0.94f): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val reduced = rememberReducedMotion()
    val scale by animateFloatAsState(
        if (isPressed && !reduced) pressed else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
