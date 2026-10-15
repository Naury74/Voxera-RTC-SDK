package com.naury.voxerartc.ui.room

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.naury.voxerartc.api.EndReason
import com.naury.voxerartc.api.MetricDirection
import com.naury.voxerartc.api.MetricKind
import com.naury.voxerartc.api.MetricSource
import com.naury.voxerartc.api.SessionState
import com.naury.voxerartc.api.VoxeraError
import com.naury.voxerartc.ui.R
import com.naury.voxerartc.ui.component.StatusChip
import com.naury.voxerartc.ui.component.VoxeraAvatar
import com.naury.voxerartc.ui.component.VoxeraButton
import com.naury.voxerartc.ui.component.VoxeraButtonTone
import com.naury.voxerartc.ui.component.VoxeraIconTile
import com.naury.voxerartc.ui.component.VoxeraIcons
import com.naury.voxerartc.ui.glass.GlassLevel
import com.naury.voxerartc.ui.glass.GlassSurface
import com.naury.voxerartc.ui.theme.VoxeraRadius
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme

/** 연결 중(G-04). 방 이름 아바타 뒤로 물결이 퍼지고, 지금 단계를 두 칸 진행 표시로 보여 준다. */
@Composable
internal fun ConnectingContent(state: SessionState, simulated: Boolean, onCancel: () -> Unit) {
    val connecting = state is SessionState.Connecting
    val label = stringResource(if (connecting) R.string.voxera_state_connecting else R.string.voxera_state_preparing)
    val room = when (state) {
        is SessionState.Preparing -> state.roomId
        is SessionState.Connecting -> state.roomId
        else -> ""
    }
    CenteredColumn(simulated) {
        Box(contentAlignment = Alignment.Center) {
            SpeakingRipple(speaking = true, level = 0.5f, size = 128.dp)
            VoxeraAvatar(id = room.ifEmpty { "room" }, name = room.ifEmpty { "?" }, size = 128.dp)
        }
        // 물결 고리가 아바타 밖으로 퍼지므로 이름과 사이를 넉넉히 둔다.
        Spacer(Modifier.height(48.dp))
        Text(room, color = VoxeraTheme.colors.foreground, style = VoxeraTheme.typography.headline, textAlign = TextAlign.Center)
        Spacer(Modifier.height(VoxeraSpacing.xs))
        Text(
            label,
            color = VoxeraTheme.colors.foregroundSecondary,
            style = VoxeraTheme.typography.body,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(VoxeraSpacing.md))
        StepBar(steps = 2, current = if (connecting) 1 else 0)
        Spacer(Modifier.height(VoxeraSpacing.xxl))
        VoxeraButton(stringResource(R.string.voxera_action_cancel), onClick = onCancel, tone = VoxeraButtonTone.Secondary, modifier = Modifier.widthIn(min = 160.dp))
    }
}

/** 단계 진행 막대. 지난 단계와 지금 단계는 채운다. 글자로 단계가 이미 나오므로 장식으로 둔다. */
@Composable
private fun StepBar(steps: Int, current: Int) {
    val colors = VoxeraTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(steps) { index ->
            Box(
                Modifier
                    .size(width = 28.dp, height = 4.dp)
                    .background(
                        if (index <= current) colors.primary else colors.foreground.copy(alpha = 0.16f),
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

@Composable
internal fun ProgressContent(label: String, simulated: Boolean) {
    CenteredColumn(simulated) {
        Text(label, color = VoxeraTheme.colors.foregroundSecondary, style = VoxeraTheme.typography.body)
    }
}

/**
 * 통화 종료 요약(U-12/G-13). 통화 시간, 종료 이유, 모은 품질 표본이 있으면 평균 왕복 시간·손실과 상태를 보여 준다.
 * 표본이 없으면 품질 칸을 그리지 않는다(추측 값 금지).
 */
@Composable
internal fun EndedContent(
    reason: EndReason,
    durationMs: Long?,
    history: QualityHistory,
    simulated: Boolean,
    onExit: () -> Unit,
    onRejoin: (() -> Unit)?,
    onSaveReport: (() -> Unit)?,
) {
    val colors = VoxeraTheme.colors
    CenteredColumn(simulated) {
        VoxeraIconTile(VoxeraIcons.CallEnd, colors.danger, size = 64.dp)
        Spacer(Modifier.height(VoxeraSpacing.lg))
        Text(
            stringResource(R.string.voxera_end_title),
            color = colors.foreground,
            style = VoxeraTheme.typography.headline,
            modifier = Modifier.semantics { heading() },
        )
        if (durationMs != null) {
            Text(formatDuration(durationMs), color = colors.primary, style = VoxeraTheme.typography.display)
        }
        Spacer(Modifier.height(VoxeraSpacing.xxs))
        Text(stringResource(reason.messageRes()), color = colors.foregroundSecondary, style = VoxeraTheme.typography.body, textAlign = TextAlign.Center)
        val rtt = history.average(MetricKind.ROUND_TRIP_TIME, MetricDirection.NONE)
        val loss = history.average(MetricKind.PACKET_LOSS, MetricDirection.RECEIVE)
        val jitter = history.average(MetricKind.JITTER, MetricDirection.RECEIVE)
        val health = healthOf(rtt, loss, jitter)
        if (health != null) {
            Spacer(Modifier.height(VoxeraSpacing.lg))
            GlassSurface(level = GlassLevel.Low, shape = RoundedCornerShape(VoxeraRadius.xl), modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.voxera_end_quality), color = colors.foreground, style = VoxeraTheme.typography.label, modifier = Modifier.weight(1f))
                        StatusChip(stringResource(health.labelRes()), health.color())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.sm)) {
                        SummaryValue(stringResource(R.string.voxera_metric_rtt), formatMetric(MetricKind.ROUND_TRIP_TIME, rtt), Modifier.weight(1f))
                        SummaryValue(stringResource(R.string.voxera_metric_loss), formatMetric(MetricKind.PACKET_LOSS, loss), Modifier.weight(1f))
                    }
                    if (history.latest?.source == MetricSource.SIMULATED) {
                        Text(stringResource(R.string.voxera_stats_simulated), color = colors.warning, style = VoxeraTheme.typography.caption)
                    }
                }
            }
        }
        Spacer(Modifier.height(VoxeraSpacing.xxl))
        ExitActions(onExit, onRejoin, rejoinLabel = stringResource(R.string.voxera_action_rejoin), onSaveReport)
    }
}

@Composable
private fun SummaryValue(label: String, value: String?, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = VoxeraTheme.colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
        Text(value ?: stringResource(R.string.voxera_metric_na), color = VoxeraTheme.colors.foreground, style = VoxeraTheme.typography.title)
    }
}

/** 연결 실패·통화 끊김(G-14). 이유와 할 일, 기술 코드. 다시 해도 같은 결과일 오류는 재시도 버튼을 숨긴다. */
@Composable
internal fun FailedContent(
    error: VoxeraError,
    dropped: Boolean,
    simulated: Boolean,
    onExit: () -> Unit,
    onRejoin: (() -> Unit)?,
    onSaveReport: (() -> Unit)?,
) {
    val colors = VoxeraTheme.colors
    CenteredColumn(simulated) {
        VoxeraIconTile(if (dropped) VoxeraIcons.Wifi else VoxeraIcons.Shield, colors.warning, size = 64.dp)
        Spacer(Modifier.height(VoxeraSpacing.lg))
        Text(
            stringResource(if (dropped) R.string.voxera_dropped_title else R.string.voxera_failed_title),
            color = colors.foreground,
            style = VoxeraTheme.typography.headline,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(VoxeraSpacing.md))
        GlassSurface(level = GlassLevel.Low, shape = RoundedCornerShape(VoxeraRadius.xl), modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xxs)) {
                Text(stringResource(error.messageRes()), color = colors.foreground, style = VoxeraTheme.typography.body)
                val code = if (error is VoxeraError.InternalFailure) "${error.code} · ${error.technicalCode}" else error.code
                Text(stringResource(R.string.voxera_error_code, code), color = colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
            }
        }
        Spacer(Modifier.height(VoxeraSpacing.xxl))
        ExitActions(onExit, onRejoin.takeIf { error.isRetryable }, rejoinLabel = stringResource(R.string.voxera_action_retry), onSaveReport)
    }
}

@Composable
private fun ExitActions(onExit: () -> Unit, onRejoin: (() -> Unit)?, rejoinLabel: String, onSaveReport: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.sm)) {
        if (onRejoin != null) VoxeraButton(rejoinLabel, onClick = onRejoin, icon = VoxeraIcons.Refresh, modifier = Modifier.fillMaxWidth())
        if (onSaveReport != null) {
            VoxeraButton(stringResource(R.string.voxera_action_save_report), onClick = onSaveReport, tone = VoxeraButtonTone.Secondary, modifier = Modifier.fillMaxWidth())
        }
        VoxeraButton(stringResource(R.string.voxera_action_close), onClick = onExit, tone = VoxeraButtonTone.Secondary, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun CenteredColumn(simulated: Boolean, content: @Composable () -> Unit) {
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
internal fun DemoChip(modifier: Modifier = Modifier) {
    StatusChip(stringResource(R.string.voxera_demo_badge), VoxeraTheme.colors.warning, modifier)
}

internal fun CallHealth.labelRes(): Int = when (this) {
    CallHealth.STABLE -> R.string.voxera_health_stable
    CallHealth.FAIR -> R.string.voxera_health_fair
    CallHealth.POOR -> R.string.voxera_health_poor
}

@Composable
internal fun CallHealth.color(): Color = when (this) {
    CallHealth.STABLE -> VoxeraTheme.colors.primary
    CallHealth.FAIR -> VoxeraTheme.colors.warning
    CallHealth.POOR -> VoxeraTheme.colors.danger
}

/** 음성 통화 이름 아래에 쓰는 연결 상태 문구. */
internal fun CallHealth.statusRes(): Int = when (this) {
    CallHealth.STABLE -> R.string.voxera_status_stable
    CallHealth.FAIR -> R.string.voxera_status_fair
    CallHealth.POOR -> R.string.voxera_status_poor
}
