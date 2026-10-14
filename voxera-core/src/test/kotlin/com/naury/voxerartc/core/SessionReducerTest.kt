package com.naury.voxerartc.core

import com.google.common.truth.Truth.assertThat
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.EndReason
import com.naury.voxerartc.api.SessionState
import com.naury.voxerartc.api.VoxeraError
import org.junit.Test

class SessionReducerTest {

    private fun SessionMachine.on(action: SessionAction): SessionMachine = SessionReducer.reduce(this, action).machine
    private fun SessionMachine.accepts(action: SessionAction): Boolean = SessionReducer.reduce(this, action).accepted

    private fun connected(): SessionMachine = SessionMachine()
        .on(SessionAction.JoinRequested("room", CallMode.VOICE))
        .on(SessionAction.CredentialsReady(1))
        .on(SessionAction.Connected(1, atMs = 100))

    @Test
    fun `입장부터 종료까지 정상 전이와 통화 시간`() {
        val joined = SessionMachine().on(SessionAction.JoinRequested("room", CallMode.VOICE))
        assertThat(joined.generation).isEqualTo(1)
        assertThat(joined.state).isEqualTo(SessionState.Preparing("room", CallMode.VOICE))

        val live = connected()
        assertThat(live.state).isEqualTo(SessionState.Connected("room", CallMode.VOICE, 100))

        val ending = live.on(SessionAction.LeaveRequested(EndReason.USER_LEFT, atMs = 4_100))
        assertThat(ending.state).isEqualTo(SessionState.Ending(EndReason.USER_LEFT))
        assertThat(ending.on(SessionAction.Released(1, 4_200)).state).isEqualTo(SessionState.Ended(EndReason.USER_LEFT, 4_000))
    }

    @Test
    fun `통화 중 두 번째 입장 요청은 거부한다`() {
        assertThat(connected().accepts(SessionAction.JoinRequested("other", CallMode.VIDEO))).isFalse()
    }

    @Test
    fun `연결 중 나가면 늦게 도착한 연결 성공을 버린다`() {
        val ending = SessionMachine()
            .on(SessionAction.JoinRequested("room", CallMode.VOICE))
            .on(SessionAction.CredentialsReady(1))
            .on(SessionAction.LeaveRequested(EndReason.USER_LEFT, 50))
        assertThat(ending.state).isEqualTo(SessionState.Ending(EndReason.CANCELLED))
        assertThat(ending.accepts(SessionAction.Connected(1, 60))).isFalse()
        val ended = ending.on(SessionAction.Released(1, 70))
        assertThat(ended.state).isEqualTo(SessionState.Ended(EndReason.CANCELLED, null))
        assertThat(ended.accepts(SessionAction.Connected(1, 80))).isFalse()
    }

    @Test
    fun `이전 세대의 이벤트는 새 세대에 영향을 주지 않는다`() {
        val second = connected()
            .on(SessionAction.LeaveRequested(EndReason.USER_LEFT, 200))
            .on(SessionAction.Released(1, 210))
            .on(SessionAction.JoinRequested("room", CallMode.VOICE))
        assertThat(second.generation).isEqualTo(2)
        assertThat(second.accepts(SessionAction.CredentialsReady(1))).isFalse()
        assertThat(second.accepts(SessionAction.Failed(1, VoxeraError.NetworkUnavailable, 220))).isFalse()
        assertThat(second.accepts(SessionAction.CredentialsReady(2))).isTrue()
    }

    @Test
    fun `재연결은 복구되거나 제한 시간 뒤 실패한다`() {
        val reconnecting = connected().on(SessionAction.Reconnecting(1, atMs = 1_000, deadlineMs = 31_000))
        assertThat(reconnecting.state).isEqualTo(SessionState.Reconnecting("room", CallMode.VOICE, 100, 1_000, 31_000))
        assertThat(reconnecting.on(SessionAction.Reconnected(1)).state).isEqualTo(SessionState.Connected("room", CallMode.VOICE, 100))

        assertThat(reconnecting.accepts(SessionAction.ReconnectDeadlinePassed(1, 30_999))).isFalse()
        val ending = reconnecting.on(SessionAction.ReconnectDeadlinePassed(1, 31_000))
        assertThat(ending.state).isEqualTo(SessionState.Ending(EndReason.AFTER_FAILURE))
        assertThat(ending.accepts(SessionAction.Reconnected(1))).isFalse()
        assertThat(ending.on(SessionAction.Failed(1, VoxeraError.NetworkUnavailable, 31_100)).state)
            .isEqualTo(SessionState.Failed(VoxeraError.NetworkUnavailable, 30_900))
    }

    @Test
    fun `실패 뒤 나가기는 바로 종료되고 다시 입장할 수 있다`() {
        val failed = SessionMachine()
            .on(SessionAction.JoinRequested("room", CallMode.VOICE))
            .on(SessionAction.Failed(1, VoxeraError.ServerUnreachable, 10))
        assertThat(failed.state).isEqualTo(SessionState.Failed(VoxeraError.ServerUnreachable, null))
        assertThat(failed.on(SessionAction.LeaveRequested(EndReason.USER_LEFT, 20)).state)
            .isEqualTo(SessionState.Ended(EndReason.AFTER_FAILURE, null))
        assertThat(failed.accepts(SessionAction.JoinRequested("room", CallMode.VOICE))).isTrue()
    }

    @Test
    fun `종료 후 나가기와 중복 해제는 무시한다`() {
        val ended = connected()
            .on(SessionAction.LeaveRequested(EndReason.USER_LEFT, 200))
            .on(SessionAction.Released(1, 210))
        assertThat(ended.accepts(SessionAction.LeaveRequested(EndReason.USER_LEFT, 300))).isFalse()
        assertThat(ended.accepts(SessionAction.Released(1, 310))).isFalse()
        assertThat(ended.accepts(SessionAction.RemoteEnded(1, 320))).isFalse()
    }

    @Test
    fun `상대가 끝내면 REMOTE_ENDED로 종료한다`() {
        val ended = connected().on(SessionAction.RemoteEnded(1, 600)).on(SessionAction.Released(1, 610))
        assertThat(ended.state).isEqualTo(SessionState.Ended(EndReason.REMOTE_ENDED, 500))
    }
}
