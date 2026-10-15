package com.naury.voxerartc.ui.component

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import com.naury.voxerartc.ui.glass.pressScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naury.voxerartc.api.ConnectionQuality
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.ui.R
import com.naury.voxerartc.ui.glass.GlassPill
import com.naury.voxerartc.ui.theme.VoxeraRadius
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import com.naury.voxerartc.ui.video.VoxeraVideoRenderer

/**
 * 참가자 타일. 카메라가 켜져 있고 [renderer]가 있으면 영상, 아니면 이니셜 아바타를 보여 준다. 카메라를 끄면
 * 마지막 프레임을 남기지 않고 아바타로 바뀐다. 이름표는 가장자리에서 12dp 안쪽에 고정한다.
 *
 * 누르면 [onTap](예: 이 사람 크게 보기, 조작 막대 보이기/숨기기)을, 길게 누르면 [onTogglePin]을 부른다. 누르는 동안 살짝 작아진다.
 * 고정된 타일은 오른쪽 위에 고정 해제 버튼이 생긴다. TalkBack에서는 두 번 탭하면 [onTap]을 부르고 [tapLabel]로 읽으며, [onTap]이 없으면 고정/해제한다.
 *
 * @param onTogglePin `null`이 아니면 길게 눌러 고정/해제한다. [onTap]이 없으면 짧게 눌러도 고정/해제한다.
 * @param labelPadding 이름표를 더 안쪽으로 옮길 여백. 전체 화면 타일에서 조작 막대나 시스템 막대를 피할 때 쓴다.
 * @param showScreenShare `true`면 카메라 대신 화면 공유 트랙을 그리고 이름표에 "화면"을 붙인다.
 * @param tapLabel [onTogglePin]이 없을 때 TalkBack 두 번 탭으로 [onTap]을 부르게 하고 그 동작 이름으로 읽는다.
 * @param shape 타일 모서리. 기본은 24dp.
 * @param speakingOutline 말하는 동안 테두리를 그린다. 화면을 꽉 채운 타일에서는 끄고 이름표의 막대만 남긴다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun ParticipantTile(
    participant: Participant,
    renderer: VoxeraVideoRenderer?,
    modifier: Modifier = Modifier,
    pinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
    labelPadding: PaddingValues = PaddingValues(0.dp),
    showScreenShare: Boolean = false,
    onTap: (() -> Unit)? = null,
    tapLabel: String? = null,
    shape: RoundedCornerShape = RoundedCornerShape(VoxeraRadius.xl),
    speakingOutline: Boolean = true,
) {
    val colors = VoxeraTheme.colors
    val haptics = LocalHapticFeedback.current
    val baseName = if (participant.isLocal) stringResource(R.string.voxera_you) else participant.displayName
    val name = if (showScreenShare) stringResource(R.string.voxera_share_label, baseName) else baseName
    val weak = !participant.isLocal && (participant.connectionQuality == ConnectionQuality.POOR || participant.connectionQuality == ConnectionQuality.LOST)
    val description = buildList {
        add(name)
        if (pinned) add(stringResource(R.string.voxera_tile_pinned))
        if (participant.isHandRaised) add(stringResource(R.string.voxera_hand_raised))
        if (participant.isSpeaking) add(stringResource(R.string.voxera_speaking))
        if (!participant.isMicrophoneEnabled) add(stringResource(R.string.voxera_mic_off))
        if (participant.camera == null) add(stringResource(R.string.voxera_camera_off))
        if (weak) add(stringResource(R.string.voxera_weak_connection))
    }.joinToString(", ")
    val pinLabel = stringResource(if (pinned) R.string.voxera_tile_unpin else R.string.voxera_tile_pin)
    val track = if (showScreenShare) participant.screenShare else participant.camera
    val speakingGlow by animateFloatAsState(if (participant.isSpeaking) 1f else 0f, tween(220), label = "speaking")
    val tap = onTap ?: onTogglePin
    val interaction = remember { MutableInteractionSource() }
    BoxWithConstraints(
        modifier = modifier
            .pressScale(interaction, pressed = 0.97f)
            .clip(shape)
            .background(colors.surfaceElevated)
            .then(
                if (tap != null || onTogglePin != null) {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        role = Role.Button,
                        onClick = { tap?.invoke() },
                        onLongClickLabel = pinLabel.takeIf { onTap != null && onTogglePin != null },
                        onLongClick = if (onTap != null && onTogglePin != null) {
                            {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTogglePin()
                            }
                        } else null,
                    )
                } else Modifier,
            )
            .clearAndSetSemantics {
                contentDescription = description
                if (onTap != null && tapLabel != null) {
                    role = Role.Button
                    onClick(label = tapLabel) { onTap(); true }
                } else if (onTogglePin != null) {
                    role = Role.Button
                    onClick(label = pinLabel) { onTogglePin(); true }
                }
                if (onTogglePin != null && onTap != null && tapLabel != null) {
                    customActions = listOf(CustomAccessibilityAction(pinLabel) { onTogglePin(); true })
                }
            },
    ) {
        val compact = maxWidth < 140.dp
        if (track != null && renderer != null) {
            renderer.Render(track, mirror = participant.isLocal && !showScreenShare, modifier = Modifier.fillMaxSize())
        } else {
            val avatar = (minOf(maxWidth, maxHeight) * 0.42f).coerceIn(40.dp, 120.dp)
            VoxeraAvatar(
                id = participant.id,
                name = baseName,
                size = avatar,
                speaking = participant.isSpeaking,
                audioLevel = participant.audioLevel,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (speakingOutline && speakingGlow > 0f) {
            Box(Modifier.fillMaxSize().border(3.dp, colors.primary.copy(alpha = speakingGlow), shape))
        }
        GlassPill(Modifier.align(Alignment.BottomStart).padding(labelPadding).padding(if (compact) VoxeraSpacing.xs else VoxeraSpacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                if (!participant.isMicrophoneEnabled) {
                    Icon(VoxeraIcons.MicOff, contentDescription = null, tint = colors.onScrim, modifier = Modifier.size(14.dp))
                } else if (participant.isSpeaking) {
                    AudioLevelBars(participant.audioLevel, colors.primary)
                }
                Text(name, color = colors.onScrim, style = VoxeraTheme.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (weak) SignalBars(participant.connectionQuality, Modifier.size(width = 14.dp, height = 12.dp))
            }
        }
        // 손을 든 사람은 왼쪽 위에 손 배지가 통 튀어나온다.
        PoppingHandBadge(
            participant.isHandRaised,
            Modifier.align(Alignment.TopStart).padding(if (compact) VoxeraSpacing.xs else VoxeraSpacing.sm),
            size = if (compact) 24.dp else 32.dp,
        )
        // 고정된 타일은 오른쪽 위 버튼으로 바로 풀 수 있다(48dp 터치 영역).
        if (pinned && onTogglePin != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(VoxeraSpacing.xxs)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = pinLabel, onClick = onTogglePin)
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                GlassPill(Modifier) {
                    Icon(VoxeraIcons.PushPin, contentDescription = null, tint = colors.onScrim, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** 말하는 중 표시. 세 막대 높이가 [level](0~1)에 따라 바뀐다. 장식이라 읽지 않는다. */
@Composable
internal fun AudioLevelBars(level: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    val animated by animateFloatAsState(level.coerceIn(0f, 1f), tween(120), label = "level")
    Canvas(modifier.size(width = 12.dp, height = height)) {
        val bar = size.width / 5f
        val shapes = listOf(0.55f, 1f, 0.7f)
        shapes.forEachIndexed { index, weight ->
            val h = size.height * (0.3f + 0.7f * animated * weight)
            drawRoundRect(
                color,
                topLeft = Offset(index * bar * 2f, (size.height - h) / 2f),
                size = Size(bar, h),
                cornerRadius = CornerRadius(bar / 2f),
            )
        }
    }
}

/** 연결 상태 막대(약함 1칸, 끊김 0칸). 뜻은 타일 설명이 글자로 전하므로 읽지 않는다. */
@Composable
internal fun SignalBars(quality: ConnectionQuality, modifier: Modifier = Modifier) {
    val colors = VoxeraTheme.colors
    val filled = when (quality) {
        ConnectionQuality.EXCELLENT -> 3
        ConnectionQuality.GOOD -> 2
        ConnectionQuality.POOR -> 1
        ConnectionQuality.LOST, ConnectionQuality.UNKNOWN -> 0
    }
    val on = if (filled <= 1) colors.warning else colors.primary
    val off = colors.onScrim.copy(alpha = 0.3f)
    Canvas(modifier) {
        val bar = size.width / 5f
        repeat(3) { index ->
            val h = size.height * (index + 1) / 3f
            drawRoundRect(
                if (index < filled) on else off,
                topLeft = Offset(index * bar * 2f, size.height - h),
                size = Size(bar, h),
                cornerRadius = CornerRadius(bar / 3f),
            )
        }
    }
}

/** [raised]가 되면 살짝 기울어진 채 통 튀어나오고, 내리면 줄어들며 사라지는 [HandBadge]. */
@Composable
internal fun PoppingHandBadge(raised: Boolean, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val hand by animateFloatAsState(
        if (raised) 1f else 0f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "hand",
    )
    if (hand <= 0.01f) return
    HandBadge(
        modifier.graphicsLayer {
            scaleX = hand
            scaleY = hand
            rotationZ = (1f - hand) * -25f
        },
        size = size,
    )
}

/** 손을 든 사람 표시. 장식이라 읽지 않으며, 읽을 내용은 부르는 쪽 설명에 넣는다. */
@Composable
internal fun HandBadge(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(VoxeraTheme.colors.warning)
            .border(1.5.dp, VoxeraTheme.colors.canvas.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(VoxeraIcons.Hand, contentDescription = null, tint = VoxeraTheme.colors.onDanger, modifier = Modifier.size(size * 0.55f))
    }
}
