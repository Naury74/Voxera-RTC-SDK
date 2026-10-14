package com.naury.voxerartc.ui.prejoin

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlin.math.log10
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * PCM16 표본의 RMS를 0~1 레벨로 바꾼다. -50dBFS 이하는 0, 0dBFS는 1이며 그 사이는 dB에 비례한다.
 */
internal fun levelOf(samples: ShortArray, count: Int): Float {
    if (count <= 0) return 0f
    var sum = 0.0
    for (i in 0 until count) {
        val v = samples[i] / 32768.0
        sum += v * v
    }
    val rms = sqrt(sum / count)
    if (rms <= 0.0) return 0f
    val db = 20 * log10(rms)
    return ((db + 50) / 50).toFloat().coerceIn(0f, 1f)
}

/**
 * 입장 전 마이크 레벨(0~1). [enabled]이고 녹음 권한이 있을 때만 마이크를 열고, 화면에서 사라지거나 꺼지면 바로 닫는다.
 * 소리는 레벨 계산에만 쓰며 저장하거나 보내지 않는다. 마이크를 열 수 없으면 `null`.
 */
@SuppressLint("MissingPermission")
@Composable
internal fun rememberMicLevel(enabled: Boolean): State<Float?> = produceState<Float?>(null, enabled) {
    if (!enabled) {
        value = null
        return@produceState
    }
    withContext(Dispatchers.IO) {
        val rate = 16_000
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return@withContext
        val record = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer * 2)
        }.getOrNull()
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            record?.release()
            return@withContext
        }
        try {
            record.startRecording()
            val buffer = ShortArray(rate / 20) // 50ms
            var smoothed = 0f
            while (isActive) {
                val read = record.read(buffer, 0, buffer.size)
                if (read < 0) break
                // 올라갈 때는 빠르게, 내려갈 때는 천천히 따라가 막대가 떨리지 않게 한다.
                val level = levelOf(buffer, read)
                smoothed = if (level > smoothed) level else smoothed * 0.8f + level * 0.2f
                value = smoothed
            }
        } catch (_: RuntimeException) {
            // 다른 앱이 마이크를 쓰고 있으면 읽기가 실패한다. 레벨만 보이지 않을 뿐 입장에는 영향이 없다.
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }
}
