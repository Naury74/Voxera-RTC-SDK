package com.naury.voxerartc.showcase

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.naury.voxerartc.android.VoxeraPictureInPicture
import com.naury.voxerartc.api.RtcEngineKind
import com.naury.voxerartc.ui.video.LocalVoxeraVideoRenderer
import com.naury.voxerartc.livekit.compose.LiveKitCameraPreviewContent
import com.naury.voxerartc.livekit.compose.rememberScreenShareLauncher
import com.naury.voxerartc.testkit.DemoScreenShareConsent
import kotlinx.coroutines.launch
import com.naury.voxerartc.ui.call.VoxeraCallScreen
import com.naury.voxerartc.ui.prejoin.VoxeraPrejoinScreen
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
            var showPrejoin by rememberSaveable { mutableStateOf(false) }
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
            val form = viewModel.form

            // 진행 중 통화 알림용(Android 13+). 실제 통화에 들어갈 때 한 번 묻는다.
            val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

            val call = active
            when {
                call != null -> {
                    BackHandler(onBack = viewModel::exit)
                    val renderer = if (call.session.engineKind == RtcEngineKind.LIVE) viewModel.liveVideoRenderer else null
                    val coordinator = call.coordinator
                    // 통화마다 PiP를 붙이고, 통화 화면을 떠나면 뗀다. PiP 창의 종료 버튼은 화면의 종료 버튼과 같은 동작이다.
                    val pip = remember(call.session) {
                        VoxeraPictureInPicture.attach(this@MainActivity, call.session, onHangUp = {
                            lifecycleScope.launch { if (coordinator != null) coordinator.hangup() else call.session.leave() }
                        })
                    }
                    DisposableEffect(pip) { onDispose { pip.close() } }
                    val inPip by pip.isInPictureInPicture.collectAsStateWithLifecycle()
                    val enterPip = if (VoxeraPictureInPicture.isSupported(this@MainActivity)) ({ pip.enter(); Unit }) else null
                    CompositionLocalProvider(LocalVoxeraVideoRenderer provides renderer) {
                        if (coordinator != null) {
                            VoxeraCallScreen(
                                coordinator = coordinator,
                                session = call.session,
                                onExit = viewModel::exit,
                                theme = theme,
                                pictureInPicture = inPip,
                                onEnterPictureInPicture = enterPip,
                            )
                        } else {
                            val live = call.session.engineKind == RtcEngineKind.LIVE
                            val liveShare = if (live) rememberScreenShareLauncher(call.session) else null
                            val scope = rememberCoroutineScope()
                            VoxeraRoomScreen(
                                session = call.session,
                                onExit = viewModel::exit,
                                theme = theme,
                                onRejoin = viewModel::rejoin,
                                pictureInPicture = inPip,
                                onEnterPictureInPicture = enterPip,
                                onShareScreen = liveShare ?: { scope.launch { call.session.startScreenShare(DemoScreenShareConsent) }; Unit },
                                onSaveReport = {
                                    val file = viewModel.saveReport()
                                    val text = if (file != null) getString(R.string.report_saved, file.name) else getString(R.string.report_failed)
                                    Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }
                }
                showPrejoin -> {
                    BackHandler { showPrejoin = false }
                    // 권한은 이 화면에서 마이크·카메라를 켜거나 입장할 때만 묻는다. 거부해도 해당 미디어 없이 입장한다.
                    VoxeraPrejoinScreen(
                        roomName = form.roomName.trim(),
                        displayName = form.displayName.trim(),
                        initialMode = form.mode,
                        onJoin = { choice ->
                            showPrejoin = false
                            showSetup = false
                            viewModel.startLive(choice)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onBack = { showPrejoin = false },
                        theme = theme,
                        preview = { modifier -> LiveKitCameraPreviewContent(modifier) },
                    )
                }
                showSetup -> {
                    BackHandler { showSetup = false }
                    ConnectScreen(
                        theme = theme,
                        form = form,
                        onFormChange = viewModel::updateForm,
                        onBack = { showSetup = false },
                        onConnect = { showPrejoin = true },
                    )
                }
                else -> HomeScreen(
                    theme = theme,
                    onThemeChange = { themeMode = it },
                    onStart = viewModel::startDemo,
                    onDemoCall = viewModel::startDemoCall,
                    onConnectLiveKit = { showSetup = true },
                )
            }
        }
    }
}
