package com.naury.voxerartc.core

import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.EndReason
import com.naury.voxerartc.api.SessionState
import com.naury.voxerartc.api.VoxeraError

/**
 * 세션 상태와 현재 연결 세대(generation). 세대는 입장할 때마다 1씩 늘어나며, 이전 세대의 콜백은 모두 무시된다.
 *
 * [connectedAtMs]·[endedAtMs]는 종료 화면의 통화 시간 계산에만 쓴다.
 */
internal data class SessionMachine(
    val generation: Long = 0,
    val state: SessionState = SessionState.Idle,
    val connectedAtMs: Long? = null,
    val endedAtMs: Long? = null,
) {
    val durationMs: Long? get() = connectedAtMs?.let { start -> endedAtMs?.let { it - start } }
}

/** 사용자 입력·엔진 이벤트·타이머를 하나로 합친 입력. 세대가 붙은 입력은 현재 세대와 같을 때만 반영된다. */
internal sealed interface SessionAction {
    data class JoinRequested(val roomId: String, val mode: CallMode) : SessionAction
    data class CredentialsReady(val generation: Long) : SessionAction
    data class Connected(val generation: Long, val atMs: Long) : SessionAction
    data class Reconnecting(val generation: Long, val atMs: Long, val deadlineMs: Long) : SessionAction
    data class Reconnected(val generation: Long) : SessionAction
    data class ReconnectDeadlinePassed(val generation: Long, val atMs: Long) : SessionAction

    /** 연결 실패 또는 엔진 쪽 종료. 자원 정리가 끝난 뒤에 보낸다. */
    data class Failed(val generation: Long, val error: VoxeraError, val atMs: Long) : SessionAction
    data class RemoteEnded(val generation: Long, val atMs: Long) : SessionAction
    data class LeaveRequested(val reason: EndReason, val atMs: Long) : SessionAction
    data class Released(val generation: Long, val atMs: Long) : SessionAction
}

/** [SessionReducer.reduce] 결과. [accepted]가 `false`면 상태는 그대로이고 호출자는 늦게 도착한 결과를 정리해야 한다. */
internal data class Transition(val machine: SessionMachine, val accepted: Boolean)

/**
 * 순수 상태 전이 함수. 부수 효과는 [DefaultVoxeraSession]이 전이가 받아들여졌을 때만 실행한다.
 *
 * - 종료 중·종료 후에 도착한 연결 성공은 받아들이지 않는다(연결 중 나가기 경쟁).
 * - 실패(Failed)나 종료(Ended) 뒤에만 새 세대로 다시 입장할 수 있다.
 */
internal object SessionReducer {

    fun reduce(machine: SessionMachine, action: SessionAction): Transition {
        val state = machine.state
        fun move(next: SessionState) = Transition(machine.copy(state = next), accepted = true)
        fun end(reason: EndReason, atMs: Long) =
            Transition(machine.copy(state = SessionState.Ending(reason), endedAtMs = atMs), accepted = true)
        val ignore = Transition(machine, accepted = false)
        fun current(generation: Long) = generation == machine.generation

        return when (action) {
            is SessionAction.JoinRequested -> when (state) {
                SessionState.Idle, is SessionState.Ended, is SessionState.Failed -> Transition(
                    SessionMachine(machine.generation + 1, SessionState.Preparing(action.roomId, action.mode)),
                    accepted = true,
                )
                else -> ignore
            }

            is SessionAction.CredentialsReady ->
                if (current(action.generation) && state is SessionState.Preparing) {
                    move(SessionState.Connecting(state.roomId, state.mode))
                } else ignore

            is SessionAction.Connected ->
                if (current(action.generation) && state is SessionState.Connecting) {
                    Transition(
                        machine.copy(state = SessionState.Connected(state.roomId, state.mode, action.atMs), connectedAtMs = action.atMs),
                        accepted = true,
                    )
                } else ignore

            is SessionAction.Reconnecting ->
                if (current(action.generation) && state is SessionState.Connected) {
                    move(SessionState.Reconnecting(state.roomId, state.mode, state.connectedAtMs, action.atMs, action.deadlineMs))
                } else ignore

            is SessionAction.Reconnected ->
                if (current(action.generation) && state is SessionState.Reconnecting) {
                    move(SessionState.Connected(state.roomId, state.mode, state.connectedAtMs))
                } else ignore

            is SessionAction.ReconnectDeadlinePassed ->
                if (current(action.generation) && state is SessionState.Reconnecting && action.atMs >= state.deadlineMs) {
                    end(EndReason.AFTER_FAILURE, action.atMs)
                } else ignore

            is SessionAction.Failed ->
                if (current(action.generation) && state.isActive()) {
                    val ended = machine.copy(endedAtMs = action.atMs)
                    Transition(ended.copy(state = SessionState.Failed(action.error, ended.durationMs)), accepted = true)
                } else if (current(action.generation) && state is SessionState.Ending && state.reason == EndReason.AFTER_FAILURE) {
                    // 재연결 제한 시간이 지나 정리하던 중이면 실패로 마무리한다.
                    move(SessionState.Failed(action.error, machine.durationMs))
                } else ignore

            is SessionAction.RemoteEnded ->
                if (current(action.generation) && state.isActive()) {
                    end(EndReason.REMOTE_ENDED, action.atMs)
                } else ignore

            is SessionAction.LeaveRequested -> when (state) {
                is SessionState.Preparing, is SessionState.Connecting -> end(EndReason.CANCELLED, action.atMs)
                is SessionState.Connected, is SessionState.Reconnecting -> end(action.reason, action.atMs)
                is SessionState.Failed -> move(SessionState.Ended(EndReason.AFTER_FAILURE, state.durationMs))
                else -> ignore
            }

            is SessionAction.Released ->
                if (current(action.generation) && state is SessionState.Ending) {
                    move(SessionState.Ended(state.reason, machine.durationMs))
                } else ignore
        }
    }

    private fun SessionState.isActive(): Boolean = when (this) {
        is SessionState.Preparing, is SessionState.Connecting, is SessionState.Connected, is SessionState.Reconnecting -> true
        else -> false
    }
}
