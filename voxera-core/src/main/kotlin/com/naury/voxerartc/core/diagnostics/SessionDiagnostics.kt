package com.naury.voxerartc.core.diagnostics

import com.naury.voxerartc.api.MetricDirection
import com.naury.voxerartc.api.MetricKind
import com.naury.voxerartc.api.MetricSource
import com.naury.voxerartc.api.MonotonicClock
import com.naury.voxerartc.api.QualitySnapshot
import com.naury.voxerartc.api.SessionState
import com.naury.voxerartc.core.VoxeraSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.Locale

/**
 * 진단 리포트를 만들 때 호스트가 넘기는 기기·앱 정보. 사용자 식별자를 넣지 않는다.
 *
 * @property appVersion 호스트 앱 버전 이름.
 * @property os 예: "Android 16 (API 36)".
 * @property device 예: 제조사·모델. 넣고 싶지 않으면 빈 문자열.
 */
public data class DiagnosticContext(val appVersion: String, val os: String, val device: String)

/** 리포트의 상태 기록. [code]는 실패일 때의 `VoxeraError.code`다. */
public data class DiagnosticEvent(val atMs: Long, val state: String, val code: String? = null)

/** 품질 표본 하나에서 고른 값. 값이 없으면 `null`로 둔다. */
public data class DiagnosticQuality(
    val atMs: Long,
    val source: MetricSource,
    val rttMs: Double?,
    val jitterMs: Double?,
    val lossPercent: Double?,
    val sendBitrate: Double?,
    val receiveBitrate: Double?,
    val receiveFps: Double?,
)

/**
 * 통화 진단 리포트. 토큰, 서버 주소, 참가자 이름·id, 미디어는 담지 않는다. 룸 이름은 SHA-256 앞 12자리로만 남긴다.
 *
 * 시간 값은 첫 기록을 0으로 한 상대 ms다.
 */
public data class DiagnosticBundle(
    val sdkVersion: String,
    val context: DiagnosticContext,
    val roomHash: String?,
    val events: List<DiagnosticEvent>,
    val quality: List<DiagnosticQuality>,
) {
    /** 사람이 읽을 수 있는 JSON. 외부 라이브러리 없이 만든다. */
    public fun toJson(): String = buildString {
        append("{\n")
        append("  \"format\": 1,\n")
        append("  \"sdkVersion\": ").append(sdkVersion.json()).append(",\n")
        append("  \"appVersion\": ").append(context.appVersion.json()).append(",\n")
        append("  \"os\": ").append(context.os.json()).append(",\n")
        append("  \"device\": ").append(context.device.json()).append(",\n")
        append("  \"roomHash\": ").append(roomHash?.json() ?: "null").append(",\n")
        append("  \"events\": [")
        events.forEachIndexed { index, event ->
            if (index > 0) append(",")
            append("\n    {\"atMs\": ").append(event.atMs).append(", \"state\": ").append(event.state.json())
            append(", \"code\": ").append(event.code?.json() ?: "null").append("}")
        }
        append(if (events.isEmpty()) "],\n" else "\n  ],\n")
        append("  \"quality\": [")
        quality.forEachIndexed { index, sample ->
            if (index > 0) append(",")
            append("\n    {\"atMs\": ").append(sample.atMs)
            append(", \"source\": ").append(sample.source.name.json())
            append(", \"rttMs\": ").append(sample.rttMs.num())
            append(", \"jitterMs\": ").append(sample.jitterMs.num())
            append(", \"lossPercent\": ").append(sample.lossPercent.num())
            append(", \"sendBps\": ").append(sample.sendBitrate.num())
            append(", \"receiveBps\": ").append(sample.receiveBitrate.num())
            append(", \"receiveFps\": ").append(sample.receiveFps.num())
            append("}")
        }
        append(if (quality.isEmpty()) "]\n" else "\n  ]\n")
        append("}\n")
    }
}

/** 리포트를 호스트가 정한 곳으로 보낸다. 기본으로 SDK는 네트워크로 보내지 않는다. 사용자 동의는 호스트가 받는다. */
public fun interface DiagnosticExporter {
    public suspend fun export(bundle: DiagnosticBundle): ExportResult
}

public sealed interface ExportResult {
    public data object Exported : ExportResult

    public data class Failed(val reason: String) : ExportResult
}

/**
 * 세션의 상태 전이와 품질 표본을 메모리에 모은다. 크기는 [maxEvents]·[maxQuality]로 제한하며 오래된 것부터 버린다.
 * 디스크에 쓰지 않으며 [snapshot]을 불렀을 때만 리포트가 만들어진다.
 */
public class SessionDiagnostics(
    private val session: VoxeraSession,
    private val clock: MonotonicClock = session.clock,
    private val maxEvents: Int = 500,
    private val maxQuality: Int = 300,
) : AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val events = ArrayDeque<DiagnosticEvent>()
    private val quality = ArrayDeque<DiagnosticQuality>()
    private var originMs: Long? = null
    private var roomId: String? = null

    /** 기록을 시작한다. 한 번만 부른다. */
    public fun start() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { session.state.collect(::record) }
        scope.launch(start = CoroutineStart.UNDISPATCHED) { session.diagnostics.collect(::record) }
    }

    private fun relative(atMs: Long): Long = synchronized(lock) {
        val origin = originMs ?: atMs.also { originMs = it }
        atMs - origin
    }

    private fun record(state: SessionState) {
        val now = relative(clock.nowMs())
        val event = when (state) {
            SessionState.Idle -> DiagnosticEvent(now, "Idle")
            is SessionState.Preparing -> DiagnosticEvent(now, "Preparing").also { synchronized(lock) { roomId = state.roomId } }
            is SessionState.Connecting -> DiagnosticEvent(now, "Connecting")
            is SessionState.Connected -> DiagnosticEvent(now, "Connected")
            is SessionState.Reconnecting -> DiagnosticEvent(now, "Reconnecting")
            is SessionState.Ending -> DiagnosticEvent(now, "Ending", state.reason.name)
            is SessionState.Ended -> DiagnosticEvent(now, "Ended", state.reason.name)
            is SessionState.Failed -> DiagnosticEvent(now, "Failed", state.error.code)
        }
        synchronized(lock) {
            events.addLast(event)
            while (events.size > maxEvents) events.removeFirst()
        }
    }

    private fun record(snapshot: QualitySnapshot) {
        val sample = DiagnosticQuality(
            atMs = relative(snapshot.timestampMs),
            source = snapshot.source,
            rttMs = snapshot.valueOf(MetricKind.ROUND_TRIP_TIME, MetricDirection.NONE),
            jitterMs = snapshot.valueOf(MetricKind.JITTER, MetricDirection.RECEIVE),
            lossPercent = snapshot.valueOf(MetricKind.PACKET_LOSS, MetricDirection.RECEIVE),
            sendBitrate = snapshot.valueOf(MetricKind.BITRATE, MetricDirection.SEND),
            receiveBitrate = snapshot.valueOf(MetricKind.BITRATE, MetricDirection.RECEIVE),
            receiveFps = snapshot.valueOf(MetricKind.FRAME_RATE, MetricDirection.RECEIVE),
        )
        synchronized(lock) {
            quality.addLast(sample)
            while (quality.size > maxQuality) quality.removeFirst()
        }
    }

    /** 지금까지 모은 기록으로 리포트를 만든다. */
    public fun snapshot(context: DiagnosticContext, sdkVersion: String): DiagnosticBundle = synchronized(lock) {
        DiagnosticBundle(sdkVersion, context, roomId?.let(::hashRoom), events.toList(), quality.toList())
    }

    override fun close() {
        scope.cancel()
    }
}

/** 룸 이름의 SHA-256 앞 12자리(16진수). 같은 룸 리포트끼리 비교할 수 있지만 이름은 드러나지 않는다. */
internal fun hashRoom(roomId: String): String =
    MessageDigest.getInstance("SHA-256").digest(roomId.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)

private fun String.json(): String = buildString {
    append('"')
    for (c in this@json) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append(String.format(Locale.ROOT, "\\u%04x", c.code)) else append(c)
        }
    }
    append('"')
}

private fun Double?.num(): String = this?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "null"
