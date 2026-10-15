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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Canvas
import com.naury.voxerartc.ui.theme.VoxeraColors
import com.naury.voxerartc.ui.theme.VoxeraGlassQuality
import com.naury.voxerartc.ui.theme.VoxeraRadius
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import com.naury.voxerartc.ui.theme.rememberReducedMotion

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
 * 테두리 반사광. 빛이 왼쪽 위에서 들어온다고 보고 왼쪽 위 모서리를 가장 밝게, 오른쪽 아래를 그다음으로 밝게 하며
 * 옆면은 [VoxeraColors.glassStroke] 정도로 어둡게 둔다. 두께감이 생겨 얇은 판이 떠 있는 것처럼 보인다.
 */
internal fun glassRim(colors: VoxeraColors, size: Size): Brush {
    val bright = if (colors.isDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.9f)
    val soft = if (colors.isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.4f)
    return Brush.linearGradient(
        0f to bright,
        0.32f to colors.glassStroke,
        0.68f to colors.glassStroke,
        1f to soft,
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
}

/**
 * Voxera 유리 질감. 층 순서는 그림자 → 반투명 단색 채움 → 1dp 외곽선 → 안쪽 테두리 반사광이다. 면 자체에는 그라디언트를 칠하지 않는다.
 *
 * 뒤 배경을 흐리게 하지 않는다. `Modifier.blur`는 대상 자체를 흐리게 하므로 쓰지 않으며, 영상 Surface 위에서는
 * 이 반투명 채움이 대체 표현이다. 불투명(OFF) 품질에서는 반사광을 그리지 않고 외곽선만 남긴다.
 */
@Composable
public fun Modifier.voxeraGlass(shape: Shape, level: GlassLevel = GlassLevel.Medium): Modifier {
    val colors = VoxeraTheme.colors
    val quality = VoxeraTheme.glassQuality
    val translucent = quality == VoxeraGlassQuality.FULL || quality == VoxeraGlassQuality.AUTO
    val shadowColor = Color.Black.copy(alpha = if (colors.isDark) 0.35f else 0.14f)
    return this
        .shadow(level.shadow, shape, ambientColor = shadowColor, spotColor = shadowColor)
        .clip(shape)
        .background(glassFill(colors, level, quality))
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val stroke = 1.dp.toPx()
            // 안쪽 반사광 선은 외곽선 바로 안에 그린다.
            val inner = shape.createOutline(Size(size.width - stroke * 2, size.height - stroke * 2), layoutDirection, this)
            val rim = glassRim(colors, size)
            onDrawWithContent {
                drawContent()
                drawOutline(outline, colors.glassStroke, style = Stroke(stroke))
                if (translucent) translate(stroke, stroke) { drawOutline(inner, rim, style = Stroke(stroke)) }
            }
        }
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

/** 통화 조작 막대. 양 끝이 둥근 캡슐, [GlassLevel.Medium]. 위치는 화면이 하단 안전 영역 안에 고정한다. */
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
 * 영상 위 이름·상태 알약. 흰 벽 같은 밝은 영상에서도 [VoxeraColors.onScrim] 글자가 읽히도록 불투명도를 0.75 이상으로 둔다.
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
 * @param emphasis 예전 호환용. 지금은 쓰지 않는다.
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
