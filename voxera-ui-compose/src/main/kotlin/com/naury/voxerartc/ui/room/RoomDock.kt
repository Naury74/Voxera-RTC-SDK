package com.naury.voxerartc.ui.room

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.LocalMediaState
import com.naury.voxerartc.ui.R
import com.naury.voxerartc.ui.component.CallControlButton
import com.naury.voxerartc.ui.component.VoxeraIcons
import com.naury.voxerartc.ui.glass.GlassDock
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme

/** 조작 막대의 버튼 하나. [essential]이면 좁은 화면에서도 막대에 남고, 아니면 "더보기"로 옮겨질 수 있다. */
internal data class DockControl(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val essential: Boolean,
    val stateLabel: String? = null,
    val active: Boolean = false,
    val pending: Boolean = false,
    val danger: Boolean = false,
    val enabled: Boolean = true,
)

/** 버튼 [count]개를 원 [circle] 크기로 놓을 때 필요한 막대 폭. 버튼 폭은 원 + 8dp, 사이 4dp, 막대 안쪽 여백 12dp씩. */
internal fun dockWidth(count: Int, circle: Dp): Dp = (circle + 8.dp) * count + 4.dp * (count - 1) + 24.dp

/**
 * 폭에 맞춘 배치: 52dp 원 → 48dp 원(최소 터치 크기) → 그래도 넘치면 필수가 아닌 버튼을 "더보기"로 옮긴다.
 * 돌려주는 값은 (막대에 둘 버튼, 더보기로 옮긴 버튼, 원 크기).
 */
private fun layoutDock(controls: List<DockControl>, more: (List<DockControl>) -> DockControl, maxWidth: Dp): Triple<List<DockControl>, List<DockControl>, Dp> {
    if (dockWidth(controls.size, 52.dp) <= maxWidth) return Triple(controls, emptyList(), 52.dp)
    if (dockWidth(controls.size, 48.dp) <= maxWidth) return Triple(controls, emptyList(), 48.dp)
    var slots = controls.size
    while (slots > 1 && dockWidth(slots, 48.dp) > maxWidth) slots--
    val essential = controls.filter { it.essential }
    val optional = controls.filterNot { it.essential }
    val keep = optional.take((slots - essential.size - 1).coerceAtLeast(0))
    val overflow = optional - keep.toSet()
    val shown = controls.filter { it.essential || it in keep }
    // 종료 버튼은 항상 마지막에 두고, 더보기는 그 앞에 둔다.
    val end = shown.last()
    return Triple(shown.dropLast(1) + more(overflow) + end, overflow, 48.dp)
}

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
    onShare: (() -> Unit)?,
    onEnd: () -> Unit,
    onMore: (List<DockControl>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val on = stringResource(R.string.voxera_control_on)
    val off = stringResource(R.string.voxera_control_off)
    val pending = stringResource(R.string.voxera_control_pending)
    fun stateOf(actual: Boolean, isPending: Boolean) = if (isPending) pending else if (actual) on else off
    val video = mode == CallMode.VIDEO
    val controls = buildList {
        add(
            DockControl(
                icon = if (media.microphone.actual) VoxeraIcons.Mic else VoxeraIcons.MicOff,
                label = stringResource(R.string.voxera_control_mic),
                onClick = onMic,
                essential = true,
                stateLabel = stateOf(media.microphone.actual, media.microphone.isPending),
                active = media.microphone.actual,
                pending = media.microphone.isPending,
            ),
        )
        if (video) {
            add(
                DockControl(
                    icon = if (media.camera.actual) VoxeraIcons.Videocam else VoxeraIcons.VideocamOff,
                    label = stringResource(R.string.voxera_control_camera),
                    onClick = onCamera,
                    essential = true,
                    stateLabel = stateOf(media.camera.actual, media.camera.isPending),
                    active = media.camera.actual,
                    pending = media.camera.isPending,
                ),
            )
            if (media.canSwitchCamera) {
                add(DockControl(VoxeraIcons.SwitchCamera, stringResource(R.string.voxera_control_flip), onFlip, essential = false, enabled = media.camera.actual))
            }
            add(DockControl(VoxeraIcons.AutoFix, stringResource(R.string.voxera_control_effects), onEffects, essential = false, active = !media.videoEffect.isNone))
            if (onShare != null) {
                add(
                    DockControl(
                        icon = VoxeraIcons.ScreenShare,
                        label = stringResource(R.string.voxera_control_share),
                        onClick = onShare,
                        essential = false,
                        stateLabel = stateOf(media.screenShare.actual, media.screenShare.isPending),
                        active = media.screenShare.actual,
                        pending = media.screenShare.isPending,
                    ),
                )
            }
        }
        if (onReact != null) {
            add(DockControl(VoxeraIcons.Mood, stringResource(R.string.voxera_control_react), onReact, essential = false))
        }
        if (media.audioRoutes.isNotEmpty()) {
            add(DockControl(media.selectedRouteIcon(), stringResource(R.string.voxera_control_audio), onAudio, essential = false))
        }
        add(DockControl(VoxeraIcons.CallEnd, stringResource(R.string.voxera_control_end), onEnd, essential = true, danger = true))
    }
    val moreLabel = stringResource(R.string.voxera_control_more)
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        val (shown, _, circle) = layoutDock(controls, { overflow -> DockControl(Icons.Filled.MoreVert, moreLabel, { onMore(overflow) }, essential = true) }, maxWidth)
        // 버튼이 늘거나 줄면(카메라 켜기, 화면 공유) 막대 폭이 부드럽게 바뀐다.
        GlassDock(Modifier.animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))) {
            Row(horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                shown.forEach { control ->
                    CallControlButton(
                        icon = control.icon,
                        label = control.label,
                        onClick = control.onClick,
                        stateLabel = control.stateLabel,
                        active = control.active,
                        pending = control.pending,
                        danger = control.danger,
                        enabled = control.enabled,
                        circleSize = circle,
                    )
                }
            }
        }
    }
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
                Icon(control.icon, contentDescription = null, tint = if (control.active) VoxeraTheme.colors.primary else VoxeraTheme.colors.foreground)
                Text(control.label, color = VoxeraTheme.colors.foreground, style = VoxeraTheme.typography.body, modifier = Modifier.weight(1f))
                if (control.stateLabel != null) {
                    Text(control.stateLabel, color = VoxeraTheme.colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
                }
            }
        }
    }
}
