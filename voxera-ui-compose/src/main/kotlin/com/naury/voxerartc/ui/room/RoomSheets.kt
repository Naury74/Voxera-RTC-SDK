package com.naury.voxerartc.ui.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naury.voxerartc.api.AudioRoute
import com.naury.voxerartc.api.EffectsAvailability
import com.naury.voxerartc.api.VideoEffect
import com.naury.voxerartc.api.AudioRouteType
import com.naury.voxerartc.api.LocalMediaState
import com.naury.voxerartc.api.MetricDirection
import com.naury.voxerartc.api.MetricKind
import com.naury.voxerartc.api.MetricSource
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.api.QualitySnapshot
import com.naury.voxerartc.core.VoxeraSession
import com.naury.voxerartc.ui.R
import com.naury.voxerartc.ui.component.StatusChip
import com.naury.voxerartc.ui.component.VoxeraAvatar
import com.naury.voxerartc.ui.component.VoxeraIcons
import com.naury.voxerartc.ui.glass.GlassLevel
import com.naury.voxerartc.ui.glass.GlassSurface
import com.naury.voxerartc.ui.theme.VoxeraRadius
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import java.util.Locale

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        color = VoxeraTheme.colors.foreground,
        style = VoxeraTheme.typography.title,
        modifier = Modifier.padding(bottom = VoxeraSpacing.sm).semantics { heading() },
    )
}

internal fun AudioRouteType.icon(): ImageVector = when (this) {
    AudioRouteType.EARPIECE -> Icons.Filled.Phone
    AudioRouteType.SPEAKER -> VoxeraIcons.VolumeUp
    AudioRouteType.BLUETOOTH -> VoxeraIcons.Bluetooth
    AudioRouteType.WIRED_HEADSET, AudioRouteType.USB -> VoxeraIcons.Headset
    AudioRouteType.UNKNOWN -> VoxeraIcons.VolumeUp
}

internal fun LocalMediaState.selectedRouteIcon(): ImageVector =
    audioRoutes.firstOrNull { it.id == selectedAudioRouteId }?.type?.icon() ?: VoxeraIcons.VolumeUp

/** 오디오 출력 선택(U-08). 지금 고를 수 없는 경로는 이유와 함께 비활성으로 보여 준다. */
@Composable
internal fun AudioRouteSheet(media: LocalMediaState, onSelect: (String) -> Unit) {
    SheetTitle(stringResource(R.string.voxera_routes_title))
    Column(verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xxs)) {
        media.audioRoutes.forEach { route -> RouteRow(route, selected = route.id == media.selectedAudioRouteId, onSelect = { onSelect(route.id) }) }
    }
}

@Composable
private fun RouteRow(route: AudioRoute, selected: Boolean, onSelect: () -> Unit) {
    val colors = VoxeraTheme.colors
    val typeLabel = stringResource(route.type.labelRes())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(VoxeraRadius.sm))
            .clickable(enabled = route.isSelectable && !selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { this.selected = selected }
            .padding(horizontal = VoxeraSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.sm),
    ) {
        Icon(route.type.icon(), contentDescription = null, tint = if (selected) colors.primary else colors.foreground)
        Column(Modifier.weight(1f)) {
            Text(typeLabel, color = colors.foreground, style = VoxeraTheme.typography.body)
            val detail = when {
                !route.isSelectable -> stringResource(R.string.voxera_route_unavailable)
                route.label != typeLabel -> route.label
                else -> null
            }
            if (detail != null) Text(detail, color = colors.foregroundSecondary, style = VoxeraTheme.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.voxera_route_selected), tint = colors.primary)
    }
}

/** 참가자 목록(G-10). 권한이 필요한 관리 동작은 아직 제공하지 않는다. */
@Composable
internal fun PeopleSheet(participants: List<Participant>) {
    SheetTitle(stringResource(R.string.voxera_people_title, participants.size))
    LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
        items(participants, key = { it.id }) { participant ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.sm), modifier = Modifier.heightIn(min = 48.dp)) {
                VoxeraAvatar(participant.id, participant.displayName, size = 40.dp, speaking = participant.isSpeaking, audioLevel = participant.audioLevel)
                Text(
                    if (participant.isLocal) "${participant.displayName} (${stringResource(R.string.voxera_you)})" else participant.displayName,
                    color = VoxeraTheme.colors.foreground,
                    style = VoxeraTheme.typography.body,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    if (participant.isMicrophoneEnabled) VoxeraIcons.Mic else VoxeraIcons.MicOff,
                    contentDescription = if (participant.isMicrophoneEnabled) null else stringResource(R.string.voxera_mic_off),
                    tint = if (participant.isMicrophoneEnabled) VoxeraTheme.colors.foregroundSecondary else VoxeraTheme.colors.danger,
                    modifier = Modifier.size(20.dp),
                )
                Icon(
                    if (participant.camera != null) VoxeraIcons.Videocam else VoxeraIcons.VideocamOff,
                    contentDescription = if (participant.camera != null) null else stringResource(R.string.voxera_camera_off),
                    tint = VoxeraTheme.colors.foregroundSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** 화면에 보일 측정 항목 순서. */
internal val QualityRows: List<Triple<MetricKind, MetricDirection, Int>> = listOf(
    Triple(MetricKind.ROUND_TRIP_TIME, MetricDirection.NONE, R.string.voxera_metric_rtt),
    Triple(MetricKind.JITTER, MetricDirection.RECEIVE, R.string.voxera_metric_jitter),
    Triple(MetricKind.PACKET_LOSS, MetricDirection.RECEIVE, R.string.voxera_metric_loss),
    Triple(MetricKind.BITRATE, MetricDirection.SEND, R.string.voxera_metric_bitrate_send),
    Triple(MetricKind.BITRATE, MetricDirection.RECEIVE, R.string.voxera_metric_bitrate_receive),
    Triple(MetricKind.FRAME_RATE, MetricDirection.RECEIVE, R.string.voxera_metric_fps),
)

/** 측정값 문구. 값이 없으면 `null`이며 화면은 N/A를 쓴다(0으로 바꾸지 않는다). */
internal fun formatMetric(kind: MetricKind, value: Double?): String? = value?.let {
    when (kind) {
        MetricKind.ROUND_TRIP_TIME, MetricKind.JITTER -> String.format(Locale.ROOT, "%.0f ms", it)
        MetricKind.PACKET_LOSS -> String.format(Locale.ROOT, "%.1f %%", it)
        MetricKind.BITRATE -> formatBitrate(it)
        MetricKind.FRAME_RATE -> String.format(Locale.ROOT, "%.1f", it)
        MetricKind.RESOLUTION_HEIGHT -> String.format(Locale.ROOT, "%.0fp", it)
    }
}

/** 통화 품질(G-12). 송신·수신을 나누어 쓰고, 데모 값은 시뮬레이션이라고 표시한다. */
@Composable
internal fun QualitySheet(session: VoxeraSession) {
    val snapshot: QualitySnapshot? by session.diagnostics.collectAsStateWithLifecycle(initialValue = null)
    val colors = VoxeraTheme.colors
    SheetTitle(stringResource(R.string.voxera_stats_title))
    val current = snapshot
    if (current == null) {
        Text(stringResource(R.string.voxera_stats_waiting), color = colors.foregroundSecondary, style = VoxeraTheme.typography.body)
        return
    }
    if (current.source == MetricSource.SIMULATED) {
        StatusChip(stringResource(R.string.voxera_stats_simulated), colors.warning, Modifier.padding(bottom = VoxeraSpacing.sm))
    }
    Column(verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
        QualityRows.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
                pair.forEach { (kind, direction, label) ->
                    val value = formatMetric(kind, current.valueOf(kind, direction))
                    GlassSurface(level = GlassLevel.Low, modifier = Modifier.weight(1f)) {
                        Column {
                            Text(stringResource(label), color = colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
                            Text(
                                value ?: stringResource(R.string.voxera_metric_na),
                                color = if (value == null) colors.foregroundSecondary else colors.foreground,
                                style = VoxeraTheme.typography.title,
                            )
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.voxera_stats_rtt_note), color = colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
    }
}

/** 효과 프리셋. 값은 [VideoEffect] 범위 안의 보수적인 값만 쓴다. */
internal val EffectPresets: List<Pair<Int, VideoEffect>> = listOf(
    R.string.voxera_effect_none to VideoEffect.None,
    R.string.voxera_effect_bright to VideoEffect(brightness = 0.12f),
    R.string.voxera_effect_vivid to VideoEffect(contrast = 1.1f, saturation = 1.35f),
    R.string.voxera_effect_soft to VideoEffect(contrast = 0.88f, saturation = 0.9f),
    R.string.voxera_effect_mono to VideoEffect(saturation = 0f),
)

/** 카메라 효과(U-10/G-11). 지원하지 않거나 성능 때문에 꺼졌으면 이유를 보여 주고 고를 수 없게 한다. */
@Composable
internal fun EffectsSheet(media: LocalMediaState, onSelect: (VideoEffect) -> Unit) {
    val colors = VoxeraTheme.colors
    SheetTitle(stringResource(R.string.voxera_effects_title))
    when (media.effectsAvailability) {
        EffectsAvailability.UNSUPPORTED -> {
            Text(stringResource(R.string.voxera_effects_unsupported), color = colors.foregroundSecondary, style = VoxeraTheme.typography.body)
            return
        }
        EffectsAvailability.DISABLED_FOR_PERFORMANCE ->
            Text(
                stringResource(R.string.voxera_effects_throttled),
                color = colors.warning,
                style = VoxeraTheme.typography.body,
                modifier = Modifier.padding(bottom = VoxeraSpacing.sm),
            )
        EffectsAvailability.AVAILABLE -> Unit
    }
    Column(verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xxs), modifier = Modifier.selectableGroup()) {
        EffectPresets.forEach { (label, effect) ->
            val selected = media.videoEffect == effect
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(VoxeraRadius.sm))
                    .selectable(selected = selected, enabled = media.camera.actual, role = Role.RadioButton) { onSelect(effect) }
                    .padding(horizontal = VoxeraSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(label), color = colors.foreground, style = VoxeraTheme.typography.body, modifier = Modifier.weight(1f))
                if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = colors.primary)
            }
        }
        Text(stringResource(R.string.voxera_effects_note), color = colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
    }
}
