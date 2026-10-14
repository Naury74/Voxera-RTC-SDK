package com.naury.voxerartc.core

import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.JoinResult
import com.naury.voxerartc.api.LeaveReason
import com.naury.voxerartc.api.LocalMediaState
import com.naury.voxerartc.api.MediaActionResult
import com.naury.voxerartc.api.MonotonicClock
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.api.QualitySnapshot
import com.naury.voxerartc.api.RtcEngineKind
import com.naury.voxerartc.api.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 룸 하나의 미디어 연결과 참가자를 관리한다. 한 번에 하나의 연결만 가지며, 끝난 뒤 다시 `join`할 수 있다.
 *
 * 모든 상태는 [StateFlow]로 관찰한다. suspend 함수는 main-safe이고 어느 스레드에서 불러도 된다.
 * 다 쓰면 [close]를 불러 코루틴·엔진·리스너를 해제한다.
 */
public interface VoxeraSession : AutoCloseable {
    /** 엔진 종류. [RtcEngineKind.SIMULATED]면 UI는 실제 통화가 아님을 표시해야 한다. */
    public val engineKind: RtcEngineKind

    /** 통화 시간 계산에 쓰는 시계. [SessionState.Connected.connectedAtMs]와 같은 기준이다. */
    public val clock: MonotonicClock

    public val state: StateFlow<SessionState>

    /** 로컬 참가자가 먼저 오고, 나머지는 들어온 순서. 연결되지 않으면 비어 있다. */
    public val participants: StateFlow<List<Participant>>

    public val localMedia: StateFlow<LocalMediaState>

    /** 엔진이 보내는 품질 표본. 마지막 값 하나를 다시 보내 준다. */
    public val diagnostics: Flow<QualitySnapshot>

    /**
     * 룸에 들어간다. 연결이 끝나거나 실패할 때까지 suspend된다.
     *
     * - 이미 연결 중이거나 같은 SDK의 다른 세션이 통화 중이면 [com.naury.voxerartc.api.VoxeraError.ConcurrentSession].
     * - 기다리는 동안 [leave]하면 [com.naury.voxerartc.api.VoxeraError.UserCancelled]를 돌려주고, 늦게 도착한 연결 성공은 버린다.
     * - 호출한 코루틴이 취소되면 입장도 취소된다.
     *
     * @param media `null`이면 [VoxeraSessionConfig.media]를 쓴다.
     */
    public suspend fun join(roomId: String, mode: CallMode, media: MediaPreferences? = null): JoinResult

    /** 마이크를 켜거나 끈다. 빠르게 여러 번 불러도 마지막 요청이 최종 상태가 된다. 연결 전에는 NotConnected로 실패한다. */
    public suspend fun setMicrophoneEnabled(enabled: Boolean): MediaActionResult

    public suspend fun setCameraEnabled(enabled: Boolean): MediaActionResult

    public suspend fun switchCamera(): MediaActionResult

    /** [LocalMediaState.audioRoutes]의 id 중 하나를 고른다. */
    public suspend fun selectAudioRoute(routeId: String): MediaActionResult

    /** 통화를 끝내고 자원을 정리한다. 어떤 상태에서 불러도 안전하며 여러 번 불러도 된다. */
    public suspend fun leave(reason: LeaveReason = LeaveReason.USER)

    /** 진행 중인 통화를 끝내고 세션을 닫는다. 닫힌 세션의 `join`은 실패한다. */
    override fun close()
}
