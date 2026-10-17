package com.naury.voxerartc.ui.room

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KCallLayer
import com.naury.kinetica.ui.KParticipantGrid
import com.naury.kinetica.ui.KParticipantGridState
import com.naury.kinetica.ui.KTileRole
import com.naury.kinetica.ui.RollingText
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.ui.R
import com.naury.voxerartc.ui.component.PoppingHandBadge
import com.naury.voxerartc.ui.component.StatusChip
import com.naury.voxerartc.ui.component.VoxeraAvatar
import com.naury.voxerartc.ui.component.VoxeraIcons
import com.naury.voxerartc.ui.component.VoxeraTile
import com.naury.voxerartc.ui.theme.VoxeraRadius
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import com.naury.voxerartc.ui.theme.enterOnAppear
import com.naury.voxerartc.ui.theme.rememberReducedMotion
import com.naury.voxerartc.ui.video.VoxeraVideoRenderer

/** 상단 상태 줄과 하단 조작 막대가 가리지 않도록 비워 두는 높이. */
private val TopReserve = 64.dp
internal val BottomReserve = 116.dp

/** 조작 막대를 숨겼을 때 아래에 남기는 여백. */
private val HiddenBottomReserve = 16.dp

/**
 * 영상 무대. [model]이 정한 대로 격자 컴포넌트가 타일을 놓고, 격자 ↔ 크게 보기 ↔ 화면 공유가 바뀌면 같은 타일이 새 자리로 스프링처럼 옮겨 간다
 * (타일이 다시 만들어지지 않아 영상이 끊기지 않는다).
 *
 * 같은 [grid]로 두 번 그린다. 화면 배경(유리 바가 비추는 기록)에는 [KCallLayer.Video]로 영상과 아바타만, 그 위층에는 [KCallLayer.Overlay]로
 * 이름표·말하는 사람 테두리·누르기·TalkBack을 그린다. 그래서 테두리가 돌아도 유리 블러는 영상이 바뀔 때만 다시 한다.
 *
 * - 격자 타일을 누르면 그 사람을 크게 보고, 나머지는 손가락으로 넘겨 보는 작은 줄로 간다. 크게 보이는 타일을 누르면 조작 막대를 숨기거나 보인다.
 * - 1:1은 상대가 화면 전체, 나는 끌어 옮기는 작은 화면. 작은 화면을 누르면 서로 바뀐다.
 * - 9명이 넘는 갤러리는 9칸씩 쪽을 넘긴다.
 *
 * @param onTap 크게 보이는 타일을 누름(조작 막대 보이기/숨기기).
 * @param chromeVisible 조작 막대가 보이는지. 숨기면 무대가 아래까지 넓어진다.
 */
@Composable
internal fun VideoStage(
    model: StageModel,
    renderer: VoxeraVideoRenderer?,
    pinnedId: String?,
    layer: KCallLayer,
    grid: KParticipantGridState,
    onTogglePin: (String) -> Unit,
    modifier: Modifier = Modifier,
    topReserve: Dp = TopReserve,
    onTap: (() -> Unit)? = null,
    chromeVisible: Boolean = true,
) {
    val top by animateDpAsState(if (chromeVisible) maxOf(topReserve, TopReserve) else HiddenBottomReserve, label = "top")
    val bottom by animateDpAsState(if (chromeVisible) BottomReserve else HiddenBottomReserve, label = "bottom")
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    val padding = PaddingValues(
        start = insets.calculateStartPadding(direction) + VoxeraSpacing.xs,
        top = insets.calculateTopPadding() + top,
        end = insets.calculateEndPadding(direction) + VoxeraSpacing.xs,
        bottom = insets.calculateBottomPadding() + bottom,
    )
    val localId = model.items.firstOrNull { it.participant.isLocal && !it.screenShare }?.key
    val crowded = model.items.size > 4
    KParticipantGrid(
        model.items,
        key = { it.key },
        modifier = modifier,
        state = grid,
        focusKey = model.focusKey,
        selfKey = model.selfKey,
        contentPadding = padding,
        spacing = VoxeraSpacing.xs,
        layer = layer,
    ) { item, role ->
        StageTile(
            item,
            role,
            renderer,
            pinnedId = pinnedId,
            layer = layer,
            crowded = crowded,
            fullLabel = PaddingValues(start = insets.calculateStartPadding(direction) + VoxeraSpacing.xs, bottom = insets.calculateBottomPadding() + bottom),
            onTogglePin = onTogglePin,
            onTap = onTap,
            onSwap = { localId?.let(onTogglePin) },
            chromeVisible = chromeVisible,
        )
    }
}

@Composable
private fun StageTile(
    item: StageItem,
    role: KTileRole,
    renderer: VoxeraVideoRenderer?,
    pinnedId: String?,
    layer: KCallLayer,
    crowded: Boolean,
    fullLabel: PaddingValues,
    onTogglePin: (String) -> Unit,
    onTap: (() -> Unit)?,
    onSwap: () -> Unit,
    chromeVisible: Boolean,
) {
    val person = item.participant
    // 조작 막대가 숨어 있으면 어느 타일을 눌러도 먼저 막대를 꺼낸다. 막대가 보일 때 누르면 원래 동작(크게 보기 등)을 한다.
    val reveal = !chromeVisible && onTap != null
    val radius by animateDpAsState(
        when (role) {
            KTileRole.Full -> 0.dp
            KTileRole.Strip -> VoxeraRadius.md
            KTileRole.Floating -> VoxeraRadius.lg
            KTileRole.Grid -> if (crowded) VoxeraRadius.md else VoxeraRadius.xl
            KTileRole.Focus -> VoxeraRadius.xl
        },
        label = "radius",
    )
    val spotlight = stringResource(R.string.voxera_tile_spotlight)
    val swap = stringResource(R.string.voxera_tile_swap)
    val controls = stringResource(R.string.voxera_tile_toggle_controls)
    val small = role == KTileRole.Grid || role == KTileRole.Strip
    VoxeraTile(
        person,
        renderer,
        Modifier.fillMaxSize(),
        layer = layer,
        pinned = role == KTileRole.Focus && person.id == pinnedId,
        onTogglePin = if (item.screenShare || role == KTileRole.Full || role == KTileRole.Floating) null else ({ onTogglePin(person.id) }),
        labelPadding = if (role == KTileRole.Full) fullLabel else PaddingValues(0.dp),
        screenShare = item.screenShare,
        onTap = when {
            reveal -> onTap
            small -> ({ onTogglePin(person.id) })
            role == KTileRole.Floating -> onSwap
            else -> onTap
        },
        tapLabel = when {
            reveal -> controls
            small -> spotlight
            role == KTileRole.Floating -> swap
            else -> controls.takeIf { onTap != null }
        },
        shape = RoundedCornerShape(radius),
        speakingOutline = role != KTileRole.Full,
    )
}

/**
 * 음성 무대. 1:1은 큰 아바타와 말할 때 퍼지는 물결, 이름·시간·연결 상태. 여럿이면 아바타 격자.
 *
 * @param status 이름 아래 상태 문구(예: "연결 안정"). `null`이면 쓰지 않는다.
 */
@Composable
internal fun VoiceStage(participants: List<Participant>, elapsed: String, status: String?, modifier: Modifier = Modifier) {
    val remotes = participants.filterNot { it.isLocal }
    BoxWithConstraints(modifier.windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
        // 가로 모드처럼 세로가 짧으면 아바타와 여백을 줄여 이름·시간이 조작 막대에 가리지 않게 한다.
        val avatarSize = (maxHeight * 0.26f).coerceIn(88.dp, 168.dp)
        val gap = if (maxHeight < 560.dp) VoxeraSpacing.md else 48.dp
        if (remotes.size <= 1) {
            val focus = remotes.firstOrNull()
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 96.dp)) {
                if (focus != null) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.enterOnAppear(0, scale = true)) {
                        SpeakingRipple(focus.isSpeaking, focus.audioLevel, avatarSize)
                        VoxeraAvatar(focus.id, focus.displayName, size = avatarSize, speaking = focus.isSpeaking, audioLevel = focus.audioLevel)
                        PoppingHandBadge(focus.isHandRaised, Modifier.align(Alignment.TopEnd), size = 40.dp)
                    }
                    // 물결 고리가 아바타 밖으로 퍼지므로 이름과 사이를 넉넉히 둔다.
                    Spacer(Modifier.height(gap))
                    Text(focus.displayName, color = VoxeraTheme.colors.foreground, style = VoxeraTheme.typography.display, textAlign = TextAlign.Center, modifier = Modifier.enterOnAppear(1))
                    Spacer(Modifier.height(VoxeraSpacing.xxs))
                    RollingText(elapsed, style = VoxeraTheme.typography.title.copy(color = VoxeraTheme.colors.primary), modifier = Modifier.enterOnAppear(2))
                    if (status != null) {
                        Spacer(Modifier.height(VoxeraSpacing.xxs))
                        // 연결 상태가 바뀌면 문구가 서서히 바뀐다.
                        AnimatedContent(status, transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) }, label = "status", modifier = Modifier.enterOnAppear(3)) { text ->
                            Text(text, color = VoxeraTheme.colors.foregroundSecondary, style = VoxeraTheme.typography.body)
                        }
                    }
                    if (!focus.isMicrophoneEnabled) {
                        Spacer(Modifier.height(VoxeraSpacing.sm))
                        StatusChip(stringResource(R.string.voxera_mic_off), VoxeraTheme.colors.warning, icon = VoxeraIcons.MicOff)
                    }
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        SpeakingRipple(speaking = true, level = 0.4f, size = 120.dp)
                        Icon(VoxeraIcons.Person, contentDescription = null, tint = VoxeraTheme.colors.foregroundSecondary, modifier = Modifier.size(56.dp))
                    }
                    Spacer(Modifier.height(VoxeraSpacing.lg))
                    Text(stringResource(R.string.voxera_waiting_others), color = VoxeraTheme.colors.foregroundSecondary, style = VoxeraTheme.typography.body)
                }
            }
        } else {
            VoiceGroupGrid(participants, Modifier.padding(top = 72.dp, bottom = 120.dp))
        }
    }
}

/** 말하는 동안 아바타 뒤로 퍼져 나가는 고리 두 개. 움직임 줄이기 설정이면 정지한 옅은 고리만 둔다. */
@Composable
internal fun SpeakingRipple(speaking: Boolean, level: Float, size: Dp) {
    val color = VoxeraTheme.colors.primary
    val reduced = rememberReducedMotion()
    val strength = if (speaking) 0.35f + level.coerceIn(0f, 1f) * 0.65f else 0f
    if (strength == 0f) return
    val progress = if (reduced) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "ripple")
        val value by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Restart), label = "ripple-progress")
        value
    }
    // 고리는 아바타 크기 칸 밖으로 넘쳐 그리며 자리를 차지하지 않는다(작은 PiP 창에서도 이름이 밀려나지 않게).
    Canvas(Modifier.size(size)) {
        val base = size.toPx() / 2f
        listOf(progress, (progress + 0.5f) % 1f).forEach { p ->
            drawCircle(color.copy(alpha = (1f - p) * 0.28f * strength), radius = base * (1f + 0.75f * p))
        }
    }
}

/** 음성 그룹 통화: 아바타 격자. 말하는 사람은 고리로 표시한다. 화면보다 많으면 위아래로 넘겨 모두 본다. */
@Composable
internal fun VoiceGroupGrid(participants: List<Participant>, modifier: Modifier = Modifier) {
    val shown = participants
    val columns = if (shown.size <= 4) 2 else 3
    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = VoxeraSpacing.md)) {
        val cellWidth = (maxWidth - VoxeraSpacing.md * (columns - 1)) / columns
        val avatar = (cellWidth * 0.6f).coerceIn(56.dp, 120.dp)
        // 적을 때는 가운데에 모으고, 많으면 위에서부터 넘긴다.
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(vertical = VoxeraSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.lg, Alignment.CenterVertically),
        ) {
            shown.chunked(columns).forEachIndexed { rowIndex, row ->
                // 덜 찬 마지막 줄도 가운데로 모은다.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.md, Alignment.CenterHorizontally)) {
                    row.forEachIndexed { colIndex, participant ->
                        key(participant.id) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(cellWidth).enterOnAppear(rowIndex * columns + colIndex, scale = true)) {
                            Box(contentAlignment = Alignment.Center) {
                                SpeakingRipple(participant.isSpeaking, participant.audioLevel, avatar)
                                VoxeraAvatar(participant.id, participant.displayName, size = avatar, speaking = participant.isSpeaking, audioLevel = participant.audioLevel)
                                PoppingHandBadge(participant.isHandRaised, Modifier.align(Alignment.TopEnd), size = 28.dp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = VoxeraSpacing.xs)) {
                                if (!participant.isMicrophoneEnabled) {
                                    Icon(VoxeraIcons.MicOff, contentDescription = stringResource(R.string.voxera_mic_off), tint = VoxeraTheme.colors.warning, modifier = Modifier.size(14.dp).padding(end = 2.dp))
                                }
                                Text(
                                    if (participant.isLocal) stringResource(R.string.voxera_you) else participant.displayName,
                                    color = VoxeraTheme.colors.foreground,
                                    style = VoxeraTheme.typography.label,
                                    maxLines = 1,
                                )
                            }
                        }
                        }
                    }
                }
            }
        }
    }
}
