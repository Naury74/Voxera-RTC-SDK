package com.naury.voxerartc.showcase

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.RtcCredentialsProvider
import com.naury.voxerartc.core.MediaPreferences
import com.naury.voxerartc.core.Voxera
import com.naury.voxerartc.core.VoxeraSession
import com.naury.voxerartc.core.VoxeraSessionConfig
import com.naury.voxerartc.livekit.LiveKitRtcEngineFactory
import com.naury.voxerartc.livekit.LiveKitTokenSources
import com.naury.voxerartc.livekit.compose.asComposeRenderer
import com.naury.voxerartc.ui.video.VoxeraVideoRenderer
import com.naury.voxerartc.invitation.CallCoordinator
import com.naury.voxerartc.invitation.CallInfo
import com.naury.voxerartc.invitation.InvitationEventType
import com.naury.voxerartc.invitation.InviteResult
import com.naury.voxerartc.testkit.DemoScenario
import com.naury.voxerartc.testkit.FakeInvitationGateway
import com.naury.voxerartc.testkit.VoxeraDemo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 진행 중인 통화와 BYO 입력값. 화면 회전으로 Activity가 다시 만들어져도 세션과 통화 상태를 유지한다.
 *
 * 입력값(토큰 포함)은 이 ViewModel 메모리에만 있으며 SavedState나 파일에 저장하지 않는다. 프로세스가 끝나면 사라진다.
 */
class ShowcaseViewModel(application: Application) : AndroidViewModel(application) {

    /** [coordinator]가 있으면 Call Mode(초대 신호 + 룸 미디어)이고, 입장은 coordinator가 정한다. */
    data class ActiveCall(val session: VoxeraSession, val roomId: String, val mode: CallMode, val coordinator: CallCoordinator? = null)

    private val _active = MutableStateFlow<ActiveCall?>(null)
    val active: StateFlow<ActiveCall?> = _active.asStateFlow()

    private val _form = MutableStateFlow(ConnectForm())
    val form: StateFlow<ConnectForm> = _form.asStateFlow()

    private val liveEngines by lazy { LiveKitRtcEngineFactory(application) }
    private val liveSdk by lazy { Voxera.create(liveEngines) }

    /** 실제 통화 영상 렌더러. 데모 세션은 UI가 자체 가짜 영상을 쓴다. */
    val liveVideoRenderer: VoxeraVideoRenderer by lazy { liveEngines.videoTracks.asComposeRenderer() }

    fun updateForm(form: ConnectForm) {
        _form.value = form
    }

    fun startDemo(scenario: DemoScenario) {
        start(ActiveCall(VoxeraDemo.createFakeSession(scenario), VoxeraDemo.ROOM_ID, scenario.mode))
    }

    /**
     * 가짜 호스트 gateway로 Call Mode를 시연한다. [incoming]이면 잠시 뒤 전화가 걸려 오고, 아니면 바로 걸어 상대가 3초 뒤 받는다.
     * 실제 앱에서는 호스트 서버·푸시가 이 gateway를 구현한다.
     */
    fun startDemoCall(incoming: Boolean) {
        val session = VoxeraDemo.createFakeSession(DemoScenario.VIDEO_ONE_ON_ONE)
        val gateway = FakeInvitationGateway(viewModelScope)
        val coordinator = CallCoordinator(gateway, session).also { it.start() }
        replace(ActiveCall(session, VoxeraDemo.ROOM_ID, CallMode.VIDEO, coordinator))
        viewModelScope.launch {
            if (incoming) {
                delay(1_500)
                gateway.ring("demo-call", "Alex Morgan", CallMode.VIDEO)
            } else {
                gateway.answer = FakeInvitationGateway.Answer(InvitationEventType.ACCEPTED, delayMs = 3_000)
                gateway.initiateResult = { request ->
                    InviteResult.Created(CallInfo("demo-call", "demo-room", request.mode, "alex", "Alex Morgan"), gateway.nextRevision())
                }
                coordinator.call(listOf("alex"), CallMode.VIDEO)
            }
        }
    }

    /** 입력값으로 실제 LiveKit 룸에 들어간다. 권한 요청은 호출하기 전에 화면이 마친다. */
    fun startLive() {
        val form = _form.value
        val roomId = form.roomName.trim()
        val credentials: RtcCredentialsProvider = when (form.method) {
            CredentialMethod.DEV_TOKEN_SERVER -> LiveKitTokenSources.developmentTokenServer(form.tokenServerId, form.displayName.trim())
            CredentialMethod.EXISTING_TOKEN -> LiveKitTokenSources.literal(form.serverUrl, form.accessToken)
            CredentialMethod.TOKEN_ENDPOINT -> LiveKitTokenSources.endpoint(form.endpointUrl, form.displayName.trim())
        }
        val session = liveSdk.createSession(
            VoxeraSessionConfig(
                credentials = credentials,
                media = MediaPreferences(startWithMicrophone = true, startWithCamera = form.mode == CallMode.VIDEO),
                serverUrlPolicy = form.serverUrlPolicy,
            ),
        )
        start(ActiveCall(session, roomId, form.mode))
    }

    /** 같은 세션으로 다시 입장한다. 끝난 세션은 새 세대로 연결된다. */
    fun rejoin() {
        _active.value?.let(::join)
    }

    fun exit() {
        replace(null)
    }

    private fun start(call: ActiveCall) {
        replace(call)
        join(call)
    }

    private fun replace(call: ActiveCall?) {
        _active.value?.let { previous ->
            previous.coordinator?.close()
            previous.session.close()
        }
        _active.value = call
    }

    private fun join(call: ActiveCall) {
        viewModelScope.launch { call.session.join(call.roomId, call.mode) }
    }

    override fun onCleared() {
        replace(null)
    }
}
