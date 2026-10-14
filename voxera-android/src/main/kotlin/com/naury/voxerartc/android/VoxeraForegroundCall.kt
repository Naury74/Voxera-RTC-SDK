package com.naury.voxerartc.android

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.naury.voxerartc.api.SessionState
import com.naury.voxerartc.core.VoxeraSession
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 통화가 연결된 동안(재연결 포함) foreground service와 진행 중 알림을 유지한다. 앱이 백그라운드로 가도 상대에게 내 소리가 계속 간다.
 *
 * - 사용자가 입장한 직후(앱이 앞에 있을 때) 불러야 한다. Android 12+는 백그라운드에서 foreground service를 시작할 수 없다.
 * - 알림은 Android 13+에서 `POST_NOTIFICATIONS`가 있어야 보인다. 없어도 서비스는 동작한다(호스트가 요청한다).
 * - 카메라를 켜고 끄면 서비스 타입을 맞춰 바꾼다.
 *
 * - 알림에는 통화 경과 시간과 종료 버튼이 있다. 종료 버튼은 [onHangUp]을 부른다(기본은 `session.leave()`).
 *
 * 반환한 [AutoCloseable]을 닫거나 통화가 끝나면 서비스를 멈춘다.
 *
 * @param title 알림 제목(예: 앱 이름이나 상대 이름). 토큰 같은 비밀 값을 넣지 않는다.
 * @param onHangUp 알림의 종료 버튼. Call Mode면 `CallCoordinator.hangup()`을 넘긴다.
 */
public object VoxeraForegroundCall {

    @JvmOverloads
    public fun keepWhileConnected(
        context: Context,
        session: VoxeraSession,
        title: String,
        onHangUp: (() -> Unit)? = null,
    ): AutoCloseable {
        val appContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val hangUp: () -> Unit = onHangUp ?: { scope.launch { session.leave() } }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                hangUp()
            }
        }
        ContextCompat.registerReceiver(appContext, receiver, IntentFilter(VoxeraCallService.hangUpAction(appContext)), ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            combine(session.state, session.localMedia) { state, media ->
                val connectedAt = when (state) {
                    is SessionState.Connected -> state.connectedAtMs
                    is SessionState.Reconnecting -> state.connectedAtMs
                    else -> null
                }
                if (connectedAt == null) {
                    Spec(0, 0L)
                } else {
                    // 세션 시계(단조 시계) 기준 시작 시각을 알림이 쓰는 벽시계 시각으로 바꾼다.
                    val startedAt = System.currentTimeMillis() - (session.clock.nowMs() - connectedAt)
                    Spec(
                        callServiceTypes(
                            microphoneGranted = appContext.isGranted(Manifest.permission.RECORD_AUDIO),
                            cameraGranted = appContext.isGranted(Manifest.permission.CAMERA),
                            cameraActive = media.camera.actual || media.camera.desired,
                        ),
                        startedAt,
                    )
                }
            }.distinctUntilChanged { old, new -> old.types == new.types && (old.types == 0 || abs(old.startedAt - new.startedAt) < 2_000) }
                .collect { spec -> if (spec.types == 0) stop(appContext) else start(appContext, spec.types, title, spec.startedAt) }
        }
        return AutoCloseable {
            scope.cancel()
            runCatching { appContext.unregisterReceiver(receiver) }
            stop(appContext)
        }
    }

    private data class Spec(val types: Int, val startedAt: Long)

    private fun start(context: Context, types: Int, title: String, startedAt: Long) {
        val intent = Intent(context, VoxeraCallService::class.java)
            .putExtra(VoxeraCallService.EXTRA_TYPES, types)
            .putExtra(VoxeraCallService.EXTRA_TITLE, title)
            .putExtra(VoxeraCallService.EXTRA_STARTED_AT, startedAt)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (_: RuntimeException) {
            // 앱이 백그라운드라 시작할 수 없음(Android 12+). 다음 상태 변화나 앱 복귀 때 다시 시도된다.
        }
    }

    private fun stop(context: Context) {
        context.stopService(Intent(context, VoxeraCallService::class.java))
    }

    private fun Context.isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
