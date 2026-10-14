package com.naury.voxerartc.ui.room

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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
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
private enum class RoomSheet { NONE, AUDIO, PEOPLE, QUALITY }

/**
 * 룸 통화 화면. [session]의 상태만 관찰하며 세션을 만들거나 닫지 않는다. 입장(`join`)은 호스트가 사용자 동작에 맞춰 부른다.
 *
 * 화면이 사라져도 세션은 유지되므로, 통화를 끝내려면 호스트가 `leave`/`close`를 부르거나 사용자가 종료 버튼을 누른다.
 * [VoxeraRoomScreen]은 [VoxeraTheme]을 직접 적용하므로 호스트 `MaterialTheme` 안에 두어도 색이 섞이지 않는다.
 *
 * @param onExit 종료·실패 화면에서 닫기를 누름. 호스트 내비게이션으로 돌아간다.
 * @param onRejoin `null`이 아니면 종료·실패 화면에 다시 입장 버튼을 보여 준다.
 * @param onEnd 종료·취소 버튼 동작. `null`이면 `session.leave()`. Call Mode에서는 `CallCoordinator.hangup`을 넘긴다.
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
                is SessionState.Connected -> CallContent(session, current.mode, current.connectedAtMs, null, participants, media, renderer, simulated, leave)
                is SessionState.Reconnecting -> CallContent(session, current.mode, current.connectedAtMs, current, participants, media, renderer, simulated, leave)
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
        Column(
            modifier = Modifier.align(Alignment.Center).widthIn(max = 420.dp).padding(horizontal = VoxeraSpacing.xl),
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
) {
    var sheet by rememberSaveable { mutableStateOf(RoomSheet.NONE) }
    var pinnedId by rememberSaveable { mutableStateOf<String?>(null) }
    val elapsed = rememberElapsed(session.clock, connectedAtMs)
    val window = LocalWindowInfo.current.containerSize
    val landscape = window.width > window.height
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        val stageModifier = Modifier.fillMaxSize()
        if (mode == CallMode.VIDEO) {
            VideoStage(participants, renderer, pinnedId, landscape, onTogglePin = { id -> pinnedId = if (pinnedId == id) null else id }, modifier = stageModifier)
        } else {
            VoiceStage(participants, elapsed, modifier = stageModifier)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = VoxeraSpacing.md, vertical = VoxeraSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs),
        ) {
            TopBar(participants.size, elapsed, simulated, onPeople = { sheet = RoomSheet.PEOPLE }, onQuality = { sheet = RoomSheet.QUALITY })
            // 마이크·카메라 조작이 실패하면(권한 없음, 장치 점유) 이유와 할 일을 알린다. 다음 조작이 성공하면 사라진다.
            val mediaError = media.microphone.lastError ?: media.camera.lastError
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
            onEnd = onLeave,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = VoxeraSpacing.md, start = VoxeraSpacing.md, end = VoxeraSpacing.md),
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
                RoomSheet.NONE -> Unit
            }
        }
    }
}

@Composable
private fun TopBar(count: Int, elapsed: String, simulated: Boolean, onPeople: () -> Unit, onQuality: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
        GlassSurface(level = GlassLevel.Medium, contentPadding = PaddingValues(horizontal = VoxeraSpacing.sm, vertical = VoxeraSpacing.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
                Text(elapsed, color = VoxeraTheme.colors.primary, style = VoxeraTheme.typography.label)
                Text(pluralStringResource(R.plurals.voxera_participant_count, count, count), color = VoxeraTheme.colors.foreground, style = VoxeraTheme.typography.caption)
            }
        }
        if (simulated) DemoChip()
        Spacer(Modifier.weight(1f))
        RoundIconButton(VoxeraIcons.Group, stringResource(R.string.voxera_control_people), onPeople)
        RoundIconButton(VoxeraIcons.Insights, stringResource(R.string.voxera_control_stats), onQuality)
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).padding(2.dp),
    ) {
        GlassSurface(
            shape = CircleShape,
            level = GlassLevel.Medium,
            contentPadding = PaddingValues(VoxeraSpacing.xs),
        ) {
            Icon(icon, contentDescription = label, tint = VoxeraTheme.colors.foreground, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CallDock(
    mode: CallMode,
    media: LocalMediaState,
    onMic: () -> Unit,
    onCamera: () -> Unit,
    onFlip: () -> Unit,
    onAudio: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val on = stringResource(R.string.voxera_control_on)
    val off = stringResource(R.string.voxera_control_off)
    val pending = stringResource(R.string.voxera_control_pending)
    fun stateOf(actual: Boolean, isPending: Boolean) = if (isPending) pending else if (actual) on else off
    GlassDock(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
            CallControlButton(
                icon = if (media.microphone.actual) VoxeraIcons.Mic else VoxeraIcons.MicOff,
                label = stringResource(R.string.voxera_control_mic),
                stateLabel = stateOf(media.microphone.actual, media.microphone.isPending),
                active = media.microphone.actual,
                pending = media.microphone.isPending,
                onClick = onMic,
            )
            if (mode == CallMode.VIDEO) {
                CallControlButton(
                    icon = if (media.camera.actual) VoxeraIcons.Videocam else VoxeraIcons.VideocamOff,
                    label = stringResource(R.string.voxera_control_camera),
                    stateLabel = stateOf(media.camera.actual, media.camera.isPending),
                    active = media.camera.actual,
                    pending = media.camera.isPending,
                    onClick = onCamera,
                )
                if (media.canSwitchCamera) {
                    CallControlButton(
                        icon = VoxeraIcons.SwitchCamera,
                        label = stringResource(R.string.voxera_control_flip),
                        enabled = media.camera.actual,
                        onClick = onFlip,
                    )
                }
            }
            if (media.audioRoutes.isNotEmpty()) {
                CallControlButton(icon = media.selectedRouteIcon(), label = stringResource(R.string.voxera_control_audio), onClick = onAudio)
            }
            CallControlButton(icon = VoxeraIcons.CallEnd, label = stringResource(R.string.voxera_control_end), danger = true, onClick = onEnd)
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
