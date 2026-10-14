package com.naury.voxerartc.ui.room

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.naury.voxerartc.api.MetricDirection
import com.naury.voxerartc.api.MetricKind
import com.naury.voxerartc.api.QualitySnapshot
import com.naury.voxerartc.core.VoxeraSession

/** 측정값에서 고른 통화 상태. 근거가 되는 값이 하나도 없으면 고르지 않는다. */
internal enum class CallHealth { STABLE, FAIR, POOR }

/**
 * 최근 품질 표본을 [capacity]개까지 들고 있는다. 통화 화면이 떠 있는 동안 모아, 품질 시트의 추이 그래프와
 * 종료 화면의 요약에 쓴다. 값은 화면 안에만 있고 저장하거나 보내지 않는다.
 */
@Stable
internal class QualityHistory(private val capacity: Int = 30) {
    var samples: List<QualitySnapshot> by mutableStateOf(emptyList())
        private set

    val latest: QualitySnapshot? get() = samples.lastOrNull()

    fun add(snapshot: QualitySnapshot) {
        samples = (samples + snapshot).takeLast(capacity)
    }

    /** [kind]/[direction] 값의 시간순 목록. 없는 표본은 건너뛴다. */
    fun series(kind: MetricKind, direction: MetricDirection): List<Double> =
        samples.mapNotNull { it.valueOf(kind, direction) }

    /** 모은 표본 전체의 평균. 값이 없으면 `null`. */
    fun average(kind: MetricKind, direction: MetricDirection): Double? =
        series(kind, direction).takeIf { it.isNotEmpty() }?.average()
}

/**
 * 왕복 시간·수신 손실·지터로 상태를 고른다. 기준(나쁨/보통): 손실 5%/2%, 왕복 400/200ms, 지터 50/30ms.
 * 세 값이 모두 없으면 `null`이며 화면은 상태 글자를 쓰지 않는다.
 */
internal fun healthOf(rttMs: Double?, lossPercent: Double?, jitterMs: Double?): CallHealth? {
    if (rttMs == null && lossPercent == null && jitterMs == null) return null
    fun over(value: Double?, limit: Double) = value != null && value > limit
    return when {
        over(lossPercent, 5.0) || over(rttMs, 400.0) || over(jitterMs, 50.0) -> CallHealth.POOR
        over(lossPercent, 2.0) || over(rttMs, 200.0) || over(jitterMs, 30.0) -> CallHealth.FAIR
        else -> CallHealth.STABLE
    }
}

internal fun QualitySnapshot.health(): CallHealth? = healthOf(
    valueOf(MetricKind.ROUND_TRIP_TIME, MetricDirection.NONE),
    valueOf(MetricKind.PACKET_LOSS, MetricDirection.RECEIVE),
    valueOf(MetricKind.JITTER, MetricDirection.RECEIVE),
)

/** 통화 화면 수명 동안 [session]의 품질 표본을 모은다. */
@Composable
internal fun rememberQualityHistory(session: VoxeraSession): QualityHistory {
    val history = remember(session) { QualityHistory() }
    LaunchedEffect(session) { session.diagnostics.collect { history.add(it) } }
    return history
}
