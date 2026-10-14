package com.naury.voxerartc.showcase

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.RtcEngineKind
import com.naury.voxerartc.ui.video.LocalVoxeraVideoRenderer
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
            var showSetup by rememberSaveable { mutableStateOf(false) }
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
            val form by viewModel.form.collectAsStateWithLifecycle()

            // 권한은 사용자가 연결을 누른 직후에만 묻는다. 거부해도 입장하며 해당 미디어 없이 연결된다.
            val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                viewModel.startLive()
            }

            val call = active
            when {
                call != null -> {
                    BackHandler(onBack = viewModel::exit)
                    val renderer = if (call.session.engineKind == RtcEngineKind.LIVE) viewModel.liveVideoRenderer else null
                    CompositionLocalProvider(LocalVoxeraVideoRenderer provides renderer) {
                        VoxeraRoomScreen(session = call.session, onExit = viewModel::exit, theme = theme, onRejoin = viewModel::rejoin)
                    }
                }
                showSetup -> {
                    BackHandler { showSetup = false }
                    ConnectScreen(
                        theme = theme,
                        form = form,
                        onFormChange = viewModel::updateForm,
                        onBack = { showSetup = false },
                        onConnect = { permissions.launch(callPermissions(form.mode)) },
                    )
                }
                else -> HomeScreen(
                    theme = theme,
                    onThemeChange = { themeMode = it },
                    onStart = viewModel::startDemo,
                    onConnectLiveKit = { showSetup = true },
                )
            }
        }
    }
}

/** 통화에 필요한 런타임 권한. 블루투스 이어폰 경로는 Android 12부터 별도 권한이 필요하다. */
private fun callPermissions(mode: CallMode): Array<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    if (mode == CallMode.VIDEO) add(Manifest.permission.CAMERA)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
}.toTypedArray()
