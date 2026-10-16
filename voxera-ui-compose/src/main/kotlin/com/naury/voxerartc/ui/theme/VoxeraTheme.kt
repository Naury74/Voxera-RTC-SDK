package com.naury.voxerartc.ui.theme

import android.content.Context
import android.os.PowerManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KineticaColors
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.SurfaceStyle

/** 밝기 테마. [SYSTEM]은 호스트 앱의 uiMode를 따른다. */
public enum class VoxeraThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 반투명 조작 패널 품질.
 *
 * - [AUTO]: 배터리 절약 모드나 고대비 설정에서는 [REDUCED]로 낮춘다.
 * - [FULL]: 반투명 tint, 테두리, 반사광을 모두 그린다. 배경 흐림(backdrop blur)은 어느 단계에서도 쓰지 않는다. Android에는 영상 Surface 뒤를
 *   흐리게 하는 범용 방법이 없으므로 흐린 것처럼 흉내 내지도 않는다.
 * - [REDUCED]: 불투명도를 높여 영상 위 글자 대비를 우선한다.
 * - [OFF]: 불투명 표면.
 */
public enum class VoxeraGlassQuality { AUTO, FULL, REDUCED, OFF }

/**
 * 호스트 색 덮어쓰기. `null`인 항목은 기본 토큰을 쓴다. 글자 대비가 부족한 값은 받아들이지 않는다([resolveVoxeraColors]).
 */
@Immutable
public data class VoxeraPaletteOverride(
    val primary: Color? = null,
    val onPrimary: Color? = null,
    val secondary: Color? = null,
    val danger: Color? = null,
    val canvas: Color? = null,
    val surface: Color? = null,
    val foreground: Color? = null,
    val foregroundSecondary: Color? = null,
)

/**
 * Voxera UI 테마 설정. 호스트의 `MaterialTheme`는 바꾸지 않으며 Voxera 화면 안에서만 적용된다.
 * 설정을 바꾸면 UI만 다시 그리고 세션과 영상 트랙은 다시 만들지 않는다.
 *
 * @property highContrast `true`면 보조 글자색을 기본 글자색으로 올리고 패널을 불투명하게 한다.
 */
@Immutable
public data class VoxeraThemeConfig(
    val mode: VoxeraThemeMode = VoxeraThemeMode.SYSTEM,
    val glassQuality: VoxeraGlassQuality = VoxeraGlassQuality.AUTO,
    val palette: VoxeraPaletteOverride? = null,
    val fontFamily: FontFamily? = null,
    val highContrast: Boolean = false,
)

/** 덮어쓰기를 적용한 색과, 대비 부족으로 받아들이지 않은 항목 이름. */
public data class ResolvedVoxeraColors(val colors: VoxeraColors, val rejected: List<String>)

/**
 * [base]에 [override]를 적용한다. 글자와 배경의 대비가 일반 글자는 4.5:1, 강조 버튼은 3:1에 못 미치면
 * 그 항목은 버리고 기본값을 쓴다.
 */
public fun resolveVoxeraColors(base: VoxeraColors, override: VoxeraPaletteOverride?, highContrast: Boolean): ResolvedVoxeraColors {
    val rejected = mutableListOf<String>()
    var colors = base
    if (override != null) {
        fun pick(name: String, candidate: Color?, current: Color, accept: (Color) -> Boolean): Color =
            when {
                candidate == null -> current
                accept(candidate) -> candidate
                else -> current.also { rejected += name }
            }
        val canvas = pick("canvas", override.canvas, base.canvas) { contrastRatio(base.foreground, it) >= TEXT_CONTRAST }
        val surface = pick("surface", override.surface, base.surface) { contrastRatio(base.foreground, it) >= TEXT_CONTRAST }
        val foreground = pick("foreground", override.foreground, base.foreground) {
            contrastRatio(it, canvas) >= TEXT_CONTRAST && contrastRatio(it, surface) >= TEXT_CONTRAST
        }
        val foregroundSecondary = pick("foregroundSecondary", override.foregroundSecondary, base.foregroundSecondary) {
            contrastRatio(it, canvas) >= TEXT_CONTRAST
        }
        val primary = pick("primary", override.primary, base.primary) { contrastRatio(it, canvas) >= GRAPHIC_CONTRAST }
        val onPrimary = pick("onPrimary", override.onPrimary, if (primary == base.primary) base.onPrimary else readableOn(primary)) {
            contrastRatio(it, primary) >= TEXT_CONTRAST
        }
        val secondary = pick("secondary", override.secondary, base.secondary) { contrastRatio(it, canvas) >= GRAPHIC_CONTRAST }
        val danger = pick("danger", override.danger, base.danger) { contrastRatio(it, canvas) >= GRAPHIC_CONTRAST }
        colors = base.copy(
            canvas = canvas,
            surface = surface,
            foreground = foreground,
            foregroundSecondary = foregroundSecondary,
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            danger = danger,
            onDanger = readableOn(danger),
        )
    }
    if (highContrast) {
        colors = colors.copy(
            foregroundSecondary = colors.foreground,
            glassTint = colors.surface,
            glassStroke = colors.foreground.copy(alpha = 0.6f),
        )
    }
    return ResolvedVoxeraColors(colors, rejected)
}

private const val TEXT_CONTRAST = 4.5f
private const val GRAPHIC_CONTRAST = 3f

/** [background] 위에서 더 잘 읽히는 흰색 또는 검정에 가까운 글자색. */
internal fun readableOn(background: Color): Color {
    val dark = Color(0xFF0A1420)
    return if (contrastRatio(Color.White, background) >= contrastRatio(dark, background)) Color.White else dark
}

/** 현재 테마에서 쓰는 값 묶음. */
@Immutable
public data class VoxeraTokens(
    val colors: VoxeraColors,
    val typography: VoxeraTypography,
    val glassQuality: VoxeraGlassQuality,
    val rejectedOverrides: List<String> = emptyList(),
)

/** 간격(dp): 4, 8, 12, 16, 20, 24, 32. */
public object VoxeraSpacing {
    public val xxs: androidx.compose.ui.unit.Dp = 4.dp
    public val xs: androidx.compose.ui.unit.Dp = 8.dp
    public val sm: androidx.compose.ui.unit.Dp = 12.dp
    public val md: androidx.compose.ui.unit.Dp = 16.dp
    public val lg: androidx.compose.ui.unit.Dp = 20.dp
    public val xl: androidx.compose.ui.unit.Dp = 24.dp
    public val xxl: androidx.compose.ui.unit.Dp = 32.dp
}

/** 모서리 반지름(dp): 12, 16, 20, 24, 32와 통화 조작 막대 28. */
public object VoxeraRadius {
    public val sm: androidx.compose.ui.unit.Dp = 12.dp
    public val md: androidx.compose.ui.unit.Dp = 16.dp
    public val lg: androidx.compose.ui.unit.Dp = 20.dp
    public val xl: androidx.compose.ui.unit.Dp = 24.dp
    public val xxl: androidx.compose.ui.unit.Dp = 32.dp
    public val dock: androidx.compose.ui.unit.Dp = 28.dp
}

private val LocalVoxeraTokens = staticCompositionLocalOf {
    VoxeraTokens(VoxeraColors.dark(), VoxeraTypography.default(), VoxeraGlassQuality.FULL)
}

/** [VoxeraTheme] 안에서 현재 토큰을 읽는다. */
public object VoxeraTheme {
    public val colors: VoxeraColors
        @Composable get() = LocalVoxeraTokens.current.colors

    public val typography: VoxeraTypography
        @Composable get() = LocalVoxeraTokens.current.typography

    /** AUTO를 실제 값으로 풀어 낸 품질. 컴포넌트는 이 값을 보고 투명도를 정한다. */
    public val glassQuality: VoxeraGlassQuality
        @Composable get() = LocalVoxeraTokens.current.glassQuality

    public val tokens: VoxeraTokens
        @Composable get() = LocalVoxeraTokens.current
}

/** Voxera 토큰을 적용한다. 호스트의 `MaterialTheme`는 그대로 둔다. */
@Composable
public fun VoxeraTheme(config: VoxeraThemeConfig = VoxeraThemeConfig(), content: @Composable () -> Unit) {
    val dark = when (config.mode) {
        VoxeraThemeMode.SYSTEM -> isSystemInDarkTheme()
        VoxeraThemeMode.LIGHT -> false
        VoxeraThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val powerSave = remember(context) { context.isPowerSaveMode() }
    val tokens = remember(config, dark, powerSave) {
        val resolved = resolveVoxeraColors(if (dark) VoxeraColors.dark() else VoxeraColors.light(), config.palette, config.highContrast)
        VoxeraTokens(
            colors = resolved.colors,
            typography = VoxeraTypography.default(config.fontFamily ?: FontFamily.Default),
            glassQuality = resolveGlassQuality(config.glassQuality, powerSave = powerSave, highContrast = config.highContrast),
            rejectedOverrides = resolved.rejected,
        )
    }
    CompositionLocalProvider(LocalVoxeraTokens provides tokens) {
        // 시트·토스트·스위치 같은 구성 요소는 Kinetica UI 키트로 그린다. 같은 색과 글꼴을 쓰도록 Voxera 토큰을 넘기고,
        // 누름 표시도 요소 모양을 따라 옅게 덮였다 사라지는 Kinetica 방식으로 통일한다.
        KineticaTheme(
            darkTheme = tokens.colors.isDark,
            surface = tokens.colors.kineticaSurface(),
            colors = tokens.colors.kineticaColors(),
            textStyle = TextStyle(fontFamily = config.fontFamily ?: FontFamily.Default),
            content = content,
        )
    }
}

/** Voxera 표면 색을 Kinetica 단색 표면으로 옮긴다. 영상 위 패널은 따로 글래스를 고른다. */
internal fun VoxeraColors.kineticaSurface(): SurfaceStyle = SurfaceStyle.Solid(
    color = surface,
    contentColor = foreground,
    background = canvas,
    border = glassStroke,
    borderWidth = 1.dp,
)

internal fun VoxeraColors.kineticaColors(): KineticaColors = KineticaColors(
    accent = primary,
    onAccent = onPrimary,
    positive = primary,
    onPositive = onPrimary,
    destructive = danger,
    onDestructive = onDanger,
)

/** AUTO를 실제 품질로 바꾼다. 배터리 절약이나 고대비면 불투명도를 높인다. */
internal fun resolveGlassQuality(requested: VoxeraGlassQuality, powerSave: Boolean, highContrast: Boolean): VoxeraGlassQuality = when {
    requested != VoxeraGlassQuality.AUTO -> requested
    highContrast -> VoxeraGlassQuality.OFF
    powerSave -> VoxeraGlassQuality.REDUCED
    else -> VoxeraGlassQuality.FULL
}

private fun Context.isPowerSaveMode(): Boolean =
    (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
