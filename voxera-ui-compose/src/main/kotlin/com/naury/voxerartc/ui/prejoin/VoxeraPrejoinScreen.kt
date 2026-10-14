package com.naury.voxerartc.ui.prejoin

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.ui.R
import com.naury.voxerartc.ui.component.CallControlButton
import com.naury.voxerartc.ui.component.VoxeraAvatar
import com.naury.voxerartc.ui.component.VoxeraButton
import com.naury.voxerartc.ui.component.VoxeraButtonTone
import com.naury.voxerartc.ui.component.VoxeraIcons
import com.naury.voxerartc.ui.component.VoxeraSegmentedControl
import com.naury.voxerartc.ui.glass.GlassLevel
import com.naury.voxerartc.ui.glass.GlassSurface
import com.naury.voxerartc.ui.theme.VoxeraRadius
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import com.naury.voxerartc.ui.theme.VoxeraThemeConfig

/**
 * 입장 전 선택. 권한이 거부된 미디어는 꺼진 값으로 온다.
 *
 * @property microphone 마이크를 켠 채 입장할지(권한 허용됨).
 * @property camera 카메라를 켠 채 입장할지(영상 모드이고 권한 허용됨).
 */
public data class PrejoinChoice(val mode: CallMode, val microphone: Boolean, val camera: Boolean)

/**
 * 입장 전 대기 화면(U-03/G-03). 마이크·카메라를 켤 때와 입장할 때만 권한을 묻고, 거부하면 그 미디어 없이 입장한다.
 * "다시 묻지 않음" 상태면 시스템 대화상자를 반복하지 않고 설정 화면으로 안내한다.
 *
 * SDK는 이 화면에서만 권한을 요청한다. 호스트가 권한 흐름을 직접 다루려면 이 화면 대신 `VoxeraSession.join`을 바로 부른다.
 *
 * @param preview 카메라가 켜져 있고 권한이 있을 때 그릴 미리보기(예: `LiveKitCameraPreviewContent`). `null`이면 아바타.
 */
@Composable
public fun VoxeraPrejoinScreen(
    roomName: String,
    displayName: String,
    initialMode: CallMode,
    onJoin: (PrejoinChoice) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    theme: VoxeraThemeConfig = VoxeraThemeConfig(),
    preview: (@Composable (Modifier) -> Unit)? = null,
) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var micWanted by rememberSaveable { mutableStateOf(true) }
    var cameraWanted by rememberSaveable { mutableStateOf(initialMode == CallMode.VIDEO) }
    var requested by rememberSaveable { mutableStateOf(emptySet<String>()) }
    // 설정 화면에서 돌아오거나 대화상자가 닫힐 때 권한을 다시 읽는다.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    fun status(permission: String): PermissionStatus = resolvePermission(
        granted = context.isGranted(permission),
        requestedBefore = permission in requested,
        shouldShowRationale = context.findActivity()?.shouldShowRequestPermissionRationale(permission) == true,
    )
    val mic = remember(refresh, requested) { status(Manifest.permission.RECORD_AUDIO) }
    val camera = remember(refresh, requested) { status(Manifest.permission.CAMERA) }
    val video = mode == CallMode.VIDEO

    var pendingJoin by remember { mutableStateOf(false) }
    fun choice() = PrejoinChoice(
        mode = mode,
        microphone = micWanted && context.isGranted(Manifest.permission.RECORD_AUDIO),
        camera = video && cameraWanted && context.isGranted(Manifest.permission.CAMERA),
    )
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        requested = requested + result.keys
        refresh++
        if (pendingJoin) {
            pendingJoin = false
            onJoin(choice())
        }
    }
    fun request(vararg permissions: String) {
        val askable = permissions.filter { !context.isGranted(it) && status(it) != PermissionStatus.BLOCKED }
        if (askable.isNotEmpty()) launcher.launch(askable.toTypedArray())
    }
    fun join() {
        val needed = buildList {
            if (micWanted) add(Manifest.permission.RECORD_AUDIO)
            if (video && cameraWanted) add(Manifest.permission.CAMERA)
            // 블루투스 이어폰 경로는 Android 12부터 별도 권한이 필요하다.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
        }.filter { !context.isGranted(it) && status(it) != PermissionStatus.BLOCKED }
        if (needed.isEmpty()) {
            onJoin(choice())
        } else {
            pendingJoin = true
            launcher.launch(needed.toTypedArray())
        }
    }

    VoxeraTheme(theme) {
        val colors = VoxeraTheme.colors
        Box(modifier.fillMaxSize().background(colors.canvas), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = VoxeraSpacing.xl, vertical = VoxeraSpacing.md),
                verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.voxera_action_close), tint = colors.foreground)
                    }
                    Column {
                        Text(stringResource(R.string.voxera_prejoin_title), color = colors.foreground, style = VoxeraTheme.typography.headline, modifier = Modifier.semantics { heading() })
                        Text(roomName, color = colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(VoxeraRadius.xl))
                        .background(colors.surfaceElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    val showPreview = video && cameraWanted && camera == PermissionStatus.GRANTED && preview != null
                    if (showPreview) {
                        preview(Modifier.fillMaxSize())
                    } else {
                        VoxeraAvatar(id = "self", name = displayName.ifBlank { "?" }, size = 128.dp, speaking = micWanted && mic == PermissionStatus.GRANTED, audioLevel = 0.3f)
                    }
                }

                VoxeraSegmentedControl(
                    options = listOf(
                        CallMode.VOICE to stringResource(R.string.voxera_prejoin_mode_voice),
                        CallMode.VIDEO to stringResource(R.string.voxera_prejoin_mode_video),
                    ),
                    selected = mode,
                    onSelect = { mode = it },
                    modifier = Modifier.fillMaxWidth(),
                )

                val on = stringResource(R.string.voxera_control_on)
                val off = stringResource(R.string.voxera_control_off)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val micOn = micWanted && mic == PermissionStatus.GRANTED
                    CallControlButton(
                        icon = if (micOn) VoxeraIcons.Mic else VoxeraIcons.MicOff,
                        label = stringResource(R.string.voxera_control_mic),
                        stateLabel = if (micOn) on else off,
                        active = micOn,
                        onClick = {
                            micWanted = !micWanted || mic != PermissionStatus.GRANTED
                            if (micWanted) request(Manifest.permission.RECORD_AUDIO)
                        },
                    )
                    if (video) {
                        val cameraOn = cameraWanted && camera == PermissionStatus.GRANTED
                        CallControlButton(
                            icon = if (cameraOn) VoxeraIcons.Videocam else VoxeraIcons.VideocamOff,
                            label = stringResource(R.string.voxera_control_camera),
                            stateLabel = if (cameraOn) on else off,
                            active = cameraOn,
                            onClick = {
                                cameraWanted = !cameraWanted || camera != PermissionStatus.GRANTED
                                if (cameraWanted) request(Manifest.permission.CAMERA)
                            },
                        )
                    }
                }

                PermissionNotice(micWanted, mic, R.string.voxera_prejoin_mic_denied, R.string.voxera_prejoin_mic_blocked, context)
                if (video) PermissionNotice(cameraWanted, camera, R.string.voxera_prejoin_camera_denied, R.string.voxera_prejoin_camera_blocked, context)

                VoxeraButton(stringResource(R.string.voxera_prejoin_join), onClick = ::join, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PermissionNotice(wanted: Boolean, status: PermissionStatus, deniedText: Int, blockedText: Int, context: Context) {
    if (!wanted) return
    when (status) {
        PermissionStatus.DENIED -> GlassSurface(level = GlassLevel.Low, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(deniedText), color = VoxeraTheme.colors.foregroundSecondary, style = VoxeraTheme.typography.body)
        }
        PermissionStatus.BLOCKED -> GlassSurface(level = GlassLevel.Low, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
                Text(stringResource(blockedText), color = VoxeraTheme.colors.warning, style = VoxeraTheme.typography.body)
                VoxeraButton(
                    stringResource(R.string.voxera_prejoin_open_settings),
                    onClick = { context.openAppSettings() },
                    tone = VoxeraButtonTone.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        else -> Unit
    }
}

private fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
