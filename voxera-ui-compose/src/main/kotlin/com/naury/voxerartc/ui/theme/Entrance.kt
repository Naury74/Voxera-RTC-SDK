package com.naury.voxerartc.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** 화면 요소가 차례로 나타날 때 한 칸 사이 간격. */
internal const val STAGGER_MS = 45L

/**
 * 화면에 처음 들어올 때 살짝 아래에서 떠오르며 선명해진다. [index]번째 요소는 [STAGGER_MS]씩 늦게 시작해
 * 위에서 아래로 차례로 나타난다. 자리(레이아웃)는 처음부터 차지하므로 다른 요소가 밀리지 않는다.
 * 움직임 줄이기 설정이면 바로 보인다.
 *
 * @param scale `true`면 크기도 92%에서 커진다(아이콘·아바타처럼 강조할 요소).
 */
@Composable
public fun Modifier.enterOnAppear(index: Int = 0, scale: Boolean = false): Modifier {
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduced) return@LaunchedEffect
        delay(index * STAGGER_MS)
        progress.animateTo(1f, spring(dampingRatio = if (scale) 0.6f else 0.85f, stiffness = Spring.StiffnessMediumLow))
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 16.dp.toPx()
        if (scale) {
            val s = 0.92f + 0.08f * p
            scaleX = s
            scaleY = s
        }
    }
}
