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
 * 통화가 연결되어 있는 동안(재연결 중 포함) foreground service와 진행 중 알림을 띄워 둔다. 앱이 백그라운드로 가도 상대는 내 목소리를 계속 듣는다.
 *
 * - 입장 직후, 앱이 아직 앞에 있을 때 불러야 한다. Android 12+에서는 백그라운드에서 foreground service를 시작할 수 없다.
 * - Android 13+에서는 `POST_NOTIFICATIONS` 권한이 있어야 알림이 보인다. 권한은 호스트가 요청하며, 권한이 없어도 서비스는 동작한다.
 * - 카메라를 켜거나 끄면 서비스 타입도 그에 맞게 바꾼다.
 *
 * - 알림에는 통화 시간과 종료 버튼이 표시된다. 종료 버튼은 [onHangUp]을 부른다(기본은 `session.leave()`).
 * - 작은 아이콘은 Voxera 마크이고, 재연결·보류·화면 공유·마이크 꺼짐·영상 통화일 때는 오른쪽 아래에 배지가 붙고 본문도 그 상황을 알린다.
 *   호스트 앱이 같은 이름의 drawable(`voxera_ic_notification`, `voxera_ic_notification_muted` 등)을 두면 그 아이콘을 쓴다.
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
                    Spec(0, 0L, CallNotificationStatus.ACTIVE)
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
                        callNotificationStatus(reconnecting = state is SessionState.Reconnecting, media = media),
                    )
                }
            }.distinctUntilChanged { old, new ->
                old.types == new.types && (old.types == 0 || (old.status == new.status && abs(old.startedAt - new.startedAt) < 2_000))
            }.collect { spec -> if (spec.types == 0) stop(appContext) else start(appContext, spec, title) }
        }
        return AutoCloseable {
            scope.cancel()
            runCatching { appContext.unregisterReceiver(receiver) }
            stop(appContext)
        }
    }

    private data class Spec(val types: Int, val startedAt: Long, val status: CallNotificationStatus)

    private fun start(context: Context, spec: Spec, title: String) {
        val intent = Intent(context, VoxeraCallService::class.java)
            .putExtra(VoxeraCallService.EXTRA_TYPES, spec.types)
            .putExtra(VoxeraCallService.EXTRA_TITLE, title)
            .putExtra(VoxeraCallService.EXTRA_STARTED_AT, spec.startedAt)
            .putExtra(VoxeraCallService.EXTRA_STATUS, spec.status.name)
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
