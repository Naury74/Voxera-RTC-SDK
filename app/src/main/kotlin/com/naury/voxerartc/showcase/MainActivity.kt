package com.naury.voxerartc.showcase

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naury.voxerartc.ui.room.VoxeraRoomScreen
import com.naury.voxerartc.ui.theme.VoxeraThemeConfig
import com.naury.voxerartc.ui.theme.VoxeraThemeMode

class MainActivity : ComponentActivity() {

    private val viewModel: ShowcaseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var themeMode by rememberSaveable { mutableStateOf(VoxeraThemeMode.SYSTEM) }
            val dark = when (themeMode) {
                VoxeraThemeMode.SYSTEM -> isSystemInDarkTheme()
                VoxeraThemeMode.LIGHT -> false
                VoxeraThemeMode.DARK -> true
            }
            // 앱 안에서 고른 테마에 맞춰 상태 표시줄·내비게이션 바 아이콘 색을 바꾼다.
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            val theme = VoxeraThemeConfig(mode = themeMode)
            val active by viewModel.active.collectAsStateWithLifecycle()
            val demo = active
            if (demo == null) {
                HomeScreen(theme = theme, onThemeChange = { themeMode = it }, onStart = viewModel::start)
            } else {
                BackHandler(onBack = viewModel::exit)
                VoxeraRoomScreen(
                    session = demo.session,
                    onExit = viewModel::exit,
                    theme = theme,
                    onRejoin = viewModel::rejoin,
                )
            }
        }
    }
}
