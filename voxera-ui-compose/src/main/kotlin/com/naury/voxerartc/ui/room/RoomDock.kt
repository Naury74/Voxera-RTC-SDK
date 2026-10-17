package com.naury.voxerartc.ui.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KCallDock
import com.naury.kinetica.ui.KCallDockItem
import com.naury.kinetica.ui.KTone
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.LocalMediaState
import com.naury.voxerartc.ui.R
import com.naury.voxerartc.ui.component.VoxeraIcons
import com.naury.voxerartc.ui.glass.GlassLevel
import com.naury.voxerartc.ui.glass.voxeraSurface
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme

/**
 * 조작 막대의 버튼 하나. [essential]이면 좁은 화면에서도 막대에 남고, 아니면 "더보기"로 옮겨질 수 있다.
 * [checked]가 `null`이면 상태가 없는 동작 버튼이고, 있으면 켜짐일 때 원을 채우며 [icon]에서 [checkedIcon]으로 바뀐다.
 */
internal data class DockControl(
    val key: String,
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val essential: Boolean,
    val stateLabel: String? = null,
    val checked: Boolean? = null,
    val checkedIcon: ImageVector? = null,
    val pending: Boolean = false,
    val danger: Boolean = false,
    val enabled: Boolean = true,
    val badge: Int = 0,
    val badgeDescription: String? = null,
)

/** 오디오 알림 말풍선이 가리키는 버튼. 더보기로 접히면 더보기 버튼 자리가 이 키로 온다. */
internal const val AUDIO_CONTROL = "audio"

private fun DockControl.toItem(): KCallDockItem = KCallDockItem(
    key = key,
    icon = icon,
    label = label,
    onClick = onClick,
    checked = checked,
    checkedIcon = checkedIcon,
    tone = if (danger) KTone.Destructive else KTone.Neutral,
    pending = pending,
    badge = badge,
    enabled = enabled,
    essential = essential,
    stateDescription = stateLabel,
    badgeDescription = badgeDescription,
)

@Composable
internal fun CallDock(
    mode: CallMode,
    media: LocalMediaState,
    onMic: () -> Unit,
    onCamera: () -> Unit,
    onFlip: () -> Unit,
    onAudio: () -> Unit,
    onEffects: () -> Unit,
    onReact: (() -> Unit)?,
    onChat: (() -> Unit)?,
    chatUnread: Int,
    onShare: (() -> Unit)?,
    onEnd: () -> Unit,
    onMore: (List<DockControl>) -> Unit,
    modifier: Modifier = Modifier,
    onItemPositioned: ((key: String, bounds: Rect) -> Unit)? = null,
) {
    val on = stringResource(R.string.voxera_control_on)
    val off = stringResource(R.string.voxera_control_off)
    val pending = stringResource(R.string.voxera_control_pending)
    fun stateOf(actual: Boolean, isPending: Boolean) = if (isPending) pending else if (actual) on else off
    val video = mode == CallMode.VIDEO
    val chat = onChat?.let {
        DockControl(
            "chat",
            VoxeraIcons.Chat,
            stringResource(R.string.voxera_control_chat),
            it,
            essential = false,
            stateLabel = if (chatUnread > 0) pluralStringResource(R.plurals.voxera_chat_unread, chatUnread, chatUnread) else null,
            badge = chatUnread,
            // 읽지 않은 수는 상태 글자가 이미 읽으므로 배지를 한 번 더 읽지 않는다.
            badgeDescription = "",
        )
    }
    val controls = buildList {
        add(
            DockControl(
                key = "mic",
                icon = VoxeraIcons.MicOff,
                checkedIcon = VoxeraIcons.Mic,
                label = stringResource(R.string.voxera_control_mic),
                onClick = onMic,
                essential = true,
                stateLabel = stateOf(media.microphone.actual, media.microphone.isPending),
                checked = media.microphone.actual,
                pending = media.microphone.isPending,
            ),
        )
        if (video) {
            add(
                DockControl(
                    key = "camera",
                    icon = VoxeraIcons.VideocamOff,
                    checkedIcon = VoxeraIcons.Videocam,
                    label = stringResource(R.string.voxera_control_camera),
                    onClick = onCamera,
                    essential = true,
                    stateLabel = stateOf(media.camera.actual, media.camera.isPending),
                    checked = media.camera.actual,
                    pending = media.camera.isPending,
                ),
            )
            if (media.canSwitchCamera) {
                add(DockControl("flip", VoxeraIcons.SwitchCamera, stringResource(R.string.voxera_control_flip), onFlip, essential = false, enabled = media.camera.actual))
            }
        }
        // 막대가 좁아 더보기로 옮길 때 앞에 있는 조작이 남는다. 채팅은 카메라 전환 다음으로 남긴다.
        chat?.let(::add)
        if (video) {
            if (onShare != null) {
                add(
                    DockControl(
                        key = "share",
                        icon = VoxeraIcons.ScreenShare,
                        label = stringResource(R.string.voxera_control_share),
                        onClick = onShare,
                        essential = false,
                        stateLabel = stateOf(media.screenShare.actual, media.screenShare.isPending),
                        checked = media.screenShare.actual,
                        pending = media.screenShare.isPending,
                    ),
                )
            }
            add(DockControl("effects", VoxeraIcons.AutoFix, stringResource(R.string.voxera_control_effects), onEffects, essential = false, stateLabel = if (media.videoEffect.isNone) off else on, checked = !media.videoEffect.isNone))
        }
        if (onReact != null) {
            add(DockControl("react", VoxeraIcons.Mood, stringResource(R.string.voxera_control_react), onReact, essential = false))
        }
        if (media.audioRoutes.isNotEmpty()) {
            add(DockControl(AUDIO_CONTROL, media.selectedRouteIcon(), stringResource(R.string.voxera_control_audio), onAudio, essential = false))
        }
        add(DockControl("end", VoxeraIcons.CallEnd, stringResource(R.string.voxera_control_end), onEnd, essential = true, danger = true))
    }
    // 좁으면 원을 48dp로 줄이고, 그래도 넘치면 필수가 아닌 조작을 종료 앞 "더보기"로 접는다. 더보기 배지는 접힌 조작 배지의 합이다.
    // 버튼이 늘거나 줄면(카메라 켜기, 화면 공유) 막대 폭이 부드럽게 바뀐다.
    KCallDock(
        items = controls.map { it.toItem() },
        modifier = modifier.fillMaxWidth(),
        buttonSize = 52.dp,
        onMore = { overflow ->
            val keys = overflow.mapTo(HashSet()) { it.key }
            onMore(controls.filter { it.key in keys })
        },
        onItemPositioned = onItemPositioned,
        surface = voxeraSurface(GlassLevel.Medium),
    )
}

/** 막대에 들어가지 못한 조작을 목록으로 보여 준다. 상태가 있는 조작은 상태 글자를 함께 쓴다. */
@Composable
internal fun MoreControlsSheet(controls: List<DockControl>, onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xxs)) {
        controls.forEach { control ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = control.enabled, role = Role.Button) {
                        control.onClick()
                        onDone()
                    }
                    .padding(horizontal = VoxeraSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.sm),
            ) {
                Icon(if (control.checked == true) control.checkedIcon ?: control.icon else control.icon, contentDescription = null, tint = if (control.checked == true) VoxeraTheme.colors.primary else VoxeraTheme.colors.foreground)
                Text(control.label, color = VoxeraTheme.colors.foreground, style = VoxeraTheme.typography.body, modifier = Modifier.weight(1f))
                if (control.stateLabel != null) {
                    Text(control.stateLabel, color = VoxeraTheme.colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
                }
            }
        }
    }
}
