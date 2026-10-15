package com.naury.voxerartc.android

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.ServiceCompat
import com.naury.voxerartc.api.LocalMediaState

/**
 * 통화가 연결된 동안 앱 프로세스와 마이크·카메라 접근을 유지하는 foreground service. [VoxeraForegroundCall]만 시작·중지한다.
 *
 * 세션이나 토큰을 갖지 않으며 알림 문구와 서비스 타입만 받는다.
 */
internal class VoxeraCallService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("InlinedApi") // FOREGROUND_SERVICE_TYPE_MANIFEST는 API 29 상수이며 ServiceCompat이 29 미만에서 무시한다.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val types = intent?.getIntExtra(EXTRA_TYPES, 0) ?: 0
        if (intent == null || intent.action == ACTION_STOP || types == 0) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val startedAt = intent.getLongExtra(EXTRA_STARTED_AT, 0L)
        val status = intent.getStringExtra(EXTRA_STATUS)?.let { name -> CallNotificationStatus.entries.firstOrNull { it.name == name } } ?: CallNotificationStatus.ACTIVE
        // microphone·camera 타입은 API 30부터 있다. 그 전에는 manifest에 선언한 타입을 쓰게 한다(백그라운드 마이크 제한도 30부터).
        val effectiveTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) types else ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(title, startedAt, status), effectiveTypes)
        } catch (_: RuntimeException) {
            // 백그라운드 시작 제한·권한 회수 등으로 시작할 수 없음. 통화는 앞에 있는 동안만 유지된다.
            stopSelf()
        }
        // 프로세스가 죽으면 세션도 사라지므로 서비스를 되살리지 않는다.
        return START_NOT_STICKY
    }

    /**
     * 통화 알림. 통화 형식(CallStyle)이라 Android 12+에서는 상태 표시줄에 통화 칩이 생기고, 경과 시간과 종료 버튼이 붙는다.
     * 종료 버튼은 앱 안에서만 받는 방송을 보내며, 실제 종료는 [VoxeraForegroundCall]이 세션에 맡긴다.
     * 작은 아이콘과 본문은 [status]에 따라 바뀐다.
     */
    private fun notification(title: String, startedAt: Long, status: CallNotificationStatus): android.app.Notification {
        val caller = Person.Builder().setName(title.ifBlank { getString(R.string.voxera_call_notification_text) }).setImportant(true).build()
        val hangUp = PendingIntent.getBroadcast(
            this,
            REQUEST_HANG_UP,
            Intent(hangUpAction(this)).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, ensureChannel(this))
            .setSmallIcon(status.icon)
            .setContentTitle(title)
            .setContentText(getString(status.text))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(launchIntent())
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(caller, hangUp))
        if (startedAt > 0) builder.setWhen(startedAt).setShowWhen(true).setUsesChronometer(true)
        return builder.build()
    }

    private fun launchIntent(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        } ?: return null
        return PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    internal companion object {
        const val ACTION_STOP = "com.naury.voxerartc.android.action.STOP"
        const val EXTRA_TYPES = "types"
        const val EXTRA_TITLE = "title"
        const val EXTRA_STARTED_AT = "startedAt"
        const val EXTRA_STATUS = "status"
        private const val REQUEST_HANG_UP = 0x5603

        /** 알림 종료 버튼이 보내는 방송. 패키지마다 달라 다른 앱과 겹치지 않는다. */
        fun hangUpAction(context: Context): String = "${context.packageName}.voxera.call.HANG_UP"
        const val NOTIFICATION_ID = 0x766F78
        private const val CHANNEL_ID = "voxera_ongoing_call"

        fun ensureChannel(context: Context): String {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.voxera_call_channel_name), NotificationManager.IMPORTANCE_LOW)
                channel.description = context.getString(R.string.voxera_call_channel_description)
                manager.createNotificationChannel(channel)
            }
            return CHANNEL_ID
        }
    }
}

/**
 * 지금 켜야 할 서비스 타입. 런타임 권한이 허용된 미디어만 넣는다. 0이면 서비스를 띄우지 않는다.
 * API 29 미만에서는 타입 값이 무시되며, 서비스는 마이크 권한이 있을 때만 띄운다.
 */
@SuppressLint("InlinedApi") // 상수 값만 쓰며, API 30 미만에서는 VoxeraCallService가 이 값을 넘기지 않는다.
internal fun callServiceTypes(microphoneGranted: Boolean, cameraGranted: Boolean, cameraActive: Boolean): Int {
    var types = 0
    if (microphoneGranted) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
    if (cameraGranted && cameraActive) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
    return types
}

/**
 * 통화 알림이 보여 줄 상황. 작은 아이콘(마크 + 배지)과 본문 문구가 짝을 이룬다.
 * 여러 상황이 겹치면 사용자가 먼저 알아야 하는 것 하나만 고른다([callNotificationStatus]).
 */
internal enum class CallNotificationStatus(@DrawableRes val icon: Int, @StringRes val text: Int) {
    ACTIVE(R.drawable.voxera_ic_notification, R.string.voxera_call_notification_text),
    VIDEO(R.drawable.voxera_ic_notification_video, R.string.voxera_call_notification_video),
    MUTED(R.drawable.voxera_ic_notification_muted, R.string.voxera_call_notification_muted),
    SHARING(R.drawable.voxera_ic_notification_sharing, R.string.voxera_call_notification_sharing),
    ON_HOLD(R.drawable.voxera_ic_notification_on_hold, R.string.voxera_call_notification_on_hold),
    RECONNECTING(R.drawable.voxera_ic_notification_reconnecting, R.string.voxera_call_notification_reconnecting),
}

/**
 * 알림 상황을 고른다. 통화가 끊길 수 있는 상황(재연결, 다른 통화에 오디오를 넘긴 보류)을 먼저 알리고,
 * 그다음 남에게 보이는 것(화면 공유), 내 소리가 안 가는 것(마이크 꺼짐), 영상 통화 순이다.
 */
internal fun callNotificationStatus(reconnecting: Boolean, media: LocalMediaState): CallNotificationStatus = when {
    reconnecting -> CallNotificationStatus.RECONNECTING
    media.audioInterrupted -> CallNotificationStatus.ON_HOLD
    media.screenShare.actual -> CallNotificationStatus.SHARING
    !media.microphone.desired -> CallNotificationStatus.MUTED
    media.camera.actual || media.camera.desired -> CallNotificationStatus.VIDEO
    else -> CallNotificationStatus.ACTIVE
}
