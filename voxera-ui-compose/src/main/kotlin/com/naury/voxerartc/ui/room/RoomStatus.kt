package com.naury.voxerartc.ui.room

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.naury.voxerartc.ui.theme.enterOnAppear
import com.naury.voxerartc.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
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

/**
 * 연결 중(G-04). 방 아바타가 숨 쉬듯 커졌다 작아지고 뒤로 물결이 퍼진다. 아래 진행 막대는 빛이 계속 흘러가며,
 * 준비 → 연결 단계가 바뀌면 문구가 위로 밀려 바뀌고 막대도 다음 칸이 찬다. 요소들은 차례로 떠오르며 나타난다.
 */
@Composable
internal fun ConnectingContent(state: SessionState, simulated: Boolean, onCancel: () -> Unit) {
    val connecting = state is SessionState.Connecting
    val room = when (state) {
        is SessionState.Preparing -> state.roomId
        is SessionState.Connecting -> state.roomId
        else -> ""
    }
    val reduced = rememberReducedMotion()
    val breath = if (reduced) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "connecting")
        val value by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1_400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
        value
    }
    CenteredColumn(simulated) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.enterOnAppear(0, scale = true)) {
            SpeakingRipple(speaking = true, level = 0.5f, size = 128.dp)
            VoxeraAvatar(
                id = room.ifEmpty { "room" },
                name = room.ifEmpty { "?" },
                size = 128.dp,
                modifier = Modifier.graphicsLayer {
                    val sc = 1f + 0.04f * breath
                    scaleX = sc
                    scaleY = sc
                },
            )
        }
        // 물결 고리가 아바타 밖으로 퍼지므로 이름과 사이를 넉넉히 둔다.
        Spacer(Modifier.height(48.dp))
        Text(room, color = VoxeraTheme.colors.foreground, style = VoxeraTheme.typography.headline, textAlign = TextAlign.Center, modifier = Modifier.enterOnAppear(1))
        Spacer(Modifier.height(VoxeraSpacing.xs))
        AnimatedContent(
            targetState = connecting,
            transitionSpec = {
                (slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(260))) togetherWith (slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(160)))
            },
            label = "connecting-label",
            modifier = Modifier.enterOnAppear(2).semantics { liveRegion = LiveRegionMode.Polite },
        ) { isConnecting ->
            Text(
                stringResource(if (isConnecting) R.string.voxera_state_connecting else R.string.voxera_state_preparing),
                color = VoxeraTheme.colors.foregroundSecondary,
                style = VoxeraTheme.typography.body,
            )
        }
        Spacer(Modifier.height(VoxeraSpacing.md))
        StepBar(steps = 2, current = if (connecting) 1 else 0, modifier = Modifier.enterOnAppear(3))
        Spacer(Modifier.height(VoxeraSpacing.xxl))
        VoxeraButton(
            stringResource(R.string.voxera_action_cancel),
            onClick = onCancel,
            tone = VoxeraButtonTone.Secondary,
            modifier = Modifier.widthIn(min = 160.dp).enterOnAppear(4),
        )
    }
}

/**
 * 단계 진행 막대. 지난 단계는 채우고, 지금 단계 칸은 빛이 왼쪽에서 오른쪽으로 계속 흘러간다. 단계가 바뀌면 칸이 차오른다.
 * 글자로 단계가 이미 나오므로 장식으로 둔다.
 */
@Composable
private fun StepBar(steps: Int, current: Int, modifier: Modifier = Modifier) {
    val colors = VoxeraTheme.colors
    val reduced = rememberReducedMotion()
    val sweep = if (reduced) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "step")
        val value by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1_100, easing = LinearEasing)), label = "sweep")
        value
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(steps) { index ->
            val fill by animateFloatAsState(if (index < current) 1f else 0f, spring(stiffness = Spring.StiffnessLow), label = "fill")
            Canvas(Modifier.size(width = 36.dp, height = 4.dp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(colors.foreground.copy(alpha = 0.14f), cornerRadius = r)
                if (fill > 0f) drawRoundRect(colors.primary, size = Size(size.width * fill, size.height), cornerRadius = r)
                if (index == current) {
                    // 지금 단계: 빛 조각이 흘러간다.
                    val w = size.width * 0.45f
                    val x = (size.width + w) * sweep - w
                    clipRect { drawRoundRect(colors.primary, topLeft = Offset(x, 0f), size = Size(w, size.height), cornerRadius = r) }
                }
            }
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
    val reduced = rememberReducedMotion()
    CenteredColumn(simulated) {
        VoxeraIconTile(VoxeraIcons.CallEnd, colors.danger, size = 64.dp, modifier = Modifier.enterOnAppear(0, scale = true))
        Spacer(Modifier.height(VoxeraSpacing.lg))
        Text(
            stringResource(R.string.voxera_end_title),
            color = colors.foreground,
            style = VoxeraTheme.typography.headline,
            modifier = Modifier.enterOnAppear(1).semantics { heading() },
        )
        if (durationMs != null) {
            // 통화 시간은 0에서 실제 값까지 빠르게 올라간다. 읽기에는 최종 값만 쓴다.
            val shown = remember { Animatable(if (reduced) durationMs.toFloat() else 0f) }
            LaunchedEffect(durationMs) { shown.animateTo(durationMs.toFloat(), tween(700, easing = FastOutSlowInEasing)) }
            val final = formatDuration(durationMs)
            Text(
                formatDuration(shown.value.toLong()),
                color = colors.primary,
                style = VoxeraTheme.typography.display,
                modifier = Modifier.enterOnAppear(2).clearAndSetSemantics { contentDescription = final },
            )
        }
        Spacer(Modifier.height(VoxeraSpacing.xxs))
        Text(stringResource(reason.messageRes()), color = colors.foregroundSecondary, style = VoxeraTheme.typography.body, textAlign = TextAlign.Center, modifier = Modifier.enterOnAppear(3))
        val rtt = history.average(MetricKind.ROUND_TRIP_TIME, MetricDirection.NONE)
        val loss = history.average(MetricKind.PACKET_LOSS, MetricDirection.RECEIVE)
        val jitter = history.average(MetricKind.JITTER, MetricDirection.RECEIVE)
        val health = healthOf(rtt, loss, jitter)
        if (health != null) {
            Spacer(Modifier.height(VoxeraSpacing.lg))
            GlassSurface(level = GlassLevel.Low, shape = RoundedCornerShape(VoxeraRadius.xl), modifier = Modifier.fillMaxWidth().enterOnAppear(4)) {
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
        ExitActions(onExit, onRejoin, rejoinLabel = stringResource(R.string.voxera_action_rejoin), onSaveReport, firstIndex = 5)
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
    val reduced = rememberReducedMotion()
    // 나타난 뒤 아이콘이 좌우로 짧게 흔들려 문제가 생겼음을 알린다.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduced) return@LaunchedEffect
        delay(260)
        listOf(-8f, 7f, -5f, 3f, 0f).forEach { shake.animateTo(it, tween(70)) }
    }
    CenteredColumn(simulated) {
        VoxeraIconTile(
            if (dropped) VoxeraIcons.Wifi else VoxeraIcons.Shield,
            colors.warning,
            size = 64.dp,
            modifier = Modifier.enterOnAppear(0, scale = true).graphicsLayer { translationX = shake.value.dp.toPx() },
        )
        Spacer(Modifier.height(VoxeraSpacing.lg))
        Text(
            stringResource(if (dropped) R.string.voxera_dropped_title else R.string.voxera_failed_title),
            color = colors.foreground,
            style = VoxeraTheme.typography.headline,
            modifier = Modifier.enterOnAppear(1).semantics { heading() },
        )
        Spacer(Modifier.height(VoxeraSpacing.md))
        GlassSurface(level = GlassLevel.Low, shape = RoundedCornerShape(VoxeraRadius.xl), modifier = Modifier.fillMaxWidth().enterOnAppear(2)) {
            Column(verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xxs)) {
                Text(stringResource(error.messageRes()), color = colors.foreground, style = VoxeraTheme.typography.body)
                val code = if (error is VoxeraError.InternalFailure) "${error.code} · ${error.technicalCode}" else error.code
                Text(stringResource(R.string.voxera_error_code, code), color = colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
            }
        }
        Spacer(Modifier.height(VoxeraSpacing.xxl))
        ExitActions(onExit, onRejoin.takeIf { error.isRetryable }, rejoinLabel = stringResource(R.string.voxera_action_retry), onSaveReport, firstIndex = 3)
    }
}

@Composable
private fun ExitActions(onExit: () -> Unit, onRejoin: (() -> Unit)?, rejoinLabel: String, onSaveReport: (() -> Unit)?, firstIndex: Int = 0) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.sm)) {
        if (onRejoin != null) VoxeraButton(rejoinLabel, onClick = onRejoin, icon = VoxeraIcons.Refresh, modifier = Modifier.fillMaxWidth().enterOnAppear(firstIndex))
        if (onSaveReport != null) {
            VoxeraButton(stringResource(R.string.voxera_action_save_report), onClick = onSaveReport, tone = VoxeraButtonTone.Secondary, modifier = Modifier.fillMaxWidth().enterOnAppear(firstIndex + 1))
        }
        VoxeraButton(stringResource(R.string.voxera_action_close), onClick = onExit, tone = VoxeraButtonTone.Secondary, modifier = Modifier.fillMaxWidth().enterOnAppear(firstIndex + 2))
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
