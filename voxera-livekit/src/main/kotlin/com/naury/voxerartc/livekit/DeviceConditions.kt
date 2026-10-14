package com.naury.voxerartc.livekit

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 통화 중 기기 상태 감시. 기기가 많이 뜨거워지면 [onOverheated], 다른 통화가 오디오를 가져가거나 돌려주면
 * [onAudioInterrupted]를 부른다. 지원하지 않는 Android 버전에서는 아무것도 하지 않는다.
 *
 * - 발열: Android 10+ `PowerManager` 열 상태가 SEVERE 이상(E31).
 * - 다른 통화: Android 12+ 오디오 모드가 휴대전화 통화(`MODE_IN_CALL`)나 벨소리(`MODE_RINGTONE`)로 바뀜(E14).
 *   통화 앱 권한(READ_PHONE_STATE)은 쓰지 않는다.
 */
internal class DeviceConditions(
    context: Context,
    private val onOverheated: () -> Unit,
    private val onAudioInterrupted: (Boolean) -> Unit,
) : AutoCloseable {

    private val power = context.getSystemService(PowerManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "voxera-device-conditions").apply { isDaemon = true } }
    private var thermalListener: Any? = null
    private var modeListener: Any? = null

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startThermal()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) startMode()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startThermal() {
        val manager = power ?: return
        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            if (isOverheated(status)) onOverheated()
        }
        manager.addThermalStatusListener(executor, listener)
        thermalListener = listener
        if (isOverheated(manager.currentThermalStatus)) onOverheated()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun startMode() {
        val manager = audio ?: return
        val listener = AudioManager.OnModeChangedListener { mode -> onAudioInterrupted(isInterrupting(mode)) }
        manager.addOnModeChangedListener(executor, listener)
        modeListener = listener
    }

    override fun close() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (thermalListener as? PowerManager.OnThermalStatusChangedListener)?.let { power?.removeThermalStatusListener(it) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (modeListener as? AudioManager.OnModeChangedListener)?.let { audio?.removeOnModeChangedListener(it) }
        }
        thermalListener = null
        modeListener = null
        executor.shutdown()
    }

    internal companion object {
        /** SEVERE(3) 이상이면 효과를 끈다. 기기가 스스로 성능을 크게 낮추기 시작하는 단계다. 상수는 컴파일 때 값으로 들어간다. */
        @SuppressLint("InlinedApi")
        fun isOverheated(status: Int): Boolean = status >= PowerManager.THERMAL_STATUS_SEVERE

        /** 다른 통화가 오디오를 쓰는 모드. 우리 통화는 `MODE_IN_COMMUNICATION`이다. 상수는 컴파일 때 값으로 들어간다. */
        @SuppressLint("InlinedApi")
        fun isInterrupting(mode: Int): Boolean = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_RINGTONE ||
            mode == AudioManager.MODE_CALL_SCREENING
    }
}
