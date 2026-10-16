package com.naury.voxerartc.ui.room

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.geometry.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.SurfaceStyle
import com.naury.voxerartc.ui.glass.glassStyle
import com.naury.voxerartc.ui.glass.pressScale
import com.naury.voxerartc.ui.theme.enterOnAppear
import com.naury.voxerartc.ui.theme.rememberReducedMotion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.LocalMediaState
import com.naury.voxerartc.api.MonotonicClock
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.api.RtcEngineKind
import com.naury.voxerartc.api.SessionState
import com.naury.voxerartc.api.VideoEffect
import com.naury.voxerartc.core.VoxeraSession
import com.naury.voxerartc.ui.R
import com.naury.voxerartc.ui.component.VoxeraButton
import com.naury.voxerartc.ui.component.VoxeraButtonTone
import com.naury.voxerartc.ui.component.VoxeraIcons
import com.naury.voxerartc.ui.component.SheetGrabber
import com.naury.voxerartc.ui.glass.GlassLevel
import com.naury.voxerartc.ui.glass.GlassSurface
import com.naury.voxerartc.ui.glass.voxeraBackdrop
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import com.naury.voxerartc.ui.theme.rememberTouchExploration
import com.naury.voxerartc.ui.theme.VoxeraThemeConfig
import com.naury.voxerartc.ui.video.LocalSimulatedEffect
import com.naury.voxerartc.ui.video.LocalVoxeraVideoRenderer
import com.naury.voxerartc.ui.video.SimulatedVideoRenderer
import com.naury.voxerartc.ui.video.VoxeraVideoRenderer
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 통화 화면에서 연 시트. */
private enum class RoomSheet { NONE, AUDIO, PEOPLE, QUALITY, MORE, EFFECTS, REACTIONS, CHAT }

/** 영상 통화에서 아무 조작이 없을 때 조작 막대를 숨기기까지 기다리는 시간. 접근성 서비스가 켜져 있으면 늘어나거나 숨기지 않는다. */
private const val CHROME_TIMEOUT_MS = 4_000L

/**
 * 룸 통화 화면. [session] 상태를 관찰만 하고 세션을 만들거나 닫지 않는다. 입장(`join`)은 사용자 동작에 맞춰 호스트가 부른다.
 *
 * 화면이 사라져도 세션은 남는다. 통화를 끝내려면 호스트가 `leave`/`close`를 부르거나 사용자가 종료 버튼을 눌러야 한다.
 * [VoxeraRoomScreen]은 [VoxeraTheme]을 직접 적용하므로 호스트 `MaterialTheme` 안에 두어도 색이 섞이지 않는다.
 *
 * @param onExit 종료나 실패 화면에서 닫기를 누르면 불린다. 호스트 내비게이션으로 돌아갈 때 쓴다.
 * @param onRejoin `null`이 아니면 종료나 실패 화면에 다시 입장 버튼을 보여 준다.
 * @param onEnd 종료/취소 버튼을 눌렀을 때의 동작. `null`이면 `session.leave()`. Call Mode에서는 `CallCoordinator.hangup`을 넘긴다.
 * @param onShareScreen `null`이 아니면 영상 통화에 화면 공유 버튼을 보여 준다. 호스트가 시스템 화면 공유 승인 대화상자를 띄우고
 *   승인 결과로 `session.startScreenShare`를 부른다(예: voxera-livekit-compose의 `rememberScreenShareLauncher`).
 * @param onSaveReport `null`이 아니면 종료나 실패 화면에 진단 리포트 저장 버튼을 보여 준다. 저장 위치와 공유는 호스트가 정한다.
 * @param pictureInPicture 호스트 Activity가 PiP 창에 있으면 `true`. 조작 버튼, 시트, 알림을 빼고 말하는 사람 한 명만 작게 그린다.
 * @param onEnterPictureInPicture `null`이 아니면 상단에 PiP 버튼을 둔다. 호스트가 `enterPictureInPictureMode`를 부른다.
 * @param autoHideControls 영상 통화에서 4초 동안 조작이 없으면 상단 줄과 조작 막대를 숨긴다. 화면을 누르면 다시 나온다.
 * @param reactionsEnabled 반응 보내기 버튼과 떠오르는 반응을 보여 준다. 엔진이 지원하지 않으면 보내기는 아무 일도 하지 않는다.
 * @param chatEnabled 채팅 버튼(읽지 않은 수 배지)과 채팅 시트, 새 메시지 알림을 보여 준다.
 * @param handRaiseEnabled 반응 시트에 손들기 버튼을 둔다. [reactionsEnabled]가 `false`면 반응 시트가 없으므로 보이지 않는다.
 */
@Composable
public fun VoxeraRoomScreen(
    session: VoxeraSession,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    theme: VoxeraThemeConfig = VoxeraThemeConfig(),
    onRejoin: (() -> Unit)? = null,
    onEnd: (() -> Unit)? = null,
    onShareScreen: (() -> Unit)? = null,
    onSaveReport: (() -> Unit)? = null,
    pictureInPicture: Boolean = false,
    onEnterPictureInPicture: (() -> Unit)? = null,
    autoHideControls: Boolean = true,
    reactionsEnabled: Boolean = true,
    chatEnabled: Boolean = true,
    handRaiseEnabled: Boolean = true,
) {
    VoxeraTheme(theme) {
        val state by session.state.collectAsStateWithLifecycle()
        val participants by session.participants.collectAsStateWithLifecycle()
        val media by session.localMedia.collectAsStateWithLifecycle()
        val history = rememberQualityHistory(session)
        val simulated = session.engineKind == RtcEngineKind.SIMULATED
        val renderer = if (simulated) SimulatedVideoRenderer else LocalVoxeraVideoRenderer.current
        val scope = rememberCoroutineScope()
        val leave: () -> Unit = onEnd ?: { scope.launch { session.leave() } }
        val speaking = participants.filter { !it.isLocal && it.isSpeaking }.maxOfOrNull { it.audioLevel } ?: 0f

        CompositionLocalProvider(LocalSimulatedEffect provides if (simulated) media.videoEffect else VideoEffect.None) {
            Box(modifier.fillMaxSize().voxeraBackdrop(emphasis = speaking)) {
                // 연결 중 → 통화 → 종료처럼 단계가 바뀔 때만 화면을 바꾼다. 같은 단계 안의 변화(참가자, 재연결)는 그대로 이어진다.
                val phase = state.phase(pictureInPicture)
                // 크게 보는 사람과 보기 방식은 PiP에 들어갔다 나와도(통화 화면이 다시 만들어져도) 이어지게 여기서 들고 있는다.
                val stage = rememberStageSelection()
                val reduced = rememberReducedMotion()
                AnimatedContent(
                    targetState = phase,
                    transitionSpec = {
                        if (reduced) {
                            fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                        } else {
                            (fadeIn(tween(280, delayMillis = 60)) + scaleIn(tween(320), initialScale = 0.96f)) togetherWith
                                (fadeOut(tween(160)) + scaleOut(tween(200), targetScale = 1.02f))
                        }
                    },
                    label = "room-phase",
                ) { target ->
                    val current = state
                    when (target) {
                        RoomPhase.PIP_CALL -> when (current) {
                            is SessionState.Connected -> PictureInPictureContent(session, participants, media, renderer, current.mode, stage.pinnedId.value)
                            is SessionState.Reconnecting -> PictureInPictureContent(session, participants, media, renderer, current.mode, stage.pinnedId.value)
                            else -> PictureInPictureStatus(current)
                        }
                        RoomPhase.PIP_STATUS -> PictureInPictureStatus(current)
                        RoomPhase.CONNECTING -> ConnectingContent(current, simulated, onCancel = leave)
                        RoomPhase.ENDING -> ProgressContent(stringResource(R.string.voxera_state_ending), simulated)
                        RoomPhase.ENDED -> (current as? SessionState.Ended)?.let {
                            EndedContent(it.reason, it.durationMs, history, simulated, onExit, onRejoin, onSaveReport)
                        }
                        RoomPhase.FAILED -> (current as? SessionState.Failed)?.let {
                            FailedContent(it.error, dropped = it.durationMs != null, simulated, onExit, onRejoin, onSaveReport)
                        }
                        RoomPhase.CALL -> when (current) {
                            is SessionState.Connected ->
                                CallContent(session, current.mode, current.connectedAtMs, null, participants, media, renderer, simulated, history, leave, onShareScreen, onEnterPictureInPicture, autoHideControls, reactionsEnabled, stage, chatEnabled, handRaiseEnabled)
                            is SessionState.Reconnecting ->
                                CallContent(session, current.mode, current.connectedAtMs, current, participants, media, renderer, simulated, history, leave, onShareScreen, onEnterPictureInPicture, autoHideControls, reactionsEnabled, stage, chatEnabled, handRaiseEnabled)
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}

/** 안내 줄은 위에서 펼쳐지며 나타나 아래 내용을 부드럽게 밀어낸다. */
private val BannerEnter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow), expandFrom = Alignment.Top) + fadeIn()
private val BannerExit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow), shrinkTowards = Alignment.Top) + fadeOut()

/** 영상 무대에서 고른 것: 크게 보는 사람(고정)과 보기 방식. */
internal class StageSelection(val pinnedId: MutableState<String?>, val view: MutableState<StageView>)

@Composable
private fun rememberStageSelection(): StageSelection {
    val pinned = rememberSaveable { mutableStateOf<String?>(null) }
    val view = rememberSaveable { mutableStateOf(StageView.GALLERY) }
    return remember(pinned, view) { StageSelection(pinned, view) }
}

/** 화면 전환 단위. 이 값이 바뀔 때만 화면 전체가 바뀌는 효과를 준다. */
private enum class RoomPhase { CONNECTING, CALL, ENDING, ENDED, FAILED, PIP_CALL, PIP_STATUS }

private fun SessionState.phase(pictureInPicture: Boolean): RoomPhase = when (this) {
    is SessionState.Connected, is SessionState.Reconnecting -> if (pictureInPicture) RoomPhase.PIP_CALL else RoomPhase.CALL
    else -> when {
        pictureInPicture -> RoomPhase.PIP_STATUS
        this is SessionState.Ending -> RoomPhase.ENDING
        this is SessionState.Ended -> RoomPhase.ENDED
        this is SessionState.Failed -> RoomPhase.FAILED
        else -> RoomPhase.CONNECTING
    }
}

/** 통화 시간. 화면에 보일 때만 1초마다 다시 그린다. */
@Composable
private fun rememberElapsed(clock: MonotonicClock, connectedAtMs: Long): String {
    val now by produceState(clock.nowMs(), connectedAtMs) {
        while (true) {
            value = clock.nowMs()
            delay(1_000 - (value - connectedAtMs).mod(1_000L))
        }
    }
    return formatDuration(now - connectedAtMs)
}

@Composable
private fun CallContent(
    session: VoxeraSession,
    mode: CallMode,
    connectedAtMs: Long,
    reconnecting: SessionState.Reconnecting?,
    participants: List<Participant>,
    media: LocalMediaState,
    renderer: VoxeraVideoRenderer?,
    simulated: Boolean,
    history: QualityHistory,
    onLeave: () -> Unit,
    onShareScreen: (() -> Unit)?,
    onEnterPictureInPicture: (() -> Unit)?,
    autoHideControls: Boolean,
    reactionsEnabled: Boolean,
    stage: StageSelection,
    chatEnabled: Boolean,
    handRaiseEnabled: Boolean,
) {
    // 영상 통화 중에는 화면이 꺼지지 않게 한다. 음성 통화는 시스템 시간 초과를 따른다(근접 센서는 호스트·android 모듈 몫).
    val view = LocalView.current
    DisposableEffect(view, mode) {
        val keep = mode == CallMode.VIDEO
        if (keep) view.keepScreenOn = true
        onDispose { if (keep) view.keepScreenOn = false }
    }
    var sheet by rememberSaveable { mutableStateOf(RoomSheet.NONE) }
    var overflowControls by remember { mutableStateOf(emptyList<DockControl>()) }
    var lastRouteSelectionMs by remember { mutableLongStateOf(0L) }
    val chat by session.chat.collectAsStateWithLifecycle()
    // 채팅 시트를 열어 둔 동안 본 마지막 메시지. 이 뒤에 온 남의 메시지 수가 배지 숫자다.
    var lastReadId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(sheet, chat.lastOrNull()?.id) { if (sheet == RoomSheet.CHAT) lastReadId = chat.lastOrNull()?.id }
    val unread = if (chatEnabled) unreadCount(chat, lastReadId) else 0
    val myHand = participants.firstOrNull { it.isLocal }?.isHandRaised == true
    // 더보기 목록은 버튼 동작을 담아 저장할 수 없다. 화면이 다시 만들어져 비었으면 시트를 닫는다.
    LaunchedEffect(sheet, overflowControls) { if (sheet == RoomSheet.MORE && overflowControls.isEmpty()) sheet = RoomSheet.NONE }
    var pinnedId by stage.pinnedId
    var stageView by stage.view
    // 크게 보던 사람이 나가면 고정을 푼다.
    LaunchedEffect(participants, pinnedId) { if (pinnedId != null && participants.none { it.id == pinnedId }) pinnedId = null }
    val elapsed = rememberElapsed(session.clock, connectedAtMs)
    val window = LocalWindowInfo.current.containerSize
    val landscape = window.width > window.height
    val scope = rememberCoroutineScope()
    val health = history.latest?.health()
    val mediaError = media.microphone.lastError ?: media.camera.lastError ?: media.screenShare.lastError

    // 조작 막대 자동 숨김. 영상 통화이고, 시트·재연결·오류·공유 경고가 없을 때만 숨긴다.
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    val accessibility = LocalAccessibilityManager.current
    // TalkBack처럼 화면을 더듬어 쓰는 동안에는 숨기지 않는다. 그 방식으로는 손가락 터치가 오지 않아 다시 꺼낼 수 없다.
    val touchExploration = rememberTouchExploration()
    // 오디오 알림 말풍선이 버튼을 가리키는 동안에는 조작 막대를 숨기지 않는다.
    var routeNoticeVisible by remember { mutableStateOf(false) }
    val canHide = autoHideControls && !touchExploration && mode == CallMode.VIDEO && sheet == RoomSheet.NONE && reconnecting == null && !routeNoticeVisible &&
        mediaError == null && !media.screenShare.actual && !media.audioInterrupted && participants.any { !it.isLocal }
    LaunchedEffect(canHide) { if (!canHide) chromeVisible = true }
    LaunchedEffect(canHide, chromeVisible, lastTouch) {
        if (!canHide || !chromeVisible) return@LaunchedEffect
        val timeout = accessibility?.calculateRecommendedTimeoutMillis(CHROME_TIMEOUT_MS, containsIcons = true, containsText = true, containsControls = true)
            ?: CHROME_TIMEOUT_MS
        if (timeout == Long.MAX_VALUE) return@LaunchedEffect
        delay(timeout)
        chromeVisible = false
    }
    val toggleChrome: () -> Unit = { chromeVisible = !chromeVisible }

    // 영상 View는 Compose 배너 위에 그려지므로, 상단 상태 줄·배너의 실제 높이만큼 무대를 내려 겹치지 않게 한다.
    val density = LocalDensity.current
    var overlayHeight by remember { mutableStateOf(0.dp) }
    // 오디오 알림 말풍선의 기준점: 이 화면 상자와 오디오(또는 더보기) 버튼의 화면 위치. 값으로 들고 있어야 움직일 때 다시 그려진다.
    var screenOrigin by remember { mutableStateOf(Offset.Zero) }
    var audioButtonBounds by remember { mutableStateOf<Rect?>(null) }
    val roomGlass = SurfaceStyle.Glass(VoxeraTheme.colors.glassStyle(GlassLevel.Medium))
    // TalkBack 읽기 순서: 상단 상태·안내 → 영상 무대 → 조작 막대. 그리는 순서(무대가 맨 아래)와 다르므로 따로 정한다.
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { screenOrigin = it.positionInRoot() }
            .semantics { isTraversalGroup = true }
            // 화면 어디를 만져도 숨김 시계를 다시 맞춘다. 이벤트는 소비하지 않는다.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastTouch = System.nanoTime()
                    }
                }
            },
    ) {
        // 무대(영상·음성 화면)를 배경으로 기록해, 그 위에 뜨는 상단 바·조작 막대·시트의 유리가 뒤 영상을 실제로 흐리게 비추게 한다.
        KineticaScaffold(
            modifier = Modifier.fillMaxSize(),
            surface = roomGlass,
            background = {
                // 각 영역을 하나의 묶음으로 만들어, 안쪽 타일·버튼끼리만 순서를 매기고 영역 사이 순서는 traversalIndex로 고정한다.
                val stageModifier = Modifier.fillMaxSize().semantics { isTraversalGroup = true; traversalIndex = 1f }
                if (mode == CallMode.VIDEO) {
                    VideoStage(
                        participants,
                        renderer,
                        pinnedId,
                        landscape,
                        onTogglePin = { id -> pinnedId = if (pinnedId == id) null else id },
                        topReserve = overlayHeight,
                        onTap = if (autoHideControls) toggleChrome else null,
                        chromeVisible = chromeVisible,
                        view = stageView,
                        modifier = stageModifier,
                    )
                } else {
                    VoiceStage(participants, elapsed, health?.let { stringResource(it.statusRes()) }, modifier = stageModifier)
                }
            },
            edgeFade = false,
            systemBarIcons = false,
        ) {
            Box(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    chromeVisible,
                    enter = fadeIn() + slideInVertically { -it / 2 },
                    exit = fadeOut() + slideOutVertically { -it / 2 },
                    modifier = Modifier.semantics { isTraversalGroup = true; traversalIndex = 0f },
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(horizontal = VoxeraSpacing.md, vertical = VoxeraSpacing.xs)
                            .onSizeChanged { overlayHeight = with(density) { it.height.toDp() } },
                        verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs),
                    ) {
                        TopBar(
                            modifier = Modifier.enterOnAppear(0),
                            count = participants.size,
                            elapsed = elapsed,
                            simulated = simulated,
                            health = health,
                            onPeople = { sheet = RoomSheet.PEOPLE },
                            onQuality = { sheet = RoomSheet.QUALITY },
                            onPictureInPicture = onEnterPictureInPicture,
                            // 3명 이상 영상 통화에서 갤러리 ↔ 발표자 보기를 바꾼다. 고정한 사람이 있으면 먼저 푼다.
                            stageView = stageView.takeIf { mode == CallMode.VIDEO && participants.size >= 3 },
                            onToggleView = {
                                pinnedId = null
                                stageView = if (stageView == StageView.GALLERY) StageView.SPEAKER else StageView.GALLERY
                            },
                        )
                        // 공유 중에는 화면의 모든 내용(알림 포함)이 보인다는 것을 계속 알리고, 바로 멈출 수 있게 한다(G-09).
                        AnimatedVisibility(media.screenShare.actual, enter = BannerEnter, exit = BannerExit) {
                            GlassSurface(level = GlassLevel.Medium, modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.sm)) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(VoxeraTheme.colors.danger))
                                    Text(
                                        stringResource(R.string.voxera_share_active),
                                        color = VoxeraTheme.colors.danger,
                                        style = VoxeraTheme.typography.label,
                                        modifier = Modifier.weight(1f),
                                    )
                                    VoxeraButton(
                                        stringResource(R.string.voxera_share_stop),
                                        onClick = { scope.launch { session.stopScreenShare() } },
                                        tone = VoxeraButtonTone.Danger,
                                    )
                                }
                            }
                        }
                        // 마이크·카메라 조작이 실패하면(권한 없음, 장치 점유) 이유와 할 일을 알린다. 다음 조작이 성공하면 사라진다.
                        AnimatedVisibility(mediaError != null, enter = BannerEnter, exit = BannerExit) {
                            if (mediaError != null) {
                                GlassSurface(level = GlassLevel.Medium, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                                    Text(stringResource(mediaError.messageRes()), color = VoxeraTheme.colors.warning, style = VoxeraTheme.typography.label)
                                }
                            }
                        }
                        // 휴대전화 통화 같은 다른 통화가 오디오를 가져가면 내 소리가 가지 않는다는 것을 알린다(E14).
                        AnimatedVisibility(media.audioInterrupted, enter = BannerEnter, exit = BannerExit) {
                            GlassSurface(level = GlassLevel.Medium, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                                Column {
                                    Text(stringResource(R.string.voxera_interrupted_title), color = VoxeraTheme.colors.warning, style = VoxeraTheme.typography.label)
                                    Text(stringResource(R.string.voxera_interrupted_body), color = VoxeraTheme.colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
                                }
                            }
                        }
                        AnimatedVisibility(reconnecting != null, enter = BannerEnter, exit = BannerExit) {
                            GlassSurface(level = GlassLevel.Medium, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive }) {
                                Column {
                                    Text(stringResource(R.string.voxera_state_reconnecting), color = VoxeraTheme.colors.warning, style = VoxeraTheme.typography.label)
                                    Text(stringResource(R.string.voxera_reconnecting_hint), color = VoxeraTheme.colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
                                }
                            }
                        }
                    }
                }
                // 입퇴장 알림은 조작 막대를 숨겨도 보인다. 상단 줄이 있으면 그 아래에 둔다.
                val noticeTop by animateDpAsState(if (chromeVisible) overlayHeight else 0.dp, label = "notice")
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(top = noticeTop + VoxeraSpacing.xs, start = VoxeraSpacing.md, end = VoxeraSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs),
                ) {
                    if (handRaiseEnabled) RaisedHandChip(myHand, onLower = { scope.launch { session.setHandRaised(false) } })
                    ParticipantNotices(participants)
                    if (chatEnabled) ChatNotices(chat, participants, suppressed = sheet == RoomSheet.CHAT, onOpen = { sheet = RoomSheet.CHAT })
                }

                AnimatedVisibility(
                    chromeVisible,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .semantics { isTraversalGroup = true; traversalIndex = 2f },
                ) {
                    CallDock(
                        mode = mode,
                        media = media,
                        onMic = { scope.launch { session.setMicrophoneEnabled(!media.microphone.desired) } },
                        onCamera = { scope.launch { session.setCameraEnabled(!media.camera.desired) } },
                        onFlip = { scope.launch { session.switchCamera() } },
                        onAudio = { sheet = RoomSheet.AUDIO },
                        onEffects = { sheet = RoomSheet.EFFECTS },
                        onReact = if (reactionsEnabled) ({ sheet = RoomSheet.REACTIONS }) else null,
                        onChat = if (chatEnabled) ({ sheet = RoomSheet.CHAT }) else null,
                        chatUnread = unread,
                        onShare = onShareScreen?.let { share ->
                            { if (media.screenShare.actual) scope.launch { session.stopScreenShare() } else share() }
                        },
                        onEnd = onLeave,
                        onMore = { overflow ->
                            overflowControls = overflow
                            sheet = RoomSheet.MORE
                        },
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(bottom = VoxeraSpacing.md, start = VoxeraSpacing.md, end = VoxeraSpacing.md)
                            .enterOnAppear(3, scale = true),
                        onAudioAnchor = { audioButtonBounds = if (it.isAttached) it.boundsInRoot() else null },
                    )
                }

                // 이어폰이 연결·해제되면 오디오 버튼 위 말풍선으로 어디로 소리가 가는지 알리고, 되돌리거나 옮길 수 있게 한다.
                val audioAnchor = audioButtonBounds?.takeIf { chromeVisible }?.translate(-screenOrigin)
                AudioRouteNotices(
                    media,
                    lastRouteSelectionMs,
                    onSelect = { id ->
                        lastRouteSelectionMs = System.nanoTime() / 1_000_000
                        scope.launch { session.selectAudioRoute(id) }
                    },
                    anchor = audioAnchor,
                    onVisibleChange = { routeNoticeVisible = it },
                    modifier = Modifier.semantics { isTraversalGroup = true; traversalIndex = 0.5f },
                )

                if (reactionsEnabled) {
                    // 반응은 조작 막대를 숨겨도 보인다. 막대가 있으면 그 위에서 떠오른다.
                    ReactionOverlay(
                        session,
                        participants,
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                        bottomInset = if (chromeVisible) BottomReserve else VoxeraSpacing.xl,
                    )
                }

                RoomSheetHost(
                    visible = sheet != RoomSheet.NONE,
                    onDismiss = { sheet = RoomSheet.NONE },
                ) {
                    when (sheet) {
                        RoomSheet.AUDIO -> AudioRouteSheet(media, onSelect = { id ->
                            lastRouteSelectionMs = System.nanoTime() / 1_000_000
                            scope.launch { session.selectAudioRoute(id) }
                            sheet = RoomSheet.NONE
                        })
                        RoomSheet.PEOPLE -> {
                            val togglePin: (String) -> Unit = { id ->
                                pinnedId = if (pinnedId == id) null else id
                                sheet = RoomSheet.NONE
                            }
                            PeopleSheet(participants, pinnedId, onTogglePin = togglePin.takeIf { mode == CallMode.VIDEO })
                        }
                        RoomSheet.QUALITY -> QualitySheet(history)
                        RoomSheet.EFFECTS -> EffectsSheet(media, onSelect = { effect -> scope.launch { session.setVideoEffect(effect) } })
                        RoomSheet.REACTIONS -> ReactionPicker(
                            onPick = { reaction ->
                                scope.launch { session.sendReaction(reaction) }
                                sheet = RoomSheet.NONE
                            },
                            handRaised = myHand.takeIf { handRaiseEnabled },
                            onToggleHand = {
                                scope.launch { session.setHandRaised(!myHand) }
                                sheet = RoomSheet.NONE
                            },
                        )
                        RoomSheet.CHAT -> ChatSheet(chat, participants, onSend = { text -> scope.launch { session.sendChatMessage(text) } })
                        RoomSheet.MORE -> MoreControlsSheet(overflowControls, onDone = { if (sheet == RoomSheet.MORE) sheet = RoomSheet.NONE })
                        RoomSheet.NONE -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    modifier: Modifier,
    count: Int,
    elapsed: String,
    simulated: Boolean,
    health: CallHealth?,
    onPeople: () -> Unit,
    onQuality: () -> Unit,
    onPictureInPicture: (() -> Unit)?,
    stageView: StageView?,
    onToggleView: () -> Unit,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
        // 글꼴이 커지면 상태 알약이 줄어들고 말줄임되어, 오른쪽 버튼이 화면 밖으로 밀리지 않는다.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
            GlassSurface(
                level = GlassLevel.Medium,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = VoxeraSpacing.sm, vertical = VoxeraSpacing.xs),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
                    if (health != null) {
                        val label = stringResource(health.labelRes())
                        Box(Modifier.size(8.dp).clip(CircleShape).background(health.color()).semantics { contentDescription = label })
                    }
                    Text(elapsed, color = VoxeraTheme.colors.foreground, style = VoxeraTheme.typography.label, maxLines = 1)
                    // 좁은 상단 줄에서도 잘리지 않게 숫자만 보이고, 전체 문구는 읽어 준다.
                    val countLabel = pluralStringResource(R.plurals.voxera_participant_count, count, count)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics { contentDescription = countLabel }) {
                        Icon(VoxeraIcons.Person, contentDescription = null, tint = VoxeraTheme.colors.foregroundSecondary, modifier = Modifier.size(14.dp))
                        Text("$count", color = VoxeraTheme.colors.foregroundSecondary, style = VoxeraTheme.typography.caption, maxLines = 1)
                    }
                }
            }
            if (simulated) DemoChip()
        }
        if (stageView != null) {
            val toSpeaker = stageView == StageView.GALLERY
            RoundIconButton(
                if (toSpeaker) VoxeraIcons.Person else VoxeraIcons.GridView,
                stringResource(if (toSpeaker) R.string.voxera_view_speaker else R.string.voxera_view_gallery),
                onToggleView,
            )
        }
        if (onPictureInPicture != null) RoundIconButton(VoxeraIcons.PictureInPicture, stringResource(R.string.voxera_control_pip), onPictureInPicture)
        RoundIconButton(VoxeraIcons.Group, stringResource(R.string.voxera_control_people), onPeople)
        RoundIconButton(VoxeraIcons.Insights, stringResource(R.string.voxera_control_stats), onQuality)
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    // 터치 영역은 48dp 전체, 보이는 유리 원은 그 안의 40dp.
    val interaction = remember { MutableInteractionSource() }
    IconButton(onClick = onClick, interactionSource = interaction, modifier = Modifier.size(48.dp).semantics { contentDescription = label }) {
        GlassSurface(
            shape = CircleShape,
            level = GlassLevel.Medium,
            contentPadding = PaddingValues(VoxeraSpacing.xs),
            modifier = Modifier.pressScale(interaction, pressed = 0.88f).size(40.dp),
        ) {
            Icon(icon, contentDescription = null, tint = VoxeraTheme.colors.foreground, modifier = Modifier.size(20.dp).align(Alignment.Center))
        }
    }
}

/** 통화 화면 바텀 시트의 공통 틀. 바깥을 누르거나, 뒤로 가기, 손잡이 쪽을 아래로 끌어 닫는다. */
@Composable
private fun RoomSheetHost(visible: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val dismissInteraction = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    LaunchedEffect(visible) { if (visible) drag.snapTo(0f) }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(VoxeraTheme.colors.scrim)
                .clickable(interactionSource = dismissInteraction, indication = null, onClick = onDismiss),
        )
    }
    BackHandler(enabled = visible, onBack = onDismiss)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        // 시트는 아래에서 스프링처럼 올라와 살짝 넘쳤다 자리 잡는다.
        AnimatedVisibility(
            visible,
            enter = slideInVertically(spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(tween(120)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
        ) {
            GlassSurface(
                level = GlassLevel.High,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .offset { IntOffset(0, drag.value.roundToInt()) }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (drag.value > 120.dp.toPx()) onDismiss() else scope.launch { drag.animateTo(0f) }
                            },
                            onDragCancel = { scope.launch { drag.animateTo(0f) } },
                        ) { change, amount ->
                            change.consume()
                            scope.launch { drag.snapTo((drag.value + amount).coerceAtLeast(0f)) }
                        }
                    },
            ) {
                Column(
                    Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                        .padding(start = VoxeraSpacing.lg, end = VoxeraSpacing.lg, bottom = VoxeraSpacing.lg, top = VoxeraSpacing.xs),
                ) {
                    SheetGrabber(Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(VoxeraSpacing.sm))
                    content()
                }
            }
        }
    }
}
