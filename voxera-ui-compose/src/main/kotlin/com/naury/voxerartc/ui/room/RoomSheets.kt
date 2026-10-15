package com.naury.voxerartc.ui.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.naury.voxerartc.api.ConnectionQuality
import com.naury.voxerartc.ui.component.VoxeraIconTile
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
import com.naury.voxerartc.api.AudioRoute
import com.naury.voxerartc.api.EffectsAvailability
import com.naury.voxerartc.api.VideoEffect
import com.naury.voxerartc.api.AudioRouteType
import com.naury.voxerartc.api.LocalMediaState
import com.naury.voxerartc.api.MetricDirection
import com.naury.voxerartc.api.MetricKind
import com.naury.voxerartc.api.MetricSource
import com.naury.voxerartc.api.Participant
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
        // 지금 쓸 수 없는 경로(연결이 끊긴 이어폰 등)는 목록에 두지 않는다. 연결되면 엔진이 알려 주어 바로 나타난다.
        media.audioRoutes.filter { it.isSelectable }.forEach { route -> RouteRow(route, selected = route.id == media.selectedAudioRouteId, onSelect = { onSelect(route.id) }) }
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
        VoxeraIconTile(route.type.icon(), if (selected) colors.primary else colors.foregroundSecondary, size = 36.dp)
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

/**
 * 참가자 목록(G-10). 말하는 중·마이크·카메라·연결 상태를 보여 주고, 영상 통화면 고정/해제 버튼을 둔다.
 * 권한이 필요한 관리 동작(내보내기, 남의 마이크 끄기)은 제공하지 않는다.
 */
@Composable
internal fun PeopleSheet(participants: List<Participant>, pinnedId: String?, onTogglePin: ((String) -> Unit)?) {
    val colors = VoxeraTheme.colors
    SheetTitle(stringResource(R.string.voxera_people_title, participants.size))
    LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xxs)) {
        items(participants, key = { it.id }) { participant ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.sm), modifier = Modifier.heightIn(min = 56.dp)) {
                VoxeraAvatar(participant.id, participant.displayName, size = 40.dp, speaking = participant.isSpeaking, audioLevel = participant.audioLevel)
                Column(Modifier.weight(1f)) {
                    val you = stringResource(R.string.voxera_you)
                    Text(
                        if (participant.isLocal && participant.displayName != you) "${participant.displayName} ($you)" else participant.displayName,
                        color = colors.foreground,
                        style = VoxeraTheme.typography.body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val detail = when {
                        participant.isSpeaking -> stringResource(R.string.voxera_speaking)
                        !participant.isLocal && (participant.connectionQuality == ConnectionQuality.POOR || participant.connectionQuality == ConnectionQuality.LOST) ->
                            stringResource(R.string.voxera_weak_connection)
                        else -> null
                    }
                    if (detail != null) Text(detail, color = if (participant.isSpeaking) colors.primary else colors.warning, style = VoxeraTheme.typography.caption)
                }
                Icon(
                    if (participant.isMicrophoneEnabled) VoxeraIcons.Mic else VoxeraIcons.MicOff,
                    contentDescription = if (participant.isMicrophoneEnabled) null else stringResource(R.string.voxera_mic_off),
                    tint = if (participant.isMicrophoneEnabled) colors.foregroundSecondary else colors.danger,
                    modifier = Modifier.size(20.dp),
                )
                Icon(
                    if (participant.camera != null) VoxeraIcons.Videocam else VoxeraIcons.VideocamOff,
                    contentDescription = if (participant.camera != null) null else stringResource(R.string.voxera_camera_off),
                    tint = colors.foregroundSecondary,
                    modifier = Modifier.size(20.dp),
                )
                if (onTogglePin != null) {
                    val pinned = participant.id == pinnedId
                    IconButton(onClick = { onTogglePin(participant.id) }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            VoxeraIcons.PushPin,
                            contentDescription = stringResource(if (pinned) R.string.voxera_tile_unpin else R.string.voxera_tile_pin),
                            tint = if (pinned) colors.primary else colors.foregroundSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
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
    Triple(MetricKind.EFFECT_PROCESSING_TIME, MetricDirection.SEND, R.string.voxera_metric_effect_time),
)

/** 측정값 문구. 값이 없으면 `null`이며 화면은 N/A를 쓴다(0으로 바꾸지 않는다). */
internal fun formatMetric(kind: MetricKind, value: Double?): String? = value?.let {
    when (kind) {
        MetricKind.ROUND_TRIP_TIME, MetricKind.JITTER -> String.format(Locale.ROOT, "%.0f ms", it)
        MetricKind.EFFECT_PROCESSING_TIME -> String.format(Locale.ROOT, "%.1f ms", it)
        MetricKind.PACKET_LOSS -> String.format(Locale.ROOT, "%.1f %%", it)
        MetricKind.BITRATE -> formatBitrate(it)
        MetricKind.FRAME_RATE -> String.format(Locale.ROOT, "%.1f", it)
        MetricKind.RESOLUTION_HEIGHT -> String.format(Locale.ROOT, "%.0fp", it)
    }
}

/** 추이 그래프를 그릴 항목. 수신 손실·지터·왕복 시간·수신 프레임 수. */
private val TrendKinds = setOf(MetricKind.ROUND_TRIP_TIME, MetricKind.JITTER, MetricKind.PACKET_LOSS, MetricKind.FRAME_RATE, MetricKind.BITRATE)

/**
 * 통화 품질(G-12). 맨 위에 상태 요약, 아래에 항목별 카드와 최근 추이 그래프. 송신·수신을 나누어 쓰고, 데모 값은 시뮬레이션이라고 표시한다.
 * 화면을 연 뒤가 아니라 통화 화면이 뜬 뒤부터 모은 표본(최대 30개)을 그린다.
 */
@Composable
internal fun QualitySheet(history: QualityHistory) {
    val colors = VoxeraTheme.colors
    SheetTitle(stringResource(R.string.voxera_stats_title))
    val current = history.latest
    if (current == null) {
        Text(stringResource(R.string.voxera_stats_waiting), color = colors.foregroundSecondary, style = VoxeraTheme.typography.body)
        return
    }
    Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
        val health = current.health()
        if (health != null) {
            GlassSurface(level = GlassLevel.Low, shape = RoundedCornerShape(VoxeraRadius.lg), modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.voxera_stats_health), color = colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
                        Text(stringResource(health.labelRes()), color = health.color(), style = VoxeraTheme.typography.headline)
                    }
                    if (current.source == MetricSource.SIMULATED) StatusChip(stringResource(R.string.voxera_stats_simulated), colors.warning)
                }
            }
        } else if (current.source == MetricSource.SIMULATED) {
            StatusChip(stringResource(R.string.voxera_stats_simulated), colors.warning)
        }
        // 효과 처리 시간은 효과를 켠 경우에만 의미가 있으므로 값이 없으면 칸을 두지 않는다.
        val rows = QualityRows.filter { (kind, direction, _) -> kind != MetricKind.EFFECT_PROCESSING_TIME || current.valueOf(kind, direction) != null }
        rows.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
                pair.forEach { (kind, direction, label) ->
                    val value = formatMetric(kind, current.valueOf(kind, direction))
                    GlassSurface(level = GlassLevel.Low, shape = RoundedCornerShape(VoxeraRadius.md), modifier = Modifier.weight(1f)) {
                        Column {
                            Text(stringResource(label), color = colors.foregroundSecondary, style = VoxeraTheme.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                value ?: stringResource(R.string.voxera_metric_na),
                                color = if (value == null) colors.foregroundSecondary else colors.foreground,
                                style = VoxeraTheme.typography.title,
                            )
                            val series = history.series(kind, direction)
                            if (kind in TrendKinds && series.size >= 2) {
                                Sparkline(series, colors.primary, Modifier.fillMaxWidth().height(28.dp).padding(top = VoxeraSpacing.xxs))
                            }
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text(stringResource(R.string.voxera_stats_rtt_note), color = colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
    }
}

/** 최근 값의 꺾은선. 축은 그 구간의 최소~최대로 맞춘다. 장식이며 TalkBack은 위 숫자를 읽는다. */
@Composable
internal fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics { }) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 0.0 } ?: 1.0
        val step = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * step
            val y = size.height - ((value - min) / span * size.height * 0.85 + size.height * 0.075).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
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
    Column(verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.sm)) {
        // 기본 프리셋 다섯 개가 360dp 폭에 한 줄로 들어가게 한다. 더 늘면 옆으로 넘긴다.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs), modifier = Modifier.selectableGroup()) {
            items(EffectPresets, key = { it.first }) { (label, effect) ->
                val selected = media.videoEffect == effect
                Column(
                    Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(VoxeraRadius.sm))
                        .selectable(selected = selected, enabled = media.camera.actual, role = Role.RadioButton) { onSelect(effect) }
                        .padding(vertical = VoxeraSpacing.xxs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xxs),
                ) {
                    EffectSwatch(effect, selected, enabled = media.camera.actual)
                    Text(stringResource(label), color = if (selected) colors.primary else colors.foreground, style = VoxeraTheme.typography.caption, maxLines = 1)
                }
            }
        }
        if (!media.camera.actual) {
            Text(stringResource(R.string.voxera_effects_camera_off), color = colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
        }
        Text(stringResource(R.string.voxera_effects_note), color = colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
    }
}

/**
 * 효과 미리보기 원. 같은 추상 그라디언트에 효과의 밝기·대비·채도를 적용해 그린다(실제 카메라 화면이 아니다).
 */
@Composable
private fun EffectSwatch(effect: VideoEffect, selected: Boolean, enabled: Boolean) {
    val colors = VoxeraTheme.colors
    val base = listOf(Color(0xFFF2B48C), Color(0xFF6CA6D9), Color(0xFF4E8F6A))
    val tinted = base.map { applyEffect(it, effect) }
    Box(
        Modifier
            .size(56.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(Brush.linearGradient(tinted))
            .then(if (selected) Modifier.border(3.dp, colors.primary, CircleShape) else Modifier.border(1.dp, colors.glassStroke, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/** [VideoEffect]의 밝기·대비·채도를 색 하나에 적용한다. 미리보기 원 전용이라 근사치다. */
internal fun applyEffect(color: Color, effect: VideoEffect): Color {
    fun channel(value: Float, luma: Float): Float {
        val saturated = luma + (value - luma) * effect.saturation
        val contrasted = (saturated - 0.5f) * effect.contrast + 0.5f
        return (contrasted + effect.brightness).coerceIn(0f, 1f)
    }
    val luma = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return Color(channel(color.red, luma), channel(color.green, luma), channel(color.blue, luma), color.alpha)
}
