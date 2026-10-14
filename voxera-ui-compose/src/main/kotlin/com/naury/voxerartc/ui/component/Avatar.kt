package com.naury.voxerartc.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.voxerartc.ui.theme.VoxeraColors
import com.naury.voxerartc.ui.theme.VoxeraTheme
import com.naury.voxerartc.ui.theme.readableOn
import kotlin.math.abs

/**
 * 이름에서 만든 이니셜: 한 단어면 첫 글자, 여러 단어면 앞 두 단어의 첫 글자. 인물 사진을 번들하지 않으므로
 * 기본 아바타는 이니셜과 추상 색이다.
 */
internal fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    fun first(word: String) = String(Character.toChars(word.codePointAt(0)))
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> first(words[0]).uppercase()
        else -> (first(words[0]) + first(words[1])).uppercase()
    }
}

/** 같은 id는 항상 같은 테마 색 쌍을 받는다. 화면별 색을 따로 두지 않고 의미 토큰을 섞어 쓴다. */
internal fun avatarColors(id: String, colors: VoxeraColors): Pair<Color, Color> {
    val palette = listOf(
        colors.primary to colors.secondary,
        colors.secondary to colors.primary,
        colors.warning to colors.danger,
        colors.secondary to colors.warning,
        colors.primary to colors.warning,
        colors.danger to colors.secondary,
    )
    return palette[abs(id.hashCode()) % palette.size]
}

/**
 * 이니셜 아바타. [speaking]이면 [audioLevel](0~1)에 따라 두께가 바뀌는 강조 고리를 그린다.
 */
@Composable
public fun VoxeraAvatar(
    id: String,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    speaking: Boolean = false,
    audioLevel: Float = 0f,
) {
    val ring by animateFloatAsState(if (speaking) 0.5f + audioLevel.coerceIn(0f, 1f) * 0.5f else 0f, tween(180), label = "ring")
    val (start, end) = avatarColors(id, VoxeraTheme.colors)
    val ringWidth = (size.value * 0.06f * ring).dp
    Box(
        modifier = modifier
            .size(size)
            .then(if (ring > 0f) Modifier.border(ringWidth, VoxeraTheme.colors.primary, CircleShape) else Modifier)
            .padding(ringWidth + if (ring > 0f) 2.dp else 0.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(start, end))),
        contentAlignment = Alignment.Center,
    ) {
        Text(initialsOf(name), color = readableOn(start), fontSize = (size.value * 0.34f).sp, style = VoxeraTheme.typography.title)
    }
}
