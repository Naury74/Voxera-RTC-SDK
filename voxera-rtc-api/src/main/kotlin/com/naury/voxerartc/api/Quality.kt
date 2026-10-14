package com.naury.voxerartc.api

/** 측정 방향. 송신과 수신을 섞지 않는다. */
public enum class MetricDirection { SEND, RECEIVE, NONE }

/** 측정 항목. 단위는 [unit]으로 함께 준다. */
public enum class MetricKind { ROUND_TRIP_TIME, JITTER, PACKET_LOSS, BITRATE, FRAME_RATE, RESOLUTION_HEIGHT }

/** 측정 단위. */
public enum class MetricUnit { MILLISECONDS, PERCENT, BITS_PER_SECOND, FRAMES_PER_SECOND, PIXELS }

/** 값을 얻은 출처. 오프라인 데모 값은 [SIMULATED]로 표시해 실제 측정치로 오해하지 않게 한다. */
public enum class MetricSource { ENGINE, SIMULATED }

/**
 * 측정값 하나. 엔진이 값을 주지 않으면 [value]는 `null`이며 0으로 바꾸지 않는다.
 */
public data class QualityMetric(
    val kind: MetricKind,
    val direction: MetricDirection,
    val value: Double?,
    val unit: MetricUnit,
) {
    val isAvailable: Boolean get() = value != null
}

/** 한 시점의 품질 표본. [timestampMs]는 [MonotonicClock] 기준이다. */
public data class QualitySnapshot(
    val timestampMs: Long,
    val source: MetricSource,
    val metrics: List<QualityMetric>,
) {
    /** 해당 항목·방향의 값. 없거나 측정되지 않았으면 `null`. */
    public fun valueOf(kind: MetricKind, direction: MetricDirection): Double? =
        metrics.firstOrNull { it.kind == kind && it.direction == direction }?.value
}
