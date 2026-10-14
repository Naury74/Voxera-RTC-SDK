package com.naury.voxerartc.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 시스템 애니메이션 배율이 0(개발자 옵션, 접근성의 "애니메이션 삭제")이면 `true`. 반복 애니메이션과 튕김 효과를 끈다.
 * 설정을 바꾸면 화면을 다시 만들 때 반영된다.
 */
@Composable
public fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) == 0f
    }
}
