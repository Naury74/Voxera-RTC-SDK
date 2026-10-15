package com.naury.voxerartc.api

/**
 * 세션의 연결 수명 상태. `Idle -> Preparing -> Connecting -> Connected <-> Reconnecting -> Ending -> Ended`,
 * 실패하면 [Failed]로 간다. 시간 값은 [MonotonicClock] 기준 ms다.
 */
public sealed interface SessionState {
    /** 아직 입장하지 않음. */
    public data object Idle : SessionState

    /** 자격 증명을 받는 중. */
    public data class Preparing(val roomId: String, val mode: CallMode) : SessionState

    /** 서버에 연결하는 중. */
    public data class Connecting(val roomId: String, val mode: CallMode) : SessionState

    /** 연결됨. [connectedAtMs]부터 통화 시간을 잰다. */
    public data class Connected(val roomId: String, val mode: CallMode, val connectedAtMs: Long) : SessionState

    /** 연결이 끊겨 엔진이 복구하는 중. [sinceMs]부터 [deadlineMs]까지 복구되지 않으면 [Failed]로 바뀐다. */
    public data class Reconnecting(
        val roomId: String,
        val mode: CallMode,
        val connectedAtMs: Long,
        val sinceMs: Long,
        val deadlineMs: Long,
    ) : SessionState

    /** 자원을 정리하는 중. 이 상태에서 들어오는 엔진 이벤트는 무시한다. */
    public data class Ending(val reason: EndReason) : SessionState

    /** 정상 종료. 통화한 시간이 있으면 [durationMs]에 담긴다. */
    public data class Ended(val reason: EndReason, val durationMs: Long?) : SessionState

    /** 실패했다. 자원은 이미 정리되었으며 [error]는 사용자 안내에 쓴다. 다시 `join`하거나 나갈 수 있다. */
    public data class Failed(val error: VoxeraError, val durationMs: Long?) : SessionState
}

/** 통화가 끝난 이유. */
public enum class EndReason {
    USER_LEFT,
    HOST_LEFT,

    /** 연결되기 전에 취소함. */
    CANCELLED,

    /** 서버나 다른 참가자가 끝냄. */
    REMOTE_ENDED,

    /** 실패 상태에서 나감. */
    AFTER_FAILURE,
}

/** 단조 증가 시계(ms). 벽시계 변경에 영향을 받지 않아야 한다. */
public fun interface MonotonicClock {
    public fun nowMs(): Long

    public companion object {
        /** JVM 단조 시계. Android의 `SystemClock.elapsedRealtime`과 기준점은 다르지만 두 시점 사이의 차이는 똑같이 계산된다. */
        public val System: MonotonicClock = MonotonicClock { java.lang.System.nanoTime() / 1_000_000 }
    }
}
