package com.naury.voxerartc.android

import android.content.Context
import android.net.Uri
import android.telecom.DisconnectCause
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallsManager
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.SessionState
import com.naury.voxerartc.core.VoxeraSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 통화를 Android 통화 시스템(Telecom)에 알린다. 알리면 시스템이 이 통화를 다른 전화와 함께 관리한다.
 *
 * - 휴대전화로 전화가 와서 사용자가 받으면 시스템이 이 통화를 보류한다. 보류 동안 마이크를 끄고, 그 전화가 끝나면 통화를 이어 가며
 *   마이크를 원래대로 켠다(오디오를 돌려받았는지는 [com.naury.voxerartc.api.LocalMediaState.audioInterrupted]로 안다).
 * - 차량·시계 같은 장치에서 통화를 끊으면 [onHangUp]을 부른다.
 * - 통화가 연결될 때 등록하고, 끝나거나 실패하면 시스템에서도 끝낸다.
 *
 * Android 8(API 26) 이상에서 동작한다. 기기가 지원하지 않거나 등록에 실패하면 아무 일도 하지 않으며 통화에는 영향이 없다.
 * `MANAGE_OWN_CALLS`(설치 때 허용되는 일반 권한)는 이 모듈 매니페스트에 들어 있다. 통화 상대 이름은 [title]만 쓰고
 * 전화번호나 토큰은 시스템에 넘기지 않는다.
 *
 * 반환한 [AutoCloseable]을 닫으면 멈춘다.
 */
public object VoxeraTelecomCall {

    @JvmOverloads
    public fun registerWhileConnected(
        context: Context,
        session: VoxeraSession,
        title: String,
        onHangUp: (() -> Unit)? = null,
    ): AutoCloseable {
        val appContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val manager = runCatching {
            CallsManager(appContext).also {
                it.registerAppWithTelecom(CallsManager.CAPABILITY_BASELINE or CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING)
            }
        }.getOrNull() ?: return AutoCloseable { }
        val hangUp: () -> Unit = onHangUp ?: { scope.launch { session.leave() } }
        scope.launch {
            // 연결될 때마다(다시 입장 포함) 한 번씩 등록한다.
            session.state
                .map { (it as? SessionState.Connected)?.let { connected -> connected.connectedAtMs to connected.mode } }
                .distinctUntilChanged()
                .collect { call -> if (call != null) launch { report(manager, session, call.second, title, hangUp) } }
        }
        return AutoCloseable { scope.cancel() }
    }

    private suspend fun report(manager: CallsManager, session: VoxeraSession, mode: CallMode, title: String, hangUp: () -> Unit) {
        val attributes = CallAttributesCompat(
            displayName = title,
            // 시스템에 넘기는 주소는 식별 정보가 없는 고정 값이다.
            address = Uri.fromParts("voxera", "call", null),
            direction = CallAttributesCompat.DIRECTION_OUTGOING,
            callType = if (mode == CallMode.VIDEO) CallAttributesCompat.CALL_TYPE_VIDEO_CALL else CallAttributesCompat.CALL_TYPE_AUDIO_CALL,
            callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE,
        )
        var micBeforeHold: Boolean? = null
        // 보류가 풀리면 보류 전에 켜져 있던 마이크만 다시 켠다.
        suspend fun resume() {
            if (micBeforeHold == true) session.setMicrophoneEnabled(true)
            micBeforeHold = null
        }
        try {
            manager.addCall(
                attributes,
                onAnswer = { },
                onDisconnect = { hangUp() },
                onSetActive = { resume() },
                onSetInactive = {
                    if (micBeforeHold == null) micBeforeHold = session.localMedia.value.microphone.desired
                    session.setMicrophoneEnabled(false)
                },
            ) {
                launch { setActive() }
                // 시스템은 보류한 VoIP 통화를 다시 이어 주지 않는다. 다른 통화가 끝나면(오디오를 돌려받으면) 앱이 직접 이어 간다.
                launch {
                    session.localMedia.map { it.audioInterrupted }.distinctUntilChanged().collect { interrupted ->
                        if (!interrupted && micBeforeHold != null) {
                            setActive()
                            resume()
                        }
                    }
                }
                launch {
                    session.state.first { it !is SessionState.Connected && it !is SessionState.Reconnecting }
                    disconnect(DisconnectCause(DisconnectCause.LOCAL))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 기기가 지원하지 않거나 시스템이 통화를 받아들이지 않음. 통화 자체는 그대로 이어진다.
        }
    }
}
