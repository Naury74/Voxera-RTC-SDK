package com.naury.voxerartc.ui.room

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.naury.kinetica.ui.RollingText
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.ui.R
import com.naury.voxerartc.ui.component.ParticipantTile
import com.naury.voxerartc.ui.component.PoppingHandBadge
import com.naury.voxerartc.ui.component.StatusChip
import com.naury.voxerartc.ui.component.VoxeraAvatar
import com.naury.voxerartc.ui.component.VoxeraIcons
import com.naury.voxerartc.ui.theme.VoxeraRadius
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import com.naury.voxerartc.ui.theme.enterOnAppear
import com.naury.voxerartc.ui.theme.rememberReducedMotion
import com.naury.voxerartc.ui.video.VoxeraVideoRenderer
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 상단 상태 줄과 하단 조작 막대가 가리지 않도록 비워 두는 높이. */
private val TopReserve = 64.dp
internal val BottomReserve = 116.dp

/** 조작 막대를 숨겼을 때 아래에 남기는 여백. */
private val HiddenBottomReserve = 16.dp

/** 발표자 보기에서 크게 보이는 사람을 바꾸기 전에 기다리는 시간. 짧은 맞장구에 화면이 흔들리지 않게 한다. */
private const val SPEAKER_SWITCH_DELAY_MS = 1_500L

/**
 * 영상 무대. 모든 타일을 한 부모 아래에 두고 [planStage]가 정한 자리로 크기·위치를 스프링으로 옮긴다.
 * 그래서 격자 ↔ 크게 보기 ↔ 화면 공유가 바뀌어도 타일이 다시 만들어지지 않고(영상이 끊기지 않고) 자연스럽게 움직인다.
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
    participants: List<Participant>,
    renderer: VoxeraVideoRenderer?,
    pinnedId: String?,
    landscape: Boolean,
    onTogglePin: (String) -> Unit,
    modifier: Modifier = Modifier,
    topReserve: Dp = TopReserve,
    onTap: (() -> Unit)? = null,
    chromeVisible: Boolean = true,
    view: StageView = StageView.GALLERY,
) {
    val top by animateDpAsState(if (chromeVisible) maxOf(topReserve, TopReserve) else HiddenBottomReserve, label = "top")
    val bottom by animateDpAsState(if (chromeVisible) BottomReserve else HiddenBottomReserve, label = "bottom")
    // 발표자 보기: 말하는 사람이 바뀌어도 잠깐 기다렸다가 바꾼다.
    val talking = participants.filter { !it.isLocal && it.isSpeaking }.maxByOrNull { it.audioLevel }?.id
    var stableSpeaker by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(talking) {
        if (talking == null || talking == stableSpeaker) return@LaunchedEffect
        if (stableSpeaker != null) delay(SPEAKER_SWITCH_DELAY_MS)
        stableSpeaker = talking
    }
    var corner by rememberSaveable { mutableStateOf(Corner.BOTTOM_END) }
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val plan = with(density) {
            val start = insets.calculateStartPadding(direction).toPx() + VoxeraSpacing.xs.toPx()
            val end = insets.calculateEndPadding(direction).toPx() + VoxeraSpacing.xs.toPx()
            val areaTop = insets.calculateTopPadding().toPx() + top.toPx()
            val areaBottom = insets.calculateBottomPadding().toPx() + bottom.toPx()
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            planStage(
                participants = participants,
                pinnedId = pinnedId,
                view = view,
                lastSpeakerId = stableSpeaker,
                area = TileRect(start, areaTop, w - start - end, h - areaTop - areaBottom),
                full = TileRect(0f, 0f, w, h),
                landscape = landscape,
                gap = VoxeraSpacing.xs.toPx(),
                strip = (if (landscape) minOf(136.dp.toPx(), w * 0.18f) else minOf(136.dp.toPx(), h * 0.2f)),
                floatingWidth = (if (maxWidth > 600.dp) 160.dp else 112.dp).toPx(),
                floatingMargin = VoxeraSpacing.xs.toPx(),
                corner = corner,
            )
        }
        if (plan.paged) {
            GridStage(participants, renderer, landscape, onTogglePin, top, bottom, Modifier.fillMaxSize(), onTap.takeIf { !chromeVisible })
            return@BoxWithConstraints
        }
        val localId = participants.firstOrNull { it.isLocal }?.id
        val full = TileRect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        // 작은 줄 넘기기. 줄 모양이 바뀌면(크게 보기 시작·회전) 처음으로 돌아간다.
        val strip = plan.strip
        val scroll = remember(strip?.horizontal, plan.focusKey) { Animatable(0f) }
        LaunchedEffect(strip?.maxScroll) {
            scroll.updateBounds(0f, strip?.maxScroll ?: 0f)
            if (scroll.value > (strip?.maxScroll ?: 0f)) scroll.snapTo(strip?.maxScroll ?: 0f)
        }
        val stripModifier = if (strip != null && strip.maxScroll > 0f) Modifier.stripScroll(strip, scroll) else Modifier
        Box(Modifier.fillMaxSize().then(stripModifier)) {
            plan.tiles.forEach { placement ->
                val thumb = placement.role == TileRole.THUMB && strip != null
                // 보이는 칸 밖으로 한 칸 넘게 벗어난 작은 화면은 그리지 않는다(영상 렌더 부하를 보이는 만큼으로 제한).
                if (thumb && !strip!!.isNearlyVisible(placement.rect, scroll.value)) return@forEach
                key(placement.key) {
                    AnimatedTile(
                        placement,
                        draggable = placement.role == TileRole.FLOATING,
                        onDropped = { center -> corner = nearestCorner(center, full) },
                        shift = if (thumb) ({ strip!!.shift(scroll.value) }) else NoShift,
                    ) {
                        StageTileContent(
                            placement,
                            renderer,
                            pinnedId = pinnedId,
                            bottomLabel = insets.calculateBottomPadding() + bottom,
                            startLabel = insets.calculateStartPadding(direction),
                            onTogglePin = onTogglePin,
                            onTap = onTap,
                            onSwap = { localId?.let(onTogglePin) },
                            chromeVisible = chromeVisible,
                        )
                    }
                }
            }
        }
    }
}

private val NoShift: () -> IntOffset = { IntOffset.Zero }

/** 넘긴 거리만큼 작은 화면을 옮길 양. */
internal fun StripLayout.shift(scroll: Float): IntOffset =
    if (horizontal) IntOffset(-scroll.roundToInt(), 0) else IntOffset(0, -scroll.roundToInt())

/** [rect]를 [scroll]만큼 옮겼을 때 보이는 칸과 겹치거나 한 칸 안쪽으로 가까우면 `true`. */
internal fun StripLayout.isNearlyVisible(rect: TileRect, scroll: Float): Boolean = if (horizontal) {
    val x = rect.x - scroll
    x + rect.width > area.x - rect.width && x < area.right + rect.width
} else {
    val y = rect.y - scroll
    y + rect.height > area.y - rect.height && y < area.bottom + rect.height
}

/**
 * 작은 줄 위에서 시작한 끌기만 받아 줄을 넘긴다. 손을 떼면 속도만큼 미끄러지다 멈춘다.
 * 타일보다 먼저(Initial 단계에서) 움직임을 보고, 줄 방향으로 터치 여유 거리를 넘게 움직였을 때만 가로챈다.
 * 그 전까지는 아무것도 소비하지 않으므로 타일 누르기·길게 누르기는 그대로 타일이 받는다.
 */
private fun Modifier.stripScroll(strip: StripLayout, scroll: Animatable<Float, AnimationVector1D>): Modifier = composed {
    val scope = rememberCoroutineScope()
    val decay = rememberSplineBasedDecay<Float>()
    pointerInput(strip) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val start = down.position
            if (start.x !in strip.area.x..strip.area.right || start.y !in strip.area.y..strip.area.bottom) return@awaitEachGesture
            scope.launch { scroll.stop() }
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var dragging = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                tracker.addPosition(change.uptimeMillis, change.position)
                val along = if (strip.horizontal) change.position.x - start.x else change.position.y - start.y
                val across = if (strip.horizontal) change.position.y - start.y else change.position.x - start.x
                if (!dragging) {
                    if (abs(across) > slop && abs(across) > abs(along)) return@awaitEachGesture
                    if (abs(along) > slop) dragging = true else continue
                }
                val delta = if (strip.horizontal) change.positionChange().x else change.positionChange().y
                change.consume()
                scope.launch { scroll.snapTo((scroll.value - delta).coerceIn(0f, strip.maxScroll)) }
            }
            if (dragging) {
                val v = tracker.calculateVelocity()
                val velocity = -(if (strip.horizontal) v.x else v.y)
                scope.launch { scroll.animateDecay(velocity, decay) }
            }
        }
    }
}

/** 놓은 자리의 중심에서 가장 가까운 모서리. */
internal fun nearestCorner(center: Offset, full: TileRect): Corner {
    val right = center.x > full.x + full.width / 2
    val lower = center.y > full.y + full.height / 2
    return when {
        !right && !lower -> Corner.TOP_START
        right && !lower -> Corner.TOP_END
        !right -> Corner.BOTTOM_START
        else -> Corner.BOTTOM_END
    }
}

/**
 * 자리([TilePlacement.rect])로 스프링처럼 움직이는 상자. 처음 나타날 때는 살짝 커지며 흐려졌다 선명해진다.
 * [draggable]이면 손가락을 따라 움직이고, 놓으면 [onDropped]로 중심을 알린 뒤 새 자리로 붙는다.
 * 움직임 줄이기 설정이면 바로 그 자리에 둔다.
 */
@Composable
private fun AnimatedTile(
    placement: TilePlacement,
    draggable: Boolean,
    onDropped: (Offset) -> Unit,
    shift: () -> IntOffset = NoShift,
    content: @Composable () -> Unit,
) {
    val reduced = rememberReducedMotion()
    val rect = placement.rect
    val targetOffset = IntOffset(rect.x.roundToInt(), rect.y.roundToInt())
    val targetSize = IntSize(rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1))
    val offset = remember { Animatable(targetOffset, IntOffset.VectorConverter) }
    val size = remember { Animatable(targetSize, IntSize.VectorConverter) }
    val appear = remember { Animatable(if (reduced) 1f else 0f) }
    var dragging by remember { mutableStateOf(false) }
    var settle by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val moveSpec = spring<IntOffset>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
    val sizeSpec = spring<IntSize>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
    LaunchedEffect(targetOffset, settle) {
        if (dragging) return@LaunchedEffect
        if (reduced) offset.snapTo(targetOffset) else offset.animateTo(targetOffset, moveSpec)
    }
    LaunchedEffect(targetSize) { if (reduced) size.snapTo(targetSize) else size.animateTo(targetSize, sizeSpec) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(240)) }
    val density = LocalDensity.current
    Box(
        Modifier
            .zIndex(placement.role.z)
            .offset { offset.value + shift() }
            .size(with(density) { DpSize(size.value.width.toDp(), size.value.height.toDp()) })
            .graphicsLayer {
                alpha = appear.value
                val scale = 0.94f + 0.06f * appear.value
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (draggable) {
                    Modifier.pointerInput(Unit) {
                        val drop: () -> Unit = {
                            dragging = false
                            val v = offset.value
                            onDropped(Offset(v.x + size.value.width / 2f, v.y + size.value.height / 2f))
                            settle++
                        }
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = drop,
                            onDragCancel = drop,
                        ) { change, drag ->
                            change.consume()
                            scope.launch { offset.snapTo(offset.value + IntOffset(drag.x.roundToInt(), drag.y.roundToInt())) }
                        }
                    }
                } else Modifier,
            ),
    ) { content() }
}

/** 겹칠 때 위에 그릴 순서. 작은 화면이 큰 화면 위에 온다. 순서를 바꾸지 않고 높이만 정해 영상 View가 다시 붙지 않게 한다. */
private val TileRole.z: Float
    get() = when (this) {
        TileRole.FULL, TileRole.FOCUS, TileRole.GRID -> 0f
        TileRole.THUMB -> 1f
        TileRole.FLOATING -> 2f
    }

@Composable
private fun StageTileContent(
    placement: TilePlacement,
    renderer: VoxeraVideoRenderer?,
    pinnedId: String?,
    bottomLabel: Dp,
    startLabel: Dp,
    onTogglePin: (String) -> Unit,
    onTap: (() -> Unit)?,
    onSwap: () -> Unit,
    chromeVisible: Boolean,
) {
    val role = placement.role
    // 조작 막대가 숨어 있으면 어느 타일을 눌러도 먼저 막대를 꺼낸다. 막대가 보일 때 누르면 원래 동작(크게 보기 등)을 한다.
    val reveal = !chromeVisible && onTap != null
    val radius by animateDpAsState(
        when (role) {
            TileRole.FULL -> 0.dp
            TileRole.THUMB -> VoxeraRadius.md
            TileRole.FLOATING -> VoxeraRadius.lg
            TileRole.GRID, TileRole.FOCUS -> VoxeraRadius.xl
        },
        label = "radius",
    )
    val shape = RoundedCornerShape(radius)
    val person = placement.participant ?: return
    val spotlight = stringResource(R.string.voxera_tile_spotlight)
    val swap = stringResource(R.string.voxera_tile_swap)
    val controls = stringResource(R.string.voxera_tile_toggle_controls)
    val labelBottom by animateDpAsState(if (role == TileRole.FULL) bottomLabel else 0.dp, label = "label")
    ParticipantTile(
        person,
        renderer,
        Modifier.fillMaxSize(),
        pinned = role == TileRole.FOCUS && person.id == pinnedId,
        onTogglePin = if (placement.screenShare || role == TileRole.FULL || role == TileRole.FLOATING) null else ({ onTogglePin(person.id) }),
        showScreenShare = placement.screenShare,
        onTap = when {
            reveal -> onTap
            role == TileRole.GRID || role == TileRole.THUMB -> ({ onTogglePin(person.id) })
            role == TileRole.FLOATING -> onSwap
            else -> onTap
        },
        tapLabel = when {
            reveal -> controls
            role == TileRole.GRID || role == TileRole.THUMB -> spotlight
            role == TileRole.FLOATING -> swap
            else -> controls.takeIf { onTap != null }
        },
        shape = shape,
        speakingOutline = role != TileRole.FULL,
        labelPadding = PaddingValues(start = if (role == TileRole.FULL) startLabel + VoxeraSpacing.xs else 0.dp, bottom = labelBottom),
    )
}


@Composable
private fun GridStage(
    participants: List<Participant>,
    renderer: VoxeraVideoRenderer?,
    landscape: Boolean,
    onTogglePin: (String) -> Unit,
    top: Dp,
    bottom: Dp,
    modifier: Modifier,
    reveal: (() -> Unit)?,
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
            TileGrid(pages[page], renderer, landscape, onTogglePin, reveal)
        }
        if (pages.size > 1) {
            PageDots(pages.size, pager.currentPage, Modifier.padding(top = VoxeraSpacing.xs))
        }
    }
}

@Composable
private fun TileGrid(tiles: List<Participant>, renderer: VoxeraVideoRenderer?, landscape: Boolean, onTogglePin: (String) -> Unit, reveal: (() -> Unit)?) {
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
                        onTap = reveal ?: { onTogglePin(participant.id) },
                        tapLabel = stringResource(if (reveal != null) R.string.voxera_tile_toggle_controls else R.string.voxera_tile_spotlight),
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
