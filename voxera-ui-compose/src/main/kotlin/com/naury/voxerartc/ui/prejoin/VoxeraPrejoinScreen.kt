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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.naury.kinetica.ui.KSystemBarIcons
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.ui.R
import com.naury.voxerartc.ui.component.CallControlButton
import com.naury.voxerartc.ui.component.VoxeraAvatar
import com.naury.voxerartc.ui.component.VoxeraButton
import com.naury.voxerartc.ui.component.VoxeraButtonTone
import com.naury.voxerartc.ui.component.VoxeraIcons
import com.naury.voxerartc.ui.component.VoxeraSegmentedControl
import com.naury.voxerartc.ui.glass.GlassDock
import com.naury.voxerartc.ui.glass.GlassLevel
import com.naury.voxerartc.ui.glass.GlassPill
import com.naury.voxerartc.ui.glass.GlassSurface
import com.naury.voxerartc.ui.glass.voxeraBackdrop
import com.naury.voxerartc.ui.glass.voxeraGlass
import com.naury.voxerartc.ui.room.SpeakingRipple
import com.naury.voxerartc.ui.theme.VoxeraRadius
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import com.naury.voxerartc.ui.theme.VoxeraThemeConfig
import com.naury.voxerartc.ui.theme.enterOnAppear

/**
 * 입장 전 화면에서 고른 값. 권한이 거부된 미디어는 꺼진 상태로 들어온다.
 *
 * @property microphone 마이크를 켠 채 입장할지. `true`면 권한도 허용된 상태다.
 * @property camera 카메라를 켠 채 입장할지. `true`면 영상 모드이고 권한도 허용된 상태다.
 */
public data class PrejoinChoice(val mode: CallMode, val microphone: Boolean, val camera: Boolean)

/**
 * 입장 전 대기 화면(U-03/G-03). 권한은 마이크나 카메라를 켤 때와 입장할 때만 묻고, 거부하면 그 미디어 없이 입장한다.
 * "다시 묻지 않음" 상태면 시스템 대화상자를 다시 띄우지 않고 설정 화면으로 안내한다.
 *
 * SDK는 이 화면에서만 권한을 요청한다. 호스트가 권한 흐름을 직접 다루려면 이 화면 대신 `VoxeraSession.join`을 바로 부른다.
 *
 * @param preview 카메라가 켜져 있고 권한이 있을 때 그릴 미리보기(예: `LiveKitCameraPreviewContent`). `null`이면 아바타를 그린다.
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
        // 상태 표시줄 아이콘을 이 화면 바탕 밝기에 맞춘다. 화면을 떠나면 호스트 설정으로 돌아간다.
        KSystemBarIcons(light = colors.isDark)
        val micOn = micWanted && mic == PermissionStatus.GRANTED
        val level by rememberMicLevel(micOn)
        Box(modifier.fillMaxSize().voxeraBackdrop(emphasis = level ?: 0f), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = VoxeraSpacing.md, vertical = VoxeraSpacing.md),
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

                VoxeraSegmentedControl(
                    options = listOf(
                        CallMode.VOICE to stringResource(R.string.voxera_prejoin_mode_voice),
                        CallMode.VIDEO to stringResource(R.string.voxera_prejoin_mode_video),
                    ),
                    modifier = Modifier.fillMaxWidth().enterOnAppear(1),
                    selected = mode,
                    onSelect = {
                        // 영상으로 바꾸면 카메라를 켠 채 시작하도록 맞춘다. 권한은 입장할 때 묻는다.
                        if (it == CallMode.VIDEO && mode != CallMode.VIDEO) cameraWanted = true
                        mode = it
                    },
                )

                // 미리보기 위에 이름표와 유리 조작 막대를 겹친다(G-03).
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .enterOnAppear(2, scale = true)
                        .voxeraGlass(RoundedCornerShape(VoxeraRadius.xxl), GlassLevel.Low),
                    contentAlignment = Alignment.Center,
                ) {
                    val showPreview = video && cameraWanted && camera == PermissionStatus.GRANTED && preview != null
                    if (showPreview) {
                        preview(Modifier.fillMaxSize().clip(RoundedCornerShape(VoxeraRadius.xxl)))
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            SpeakingRipple(speaking = micOn && (level ?: 0f) > 0.15f, level = level ?: 0f, size = 128.dp)
                            VoxeraAvatar(id = "self", name = displayName.ifBlank { "?" }, size = 128.dp, speaking = micOn && (level ?: 0f) > 0.15f, audioLevel = level ?: 0f)
                        }
                    }
                    GlassPill(Modifier.align(Alignment.TopStart).padding(VoxeraSpacing.md)) {
                        Text(displayName.ifBlank { stringResource(R.string.voxera_you) }, color = colors.onScrim, style = VoxeraTheme.typography.caption)
                    }
                    val on = stringResource(R.string.voxera_control_on)
                    val off = stringResource(R.string.voxera_control_off)
                    GlassDock(Modifier.align(Alignment.BottomCenter).padding(bottom = VoxeraSpacing.md)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xxs)) {
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
                    }
                }

                // 마이크를 켜고 끌 때 레벨 막대가 펼쳐지고 접힌다.
                AnimatedVisibility(micOn, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) { MicLevelMeter(level) }
                PermissionNotice(micWanted, mic, R.string.voxera_prejoin_mic_denied, R.string.voxera_prejoin_mic_blocked, context)
                if (video) PermissionNotice(cameraWanted, camera, R.string.voxera_prejoin_camera_denied, R.string.voxera_prejoin_camera_blocked, context)

                VoxeraButton(
                    stringResource(R.string.voxera_prejoin_join),
                    onClick = ::join,
                    icon = if (video) VoxeraIcons.Videocam else VoxeraIcons.Call,
                    modifier = Modifier.fillMaxWidth().enterOnAppear(3),
                )
            }
        }
    }
}

/**
 * 마이크 레벨 막대 16칸. 말하면 차오르고, 마이크를 열 수 없으면 그 사실을 글자로 알린다.
 * TalkBack에는 "마이크 소리 감지됨/조용함"으로 읽힌다.
 */
@Composable
private fun MicLevelMeter(level: Float?) {
    val colors = VoxeraTheme.colors
    val heard = (level ?: 0f) > 0.15f
    val description = stringResource(
        when {
            level == null -> R.string.voxera_prejoin_level_unavailable
            heard -> R.string.voxera_prejoin_level_heard
            else -> R.string.voxera_prejoin_level_quiet
        },
    )
    GlassSurface(level = GlassLevel.Low, shape = RoundedCornerShape(VoxeraRadius.lg), modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.sm)) {
            Icon(VoxeraIcons.Mic, contentDescription = null, tint = if (heard) colors.primary else colors.foregroundSecondary, modifier = Modifier.size(20.dp))
            if (level == null) {
                Text(description, color = colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
            } else {
                Row(Modifier.weight(1f).height(16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    val lit = (level * 16).toInt()
                    repeat(16) { index ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (index < lit) (if (index >= 13) colors.warning else colors.primary) else colors.foreground.copy(alpha = 0.12f)),
                        )
                    }
                }
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
