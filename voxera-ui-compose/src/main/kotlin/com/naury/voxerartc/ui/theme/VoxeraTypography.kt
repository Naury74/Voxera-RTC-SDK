package com.naury.voxerartc.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Voxera 글자 체계. 크기/줄 높이(sp)는 Display 32/40, Headline 24/32, Title 20/28, Body 16/24, Label 14/20, Caption 12/16. */
@Immutable
public data class VoxeraTypography(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
) {
    public companion object {
        /** [fontFamily]를 넘기면 모든 단계에 같은 글꼴을 쓴다. 기본은 시스템 글꼴이며 번들 글꼴은 없다. */
        public fun default(fontFamily: FontFamily = FontFamily.Default): VoxeraTypography {
            fun style(size: Int, line: Int, weight: Int) =
                TextStyle(fontFamily = fontFamily, fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight(weight))
            return VoxeraTypography(
                display = style(32, 40, 700),
                headline = style(24, 32, 700),
                title = style(20, 28, 600),
                body = style(16, 24, 400),
                label = style(14, 20, 600),
                caption = style(12, 16, 500),
            )
        }
    }
}
