package com.naury.voxerartc.invitation

import com.google.common.truth.Truth.assertThat
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.MonotonicClock
import com.naury.voxerartc.api.RtcCredentials
import com.naury.voxerartc.api.RtcCredentialsProvider
import com.naury.voxerartc.api.SessionState
import com.naury.voxerartc.api.VoxeraError
import com.naury.voxerartc.core.Voxera
import com.naury.voxerartc.core.VoxeraSession
import com.naury.voxerartc.core.VoxeraSessionConfig
import com.naury.voxerartc.testkit.FakeInvitationGateway
import com.naury.voxerartc.testkit.FakeRtcEngine
import com.naury.voxerartc.testkit.FakeRtcEngineFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test

class CallCoordinatorTest {

    private val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gateway = FakeInvitationGateway(hostScope)
    private var engineFactory = FakeRtcEngineFactory()
    private val closeables = mutableListOf<AutoCloseable>()

    private fun session(): VoxeraSession = Voxera.create(engineFactory)
        .createSession(VoxeraSessionConfig(credentials = RtcCredentialsProvider { RtcCredentials("wss://t.livekit.cloud", "t") }))
        .also { closeables += it }

    private fun coordinator(session: VoxeraSession = session(), ringTimeoutMs: Long = 45_000): CallCoordinator =
        CallCoordinator(gateway, session, CallPolicy(ringTimeoutMs), MonotonicClock.System, Dispatchers.Default)
            .also { closeables += it; it.start() }

    private suspend fun CallCoordinator.await(predicate: (CallState) -> Boolean): CallState =
        withTimeout(5_000) { state.first(predicate) }

    @After
    fun tearDown() {
        closeables.forEach { it.close() }
        hostScope.cancel()
    }

    @Test
    fun `걸려 온 통화를 받으면 해당 룸에 연결된다`() = runBlocking<Unit> {
        val session = session()
        val calls = coordinator(session)
        val call = gateway.ring("call-7", "Alex Morgan", CallMode.VIDEO)
        val ringing = calls.await { it is CallState.Ringing } as CallState.Ringing
        assertThat(ringing.call).isEqualTo(call)
        assertThat(ringing.direction).isEqualTo(CallDirection.INCOMING)

        assertThat(calls.accept()).isEqualTo(CallActionResult.APPLIED)
        calls.await { it is CallState.Active }
        assertThat(engineFactory.last.connectRequests.single().roomId).isEqualTo("room-call-7")
        assertThat(gateway.requests).contains("accept:call-7")
    }

    @Test
    fun `같은 revision의 중복 푸시와 이전 revision은 무시한다`() = runBlocking<Unit> {
        val calls = coordinator()
        val call = gateway.ring("call-1", "Alex")
        calls.await { it is CallState.Ringing }
        gateway.deliver(gateway.event("call-1", InvitationEventType.CANCELLED, revision = 1))
        gateway.deliver(gateway.event("call-1", InvitationEventType.INVITED, call, revision = 1))
        delay(200)
        assertThat((calls.state.value as CallState.Ringing).call).isEqualTo(call)
    }

    @Test
    fun `수락하려는데 서버가 이미 취소했으면 룸에 들어가지 않는다`() = runBlocking<Unit> {
        gateway.decision = { action, _ ->
            if (action == "accept") InvitationDecision.Conflict(InvitationEventType.CANCELLED, 5) else InvitationDecision.Applied(6)
        }
        val calls = coordinator()
        gateway.ring("call-1", "Alex")
        calls.await { it is CallState.Ringing }
        calls.accept()
        assertThat(calls.await { it is CallState.Ended }).isEqualTo(CallState.Ended(calls.callInfo(), CallEndReason.MISSED))
        assertThat(engineFactory.created).isEmpty()
    }

    @Test
    fun `연결 뒤 더 큰 revision의 취소가 오면 룸에서 나간다`() = runBlocking<Unit> {
        val session = session()
        val calls = coordinator(session)
        gateway.ring("call-1", "Alex")
        calls.await { it is CallState.Ringing }
        calls.accept()
        calls.await { it is CallState.Active }
        gateway.deliver(gateway.event("call-1", InvitationEventType.CANCELLED, revision = 99))
        calls.await { it is CallState.Ended }
        withTimeout(5_000) { session.state.first { it is SessionState.Ended } }
        assertThat(engineFactory.last.isReleased).isTrue()
    }

    @Test
    fun `통화 중에 새 초대가 오면 BUSY로 거절한다`() = runBlocking<Unit> {
        val session = session()
        val calls = coordinator(session)
        session.join("other-room", CallMode.VOICE)
        gateway.ring("call-2", "Taylor")
        withTimeout(5_000) { while ("decline:call-2:BUSY" !in gateway.requests) delay(20) }
        assertThat(calls.state.value).isEqualTo(CallState.Idle)
    }

    @Test
    fun `발신 후 상대가 받으면 연결되고 거절하면 DECLINED`() = runBlocking<Unit> {
        gateway.answer = FakeInvitationGateway.Answer(InvitationEventType.ACCEPTED, delayMs = 50)
        val calls = coordinator()
        calls.call(listOf("user-taylor"), CallMode.VOICE)
        calls.await { it is CallState.Active }
        calls.hangup()
        assertThat(calls.state.value).isInstanceOf(CallState.Ended::class.java)
        assertThat(gateway.requests).containsAtLeast("initiate", "hangup:call-1").inOrder()

        gateway.answer = FakeInvitationGateway.Answer(InvitationEventType.DECLINED, delayMs = 50)
        calls.call(listOf("user-taylor"), CallMode.VOICE)
        assertThat((calls.await { it is CallState.Ended && it.reason == CallEndReason.DECLINED } as CallState.Ended).reason)
            .isEqualTo(CallEndReason.DECLINED)
    }

    @Test
    fun `initiate가 돌아오기 전에 도착한 수락도 반영한다`() = runBlocking<Unit> {
        gateway.initiateResult = { request ->
            val call = CallInfo("call-9", "room-9", request.mode, "u", "U")
            gateway.deliver(gateway.event("call-9", InvitationEventType.ACCEPTED, revision = 2))
            InviteResult.Created(call, revision = 1)
        }
        val calls = coordinator()
        calls.call(listOf("u"), CallMode.VOICE)
        calls.await { it is CallState.Active }
    }

    @Test
    fun `수락 뒤 미디어 연결이 실패하면 FAILED로 끝내고 상대에게 알린다`() = runBlocking<Unit> {
        engineFactory = FakeRtcEngineFactory { FakeRtcEngine().apply { connectResults.add(VoxeraError.ServerUnreachable) } }
        val calls = coordinator()
        gateway.ring("call-3", "Jamie")
        calls.await { it is CallState.Ringing }
        calls.accept()
        assertThat((calls.await { it is CallState.Ended } as CallState.Ended).reason).isEqualTo(CallEndReason.FAILED)
        withTimeout(5_000) { while ("hangup:call-3" !in gateway.requests) delay(20) }
    }

    @Test
    fun `받지 않으면 제한 시간 뒤 부재중으로 끝내고 거절을 알린다`() = runBlocking<Unit> {
        val calls = coordinator(ringTimeoutMs = 150)
        gateway.ring("call-4", "Riley")
        assertThat((calls.await { it is CallState.Ended } as CallState.Ended).reason).isEqualTo(CallEndReason.MISSED)
        withTimeout(5_000) { while ("decline:call-4:TIMEOUT" !in gateway.requests) delay(20) }
    }

    @Test
    fun `다른 기기에서 받으면 ANSWERED_ELSEWHERE`() = runBlocking<Unit> {
        val calls = coordinator()
        gateway.ring("call-5", "Casey")
        calls.await { it is CallState.Ringing }
        gateway.deliver(gateway.event("call-5", InvitationEventType.ACCEPTED))
        assertThat((calls.await { it is CallState.Ended } as CallState.Ended).reason).isEqualTo(CallEndReason.ANSWERED_ELSEWHERE)
        assertThat(engineFactory.created).isEmpty()
    }

    @Test
    fun `연결된 통화를 상대가 끝내면 COMPLETED로 끝나고 룸에서 나간다`() = runBlocking<Unit> {
        val session = session()
        val calls = coordinator(session)
        gateway.ring("call-6", "Jordan")
        calls.await { it is CallState.Ringing }
        calls.accept()
        calls.await { it is CallState.Active }
        gateway.deliver(gateway.event("call-6", InvitationEventType.ENDED))
        assertThat((calls.await { it is CallState.Ended } as CallState.Ended).reason).isEqualTo(CallEndReason.COMPLETED)
        withTimeout(5_000) { session.state.first { it is SessionState.Ended } }
    }

    private fun CallCoordinator.callInfo(): CallInfo = (state.value as CallState.Ended).call!!
}
