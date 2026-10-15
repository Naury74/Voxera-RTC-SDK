package com.naury.voxerartc.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/**
 * Voxera Glass 의미 색상. 모든 화면과 컴포넌트는 이 토큰만 쓰며 화면별 색을 따로 두지 않는다.
 * 바탕은 무채색 단색이고 색은 강조색 하나로만 쓴다. 표면을 그라디언트로 칠하지 않는다.
 *
 * - [canvas]: 영상이 없는 화면 배경. [surface]/[surfaceElevated]: 설정·목록·카드.
 * - [glassTint]/[glassStroke]/[glassHighlight]: 영상 위 조작 패널의 반투명 채움, 1dp 테두리, 위쪽 반사광.
 * - [scrim]: 영상 위 글자를 읽히게 하는 어두운 막. [onScrim]은 그 위 글자색으로 두 테마 모두 밝은 색이다.
 * - [warning]: "테스트 전용" 같은 주의 문구. 상태는 색만으로 전달하지 않고 글자·아이콘을 함께 쓴다.
 */
@Immutable
public data class VoxeraColors(
    val canvas: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val glassTint: Color,
    val glassStroke: Color,
    val glassHighlight: Color,
    val foreground: Color,
    val foregroundSecondary: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val danger: Color,
    val onDanger: Color,
    val warning: Color,
    val scrim: Color,
    val onScrim: Color,
    val focusRing: Color,
    val isDark: Boolean,
) {
    public companion object {
        public fun dark(): VoxeraColors = VoxeraColors(
            canvas = Color(0xFF0B0E14),
            surface = Color(0xFF151A22),
            surfaceElevated = Color(0xFF1E242E),
            glassTint = Color(0xCC1A1F28),
            glassStroke = Color(0x29FFFFFF),
            glassHighlight = Color(0x14FFFFFF),
            foreground = Color(0xFFF4F6FA),
            foregroundSecondary = Color(0xFFA3ACBA),
            primary = Color(0xFF16D2B0),
            onPrimary = Color(0xFF032A23),
            secondary = Color(0xFF5AA3FF),
            danger = Color(0xFFFF5663),
            onDanger = Color(0xFF2B0307),
            warning = Color(0xFFFFB547),
            scrim = Color(0xB3080A0E),
            onScrim = Color(0xFFF5FAFF),
            focusRing = Color(0xFF9CDFFF),
            isDark = true,
        )

        public fun light(): VoxeraColors = VoxeraColors(
            canvas = Color(0xFFF2F4F7),
            surface = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xFFE8EBF0),
            glassTint = Color(0xD9FFFFFF),
            glassStroke = Color(0x1F0B0E14),
            glassHighlight = Color(0x66FFFFFF),
            foreground = Color(0xFF12161D),
            foregroundSecondary = Color(0xFF5A6372),
            // 설계 초안 #008E78은 흰 글자와 4.09:1이라 WCAG 4.5:1을 넘도록 조정했다.
            primary = Color(0xFF007D69),
            onPrimary = Color(0xFFFFFFFF),
            secondary = Color(0xFF286DDD),
            danger = Color(0xFFD72841),
            onDanger = Color(0xFFFFFFFF),
            warning = Color(0xFF8A4B00),
            scrim = Color(0x8C0B0E14),
            onScrim = Color(0xFFF5FAFF),
            focusRing = Color(0xFF1254AE),
            isDark = false,
        )
    }
}

/** WCAG 2.x 대비율(1~21). 반투명 [foreground]는 [background] 위에 합성한 색으로 계산한다. */
public fun contrastRatio(foreground: Color, background: Color): Float {
    val bg = if (background.alpha < 1f) background.compositeOver(Color.Black) else background
    val fg = foreground.compositeOver(bg)
    val a = fg.luminance() + 0.05f
    val b = bg.luminance() + 0.05f
    return if (a > b) a / b else b / a
}
