package com.naury.voxerartc.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.naury.voxerartc.ui.glass.GlassLevel
import com.naury.voxerartc.ui.glass.PILL_MIN_ALPHA
import com.naury.voxerartc.ui.glass.glassFill
import org.junit.Test

class VoxeraThemeTokensTest {

    private val themes = listOf("dark" to VoxeraColors.dark(), "light" to VoxeraColors.light())

    @Test
    fun `두 테마 모두 본문 글자는 배경과 4·5대1 이상`() {
        themes.forEach { (name, c) ->
            listOf(c.canvas, c.surface, c.surfaceElevated).forEach { bg ->
                assertWithMessage("$name foreground").that(contrastRatio(c.foreground, bg)).isAtLeast(4.5f)
                assertWithMessage("$name secondary").that(contrastRatio(c.foregroundSecondary, bg)).isAtLeast(4.5f)
            }
            assertWithMessage("$name onPrimary").that(contrastRatio(c.onPrimary, c.primary)).isAtLeast(4.5f)
            assertWithMessage("$name onDanger").that(contrastRatio(c.onDanger, c.danger)).isAtLeast(4.5f)
            assertWithMessage("$name warning").that(contrastRatio(c.warning, c.canvas)).isAtLeast(4.5f)
        }
    }

    @Test
    fun `강조색과 종료 버튼은 배경과 3대1 이상`() {
        themes.forEach { (name, c) ->
            assertWithMessage("$name primary").that(contrastRatio(c.primary, c.canvas)).isAtLeast(3f)
            assertWithMessage("$name danger").that(contrastRatio(c.danger, c.canvas)).isAtLeast(3f)
            assertWithMessage("$name focus").that(contrastRatio(c.focusRing, c.canvas)).isAtLeast(3f)
        }
    }

    @Test
    fun `반투명 조작 막대 위 글자도 대비를 지킨다`() {
        themes.forEach { (name, c) ->
            val dock = glassFill(c, GlassLevel.Medium, VoxeraGlassQuality.FULL).compositeOver(c.canvas)
            assertWithMessage("$name dock").that(contrastRatio(c.foreground, dock)).isAtLeast(4.5f)
        }
    }

    @Test
    fun `영상 이름표는 흰 영상 위에서도 읽힌다`() {
        themes.forEach { (name, c) ->
            val pill = c.scrim.copy(alpha = maxOf(c.scrim.alpha, PILL_MIN_ALPHA)).compositeOver(Color.White)
            assertWithMessage("$name pill").that(contrastRatio(c.onScrim, pill)).isAtLeast(4.5f)
        }
    }

    @Test
    fun `대비가 부족한 호스트 색은 받아들이지 않는다`() {
        val base = VoxeraColors.light()
        val resolved = resolveVoxeraColors(
            base,
            VoxeraPaletteOverride(foreground = Color(0xFFE0E0E0), primary = Color(0xFF3B5BDB)),
            highContrast = false,
        )
        assertThat(resolved.rejected).containsExactly("foreground")
        assertThat(resolved.colors.foreground).isEqualTo(base.foreground)
        assertThat(resolved.colors.primary).isEqualTo(Color(0xFF3B5BDB))
        assertThat(contrastRatio(resolved.colors.onPrimary, resolved.colors.primary)).isAtLeast(4.5f)
    }

    @Test
    fun `고대비는 보조 글자를 본문색으로 올리고 패널을 불투명하게 한다`() {
        val resolved = resolveVoxeraColors(VoxeraColors.dark(), null, highContrast = true).colors
        assertThat(resolved.foregroundSecondary).isEqualTo(resolved.foreground)
        assertThat(resolved.glassTint.alpha).isEqualTo(1f)
    }

    @Test
    fun `AUTO 품질은 배터리 절약과 고대비에서 낮아지고 명시 값은 그대로 둔다`() {
        assertThat(resolveGlassQuality(VoxeraGlassQuality.AUTO, powerSave = false, highContrast = false)).isEqualTo(VoxeraGlassQuality.FULL)
        assertThat(resolveGlassQuality(VoxeraGlassQuality.AUTO, powerSave = true, highContrast = false)).isEqualTo(VoxeraGlassQuality.REDUCED)
        assertThat(resolveGlassQuality(VoxeraGlassQuality.AUTO, powerSave = false, highContrast = true)).isEqualTo(VoxeraGlassQuality.OFF)
        assertThat(resolveGlassQuality(VoxeraGlassQuality.FULL, powerSave = true, highContrast = true)).isEqualTo(VoxeraGlassQuality.FULL)
    }

    @Test
    fun `유리 불투명도는 단계별 하한을 지키고 OFF는 불투명`() {
        val c = VoxeraColors.dark()
        assertThat(glassFill(c, GlassLevel.Low, VoxeraGlassQuality.FULL).alpha).isAtLeast(0.9f)
        assertThat(glassFill(c, GlassLevel.Medium, VoxeraGlassQuality.FULL).alpha).isIn(Range.closed(0.72f, 0.9f))
        assertThat(glassFill(c, GlassLevel.High, VoxeraGlassQuality.FULL).alpha).isAtLeast(0.88f)
        // 색은 8비트 알파로 저장되므로 0.95는 242/255(0.949)가 된다.
        assertThat(glassFill(c, GlassLevel.Medium, VoxeraGlassQuality.REDUCED).alpha).isAtLeast(0.949f)
        assertThat(glassFill(c, GlassLevel.Medium, VoxeraGlassQuality.OFF).alpha).isEqualTo(1f)
    }
}
