package com.naury.voxerartc.android

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import com.naury.voxerartc.api.AudioRouteType
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.SessionState
import com.naury.voxerartc.core.VoxeraSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 음성 통화를 휴대전화(수화부)로 들을 때 귀에 대면 화면을 끈다. 볼이 화면을 눌러 통화를 끊거나 음소거하는 일을 막는다.
 * 스피커·이어폰으로 바꾸거나 영상 통화·통화 종료가 되면 바로 놓는다. 근접 센서가 없는 기기에서는 아무것도 하지 않는다.
 *
 * `WAKE_LOCK`(설치 때 허용되는 일반 권한)은 이 모듈 매니페스트에 들어 있다. 반환한 [AutoCloseable]을 닫으면 멈춘다.
 */
public object VoxeraProximityScreenOff {

    public fun whileOnEarpiece(context: Context, session: VoxeraSession): AutoCloseable {
        val power = context.applicationContext.getSystemService(PowerManager::class.java)
        if (power == null || !power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return AutoCloseable { }
        @SuppressLint("WakelockTimeout") // 통화 동안 잡고, 조건이 바뀌거나 닫을 때 놓는다.
        val lock = power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "voxera:proximity")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            combine(session.state, session.localMedia) { state, media ->
                val voice = when (state) {
                    is SessionState.Connected -> state.mode == CallMode.VOICE
                    is SessionState.Reconnecting -> state.mode == CallMode.VOICE
                    else -> false
                }
                val earpiece = media.audioRoutes.firstOrNull { it.id == media.selectedAudioRouteId }?.type == AudioRouteType.EARPIECE
                voice && earpiece
            }.distinctUntilChanged().collect { hold -> lock.set(hold) }
        }
        return AutoCloseable {
            scope.cancel()
            lock.set(false)
        }
    }

    // 잡기와 놓기는 서로 다른 상태 변화에서 일어난다. 마지막에는 close가 반드시 놓는다.
    @SuppressLint("WakelockTimeout", "Wakelock")
    private fun PowerManager.WakeLock.set(hold: Boolean) {
        if (hold && !isHeld) acquire()
        // 귀에서 뗄 때까지 기다렸다가 화면을 켠다(바로 켜면 볼에 눌릴 수 있다).
        if (!hold && isHeld) release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
    }
}
