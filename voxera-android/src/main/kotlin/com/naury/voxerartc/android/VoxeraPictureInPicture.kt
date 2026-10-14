package com.naury.voxerartc.android

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.PowerManager
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.SessionState
import com.naury.voxerartc.core.VoxeraSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 통화 중 PiP(화면 속 화면) 창 관리. 통화가 연결된 동안에만 PiP를 허용하고, 홈으로 나가면 자동으로 PiP에 들어간다.
 * PiP 창에는 마이크 끄기/켜기와 통화 종료 버튼을 둔다.
 *
 * - 호스트 Activity의 매니페스트에 `android:supportsPictureInPicture="true"`와, PiP 전환에 Activity가 다시 만들어지지 않도록
 *   `android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"`가 있어야 한다.
 * - Android 12+는 시스템이 홈 제스처에 맞춰 자연스럽게 들어가고(auto-enter), 그 아래 버전은 `onUserLeaveHint`에서 들어간다.
 * - PiP 창 안의 화면은 [isInPictureInPicture]를 보고 호스트가 그린다(예: `VoxeraRoomScreen(pictureInPicture = ...)`).
 * - 사용자가 PiP 창을 닫으면 [onDismissed]를 부른다. 기본은 아무것도 하지 않으며 통화는 foreground service로 이어진다.
 *
 * Activity가 없어지면 저절로 정리되며, 그 전에 그만 쓰려면 [close]를 부른다.
 */
public class VoxeraPictureInPicture private constructor(
    private val activity: ComponentActivity,
    private val session: VoxeraSession,
    private val onHangUp: () -> Unit,
    private val onDismissed: () -> Unit,
) : AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _isInPictureInPicture = MutableStateFlow(activity.isInPictureInPictureMode)
    private var spec = Spec(active = false, video = false, microphoneOn = false)
    private val actionPrefix = "${activity.packageName}.voxera.pip."

    /** 지금 PiP 창에 있는지. */
    public val isInPictureInPicture: StateFlow<Boolean> = _isInPictureInPicture.asStateFlow()

    private val modeListener = Consumer<PictureInPictureModeChangedInfo> { info -> _isInPictureInPicture.value = info.isInPictureInPictureMode }

    private val leaveHintListener = Runnable {
        // Android 11 이하는 auto-enter가 없으므로 홈으로 나갈 때 직접 들어간다.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && spec.active) enter()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                actionPrefix + ACTION_MIC -> scope.launch { session.setMicrophoneEnabled(!session.localMedia.value.microphone.desired) }
                actionPrefix + ACTION_HANG_UP -> onHangUp()
            }
        }
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            // PiP 창에 있는 채로 멈췄는데 화면이 켜져 있으면 사용자가 창을 닫은 것이다(화면이 꺼진 경우와 구분한다).
            val interactive = activity.getSystemService(PowerManager::class.java)?.isInteractive != false
            if (_isInPictureInPicture.value && interactive) onDismissed()
        }

        override fun onDestroy(owner: LifecycleOwner) = close()
    }

    private fun start() {
        activity.addOnPictureInPictureModeChangedListener(modeListener)
        activity.addOnUserLeaveHintListener(leaveHintListener)
        activity.lifecycle.addObserver(lifecycleObserver)
        val filter = IntentFilter().apply {
            addAction(actionPrefix + ACTION_MIC)
            addAction(actionPrefix + ACTION_HANG_UP)
        }
        ContextCompat.registerReceiver(activity, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            combine(session.state, session.localMedia) { state, media ->
                val mode = when (state) {
                    is SessionState.Connected -> state.mode
                    is SessionState.Reconnecting -> state.mode
                    else -> null
                }
                Spec(active = mode != null, video = mode == CallMode.VIDEO, microphoneOn = media.microphone.actual)
            }.distinctUntilChanged().collect { next ->
                spec = next
                apply(next)
            }
        }
    }

    /** 지금 바로 PiP에 들어간다. 통화 중이 아니거나 기기가 PiP를 지원하지 않으면 `false`. */
    public fun enter(): Boolean {
        if (!spec.active || !isSupported(activity)) return false
        return runCatching { activity.enterPictureInPictureMode(params(spec)) }.getOrDefault(false)
    }

    private fun apply(next: Spec) {
        if (!isSupported(activity)) return
        runCatching { activity.setPictureInPictureParams(params(next)) }
    }

    private fun params(spec: Spec): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            // 영상은 세로 3:4, 음성은 아바타 하나라 정사각형.
            .setAspectRatio(if (spec.video) Rational(3, 4) else Rational(1, 1))
            .setActions(actions(spec))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(spec.active)
            builder.setSeamlessResizeEnabled(!spec.video)
        }
        return builder.build()
    }

    private fun actions(spec: Spec): List<RemoteAction> {
        if (!spec.active) return emptyList()
        val mic = RemoteAction(
            Icon.createWithResource(activity, if (spec.microphoneOn) R.drawable.voxera_ic_pip_mic else R.drawable.voxera_ic_pip_mic_off),
            activity.getString(if (spec.microphoneOn) R.string.voxera_pip_mute else R.string.voxera_pip_unmute),
            activity.getString(if (spec.microphoneOn) R.string.voxera_pip_mute else R.string.voxera_pip_unmute),
            broadcast(ACTION_MIC, REQUEST_MIC),
        )
        val hangUp = RemoteAction(
            Icon.createWithResource(activity, R.drawable.voxera_ic_pip_call_end),
            activity.getString(R.string.voxera_pip_hang_up),
            activity.getString(R.string.voxera_pip_hang_up),
            broadcast(ACTION_HANG_UP, REQUEST_HANG_UP),
        )
        return listOf(mic, hangUp)
    }

    private fun broadcast(action: String, request: Int): PendingIntent =
        PendingIntent.getBroadcast(
            activity,
            request,
            Intent(actionPrefix + action).setPackage(activity.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private var closed = false

    override fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        activity.removeOnPictureInPictureModeChangedListener(modeListener)
        activity.removeOnUserLeaveHintListener(leaveHintListener)
        activity.lifecycle.removeObserver(lifecycleObserver)
        runCatching { activity.unregisterReceiver(receiver) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { activity.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build()) }
        }
    }

    private data class Spec(val active: Boolean, val video: Boolean, val microphoneOn: Boolean)

    public companion object {
        private const val ACTION_MIC = "MIC"
        private const val ACTION_HANG_UP = "HANG_UP"
        private const val REQUEST_MIC = 0x5601
        private const val REQUEST_HANG_UP = 0x5602

        /** 기기가 PiP를 지원하는지. 저사양 기기나 일부 Android TV·Go 기기는 지원하지 않는다. */
        public fun isSupported(context: Context): Boolean =
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

        /**
         * [activity]에 PiP를 붙인다. Activity의 `onCreate`에서 부른다.
         *
         * @param onHangUp PiP 창의 종료 버튼. 보통 `session.leave()`, Call Mode면 `CallCoordinator.hangup()`.
         * @param onDismissed 사용자가 PiP 창을 닫음. 통화를 끝낼지 이어 갈지는 호스트가 정한다.
         */
        public fun attach(
            activity: ComponentActivity,
            session: VoxeraSession,
            onHangUp: () -> Unit,
            onDismissed: () -> Unit = {},
        ): VoxeraPictureInPicture = VoxeraPictureInPicture(activity, session, onHangUp, onDismissed).also { it.start() }
    }
}
