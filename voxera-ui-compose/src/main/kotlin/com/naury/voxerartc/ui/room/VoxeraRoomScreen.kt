package com.naury.voxerartc.ui.room

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
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
import com.naury.voxerartc.ui.theme.VoxeraThemeConfig
import com.naury.voxerartc.ui.video.LocalVoxeraVideoRenderer
import com.naury.voxerartc.ui.video.SimulatedVideoRenderer
import com.naury.voxerartc.ui.video.VoxeraVideoRenderer
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 통화 화면에서 연 시트. */
private enum class RoomSheet { NONE, AUDIO, PEOPLE, QUALITY, MORE, EFFECTS }

/** 영상 통화에서 아무 조작이 없을 때 조작 막대를 숨기기까지 기다리는 시간. 접근성 서비스가 켜져 있으면 늘어나거나 숨기지 않는다. */
private const val CHROME_TIMEOUT_MS = 4_000L

/**
 * 룸 통화 화면. [session]의 상태만 관찰하며 세션을 만들거나 닫지 않는다. 입장(`join`)은 호스트가 사용자 동작에 맞춰 부른다.
 *
 * 화면이 사라져도 세션은 유지되므로, 통화를 끝내려면 호스트가 `leave`/`close`를 부르거나 사용자가 종료 버튼을 누른다.
 * [VoxeraRoomScreen]은 [VoxeraTheme]을 직접 적용하므로 호스트 `MaterialTheme` 안에 두어도 색이 섞이지 않는다.
 *
 * @param onExit 종료·실패 화면에서 닫기를 누름. 호스트 내비게이션으로 돌아간다.
 * @param onRejoin `null`이 아니면 종료·실패 화면에 다시 입장 버튼을 보여 준다.
 * @param onEnd 종료·취소 버튼 동작. `null`이면 `session.leave()`. Call Mode에서는 `CallCoordinator.hangup`을 넘긴다.
 * @param onShareScreen `null`이 아니면 영상 통화에 화면 공유 버튼을 보여 준다. 호스트가 시스템 화면 공유 승인 대화상자를 띄우고
 *   승인 결과로 `session.startScreenShare`를 부른다(예: voxera-livekit-compose의 `rememberScreenShareLauncher`).
 * @param onSaveReport `null`이 아니면 종료·실패 화면에 진단 리포트 저장 버튼을 보여 준다. 저장 위치와 공유는 호스트가 정한다.
 * @param pictureInPicture 호스트 Activity가 PiP 창에 있으면 `true`. 조작·시트·알림을 빼고 말하는 사람 하나만 작게 그린다.
 * @param onEnterPictureInPicture `null`이 아니면 상단에 PiP 버튼을 둔다. 호스트가 `enterPictureInPictureMode`를 부른다.
 * @param autoHideControls 영상 통화에서 4초 동안 조작이 없으면 상단 줄·조작 막대를 숨긴다. 화면을 누르면 다시 나온다.
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

        Box(modifier.fillMaxSize().voxeraBackdrop(emphasis = speaking)) {
            val current = state
            when {
                pictureInPicture && (current is SessionState.Connected || current is SessionState.Reconnecting) ->
                    PictureInPictureContent(session, participants, media, renderer, (current as? SessionState.Connected)?.mode ?: (current as SessionState.Reconnecting).mode)
                pictureInPicture -> PictureInPictureStatus(current)
                current is SessionState.Idle || current is SessionState.Preparing || current is SessionState.Connecting ->
                    ConnectingContent(current, simulated, onCancel = leave)
                current is SessionState.Ending -> ProgressContent(stringResource(R.string.voxera_state_ending), simulated)
                current is SessionState.Ended -> EndedContent(current.reason, current.durationMs, history, simulated, onExit, onRejoin, onSaveReport)
                current is SessionState.Failed -> FailedContent(current.error, dropped = current.durationMs != null, simulated, onExit, onRejoin, onSaveReport)
                current is SessionState.Connected ->
                    CallContent(session, current.mode, current.connectedAtMs, null, participants, media, renderer, simulated, history, leave, onShareScreen, onEnterPictureInPicture, autoHideControls)
                current is SessionState.Reconnecting ->
                    CallContent(session, current.mode, current.connectedAtMs, current, participants, media, renderer, simulated, history, leave, onShareScreen, onEnterPictureInPicture, autoHideControls)
            }
        }
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
) {
    var sheet by rememberSaveable { mutableStateOf(RoomSheet.NONE) }
    var overflowControls by remember { mutableStateOf(emptyList<DockControl>()) }
    var pinnedId by rememberSaveable { mutableStateOf<String?>(null) }
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
    val canHide = autoHideControls && mode == CallMode.VIDEO && sheet == RoomSheet.NONE && reconnecting == null &&
        mediaError == null && !media.screenShare.actual && participants.any { !it.isLocal }
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
    // TalkBack 읽기 순서: 상단 상태·안내 → 영상 무대 → 조작 막대. 그리는 순서(무대가 맨 아래)와 다르므로 따로 정한다.
    Box(
        Modifier
            .fillMaxSize()
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
        val stageModifier = Modifier.fillMaxSize().semantics { traversalIndex = 1f }
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
                modifier = stageModifier,
            )
        } else {
            VoiceStage(participants, elapsed, health?.let { stringResource(it.statusRes()) }, modifier = stageModifier)
        }

        AnimatedVisibility(
            chromeVisible,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier.semantics { traversalIndex = 0f },
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
                    participants.size,
                    elapsed,
                    simulated,
                    health,
                    onPeople = { sheet = RoomSheet.PEOPLE },
                    onQuality = { sheet = RoomSheet.QUALITY },
                    onPictureInPicture = onEnterPictureInPicture,
                )
                // 공유 중에는 화면의 모든 내용(알림 포함)이 보인다는 것을 계속 알리고, 바로 멈출 수 있게 한다(G-09).
                AnimatedVisibility(media.screenShare.actual, enter = fadeIn(), exit = fadeOut()) {
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
                AnimatedVisibility(mediaError != null, enter = fadeIn(), exit = fadeOut()) {
                    if (mediaError != null) {
                        GlassSurface(level = GlassLevel.Medium, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                            Text(stringResource(mediaError.messageRes()), color = VoxeraTheme.colors.warning, style = VoxeraTheme.typography.label)
                        }
                    }
                }
                AnimatedVisibility(reconnecting != null, enter = fadeIn(), exit = fadeOut()) {
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
        val noticeTop = if (chromeVisible) overlayHeight else 0.dp
        ParticipantNotices(
            participants,
            Modifier
                .align(Alignment.TopCenter)
                .then(if (chromeVisible) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing))
                .padding(top = noticeTop + VoxeraSpacing.xs, start = VoxeraSpacing.md, end = VoxeraSpacing.md),
        )

        AnimatedVisibility(
            chromeVisible,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .semantics { traversalIndex = 2f },
        ) {
            CallDock(
                mode = mode,
                media = media,
                onMic = { scope.launch { session.setMicrophoneEnabled(!media.microphone.desired) } },
                onCamera = { scope.launch { session.setCameraEnabled(!media.camera.desired) } },
                onFlip = { scope.launch { session.switchCamera() } },
                onAudio = { sheet = RoomSheet.AUDIO },
                onEffects = { sheet = RoomSheet.EFFECTS },
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
                    .padding(bottom = VoxeraSpacing.md, start = VoxeraSpacing.md, end = VoxeraSpacing.md),
            )
        }

        RoomSheetHost(
            visible = sheet != RoomSheet.NONE,
            onDismiss = { sheet = RoomSheet.NONE },
        ) {
            when (sheet) {
                RoomSheet.AUDIO -> AudioRouteSheet(media, onSelect = { id ->
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
                RoomSheet.MORE -> MoreControlsSheet(overflowControls, onDone = { if (sheet == RoomSheet.MORE) sheet = RoomSheet.NONE })
                RoomSheet.NONE -> Unit
            }
        }
    }
}

@Composable
private fun TopBar(
    count: Int,
    elapsed: String,
    simulated: Boolean,
    health: CallHealth?,
    onPeople: () -> Unit,
    onQuality: () -> Unit,
    onPictureInPicture: (() -> Unit)?,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
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
                    Text(
                        pluralStringResource(R.plurals.voxera_participant_count, count, count),
                        color = VoxeraTheme.colors.foregroundSecondary,
                        style = VoxeraTheme.typography.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (simulated) DemoChip()
        }
        if (onPictureInPicture != null) RoundIconButton(VoxeraIcons.PictureInPicture, stringResource(R.string.voxera_control_pip), onPictureInPicture)
        RoundIconButton(VoxeraIcons.Group, stringResource(R.string.voxera_control_people), onPeople)
        RoundIconButton(VoxeraIcons.Insights, stringResource(R.string.voxera_control_stats), onQuality)
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    // 터치 영역은 48dp 전체, 보이는 유리 원은 그 안의 40dp.
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).semantics { contentDescription = label }) {
        GlassSurface(
            shape = CircleShape,
            level = GlassLevel.Medium,
            contentPadding = PaddingValues(VoxeraSpacing.xs),
            modifier = Modifier.size(40.dp),
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
        AnimatedVisibility(visible, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
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
