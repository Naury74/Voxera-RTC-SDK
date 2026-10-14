package com.naury.voxerartc.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.MonotonicClock
import com.naury.voxerartc.api.RtcEngineKind
import com.naury.voxerartc.core.VoxeraSession
import com.naury.voxerartc.invitation.CallCoordinator
import com.naury.voxerartc.invitation.CallDirection
import com.naury.voxerartc.invitation.CallEndReason
import com.naury.voxerartc.invitation.CallInfo
import com.naury.voxerartc.invitation.CallState
import com.naury.voxerartc.ui.R
import com.naury.voxerartc.ui.component.CallControlButton
import com.naury.voxerartc.ui.component.StatusChip
import com.naury.voxerartc.ui.component.VoxeraAvatar
import com.naury.voxerartc.ui.component.VoxeraButton
import com.naury.voxerartc.ui.component.VoxeraButtonTone
import com.naury.voxerartc.ui.component.VoxeraIcons
import com.naury.voxerartc.ui.room.VoxeraRoomScreen
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import com.naury.voxerartc.ui.theme.VoxeraThemeConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Call Mode 화면(G-08 수신, 발신 중, 종료 사유). 미디어가 연결되면 [VoxeraRoomScreen]을 그리고, 종료 버튼은 [CallCoordinator.hangup]을 부른다.
 *
 * 실제 수신 알림(잠금 화면, 앱 종료 상태 푸시)은 호스트가 띄운다. 이 화면은 앱이 앞에 있을 때의 통화 UI다.
 *
 * @param session [coordinator]가 쓰는 세션과 같아야 한다.
 * @param onExit 종료 화면에서 닫기를 누름.
 */
@Composable
public fun VoxeraCallScreen(
    coordinator: CallCoordinator,
    session: VoxeraSession,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    theme: VoxeraThemeConfig = VoxeraThemeConfig(),
) {
    val state by coordinator.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    when (val current = state) {
        is CallState.ConnectingMedia, is CallState.Active ->
            VoxeraRoomScreen(session, onExit = onExit, modifier = modifier, theme = theme, onEnd = { scope.launch { coordinator.hangup() } })
        else -> VoxeraTheme(theme) {
            Box(modifier.fillMaxSize().background(VoxeraTheme.colors.canvas).windowInsetsPadding(WindowInsets.safeDrawing)) {
                if (session.engineKind == RtcEngineKind.SIMULATED) {
                    StatusChip(stringResource(R.string.voxera_demo_badge), VoxeraTheme.colors.warning, Modifier.align(Alignment.TopCenter).padding(top = VoxeraSpacing.md))
                }
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .widthIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = VoxeraSpacing.xl, vertical = VoxeraSpacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (current) {
                        is CallState.Ringing -> RingingContent(
                            current,
                            onAccept = { scope.launch { coordinator.accept() } },
                            onDecline = { scope.launch { coordinator.decline() } },
                            onCancel = { scope.launch { coordinator.cancel() } },
                        )
                        is CallState.Dialing -> {
                            Text(stringResource(R.string.voxera_call_dialing), color = VoxeraTheme.colors.foregroundSecondary, style = VoxeraTheme.typography.body)
                            Spacer(Modifier.height(VoxeraSpacing.xxl))
                            VoxeraButton(stringResource(R.string.voxera_call_cancel), onClick = { scope.launch { coordinator.cancel() } }, tone = VoxeraButtonTone.Secondary)
                        }
                        is CallState.Ended -> EndedCallContent(current.call, current.reason, onExit)
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun RingingContent(ringing: CallState.Ringing, onAccept: () -> Unit, onDecline: () -> Unit, onCancel: () -> Unit) {
    val call = ringing.call
    val incoming = ringing.direction == CallDirection.INCOMING
    val title = when {
        !incoming -> stringResource(R.string.voxera_call_outgoing)
        call.mode == CallMode.VIDEO -> stringResource(R.string.voxera_call_incoming_video)
        else -> stringResource(R.string.voxera_call_incoming_voice)
    }
    val remaining by produceState(remainingSeconds(ringing.deadlineMs), ringing.deadlineMs) {
        while (value > 0) {
            delay(1_000)
            value = remainingSeconds(ringing.deadlineMs)
        }
    }
    Text(title, color = VoxeraTheme.colors.foregroundSecondary, style = VoxeraTheme.typography.body, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    Spacer(Modifier.height(VoxeraSpacing.xl))
    VoxeraAvatar(call.peerId, call.peerDisplayName, size = 152.dp, speaking = true, audioLevel = 0.4f)
    Spacer(Modifier.height(VoxeraSpacing.lg))
    Text(call.peerDisplayName, color = VoxeraTheme.colors.foreground, style = VoxeraTheme.typography.headline, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(VoxeraSpacing.xs))
    Text(pluralStringResource(R.plurals.voxera_call_rings_for, remaining, remaining), color = VoxeraTheme.colors.foregroundSecondary, style = VoxeraTheme.typography.caption, textAlign = TextAlign.Center)
    Spacer(Modifier.height(VoxeraSpacing.xxl))
    if (incoming) {
        Row(horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xxl)) {
            CallControlButton(icon = VoxeraIcons.CallEnd, label = stringResource(R.string.voxera_call_decline), danger = true, onClick = onDecline)
            CallControlButton(
                icon = if (call.mode == CallMode.VIDEO) VoxeraIcons.Videocam else VoxeraIcons.Mic,
                label = stringResource(R.string.voxera_call_accept),
                accent = true,
                onClick = onAccept,
            )
        }
    } else {
        CallControlButton(icon = VoxeraIcons.CallEnd, label = stringResource(R.string.voxera_call_cancel), danger = true, onClick = onCancel)
    }
}

private fun remainingSeconds(deadlineMs: Long): Int =
    ((deadlineMs - MonotonicClock.System.nowMs()).coerceAtLeast(0) / 1000).toInt()

@Composable
private fun EndedCallContent(call: CallInfo?, reason: CallEndReason, onExit: () -> Unit) {
    if (call != null) {
        VoxeraAvatar(call.peerId, call.peerDisplayName, size = 96.dp)
        Spacer(Modifier.height(VoxeraSpacing.md))
        Text(call.peerDisplayName, color = VoxeraTheme.colors.foreground, style = VoxeraTheme.typography.title)
    }
    Spacer(Modifier.height(VoxeraSpacing.xs))
    Text(stringResource(R.string.voxera_call_ended), color = VoxeraTheme.colors.foreground, style = VoxeraTheme.typography.headline, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(VoxeraSpacing.xs))
    Text(stringResource(reason.messageRes()), color = VoxeraTheme.colors.foregroundSecondary, style = VoxeraTheme.typography.body, textAlign = TextAlign.Center)
    Spacer(Modifier.height(VoxeraSpacing.xxl))
    VoxeraButton(stringResource(R.string.voxera_action_close), onClick = onExit, tone = VoxeraButtonTone.Secondary, modifier = Modifier.fillMaxWidth())
}

internal fun CallEndReason.messageRes(): Int = when (this) {
    CallEndReason.COMPLETED -> R.string.voxera_call_end_completed
    CallEndReason.DECLINED -> R.string.voxera_call_end_declined
    CallEndReason.MISSED -> R.string.voxera_call_end_missed
    CallEndReason.BUSY -> R.string.voxera_call_end_busy
    CallEndReason.CANCELLED -> R.string.voxera_call_end_cancelled
    CallEndReason.EXPIRED -> R.string.voxera_call_end_expired
    CallEndReason.ANSWERED_ELSEWHERE -> R.string.voxera_call_end_elsewhere
    CallEndReason.FAILED -> R.string.voxera_call_end_failed
}
