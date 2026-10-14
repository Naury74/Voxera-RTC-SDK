package com.naury.voxerartc.ui.room

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.ui.R
import com.naury.voxerartc.ui.component.ParticipantTile
import com.naury.voxerartc.ui.component.StatusChip
import com.naury.voxerartc.ui.component.VoxeraAvatar
import com.naury.voxerartc.ui.component.VoxeraIcons
import com.naury.voxerartc.ui.theme.VoxeraRadius
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import com.naury.voxerartc.ui.theme.rememberReducedMotion
import com.naury.voxerartc.ui.video.VoxeraVideoRenderer
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 상단 상태 줄과 하단 조작 막대가 가리지 않도록 비워 두는 높이. */
private val TopReserve = 64.dp
internal val BottomReserve = 116.dp

/** 조작 막대를 숨겼을 때 아래에 남기는 여백. */
private val HiddenBottomReserve = 16.dp

/**
 * 영상 무대. 다른 사람이 화면을 공유하면 공유 화면을 크게, 고정한 타일이 있으면 큰 타일 + 아래 줄,
 * 2명은 상대 전체 화면 + 끌어 옮길 수 있는 내 영상, 그 밖에는 격자(9명 넘으면 쪽 넘김).
 *
 * @param onTap 무대를 짧게 누름(조작 막대 보이기/숨기기). 길게 누르면 그 타일을 고정한다.
 * @param chromeVisible 조작 막대가 보이는지. 숨기면 무대가 아래까지 넓어진다.
 */
@Composable
internal fun VideoStage(
    participants: List<Participant>,
    renderer: VoxeraVideoRenderer?,
    pinnedId: String?,
    landscape: Boolean,
    onTogglePin: (String) -> Unit,
    modifier: Modifier = Modifier,
    topReserve: Dp = TopReserve,
    onTap: (() -> Unit)? = null,
    chromeVisible: Boolean = true,
) {
    val top = if (chromeVisible) maxOf(topReserve, TopReserve) else HiddenBottomReserve
    val bottom = if (chromeVisible) BottomReserve else HiddenBottomReserve
    val pinned = participants.firstOrNull { it.id == pinnedId }
    val local = participants.firstOrNull { it.isLocal }
    val remotes = participants.filterNot { it.isLocal }
    // 다른 사람이 화면을 공유하면 그 화면을 무대에 크게 둔다(G-09). 내 공유 화면은 다시 그리지 않는다.
    val sharing = remotes.firstOrNull { it.screenShare != null }
    when {
        sharing != null -> StripStage(sharing, participants, renderer, onTogglePin, onTap, top, bottom, modifier, showScreenShare = true)
        pinned != null -> StripStage(pinned, participants.filterNot { it.id == pinned.id }, renderer, onTogglePin, onTap, top, bottom, modifier, pinned = true)
        remotes.size == 1 && local != null -> OneOnOneStage(remotes.single(), local, renderer, onTap, top, bottom, modifier)
        else -> GridStage(participants, renderer, landscape, onTogglePin, onTap, top, bottom, modifier)
    }
}

/** 화면 모서리 네 곳. 내 영상이 붙는 자리. */
private enum class Corner { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

@Composable
private fun OneOnOneStage(
    remote: Participant,
    local: Participant,
    renderer: VoxeraVideoRenderer?,
    onTap: (() -> Unit)?,
    top: Dp,
    bottom: Dp,
    modifier: Modifier,
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    // 내 영상을 누르면 큰 화면과 작은 화면을 바꾼다.
    var swapped by rememberSaveable { mutableIntStateOf(0) }
    val main = if (swapped == 1) local else remote
    val small = if (swapped == 1) remote else local
    Box(modifier) {
        ParticipantTile(
            main,
            renderer,
            Modifier.fillMaxSize(),
            onTap = onTap,
            shape = RoundedCornerShape(0.dp),
            speakingOutline = false,
            labelPadding = PaddingValues(
                start = insets.calculateStartPadding(direction) + VoxeraSpacing.xs,
                bottom = insets.calculateBottomPadding() + bottom,
            ),
        )
        FloatingTile(small, renderer, top = top, bottom = bottom, onSwap = { swapped = 1 - swapped })
    }
}

/**
 * 끌어서 옮기는 작은 영상. 손을 떼면 가장 가까운 모서리로 튕기듯 붙고, 누르면 [onSwap]. 조작 막대가 숨으면 아래 모서리도 함께 내려간다.
 * TalkBack에서는 두 번 탭으로 화면을 바꾼다.
 */
@Composable
private fun FloatingTile(participant: Participant, renderer: VoxeraVideoRenderer?, top: Dp, bottom: Dp, onSwap: () -> Unit) {
    val density = LocalDensity.current
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val swapLabel = stringResource(R.string.voxera_tile_swap)
    var corner by rememberSaveable { mutableIntStateOf(Corner.BOTTOM_END.ordinal) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = top, bottom = bottom + VoxeraSpacing.xs, start = VoxeraSpacing.md, end = VoxeraSpacing.md),
    ) {
        val tileWidth = if (maxWidth > 600.dp) 160.dp else 112.dp
        val tileHeight = tileWidth * 4 / 3
        val maxX = with(density) { (maxWidth - tileWidth).toPx().coerceAtLeast(0f) }
        val maxY = with(density) { (maxHeight - tileHeight).toPx().coerceAtLeast(0f) }
        fun target(c: Int): Offset = when (Corner.entries[c]) {
            Corner.TOP_START -> Offset(0f, 0f)
            Corner.TOP_END -> Offset(maxX, 0f)
            Corner.BOTTOM_START -> Offset(0f, maxY)
            Corner.BOTTOM_END -> Offset(maxX, maxY)
        }
        val position = remember { Animatable(target(corner), Offset.VectorConverter) }
        // 화면 크기·조작 막대가 바뀌면 같은 모서리를 지키며 따라간다.
        LaunchedEffect(maxX, maxY) {
            if (reduced) position.snapTo(target(corner)) else position.animateTo(target(corner), spring(stiffness = Spring.StiffnessMediumLow))
        }
        ParticipantTile(
            participant,
            renderer,
            Modifier
                .offset { IntOffset(position.value.x.roundToInt(), position.value.y.roundToInt()) }
                .size(width = tileWidth, height = tileHeight)
                .pointerInput(maxX, maxY) {
                    detectDragGestures(
                        onDragEnd = {
                            val (x, y) = position.value
                            val right = x > maxX / 2
                            val lower = y > maxY / 2
                            corner = when {
                                !right && !lower -> Corner.TOP_START
                                right && !lower -> Corner.TOP_END
                                !right -> Corner.BOTTOM_START
                                else -> Corner.BOTTOM_END
                            }.ordinal
                            scope.launch {
                                position.animateTo(target(corner), spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
                            }
                        },
                    ) { change, drag ->
                        change.consume()
                        scope.launch {
                            val next = position.value + drag
                            position.snapTo(Offset(next.x.coerceIn(-24f, maxX + 24f), next.y.coerceIn(-24f, maxY + 24f)))
                        }
                    }
                },
            onTap = onSwap,
            tapLabel = swapLabel,
            shape = RoundedCornerShape(VoxeraRadius.lg),
        )
    }
}

@Composable
private fun StripStage(
    focus: Participant,
    others: List<Participant>,
    renderer: VoxeraVideoRenderer?,
    onTogglePin: (String) -> Unit,
    onTap: (() -> Unit)?,
    top: Dp,
    bottom: Dp,
    modifier: Modifier,
    showScreenShare: Boolean = false,
    pinned: Boolean = false,
) {
    Column(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = top, bottom = bottom, start = VoxeraSpacing.xs, end = VoxeraSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs),
    ) {
        ParticipantTile(
            focus,
            renderer,
            Modifier.fillMaxWidth().weight(1f),
            pinned = pinned,
            showScreenShare = showScreenShare,
            onTogglePin = if (showScreenShare) null else ({ onTogglePin(focus.id) }),
            onTap = onTap,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
            items(others, key = { it.id }) { participant ->
                ParticipantTile(
                    participant,
                    renderer,
                    Modifier.size(width = 96.dp, height = 128.dp),
                    onTogglePin = { onTogglePin(participant.id) },
                    onTap = onTap,
                    shape = RoundedCornerShape(VoxeraRadius.md),
                )
            }
        }
    }
}

@Composable
private fun GridStage(
    participants: List<Participant>,
    renderer: VoxeraVideoRenderer?,
    landscape: Boolean,
    onTogglePin: (String) -> Unit,
    onTap: (() -> Unit)?,
    top: Dp,
    bottom: Dp,
    modifier: Modifier,
) {
    val pages = gridPages(participants)
    val pager = rememberPagerState { pages.size }
    Column(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = top, bottom = bottom),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 화면 밖 쪽의 타일은 그리지 않으므로 영상 디코딩·렌더 부하가 보이는 9칸으로 제한된다(E25).
        HorizontalPager(pager, Modifier.weight(1f), beyondViewportPageCount = 0, key = { pages[it].firstOrNull()?.id ?: it }) { page ->
            TileGrid(pages[page], renderer, landscape, onTogglePin, onTap)
        }
        if (pages.size > 1) {
            PageDots(pages.size, pager.currentPage, Modifier.padding(top = VoxeraSpacing.xs))
        }
    }
}

@Composable
private fun TileGrid(tiles: List<Participant>, renderer: VoxeraVideoRenderer?, landscape: Boolean, onTogglePin: (String) -> Unit, onTap: (() -> Unit)?) {
    val spec = gridFor(tiles.size, landscape)
    Column(Modifier.fillMaxSize().padding(horizontal = VoxeraSpacing.xs), verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
        tiles.chunked(spec.columns).forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
                row.forEach { participant ->
                    ParticipantTile(
                        participant,
                        renderer,
                        Modifier.weight(1f).fillMaxHeight(),
                        onTogglePin = { onTogglePin(participant.id) },
                        onTap = onTap,
                        shape = RoundedCornerShape(if (spec.columns >= 3) VoxeraRadius.md else VoxeraRadius.xl),
                    )
                }
                repeat(spec.columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** 쪽 위치 점. 지금 쪽은 길게 그린다. TalkBack에는 "2/3쪽"처럼 읽힌다. */
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val colors = VoxeraTheme.colors
    val label = stringResource(R.string.voxera_page_of, current + 1, count)
    Row(
        modifier
            .clip(CircleShape)
            .background(colors.scrim.copy(alpha = 0.5f))
            .padding(horizontal = VoxeraSpacing.xs, vertical = 6.dp)
            .clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(width = if (index == current) 16.dp else 6.dp, height = 6.dp)
                    .clip(CircleShape)
                    .background(if (index == current) colors.onScrim else colors.onScrim.copy(alpha = 0.4f)),
            )
        }
    }
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
                    Box(contentAlignment = Alignment.Center) {
                        SpeakingRipple(focus.isSpeaking, focus.audioLevel, avatarSize)
                        VoxeraAvatar(focus.id, focus.displayName, size = avatarSize, speaking = focus.isSpeaking, audioLevel = focus.audioLevel)
                    }
                    // 물결 고리가 아바타 밖으로 퍼지므로 이름과 사이를 넉넉히 둔다.
                    Spacer(Modifier.height(gap))
                    Text(focus.displayName, color = VoxeraTheme.colors.foreground, style = VoxeraTheme.typography.display, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(VoxeraSpacing.xxs))
                    Text(elapsed, color = VoxeraTheme.colors.primary, style = VoxeraTheme.typography.title)
                    if (status != null) {
                        Spacer(Modifier.height(VoxeraSpacing.xxs))
                        Text(status, color = VoxeraTheme.colors.foregroundSecondary, style = VoxeraTheme.typography.body)
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

/** 음성 그룹 통화: 아바타 격자. 말하는 사람은 고리로 표시한다. */
@Composable
internal fun VoiceGroupGrid(participants: List<Participant>, modifier: Modifier = Modifier) {
    val (shown, hidden) = visibleTiles(participants)
    val columns = if (shown.size <= 4) 2 else 3
    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = VoxeraSpacing.md)) {
        val avatar = ((maxWidth - VoxeraSpacing.md * (columns - 1)) / columns * 0.6f).coerceIn(56.dp, 120.dp)
        Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.lg)) {
            shown.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { participant ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Box(contentAlignment = Alignment.Center) {
                                SpeakingRipple(participant.isSpeaking, participant.audioLevel, avatar)
                                VoxeraAvatar(participant.id, participant.displayName, size = avatar, speaking = participant.isSpeaking, audioLevel = participant.audioLevel)
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
                    repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                }
            }
            if (hidden > 0) {
                Text(
                    pluralStringResource(R.plurals.voxera_more_participants, hidden, hidden),
                    color = VoxeraTheme.colors.foregroundSecondary,
                    style = VoxeraTheme.typography.body,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}
