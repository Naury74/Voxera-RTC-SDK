package com.naury.voxerartc.ui.call

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import com.naury.kinetica.ui.KSystemBarIcons
import com.naury.voxerartc.ui.theme.enterOnAppear
import com.naury.voxerartc.ui.theme.rememberReducedMotion
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
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
import com.naury.voxerartc.ui.glass.voxeraBackdrop
import com.naury.voxerartc.ui.room.SpeakingRipple
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import com.naury.voxerartc.ui.theme.VoxeraThemeConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Call Mode 화면. 수신(G-08), 발신 중, 종료 사유를 그린다. 미디어가 연결되면 [VoxeraRoomScreen]을 그리고, 종료 버튼은 [CallCoordinator.hangup]을 부른다.
 *
 * 잠금 화면이나 앱이 꺼진 상태에서 받는 실제 수신 알림(푸시)은 호스트가 띄운다. 이 화면은 앱이 앞에 있을 때 보이는 통화 UI다.
 *
 * @param session [coordinator]가 쓰는 세션과 같아야 한다.
 * @param onExit 종료 화면에서 닫기를 누르면 불린다.
 * @param pictureInPicture·onEnterPictureInPicture 통화 중 화면에 그대로 넘긴다([VoxeraRoomScreen] 참고).
 */
@Composable
public fun VoxeraCallScreen(
    coordinator: CallCoordinator,
    session: VoxeraSession,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    theme: VoxeraThemeConfig = VoxeraThemeConfig(),
    pictureInPicture: Boolean = false,
    onEnterPictureInPicture: (() -> Unit)? = null,
) {
    val state by coordinator.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    when (val current = state) {
        is CallState.ConnectingMedia, is CallState.Active ->
            VoxeraRoomScreen(
                session,
                onExit = onExit,
                modifier = modifier,
                theme = theme,
                onEnd = { scope.launch { coordinator.hangup() } },
                pictureInPicture = pictureInPicture,
                onEnterPictureInPicture = onEnterPictureInPicture,
            )
        else -> VoxeraTheme(theme) {
            // 상태 표시줄 아이콘을 이 화면 바탕 밝기에 맞춘다. 화면을 떠나면 호스트 설정으로 돌아간다.
            KSystemBarIcons(light = VoxeraTheme.colors.isDark)
            Box(modifier.fillMaxSize().voxeraBackdrop(emphasis = if (current is CallState.Ringing) 0.6f else 0f).windowInsetsPadding(WindowInsets.safeDrawing)) {
                if (session.engineKind == RtcEngineKind.SIMULATED) {
                    StatusChip(stringResource(R.string.voxera_demo_badge), VoxeraTheme.colors.warning, Modifier.align(Alignment.TopCenter).padding(top = VoxeraSpacing.md))
                }
                // 세로가 짧은 화면(가로 모드, 작은 폰)에서는 여백을 줄이고, 가로 모드는 상대 정보와 버튼을 좌우로 나눈다.
                val window = LocalWindowInfo.current.containerSize
                val compact = window.height < with(LocalDensity.current) { 720.dp.roundToPx() }
                val landscape = window.width > window.height
                // DEMO 표시와 제목이 겹치지 않게 위를 비운다.
                val top = if (session.engineKind == RtcEngineKind.SIMULATED) 48.dp else 0.dp
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .widthIn(max = if (landscape) 760.dp else 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = top)
                        .padding(horizontal = VoxeraSpacing.xl, vertical = if (compact) VoxeraSpacing.md else VoxeraSpacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (current) {
                        is CallState.Ringing -> RingingContent(
                            current,
                            compact = compact,
                            landscape = landscape,
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
private fun RingingContent(
    ringing: CallState.Ringing,
    compact: Boolean,
    landscape: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
) {
    if (landscape) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) { RingingPeer(ringing, compact = true) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) { RingingActions(ringing, onAccept, onDecline, onCancel) }
        }
    } else {
        RingingPeer(ringing, compact)
        Spacer(Modifier.height(if (compact) VoxeraSpacing.xl else VoxeraSpacing.xxl * 2))
        RingingActions(ringing, onAccept, onDecline, onCancel)
    }
}

/** 수신·발신 제목, 상대 아바타와 물결, 이름, 남은 시간. */
@Composable
private fun RingingPeer(ringing: CallState.Ringing, compact: Boolean) {
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
    // 물결 고리가 아바타 밖으로 퍼지므로 위아래를 넉넉히 비운다.
    val ripple = if (compact) VoxeraSpacing.xl else 64.dp
    val avatar = if (compact) 104.dp else 152.dp
    Spacer(Modifier.height(ripple))
    Box(contentAlignment = Alignment.Center, modifier = Modifier.enterOnAppear(1, scale = true)) {
        SpeakingRipple(speaking = true, level = 0.8f, size = avatar)
        VoxeraAvatar(call.peerId, call.peerDisplayName, size = avatar)
    }
    Spacer(Modifier.height(if (compact) VoxeraSpacing.xl else 48.dp))
    Text(call.peerDisplayName, color = VoxeraTheme.colors.foreground, style = VoxeraTheme.typography.display, textAlign = TextAlign.Center, modifier = Modifier.enterOnAppear(2).semantics { heading() })
    Spacer(Modifier.height(VoxeraSpacing.xs))
    Text(pluralStringResource(R.plurals.voxera_call_rings_for, remaining, remaining), color = VoxeraTheme.colors.foregroundSecondary, style = VoxeraTheme.typography.caption, textAlign = TextAlign.Center)
}

/** 수락 버튼 뒤에서 퍼져 나가는 고리. 움직임 줄이기 설정이면 그리지 않는다. 장식이다. */
@Composable
private fun AcceptPulse(modifier: Modifier) {
    if (rememberReducedMotion()) return
    val color = VoxeraTheme.colors.primary
    val transition = rememberInfiniteTransition(label = "accept")
    val p by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1_300, easing = LinearEasing)), label = "accept-pulse")
    Canvas(modifier) {
        val r = size.minDimension / 2f
        drawCircle(color.copy(alpha = 0.35f * (1f - p)), radius = r * (1f + 0.55f * p))
    }
}

/** 수신이면 거절·수락, 발신이면 취소 버튼. */
@Composable
private fun RingingActions(ringing: CallState.Ringing, onAccept: () -> Unit, onDecline: () -> Unit, onCancel: () -> Unit) {
    val call = ringing.call
    if (ringing.direction == CallDirection.INCOMING) {
        // 수락 고리가 버튼 밖으로 퍼지므로, 스크롤 영역에 잘리지 않게 둘레를 비워 둔다.
        Row(horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xxl * 2), modifier = Modifier.enterOnAppear(4, scale = true).padding(28.dp)) {
            CallControlButton(icon = VoxeraIcons.CallEnd, label = stringResource(R.string.voxera_call_decline), danger = true, onClick = onDecline, circleSize = 72.dp)
            // 수락 버튼 뒤로 고리가 맥박처럼 퍼져 누를 곳을 알려 준다.
            Box(contentAlignment = Alignment.TopCenter) {
                AcceptPulse(Modifier.padding(top = 4.dp).size(72.dp))
                CallControlButton(
                    icon = if (call.mode == CallMode.VIDEO) VoxeraIcons.Videocam else VoxeraIcons.Call,
                    label = stringResource(R.string.voxera_call_accept),
                    accent = true,
                    onClick = onAccept,
                    circleSize = 72.dp,
                )
            }
        }
    } else {
        CallControlButton(icon = VoxeraIcons.CallEnd, label = stringResource(R.string.voxera_call_cancel), danger = true, onClick = onCancel, circleSize = 72.dp)
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
