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
import com.naury.voxerartc.testkit.DemoScenario
import com.naury.voxerartc.testkit.VoxeraDemo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 진행 중인 통화와 BYO 입력값. 화면 회전으로 Activity가 다시 만들어져도 세션과 통화 상태를 유지한다.
 *
 * 입력값(토큰 포함)은 이 ViewModel 메모리에만 있으며 SavedState나 파일에 저장하지 않는다. 프로세스가 끝나면 사라진다.
 */
class ShowcaseViewModel(application: Application) : AndroidViewModel(application) {

    data class ActiveCall(val session: VoxeraSession, val roomId: String, val mode: CallMode)

    private val _active = MutableStateFlow<ActiveCall?>(null)
    val active: StateFlow<ActiveCall?> = _active.asStateFlow()

    private val _form = MutableStateFlow(ConnectForm())
    val form: StateFlow<ConnectForm> = _form.asStateFlow()

    private val liveSdk by lazy { Voxera.create(LiveKitRtcEngineFactory(application)) }

    fun updateForm(form: ConnectForm) {
        _form.value = form
    }

    fun startDemo(scenario: DemoScenario) {
        start(ActiveCall(VoxeraDemo.createFakeSession(scenario), VoxeraDemo.ROOM_ID, scenario.mode))
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
        _active.value?.session?.close()
        _active.value = null
    }

    private fun start(call: ActiveCall) {
        _active.value?.session?.close()
        _active.value = call
        join(call)
    }

    private fun join(call: ActiveCall) {
        viewModelScope.launch { call.session.join(call.roomId, call.mode) }
    }

    override fun onCleared() {
        _active.value?.session?.close()
    }
}
