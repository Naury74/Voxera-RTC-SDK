package com.naury.voxerartc.core

import com.google.common.truth.Truth.assertThat
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.CameraFacing
import com.naury.voxerartc.api.ConfigurationProblem
import com.naury.voxerartc.api.CredentialReason
import com.naury.voxerartc.api.CredentialRequest
import com.naury.voxerartc.api.CredentialsUnavailableException
import com.naury.voxerartc.api.EndReason
import com.naury.voxerartc.api.JoinResult
import com.naury.voxerartc.api.MediaActionResult
import com.naury.voxerartc.api.MediaDevice
import com.naury.voxerartc.api.MonotonicClock
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.api.RtcCredentials
import com.naury.voxerartc.api.RtcCredentialsProvider
import com.naury.voxerartc.api.SessionState
import com.naury.voxerartc.api.VoxeraError
import com.naury.voxerartc.testkit.FakeRtcEngine
import com.naury.voxerartc.testkit.FakeRtcEngineFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultVoxeraSessionTest {

    private val requests = mutableListOf<CredentialRequest>()
    private val provider = RtcCredentialsProvider { request ->
        requests += request
        RtcCredentials("wss://test.livekit.cloud/", "token-${requests.size}")
    }

    private fun TestScope.sdk(factory: FakeRtcEngineFactory = FakeRtcEngineFactory()): VoxeraSdk =
        Voxera.create(factory, MonotonicClock { testScheduler.currentTime }, StandardTestDispatcher(testScheduler))

    private fun config(
        credentials: RtcCredentialsProvider = provider,
        policy: SessionPolicy = SessionPolicy(),
    ) = VoxeraSessionConfig(credentials = credentials, policy = policy)

    @Test
    fun `가짜 엔진으로 연결부터 종료까지 상태와 참가자가 바뀐다`() = runTest {
        val factory = FakeRtcEngineFactory { FakeRtcEngine(connectDelayMs = 500) }
        val session = sdk(factory).createSession(config())
        val states = mutableListOf<SessionState>()
        val observer = launch { session.state.collect { states += it } }

        val join = async { session.join("team-room", CallMode.VOICE) }
        runCurrent()
        assertThat(session.state.value).isEqualTo(SessionState.Connecting("team-room", CallMode.VOICE))
        advanceUntilIdle()
        assertThat(join.await()).isInstanceOf(JoinResult.Connected::class.java)

        factory.last.upsertRemote(Participant("alex", "Alex", isLocal = false))
        advanceUntilIdle()
        assertThat(session.participants.value.map { it.id }).containsExactly("local", "alex").inOrder()
        assertThat(factory.last.receivedCredentials.single().serverUrl).isEqualTo("wss://test.livekit.cloud")

        advanceTimeBy(3_000)
        session.leave()
        advanceUntilIdle()
        assertThat(session.state.value).isEqualTo(SessionState.Ended(EndReason.USER_LEFT, 3_000))
        assertThat(session.participants.value).isEmpty()
        assertThat(factory.last.releaseCount).isEqualTo(1)
        // StateFlow는 같은 순간의 Ending → Ended를 합칠 수 있다.
        assertThat(states.map { it::class.simpleName })
            .containsAtLeast("Idle", "Preparing", "Connecting", "Connected", "Ended").inOrder()
        observer.cancel()
    }

    @Test
    fun `연결 중 나가면 늦은 성공을 버리고 자원을 해제한다`() = runTest {
        val factory = FakeRtcEngineFactory { FakeRtcEngine(connectDelayMs = 5_000) }
        val session = sdk(factory).createSession(config())
        val join = async { session.join("room", CallMode.VIDEO) }
        advanceTimeBy(1_000)
        session.leave()
        advanceUntilIdle()

        assertThat(join.await()).isEqualTo(JoinResult.Rejected(VoxeraError.UserCancelled))
        assertThat(session.state.value).isEqualTo(SessionState.Ended(EndReason.CANCELLED, null))
        assertThat(factory.last.isReleased).isTrue()
        assertThat(session.participants.value).isEmpty()
    }

    @Test
    fun `토큰 요청 중 나가면 엔진을 만들지 않는다`() = runTest {
        val gate = CompletableDeferred<RtcCredentials>()
        val factory = FakeRtcEngineFactory()
        val session = sdk(factory).createSession(config(credentials = { gate.await() }))
        val join = async { session.join("room", CallMode.VOICE) }
        runCurrent()
        session.leave()
        gate.complete(RtcCredentials("wss://late.livekit.cloud", "late"))
        advanceUntilIdle()

        assertThat(join.await()).isEqualTo(JoinResult.Rejected(VoxeraError.UserCancelled))
        assertThat(factory.created).isEmpty()
    }

    @Test
    fun `호출한 코루틴이 취소되면 입장도 취소하고 정리한다`() = runTest {
        val factory = FakeRtcEngineFactory { FakeRtcEngine().apply { hangOnConnect = true } }
        val session = sdk(factory).createSession(config())
        val caller = launch { session.join("room", CallMode.VOICE) }
        advanceTimeBy(1_000)
        caller.cancel()
        advanceUntilIdle()

        assertThat(session.state.value).isEqualTo(SessionState.Ended(EndReason.CANCELLED, null))
        assertThat(factory.last.isReleased).isTrue()
    }

    @Test
    fun `같은 SDK의 두 번째 세션은 통화 중 입장할 수 없다`() = runTest {
        val sdk = sdk()
        val first = sdk.createSession(config())
        val second = sdk.createSession(config())
        first.join("a", CallMode.VOICE)
        assertThat(second.join("b", CallMode.VOICE)).isEqualTo(JoinResult.Rejected(VoxeraError.ConcurrentSession))
        assertThat(first.join("a", CallMode.VOICE)).isEqualTo(JoinResult.Rejected(VoxeraError.ConcurrentSession))

        first.leave()
        advanceUntilIdle()
        assertThat(second.join("b", CallMode.VOICE)).isInstanceOf(JoinResult.Connected::class.java)
    }

    @Test
    fun `만료된 토큰은 새 토큰으로 한 번만 다시 연결한다`() = runTest {
        val factory = FakeRtcEngineFactory {
            FakeRtcEngine().apply { connectResults.addAll(listOf(VoxeraError.AuthenticationExpired, null)) }
        }
        val session = sdk(factory).createSession(config())
        assertThat(session.join("room", CallMode.VOICE)).isInstanceOf(JoinResult.Connected::class.java)
        assertThat(requests.map { it.reason }).containsExactly(CredentialReason.INITIAL_JOIN, CredentialReason.REAUTHENTICATE).inOrder()
        assertThat(factory.last.receivedCredentials.map { it.accessToken }).containsExactly("token-1", "token-2").inOrder()
    }

    @Test
    fun `새 토큰도 만료되면 AuthenticationExpired로 실패한다`() = runTest {
        val factory = FakeRtcEngineFactory {
            FakeRtcEngine().apply { connectResults.addAll(listOf(VoxeraError.AuthenticationExpired, VoxeraError.AuthenticationExpired)) }
        }
        val session = sdk(factory).createSession(config())
        assertThat(session.join("room", CallMode.VOICE)).isEqualTo(JoinResult.Rejected(VoxeraError.AuthenticationExpired))
        assertThat(session.state.value).isEqualTo(SessionState.Failed(VoxeraError.AuthenticationExpired, null))
        assertThat(factory.last.isReleased).isTrue()
        assertThat(requests).hasSize(2)
    }

    @Test
    fun `호스트 인증 실패는 다시 시도하지 않는다`() = runTest {
        var calls = 0
        val session = sdk().createSession(config(credentials = {
            calls++
            throw CredentialsUnavailableException(CredentialsUnavailableException.Kind.UNAUTHORIZED)
        }))
        assertThat(session.join("room", CallMode.VOICE)).isEqualTo(JoinResult.Rejected(VoxeraError.HostUnauthorized))
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `토큰 서버 거절은 설정 오류로 알리고 다시 시도하지 않는다`() = runTest {
        var calls = 0
        val session = sdk().createSession(config(credentials = {
            calls++
            throw CredentialsUnavailableException(CredentialsUnavailableException.Kind.REJECTED)
        }))
        assertThat(session.join("room", CallMode.VOICE))
            .isEqualTo(JoinResult.Rejected(VoxeraError.InvalidConfiguration(ConfigurationProblem.TOKEN_SOURCE_REJECTED)))
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `일시적 오류는 정해진 횟수만 늘어나는 간격으로 다시 시도한다`() = runTest {
        val attemptsAt = mutableListOf<Long>()
        val session = sdk().createSession(config(
            credentials = {
                attemptsAt += testScheduler.currentTime
                throw CredentialsUnavailableException(CredentialsUnavailableException.Kind.TRANSIENT)
            },
            policy = SessionPolicy(credentialAttempts = 3, credentialRetryDelayMs = 100),
        ))
        assertThat(session.join("room", CallMode.VOICE)).isEqualTo(JoinResult.Rejected(VoxeraError.ServerUnreachable))
        assertThat(attemptsAt).containsExactly(0L, 100L, 300L).inOrder()
    }

    @Test
    fun `공급자가 준 잘못된 주소와 빈 토큰은 연결하지 않는다`() = runTest {
        val factory = FakeRtcEngineFactory()
        val insecure = sdk(factory).createSession(config(credentials = { RtcCredentials("ws://remote.example.com", "t") }))
        assertThat(insecure.join("room", CallMode.VOICE))
            .isEqualTo(JoinResult.Rejected(VoxeraError.InvalidConfiguration(ConfigurationProblem.SERVER_URL_INSECURE)))

        val blank = sdk(factory).createSession(config(credentials = { RtcCredentials("wss://a.livekit.cloud", " ") }))
        assertThat(blank.join("room", CallMode.VOICE))
            .isEqualTo(JoinResult.Rejected(VoxeraError.InvalidConfiguration(ConfigurationProblem.TOKEN_BLANK)))
        assertThat(factory.created).isEmpty()
    }

    @Test
    fun `잘못된 룸 이름은 자격 증명을 요청하기 전에 거부한다`() = runTest {
        val session = sdk().createSession(config())
        assertThat(session.join("bad room", CallMode.VOICE))
            .isEqualTo(JoinResult.Rejected(VoxeraError.InvalidConfiguration(ConfigurationProblem.ROOM_NAME_INVALID)))
        assertThat(requests).isEmpty()
        assertThat(session.state.value).isEqualTo(SessionState.Idle)
    }

    @Test
    fun `연결 시간 초과는 ServerUnreachable로 실패한다`() = runTest {
        val factory = FakeRtcEngineFactory { FakeRtcEngine().apply { hangOnConnect = true } }
        val session = sdk(factory).createSession(config(policy = SessionPolicy(connectTimeoutMs = 2_000)))
        assertThat(session.join("room", CallMode.VOICE)).isEqualTo(JoinResult.Rejected(VoxeraError.ServerUnreachable))
        assertThat(factory.last.isReleased).isTrue()
    }

    @Test
    fun `재연결되면 통화 시작 시각을 유지한다`() = runTest {
        val factory = FakeRtcEngineFactory()
        val session = sdk(factory).createSession(config())
        session.join("room", CallMode.VOICE)
        advanceTimeBy(1_000)
        factory.last.simulateReconnecting()
        runCurrent()
        assertThat(session.state.value).isInstanceOf(SessionState.Reconnecting::class.java)
        factory.last.simulateReconnected()
        advanceUntilIdle()
        assertThat(session.state.value).isEqualTo(SessionState.Connected("room", CallMode.VOICE, 0))
        assertThat(factory.last.isReleased).isFalse()
    }

    @Test
    fun `재연결 제한 시간을 넘기면 실패하고 다른 세션이 들어갈 수 있다`() = runTest {
        val factory = FakeRtcEngineFactory()
        val sdk = sdk(factory)
        val session = sdk.createSession(config(policy = SessionPolicy(reconnectDeadlineMs = 30_000)))
        session.join("room", CallMode.VOICE)
        advanceTimeBy(5_000)
        factory.last.simulateReconnecting()
        advanceTimeBy(29_999)
        runCurrent()
        assertThat(session.state.value).isInstanceOf(SessionState.Reconnecting::class.java)
        advanceUntilIdle()

        assertThat(session.state.value).isEqualTo(SessionState.Failed(VoxeraError.NetworkUnavailable, 35_000))
        assertThat(factory.last.isReleased).isTrue()
        assertThat(sdk.createSession(config()).join("other", CallMode.VOICE)).isInstanceOf(JoinResult.Connected::class.java)
    }

    @Test
    fun `상대가 끝내면 REMOTE_ENDED로 정리한다`() = runTest {
        val factory = FakeRtcEngineFactory()
        val session = sdk(factory).createSession(config())
        session.join("room", CallMode.VOICE)
        advanceTimeBy(2_000)
        factory.last.simulateDisconnect(error = null)
        advanceUntilIdle()
        assertThat(session.state.value).isEqualTo(SessionState.Ended(EndReason.REMOTE_ENDED, 2_000))
        assertThat(factory.last.isReleased).isTrue()
    }

    @Test
    fun `통화가 끝난 뒤 도착한 엔진 이벤트는 무시한다`() = runTest {
        val factory = FakeRtcEngineFactory()
        val session = sdk(factory).createSession(config())
        session.join("room", CallMode.VOICE)
        val engine = factory.last
        session.leave()
        advanceUntilIdle()

        engine.upsertRemote(Participant("ghost", "Ghost", isLocal = false))
        engine.simulateReconnecting()
        engine.simulateDisconnect(VoxeraError.NetworkUnavailable)
        advanceUntilIdle()
        assertThat(session.participants.value).isEmpty()
        assertThat(session.state.value).isEqualTo(SessionState.Ended(EndReason.USER_LEFT, 0))
    }

    @Test
    fun `빠르게 열 번 눌러도 마지막 의도와 엔진 상태가 같다`() = runTest {
        val factory = FakeRtcEngineFactory { FakeRtcEngine(mediaAckDelayMs = 50) }
        val session = sdk(factory).createSession(config())
        session.join("room", CallMode.VOICE)
        repeat(10) { index -> launch { session.setMicrophoneEnabled(index % 2 == 0) } }
        runCurrent()
        assertThat(session.localMedia.value.microphone.desired).isFalse()
        assertThat(session.localMedia.value.microphone.isPending).isTrue()
        advanceUntilIdle()

        val mic = session.localMedia.value.microphone
        assertThat(mic.desired).isFalse()
        assertThat(mic.actual).isFalse()
        assertThat(factory.last.microphoneRequests.last()).isFalse()
        assertThat(factory.last.microphoneRequests.size).isLessThan(10)
        assertThat(session.participants.value.first { it.isLocal }.isMicrophoneEnabled).isFalse()
    }

    @Test
    fun `장치 실패는 실제 상태를 유지하고 오류를 남긴다`() = runTest {
        val factory = FakeRtcEngineFactory()
        val session = sdk(factory).createSession(config())
        session.join("room", CallMode.VIDEO, MediaPreferences(startWithMicrophone = true, startWithCamera = false))
        factory.last.failNextMediaAction(VoxeraError.MediaDeviceUnavailable(MediaDevice.CAMERA))

        val result = session.setCameraEnabled(true)
        assertThat(result).isEqualTo(MediaActionResult.Failed(VoxeraError.MediaDeviceUnavailable(MediaDevice.CAMERA)))
        val camera = session.localMedia.value.camera
        assertThat(camera.actual).isFalse()
        assertThat(camera.isPending).isFalse()
        assertThat(camera.lastError).isEqualTo(VoxeraError.MediaDeviceUnavailable(MediaDevice.CAMERA))
        assertThat(session.state.value).isInstanceOf(SessionState.Connected::class.java)
    }

    @Test
    fun `연결 전 미디어 조작은 NotConnected`() = runTest {
        val session = sdk().createSession(config())
        assertThat(session.setMicrophoneEnabled(true)).isEqualTo(MediaActionResult.Failed(VoxeraError.NotConnected))
    }

    @Test
    fun `카메라 전환과 오디오 경로 선택은 엔진 상태를 따른다`() = runTest {
        val factory = FakeRtcEngineFactory { FakeRtcEngine(cameras = listOf(CameraFacing.FRONT)) }
        val session = sdk(factory).createSession(config())
        session.join("room", CallMode.VIDEO)
        advanceUntilIdle()

        assertThat(session.switchCamera()).isEqualTo(MediaActionResult.Unsupported)
        assertThat(session.selectAudioRoute("speaker")).isEqualTo(MediaActionResult.Applied)
        advanceUntilIdle()
        assertThat(session.localMedia.value.selectedAudioRouteId).isEqualTo("speaker")
        assertThat(session.selectAudioRoute("usb")).isEqualTo(MediaActionResult.Unsupported)
    }

    @Test
    fun `음성 모드는 선호와 상관없이 카메라를 켜지 않는다`() = runTest {
        val factory = FakeRtcEngineFactory()
        val session = sdk(factory).createSession(config())
        session.join("room", CallMode.VOICE)
        assertThat(factory.last.connectRequests.single().startWithCamera).isFalse()
        assertThat(session.localMedia.value.camera.actual).isFalse()
    }

    @Test
    fun `close는 여러 번 불러도 안전하고 닫힌 세션은 입장할 수 없다`() = runTest {
        val factory = FakeRtcEngineFactory()
        val session = sdk(factory).createSession(config())
        session.join("room", CallMode.VOICE)
        session.close()
        session.close()
        advanceUntilIdle()
        assertThat(session.state.value).isEqualTo(SessionState.Ended(EndReason.HOST_LEFT, 0))
        assertThat(factory.last.releaseCount).isEqualTo(1)
        assertThat(session.join("room", CallMode.VOICE)).isEqualTo(JoinResult.Rejected(VoxeraError.InternalFailure("SESSION_CLOSED")))
    }

    @Test
    fun `실패 뒤 다시 입장하면 새 세대로 연결한다`() = runTest {
        var created = 0
        val failing = FakeRtcEngineFactory {
            FakeRtcEngine().apply { if (created++ == 0) connectResults.add(VoxeraError.ServerIncompatible) }
        }
        val retrySession = sdk(failing).createSession(config())
        assertThat(retrySession.join("room", CallMode.VOICE)).isEqualTo(JoinResult.Rejected(VoxeraError.ServerIncompatible))
        assertThat(retrySession.join("room", CallMode.VOICE)).isInstanceOf(JoinResult.Connected::class.java)
        assertThat(failing.created).hasSize(2)
        assertThat(failing.created.first().isReleased).isTrue()
    }

    @Test
    fun `화면 공유는 엔진 ack 뒤 켜지고 시스템이 멈추면 꺼진다`() = runTest {
        val factory = FakeRtcEngineFactory()
        val session = sdk(factory).createSession(config())
        session.join("room", CallMode.VIDEO)
        val consent = object : com.naury.voxerartc.api.ScreenShareConsent {}

        assertThat(session.startScreenShare(consent)).isEqualTo(MediaActionResult.Applied)
        advanceUntilIdle()
        assertThat(session.localMedia.value.screenShare.actual).isTrue()
        assertThat(session.participants.value.first { it.isLocal }.screenShare).isNotNull()

        factory.last.simulateScreenShareStopped()
        advanceUntilIdle()
        assertThat(session.localMedia.value.screenShare.actual).isFalse()
        assertThat(session.localMedia.value.screenShare.isPending).isFalse()
        assertThat(session.participants.value.first { it.isLocal }.screenShare).isNull()
    }

    @Test
    fun `연결 전 화면 공유는 NotConnected`() = runTest {
        val session = sdk().createSession(config())
        assertThat(session.startScreenShare(object : com.naury.voxerartc.api.ScreenShareConsent {}))
            .isEqualTo(MediaActionResult.Failed(VoxeraError.NotConnected))
    }
}
