package com.naury.voxerartc.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KListSection
import com.naury.kinetica.ui.KRadioRow
import com.naury.kinetica.ui.KStatCard
import com.naury.kinetica.ui.KListLeading
import com.naury.kinetica.ui.KListTrailing
import com.naury.kinetica.ui.KListItem
import com.naury.kinetica.ui.kListSection
import com.naury.kinetica.ui.SurfaceStyle
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KTone
import com.naury.voxerartc.ui.glass.glassStyle
import com.naury.voxerartc.api.AudioRoute
import com.naury.voxerartc.api.AudioRouteType
import com.naury.voxerartc.api.ConnectionQuality
import com.naury.voxerartc.api.EffectsAvailability
import com.naury.voxerartc.api.LocalMediaState
import com.naury.voxerartc.api.MetricDirection
import com.naury.voxerartc.api.MetricKind
import com.naury.voxerartc.api.MetricSource
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.api.VideoEffect
import com.naury.voxerartc.ui.R
import com.naury.voxerartc.ui.component.StatusChip
import com.naury.voxerartc.ui.component.VoxeraAvatar
import com.naury.voxerartc.ui.component.VoxeraIconTile
import com.naury.voxerartc.ui.component.VoxeraIcons
import com.naury.voxerartc.ui.glass.GlassLevel
import com.naury.voxerartc.ui.glass.GlassSurface
import com.naury.voxerartc.ui.theme.VoxeraRadius
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import java.util.Locale

@Composable
internal fun SheetTitle(text: String) {
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
        // 연결된 장치만 판 하나에 모은다. 행 전체가 라디오 버튼이고 고른 행 앞 타일이 강조색으로 바뀐다.
        val routes = media.audioRoutes.filter { it.isSelectable }
        val surface = sheetCardSurface()
        KListSection(surface = surface) {
            routes.forEach { route ->
                item(route.id) { RouteRow(route, selected = route.id == media.selectedAudioRouteId, surface = surface, onSelect = { onSelect(route.id) }) }
            }
        }
    }
}

private fun AudioRouteType.isAccessory(): Boolean = this == AudioRouteType.BLUETOOTH || this == AudioRouteType.WIRED_HEADSET || this == AudioRouteType.USB

@Composable
private fun RouteRow(route: AudioRoute, selected: Boolean, surface: SurfaceStyle, onSelect: () -> Unit) {
    val colors = VoxeraTheme.colors
    val typeLabel = stringResource(route.type.labelRes())
    val detail = when {
        !route.isSelectable -> stringResource(R.string.voxera_route_unavailable)
        // 내장 수화부·스피커의 이름은 시스템 영어 이름("Earpiece")이라 보여 주지 않는다. 이어폰은 기기 이름을 보여 준다.
        route.type.isAccessory() && route.label != typeLabel -> route.label
        else -> null
    }
    KRadioRow(
        typeLabel,
        selected = selected,
        onSelect = onSelect,
        subtitle = detail,
        leading = KListLeading.Icon(route.type.icon(), container = if (selected) colors.primary else colors.foregroundSecondary),
        enabled = route.isSelectable,
        surface = surface,
    )
}

/** 시트 안에 놓는 판. 시트 유리 위에 얹힌 옅은 한 겹으로 그려진다. */
@Composable
internal fun sheetCardSurface(): SurfaceStyle = SurfaceStyle.Glass(VoxeraTheme.colors.glassStyle(GlassLevel.Low))

/** 참가자 목록 순서: 손을 든 사람이 먼저 든 순서대로 맨 위, 나머지는 원래 순서. */
internal fun peopleOrder(participants: List<Participant>): List<Participant> =
    participants.filter { it.isHandRaised }.sortedBy { it.handRaisedAtMs } + participants.filterNot { it.isHandRaised }

/**
 * 참가자 목록(G-10). 말하는 중·마이크·카메라·연결 상태를 보여 주고, 영상 통화면 고정/해제 버튼을 둔다.
 * 권한이 필요한 관리 동작(내보내기, 남의 마이크 끄기)은 제공하지 않는다.
 */
@Composable
internal fun PeopleSheet(participants: List<Participant>, pinnedId: String?, onTogglePin: ((String) -> Unit)?) {
    val colors = VoxeraTheme.colors
    val you = stringResource(R.string.voxera_you)
    val surface = sheetCardSurface()
    SheetTitle(stringResource(R.string.voxera_people_title, participants.size))
    LazyColumn(Modifier.heightIn(max = 420.dp)) {
        kListSection(peopleOrder(participants), key = { it.id }, surface = surface) { participant ->
            val weak = !participant.isLocal && (participant.connectionQuality == ConnectionQuality.POOR || participant.connectionQuality == ConnectionQuality.LOST)
            KListItem(
                if (participant.isLocal && participant.displayName != you) "${participant.displayName} ($you)" else participant.displayName,
                supporting = when {
                    participant.isSpeaking -> stringResource(R.string.voxera_speaking)
                    weak -> stringResource(R.string.voxera_weak_connection)
                    else -> null
                },
                leading = KListLeading.Custom {
                    VoxeraAvatar(participant.id, participant.displayName, size = 40.dp, speaking = participant.isSpeaking, audioLevel = participant.audioLevel)
                },
                trailing = KListTrailing.Custom { PersonStatus(participant, pinned = participant.id == pinnedId, onTogglePin = onTogglePin) },
                surface = surface,
            )
        }
    }
}

/** 참가자 행 끝의 손들기·마이크·카메라 표시와 고정 버튼. 켜진 상태 아이콘은 읽지 않고, 꺼진 상태만 읽는다. */
@Composable
private fun PersonStatus(participant: Participant, pinned: Boolean, onTogglePin: ((String) -> Unit)?) {
    val colors = VoxeraTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
        if (participant.isHandRaised) {
            Icon(VoxeraIcons.Hand, contentDescription = stringResource(R.string.voxera_hand_raised), tint = colors.warning, modifier = Modifier.size(20.dp))
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
            KIconButton(
                VoxeraIcons.PushPin,
                stringResource(if (pinned) R.string.voxera_tile_unpin else R.string.voxera_tile_pin),
                onClick = { onTogglePin(participant.id) },
                variant = if (pinned) KVariant.Tonal else KVariant.Text,
                tone = if (pinned) KTone.Accent else KTone.Neutral,
                size = 40.dp,
            )
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
                    val series = history.series(kind, direction)
                    // 값이 바뀌면 바뀐 자리 숫자가 굴러가고, 최근 흐름은 카드 아래 꺾은선으로 보인다. 카드 하나를 한 번에 읽는다.
                    KStatCard(
                        title = stringResource(label),
                        value = value ?: stringResource(R.string.voxera_metric_na),
                        modifier = Modifier.weight(1f),
                        trend = series.filter { it.isFinite() }.map { it.toFloat() }.takeIf { kind in TrendKinds && it.size >= 2 },
                        surface = sheetCardSurface(),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
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
