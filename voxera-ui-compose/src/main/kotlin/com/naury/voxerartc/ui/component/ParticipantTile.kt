package com.naury.voxerartc.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KAvatarData
import com.naury.kinetica.ui.KCallLayer
import com.naury.kinetica.ui.KNetworkQuality
import com.naury.kinetica.ui.KParticipantTile
import com.naury.voxerartc.api.ConnectionQuality
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.ui.R
import com.naury.voxerartc.ui.theme.VoxeraRadius
import com.naury.voxerartc.ui.theme.VoxeraTheme
import com.naury.voxerartc.ui.video.VoxeraVideoRenderer

/**
 * 참가자 타일. 카메라가 켜져 있고 [renderer]가 있으면 영상, 아니면 이니셜 아바타와 말하는 동안 퍼지는 물결을 보여 준다. 카메라를
 * 끄면 마지막 프레임을 남기지 않고 아바타로 바뀐다. 이름표에는 마이크 꺼짐이나 소리 막대, 약한 연결 표시가 붙고, 손을 들면 손 배지가 튀어나온다.
 *
 * 누르면 [onTap](예: 이 사람 크게 보기, 조작 막대 보이기/숨기기)을, 길게 누르면 [onTogglePin]을 부른다. 누르는 동안 살짝 작아진다.
 * 고정된 타일은 위쪽 끝에 고정 표시가 붙는다. TalkBack은 타일 하나로 모든 상태를 읽고, 두 번 탭하면 [onTap]을 [tapLabel] 이름으로 부르며,
 * 고정과 해제는 사용자 지정 동작으로 한다. [onTap]이 없으면 두 번 탭으로 고정/해제한다.
 *
 * @param onTogglePin `null`이 아니면 길게 눌러 고정/해제한다. [onTap]이 없으면 짧게 눌러도 고정/해제한다.
 * @param labelPadding 이름표를 더 안쪽으로 옮길 여백. 전체 화면 타일에서 조작 막대나 시스템 막대를 피할 때 쓴다.
 * @param showScreenShare `true`면 카메라 대신 화면 공유 트랙을 그리고 이름표에 화면 공유임을 붙인다.
 * @param tapLabel TalkBack이 [onTap]을 읽을 동작 이름.
 * @param shape 타일 모서리. 기본은 24dp.
 * @param speakingOutline 말하는 동안 테두리를 그린다. 화면을 꽉 채운 타일에서는 끄고 이름표의 막대만 남긴다.
 */
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
    VoxeraTile(
        participant,
        renderer,
        modifier,
        layer = KCallLayer.All,
        pinned = pinned,
        onTogglePin = onTogglePin,
        labelPadding = labelPadding,
        screenShare = showScreenShare,
        onTap = onTap,
        tapLabel = tapLabel,
        shape = shape,
        speakingOutline = speakingOutline,
    )
}

/**
 * [ParticipantTile]의 구현. 통화 무대는 같은 타일을 [KCallLayer.Video](영상과 아바타, 유리 바가 비추는 배경)와
 * [KCallLayer.Overlay](이름표, 글로우 테두리, 물결, 누르기, TalkBack) 두 층으로 그려, 테두리가 돌아도 유리 블러를 다시 하지 않게 한다.
 */
@Composable
internal fun VoxeraTile(
    participant: Participant,
    renderer: VoxeraVideoRenderer?,
    modifier: Modifier = Modifier,
    layer: KCallLayer = KCallLayer.All,
    pinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
    labelPadding: PaddingValues = PaddingValues(0.dp),
    screenShare: Boolean = false,
    onTap: (() -> Unit)? = null,
    tapLabel: String? = null,
    shape: Shape = RoundedCornerShape(VoxeraRadius.xl),
    speakingOutline: Boolean = true,
) {
    val colors = VoxeraTheme.colors
    val name = if (participant.isLocal) stringResource(R.string.voxera_you) else participant.displayName
    val track = if (screenShare) participant.screenShare else participant.camera
    // 아바타는 같은 사람이 늘 같은 색이다. 흰 이니셜이 읽히도록 섞지 않은 기본 색을 쓴다.
    val avatar = remember(participant.id, name, colors) { KAvatarData(name, color = avatarColors(participant.id, colors).second) }
    // 내 연결 상태는 상단 바가 알리므로 약한 연결 표시는 상대 타일에만 붙인다.
    val quality = if (participant.isLocal) KNetworkQuality.Unknown else participant.connectionQuality.networkQuality()
    val level = participant.audioLevel
    KParticipantTile(
        name = name,
        modifier = modifier,
        video = if (track != null && renderer != null) {
            // 내 카메라는 렌더러가 뒤집는다(SurfaceView는 Compose 변환을 따르지 않는다).
            { renderer.Render(track, mirror = participant.isLocal && !screenShare, modifier = Modifier.fillMaxSize()) }
        } else {
            null
        },
        avatar = avatar,
        muted = !participant.isMicrophoneEnabled,
        speaking = participant.isSpeaking,
        audioLevel = { level },
        handRaised = participant.isHandRaised,
        quality = quality,
        pinned = pinned,
        screenShare = screenShare,
        onClick = onTap,
        onClickLabel = tapLabel,
        onTogglePin = onTogglePin,
        speakingBorder = speakingOutline,
        layer = layer,
        shape = shape,
        labelPadding = labelPadding,
    )
}

private fun ConnectionQuality.networkQuality(): KNetworkQuality = when (this) {
    ConnectionQuality.EXCELLENT -> KNetworkQuality.Excellent
    ConnectionQuality.GOOD -> KNetworkQuality.Good
    ConnectionQuality.POOR -> KNetworkQuality.Poor
    ConnectionQuality.LOST -> KNetworkQuality.Lost
    ConnectionQuality.UNKNOWN -> KNetworkQuality.Unknown
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
