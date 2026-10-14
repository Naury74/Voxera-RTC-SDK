package com.naury.voxerartc.livekit

import com.naury.voxerartc.api.MetricDirection
import com.naury.voxerartc.api.MetricKind
import com.naury.voxerartc.api.MetricSource
import com.naury.voxerartc.api.MetricUnit
import com.naury.voxerartc.api.QualityMetric
import com.naury.voxerartc.api.QualitySnapshot

/** WebRTC 통계 항목 하나(`type`과 `members`). LiveKit/WebRTC 타입에 묶이지 않도록 옮겨 담는다. */
internal data class StatEntry(val id: String, val type: String, val members: Map<String, Any?>) {
    fun number(name: String): Double? = (members[name] as? Number)?.toDouble()
    fun text(name: String): String? = members[name] as? String
}

/**
 * 송신(publisher)·수신(subscriber) WebRTC 통계로 [QualitySnapshot]을 만든다. 누적 값(바이트·패킷)은 직전 표본과의 차이로
 * 비율을 계산하므로 첫 표본의 비트레이트·손실률은 측정 불가(`null`)다. 통계에 없는 값도 0이 아니라 `null`로 둔다.
 */
internal class QualitySampler {
    private var previousAtMs: Long? = null
    private var previousSent: Double? = null
    private var previousReceived: Double? = null
    private var previousLost: Double? = null
    private var previousPackets: Double? = null

    fun sample(atMs: Long, publisher: List<StatEntry>, subscriber: List<StatEntry>): QualitySnapshot {
        val rtt = (publisher + subscriber).selectedPairRttMs()
        val inbound = subscriber.filter { it.type == "inbound-rtp" }
        val outbound = publisher.filter { it.type == "outbound-rtp" }
        val jitterMs = inbound.mapNotNull { it.number("jitter") }.maxOrNull()?.times(1000)
        val fps = inbound.filter { it.text("kind") == "video" }.mapNotNull { it.number("framesPerSecond") }.maxOrNull()
        val height = inbound.filter { it.text("kind") == "video" }.mapNotNull { it.number("frameHeight") }.maxOrNull()

        val sent = outbound.sumOrNull("bytesSent")
        val received = inbound.sumOrNull("bytesReceived")
        val lost = inbound.sumOrNull("packetsLost")
        val packets = inbound.sumOrNull("packetsReceived")

        val elapsedSeconds = previousAtMs?.let { (atMs - it) / 1000.0 }?.takeIf { it > 0 }
        fun rate(now: Double?, before: Double?): Double? =
            if (now == null || before == null || elapsedSeconds == null || now < before) null else (now - before) * 8 / elapsedSeconds
        val lossPercent = if (lost != null && packets != null && previousLost != null && previousPackets != null) {
            val lostDelta = lost - previousLost!!
            val totalDelta = lostDelta + (packets - previousPackets!!)
            if (totalDelta > 0 && lostDelta >= 0) lostDelta / totalDelta * 100 else if (totalDelta == 0.0) null else 0.0
        } else null

        val snapshot = QualitySnapshot(
            timestampMs = atMs,
            source = MetricSource.ENGINE,
            metrics = listOf(
                QualityMetric(MetricKind.ROUND_TRIP_TIME, MetricDirection.NONE, rtt, MetricUnit.MILLISECONDS),
                QualityMetric(MetricKind.JITTER, MetricDirection.RECEIVE, jitterMs, MetricUnit.MILLISECONDS),
                QualityMetric(MetricKind.PACKET_LOSS, MetricDirection.RECEIVE, lossPercent, MetricUnit.PERCENT),
                QualityMetric(MetricKind.BITRATE, MetricDirection.SEND, rate(sent, previousSent), MetricUnit.BITS_PER_SECOND),
                QualityMetric(MetricKind.BITRATE, MetricDirection.RECEIVE, rate(received, previousReceived), MetricUnit.BITS_PER_SECOND),
                QualityMetric(MetricKind.FRAME_RATE, MetricDirection.RECEIVE, fps, MetricUnit.FRAMES_PER_SECOND),
                QualityMetric(MetricKind.RESOLUTION_HEIGHT, MetricDirection.RECEIVE, height, MetricUnit.PIXELS),
            ),
        )
        previousAtMs = atMs
        previousSent = sent
        previousReceived = received
        previousLost = lost
        previousPackets = packets
        return snapshot
    }

    /** 실제로 쓰이는 ICE 후보 쌍의 현재 RTT(ms). 브라우저·WebRTC 버전에 따라 `selected`/`nominated`로 표시된다. */
    private fun List<StatEntry>.selectedPairRttMs(): Double? {
        val transportPairIds = filter { it.type == "transport" }.mapNotNull { it.text("selectedCandidatePairId") }.toSet()
        return filter { it.type == "candidate-pair" }
            .filter { it.id in transportPairIds || (it.text("state") == "succeeded" && it.members["nominated"] == true) }
            .mapNotNull { it.number("currentRoundTripTime") }
            .minOrNull()
            ?.times(1000)
    }

    private fun List<StatEntry>.sumOrNull(name: String): Double? =
        mapNotNull { it.number(name) }.takeIf { it.isNotEmpty() }?.sum()
}
