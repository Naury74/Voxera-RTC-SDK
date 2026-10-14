package com.naury.voxerartc.ui.room

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.EndReason
import com.naury.voxerartc.api.LocalMediaState
import com.naury.voxerartc.api.MonotonicClock
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.api.RtcEngineKind
import com.naury.voxerartc.api.SessionState
import com.naury.voxerartc.api.VoxeraError
import com.naury.voxerartc.core.VoxeraSession
import com.naury.voxerartc.ui.R
import com.naury.voxerartc.ui.component.CallControlButton
import com.naury.voxerartc.ui.component.StatusChip
import com.naury.voxerartc.ui.component.VoxeraAvatar
import com.naury.voxerartc.ui.component.VoxeraButton
import com.naury.voxerartc.ui.component.VoxeraButtonTone
import com.naury.voxerartc.ui.component.VoxeraIcons
import com.naury.voxerartc.ui.glass.GlassDock
import com.naury.voxerartc.ui.glass.GlassLevel
import com.naury.voxerartc.ui.glass.GlassSurface
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import com.naury.voxerartc.ui.theme.VoxeraThemeConfig
import com.naury.voxerartc.ui.video.LocalVoxeraVideoRenderer
import com.naury.voxerartc.ui.video.SimulatedVideoRenderer
import com.naury.voxerartc.ui.video.VoxeraVideoRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 통화 화면에서 연 시트. */
private enum class RoomSheet { NONE, AUDIO, PEOPLE, QUALITY, MORE, EFFECTS }

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
) {
    VoxeraTheme(theme) {
        val state by session.state.collectAsStateWithLifecycle()
        val participants by session.participants.collectAsStateWithLifecycle()
        val media by session.localMedia.collectAsStateWithLifecycle()
        val simulated = session.engineKind == RtcEngineKind.SIMULATED
        val renderer = if (simulated) SimulatedVideoRenderer else LocalVoxeraVideoRenderer.current
        val scope = rememberCoroutineScope()
        val leave: () -> Unit = onEnd ?: { scope.launch { session.leave() } }

        Box(
            modifier
                .fillMaxSize()
                .background(VoxeraTheme.colors.canvas),
        ) {
            when (val current = state) {
                SessionState.Idle, is SessionState.Preparing, is SessionState.Connecting ->
                    ConnectingContent(current, simulated, onCancel = leave)
                is SessionState.Ending -> ProgressContent(stringResource(R.string.voxera_state_ending), simulated)
                is SessionState.Ended -> EndedContent(current.reason, current.durationMs, simulated, onExit, onRejoin, onSaveReport)
                is SessionState.Failed -> FailedContent(current.error, dropped = current.durationMs != null, simulated, onExit, onRejoin, onSaveReport)
                is SessionState.Connected ->
                    CallContent(session, current.mode, current.connectedAtMs, null, participants, media, renderer, simulated, leave, onShareScreen)
                is SessionState.Reconnecting ->
                    CallContent(session, current.mode, current.connectedAtMs, current, participants, media, renderer, simulated, leave, onShareScreen)
            }
        }
    }
}

@Composable
private fun ConnectingContent(state: SessionState, simulated: Boolean, onCancel: () -> Unit) {
    val label = stringResource(
        if (state is SessionState.Connecting) R.string.voxera_state_connecting else R.string.voxera_state_preparing,
    )
    val room = when (state) {
        is SessionState.Preparing -> state.roomId
        is SessionState.Connecting -> state.roomId
        else -> ""
    }
    CenteredColumn(simulated) {
        VoxeraAvatar(id = room.ifEmpty { "room" }, name = room.ifEmpty { "?" }, size = 128.dp, speaking = true, audioLevel = 0.3f)
        Spacer(Modifier.height(VoxeraSpacing.xl))
        Text(room, color = VoxeraTheme.colors.foreground, style = VoxeraTheme.typography.headline, textAlign = TextAlign.Center)
        Spacer(Modifier.height(VoxeraSpacing.xs))
        Text(
            label,
            color = VoxeraTheme.colors.foregroundSecondary,
            style = VoxeraTheme.typography.body,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(VoxeraSpacing.xxl))
        VoxeraButton(stringResource(R.string.voxera_action_cancel), onClick = onCancel, tone = VoxeraButtonTone.Secondary, modifier = Modifier.widthIn(min = 160.dp))
    }
}

@Composable
private fun ProgressContent(label: String, simulated: Boolean) {
    CenteredColumn(simulated) {
        Text(label, color = VoxeraTheme.colors.foregroundSecondary, style = VoxeraTheme.typography.body)
    }
}

@Composable
private fun EndedContent(
    reason: EndReason,
    durationMs: Long?,
    simulated: Boolean,
    onExit: () -> Unit,
    onRejoin: (() -> Unit)?,
    onSaveReport: (() -> Unit)?,
) {
    CenteredColumn(simulated) {
        Text(
            stringResource(R.string.voxera_end_title),
            color = VoxeraTheme.colors.foreground,
            style = VoxeraTheme.typography.headline,
            modifier = Modifier.semantics { heading() },
        )
        if (durationMs != null) {
            Spacer(Modifier.height(VoxeraSpacing.xs))
            Text(stringResource(R.string.voxera_end_duration, formatDuration(durationMs)), color = VoxeraTheme.colors.primary, style = VoxeraTheme.typography.title)
        }
        Spacer(Modifier.height(VoxeraSpacing.xs))
        Text(stringResource(reason.messageRes()), color = VoxeraTheme.colors.foregroundSecondary, style = VoxeraTheme.typography.body, textAlign = TextAlign.Center)
        Spacer(Modifier.height(VoxeraSpacing.xxl))
        ExitActions(onExit, onRejoin, rejoinLabel = stringResource(R.string.voxera_action_rejoin), onSaveReport)
    }
}

@Composable
private fun FailedContent(
    error: VoxeraError,
    dropped: Boolean,
    simulated: Boolean,
    onExit: () -> Unit,
    onRejoin: (() -> Unit)?,
    onSaveReport: (() -> Unit)?,
) {
    CenteredColumn(simulated) {
        Text(
            stringResource(if (dropped) R.string.voxera_dropped_title else R.string.voxera_failed_title),
            color = VoxeraTheme.colors.foreground,
            style = VoxeraTheme.typography.headline,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(VoxeraSpacing.sm))
        GlassSurface(level = GlassLevel.Low, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xxs)) {
                Text(stringResource(error.messageRes()), color = VoxeraTheme.colors.foreground, style = VoxeraTheme.typography.body)
                val code = if (error is VoxeraError.InternalFailure) "${error.code} · ${error.technicalCode}" else error.code
                Text(stringResource(R.string.voxera_error_code, code), color = VoxeraTheme.colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
            }
        }
        Spacer(Modifier.height(VoxeraSpacing.xxl))
        ExitActions(onExit, onRejoin.takeIf { error.isRetryable }, rejoinLabel = stringResource(R.string.voxera_action_retry), onSaveReport)
    }
}

@Composable
private fun ExitActions(onExit: () -> Unit, onRejoin: (() -> Unit)?, rejoinLabel: String, onSaveReport: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.sm)) {
        if (onRejoin != null) VoxeraButton(rejoinLabel, onClick = onRejoin, modifier = Modifier.fillMaxWidth())
        if (onSaveReport != null) {
            VoxeraButton(stringResource(R.string.voxera_action_save_report), onClick = onSaveReport, tone = VoxeraButtonTone.Secondary, modifier = Modifier.fillMaxWidth())
        }
        VoxeraButton(stringResource(R.string.voxera_action_close), onClick = onExit, tone = VoxeraButtonTone.Secondary, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CenteredColumn(simulated: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (simulated) DemoChip(Modifier.align(Alignment.TopCenter).padding(top = VoxeraSpacing.md))
        // 큰 글꼴·작은 화면에서도 아래 버튼에 닿을 수 있게 세로로 스크롤한다.
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = VoxeraSpacing.xl, vertical = VoxeraSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

@Composable
private fun DemoChip(modifier: Modifier = Modifier) {
    StatusChip(stringResource(R.string.voxera_demo_badge), VoxeraTheme.colors.warning, modifier)
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
    onLeave: () -> Unit,
    onShareScreen: (() -> Unit)?,
) {
    var sheet by rememberSaveable { mutableStateOf(RoomSheet.NONE) }
    var overflowControls by remember { mutableStateOf(emptyList<DockControl>()) }
    var pinnedId by rememberSaveable { mutableStateOf<String?>(null) }
    val elapsed = rememberElapsed(session.clock, connectedAtMs)
    val window = LocalWindowInfo.current.containerSize
    val landscape = window.width > window.height
    val scope = rememberCoroutineScope()

    // 영상 View는 Compose 배너 위에 그려지므로, 상단 상태 줄·배너의 실제 높이만큼 무대를 내려 겹치지 않게 한다.
    val density = LocalDensity.current
    var overlayHeight by remember { mutableStateOf(0.dp) }
    // TalkBack 읽기 순서: 상단 상태·안내 → 영상 무대 → 조작 막대. 그리는 순서(무대가 맨 아래)와 다르므로 따로 정한다.
    Box(Modifier.fillMaxSize().semantics { isTraversalGroup = true }) {
        val stageModifier = Modifier.fillMaxSize().semantics { traversalIndex = 1f }
        if (mode == CallMode.VIDEO) {
            VideoStage(
                participants,
                renderer,
                pinnedId,
                landscape,
                onTogglePin = { id -> pinnedId = if (pinnedId == id) null else id },
                topReserve = overlayHeight,
                modifier = stageModifier,
            )
        } else {
            VoiceStage(participants, elapsed, modifier = stageModifier)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = VoxeraSpacing.md, vertical = VoxeraSpacing.xs)
                .onSizeChanged { overlayHeight = with(density) { it.height.toDp() } }
                .semantics { traversalIndex = 0f },
            verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs),
        ) {
            TopBar(participants.size, elapsed, simulated, onPeople = { sheet = RoomSheet.PEOPLE }, onQuality = { sheet = RoomSheet.QUALITY })
            // 공유 중에는 화면의 모든 내용(알림 포함)이 보인다는 것을 계속 알리고, 바로 멈출 수 있게 한다(G-09).
            AnimatedVisibility(media.screenShare.actual, enter = fadeIn(), exit = fadeOut()) {
                GlassSurface(level = GlassLevel.Medium, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.sm)) {
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
            val mediaError = media.microphone.lastError ?: media.camera.lastError ?: media.screenShare.lastError
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
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = VoxeraSpacing.md, start = VoxeraSpacing.md, end = VoxeraSpacing.md)
                .semantics { traversalIndex = 2f },
        )

        RoomSheetHost(
            visible = sheet != RoomSheet.NONE,
            onDismiss = { sheet = RoomSheet.NONE },
        ) {
            when (sheet) {
                RoomSheet.AUDIO -> AudioRouteSheet(media, onSelect = { id ->
                    scope.launch { session.selectAudioRoute(id) }
                    sheet = RoomSheet.NONE
                })
                RoomSheet.PEOPLE -> PeopleSheet(participants)
                RoomSheet.QUALITY -> QualitySheet(session)
                RoomSheet.EFFECTS -> EffectsSheet(media, onSelect = { effect -> scope.launch { session.setVideoEffect(effect) } })
                RoomSheet.MORE -> MoreControlsSheet(overflowControls, onDone = { if (sheet == RoomSheet.MORE) sheet = RoomSheet.NONE })
                RoomSheet.NONE -> Unit
            }
        }
    }
}

@Composable
private fun TopBar(count: Int, elapsed: String, simulated: Boolean, onPeople: () -> Unit, onQuality: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
        // 글꼴이 커지면 상태 알약이 줄어들고 말줄임되어, 오른쪽 버튼이 화면 밖으로 밀리지 않는다.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
            GlassSurface(
                level = GlassLevel.Medium,
                contentPadding = PaddingValues(horizontal = VoxeraSpacing.sm, vertical = VoxeraSpacing.xxs),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
                    Text(elapsed, color = VoxeraTheme.colors.primary, style = VoxeraTheme.typography.label, maxLines = 1)
                    Text(
                        pluralStringResource(R.plurals.voxera_participant_count, count, count),
                        color = VoxeraTheme.colors.foreground,
                        style = VoxeraTheme.typography.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (simulated) DemoChip()
        }
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

/** 조작 막대의 버튼 하나. [essential]이면 좁은 화면에서도 막대에 남고, 아니면 "더보기"로 옮겨질 수 있다. */
private data class DockControl(
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
private fun CallDock(
    mode: CallMode,
    media: LocalMediaState,
    onMic: () -> Unit,
    onCamera: () -> Unit,
    onFlip: () -> Unit,
    onAudio: () -> Unit,
    onEffects: () -> Unit,
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
        if (media.audioRoutes.isNotEmpty()) {
            add(DockControl(media.selectedRouteIcon(), stringResource(R.string.voxera_control_audio), onAudio, essential = false))
        }
        add(DockControl(VoxeraIcons.CallEnd, stringResource(R.string.voxera_control_end), onEnd, essential = true, danger = true))
    }
    val moreLabel = stringResource(R.string.voxera_control_more)
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        val (shown, _, circle) = layoutDock(controls, { overflow -> DockControl(Icons.Filled.MoreVert, moreLabel, { onMore(overflow) }, essential = true) }, maxWidth)
        GlassDock {
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
private fun MoreControlsSheet(controls: List<DockControl>, onDone: () -> Unit) {
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

@Composable
private fun VoiceStage(participants: List<Participant>, elapsed: String, modifier: Modifier = Modifier) {
    val remotes = participants.filterNot { it.isLocal }
    Box(modifier.windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
        if (remotes.size <= 1) {
            val focus = remotes.firstOrNull()
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 96.dp)) {
                if (focus != null) {
                    VoxeraAvatar(focus.id, focus.displayName, size = 152.dp, speaking = focus.isSpeaking, audioLevel = focus.audioLevel)
                    Spacer(Modifier.height(VoxeraSpacing.lg))
                    Text(focus.displayName, color = VoxeraTheme.colors.foreground, style = VoxeraTheme.typography.headline, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(VoxeraSpacing.xxs))
                    Text(elapsed, color = VoxeraTheme.colors.primary, style = VoxeraTheme.typography.title)
                } else {
                    Text(stringResource(R.string.voxera_waiting_others), color = VoxeraTheme.colors.foregroundSecondary, style = VoxeraTheme.typography.body)
                }
            }
        } else {
            VoiceGroupGrid(participants, Modifier.padding(top = 72.dp, bottom = 120.dp))
        }
    }
}

/** 통화 화면 바텀 시트의 공통 틀. 바깥을 누르거나 뒤로 가기로 닫는다. */
@Composable
private fun RoomSheetHost(visible: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val dismissInteraction = remember { MutableInteractionSource() }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(VoxeraTheme.colors.scrim)
                .then(Modifier.clickableWithoutIndication(dismissInteraction, onDismiss)),
        )
    }
    BackHandler(enabled = visible, onBack = onDismiss)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            GlassSurface(
                level = GlassLevel.High,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
            ) {
                Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)).padding(VoxeraSpacing.lg)) { content() }
            }
        }
    }
}

private fun Modifier.clickableWithoutIndication(
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier = this.then(Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick))
