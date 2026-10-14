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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

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
        // microphone·camera 타입은 API 30부터 있다. 그 전에는 manifest에 선언한 타입을 쓰게 한다(백그라운드 마이크 제한도 30부터).
        val effectiveTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) types else ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(title), effectiveTypes)
        } catch (_: RuntimeException) {
            // 백그라운드 시작 제한·권한 회수 등으로 시작할 수 없음. 통화는 앞에 있는 동안만 유지된다.
            stopSelf()
        }
        // 프로세스가 죽으면 세션도 사라지므로 서비스를 되살리지 않는다.
        return START_NOT_STICKY
    }

    private fun notification(title: String) = NotificationCompat.Builder(this, ensureChannel(this))
        .setSmallIcon(R.drawable.voxera_ic_call)
        .setContentTitle(title)
        .setContentText(getString(R.string.voxera_call_notification_text))
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(launchIntent())
        .build()

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
