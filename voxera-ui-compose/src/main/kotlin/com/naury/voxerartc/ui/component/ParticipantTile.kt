package com.naury.voxerartc.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.ui.R
import com.naury.voxerartc.ui.glass.GlassPill
import com.naury.voxerartc.ui.theme.VoxeraRadius
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import com.naury.voxerartc.ui.video.VoxeraVideoRenderer

/**
 * 참가자 타일. 카메라가 켜져 있고 [renderer]가 있으면 영상, 아니면 이니셜 아바타를 보여 준다. 카메라를 끄면
 * 마지막 프레임 대신 아바타로 바뀐다. 이름표는 12dp 안쪽에 고정한다.
 *
 * @param onTogglePin `null`이 아니면 탭으로 고정/해제한다.
 * @param labelPadding 이름표를 더 안쪽으로 옮길 여백. 전체 화면 타일에서 조작 막대·시스템 막대를 피할 때 쓴다.
 */
@Composable
public fun ParticipantTile(
    participant: Participant,
    renderer: VoxeraVideoRenderer?,
    modifier: Modifier = Modifier,
    pinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
    labelPadding: PaddingValues = PaddingValues(0.dp),
) {
    val colors = VoxeraTheme.colors
    val shape = RoundedCornerShape(VoxeraRadius.md)
    val name = if (participant.isLocal) stringResource(R.string.voxera_you) else participant.displayName
    val description = buildList {
        add(name)
        if (participant.isSpeaking) add(stringResource(R.string.voxera_speaking))
        if (!participant.isMicrophoneEnabled) add(stringResource(R.string.voxera_mic_off))
        if (participant.camera == null) add(stringResource(R.string.voxera_camera_off))
    }.joinToString(", ")
    val pinLabel = stringResource(if (pinned) R.string.voxera_tile_unpin else R.string.voxera_tile_pin)
    val track = participant.camera
    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(colors.surfaceElevated)
            .then(if (participant.isSpeaking) Modifier.border(2.dp, colors.primary, shape) else Modifier)
            .then(
                if (onTogglePin != null) Modifier.clickable(role = Role.Button, onClickLabel = pinLabel, onClick = onTogglePin)
                else Modifier,
            )
            .clearAndSetSemantics {
                contentDescription = description
                if (onTogglePin != null) {
                    role = Role.Button
                    onClick(label = pinLabel) { onTogglePin(); true }
                }
            },
    ) {
        if (track != null && renderer != null) {
            renderer.Render(track, mirror = participant.isLocal, modifier = Modifier.fillMaxSize())
        } else {
            val avatar = (minOf(maxWidth, maxHeight) * 0.42f).coerceIn(40.dp, 120.dp)
            VoxeraAvatar(
                id = participant.id,
                name = name,
                size = avatar,
                speaking = participant.isSpeaking,
                audioLevel = participant.audioLevel,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        GlassPill(Modifier.align(Alignment.BottomStart).padding(labelPadding).padding(VoxeraSpacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                if (!participant.isMicrophoneEnabled) {
                    Icon(VoxeraIcons.MicOff, contentDescription = null, tint = colors.onScrim, modifier = Modifier.size(14.dp))
                }
                Text(name, color = colors.onScrim, style = VoxeraTheme.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
