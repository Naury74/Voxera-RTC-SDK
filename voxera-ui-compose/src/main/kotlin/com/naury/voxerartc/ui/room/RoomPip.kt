package com.naury.voxerartc.ui.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.LocalMediaState
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.api.SessionState
import com.naury.voxerartc.core.VoxeraSession
import com.naury.voxerartc.ui.R
import com.naury.voxerartc.ui.component.ParticipantTile
import com.naury.voxerartc.ui.component.VoxeraAvatar
import com.naury.voxerartc.ui.component.VoxeraIcons
import com.naury.voxerartc.ui.glass.GlassPill
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import com.naury.voxerartc.ui.video.VoxeraVideoRenderer

/**
 * PiP 창에서 크게 보일 사람. 화면을 공유하는 사람 → 통화 화면에서 크게 보던(고정한) 사람 → 지금 말하는 원격 참가자 → 마지막으로 말한 사람 → 카메라가 켜진 원격 참가자
 * → 첫 원격 참가자 → 나. 말이 잠깐 끊겨도 [lastSpeakerId]를 써서 창이 깜빡이며 바뀌지 않게 한다.
 */
internal fun pipFocus(participants: List<Participant>, lastSpeakerId: String?, pinnedId: String? = null): Participant? {
    val remotes = participants.filterNot { it.isLocal }
    return remotes.firstOrNull { it.screenShare != null }
        ?: participants.firstOrNull { it.id == pinnedId }
        ?: remotes.filter { it.isSpeaking }.maxByOrNull { it.audioLevel }
        ?: remotes.firstOrNull { it.id == lastSpeakerId }
        ?: remotes.firstOrNull { it.camera != null }
        ?: remotes.firstOrNull()
        ?: participants.firstOrNull { it.isLocal }
}

/**
 * PiP 창 안의 통화(E20). 조작·시트·알림은 그리지 않고 한 사람만 보여 준다. 유리 효과 대신 불투명 이름표를 쓴다.
 * PiP에 들어가고 나올 때 무대가 새로 그려지므로 영상 렌더러는 한 번에 한 곳에만 붙는다.
 */
@Composable
internal fun PictureInPictureContent(
    session: VoxeraSession,
    participants: List<Participant>,
    media: LocalMediaState,
    renderer: VoxeraVideoRenderer?,
    mode: CallMode,
    pinnedId: String? = null,
) {
    var lastSpeakerId by remember(session) { mutableStateOf<String?>(null) }
    val speaker = participants.filter { !it.isLocal && it.isSpeaking }.maxByOrNull { it.audioLevel }?.id
    SideEffect { if (speaker != null) lastSpeakerId = speaker }
    val focus = pipFocus(participants, lastSpeakerId, pinnedId)
    Box(Modifier.fillMaxSize()) {
        if (focus != null) {
            if (mode == CallMode.VIDEO) {
                ParticipantTile(
                    focus,
                    renderer,
                    Modifier.fillMaxSize(),
                    showScreenShare = focus.screenShare != null && !focus.isLocal,
                    shape = RoundedCornerShape(0.dp),
                    speakingOutline = false,
                )
            } else {
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val avatar = (minOf(maxWidth, maxHeight) * 0.5f).coerceIn(40.dp, 120.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xxs)) {
                        Box(contentAlignment = Alignment.Center) {
                            SpeakingRipple(focus.isSpeaking, focus.audioLevel, avatar)
                            VoxeraAvatar(focus.id, focus.displayName, size = avatar, speaking = focus.isSpeaking, audioLevel = focus.audioLevel)
                        }
                        Text(
                            if (focus.isLocal) stringResource(R.string.voxera_you) else focus.displayName,
                            color = VoxeraTheme.colors.foreground,
                            style = VoxeraTheme.typography.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        // 내 마이크가 꺼져 있으면 작은 창에서도 알 수 있게 한다.
        if (!media.microphone.actual) {
            GlassPill(Modifier.align(Alignment.TopEnd).padding(VoxeraSpacing.xs)) {
                Icon(VoxeraIcons.MicOff, contentDescription = stringResource(R.string.voxera_mic_off), tint = VoxeraTheme.colors.onScrim, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** PiP 창에서 통화 중이 아닐 때(연결 중·종료·실패) 보여 줄 짧은 상태 한 줄. 자세한 내용은 창을 키우면 나온다. */
@Composable
internal fun PictureInPictureStatus(state: SessionState) {
    val text = when (state) {
        is SessionState.Ended -> stringResource(R.string.voxera_end_title) + (state.durationMs?.let { " · " + formatDuration(it) } ?: "")
        is SessionState.Failed -> stringResource(if (state.durationMs != null) R.string.voxera_dropped_title else R.string.voxera_failed_title)
        is SessionState.Ending -> stringResource(R.string.voxera_state_ending)
        else -> stringResource(R.string.voxera_state_connecting)
    }
    Box(Modifier.fillMaxSize().padding(VoxeraSpacing.sm), contentAlignment = Alignment.Center) {
        Text(text, color = VoxeraTheme.colors.foreground, style = VoxeraTheme.typography.label, textAlign = TextAlign.Center)
    }
}
