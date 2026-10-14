package com.naury.voxerartc.livekit

import com.naury.voxerartc.api.VideoEffect
import io.livekit.android.room.track.video.NoDropVideoProcessor
import livekit.org.webrtc.JavaI420Buffer
import livekit.org.webrtc.VideoFrame
import livekit.org.webrtc.VideoSink
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/** 효과 하나를 Y(밝기)·U/V(색) 값 256개의 조회표로 바꾼 것. 프레임마다 계산하지 않고 효과가 바뀔 때만 만든다. */
internal class ColorLut(effect: VideoEffect) {
    val luma = ByteArray(256)
    val chroma = ByteArray(256)

    init {
        for (value in 0..255) {
            val normalized = (value / 255f - 0.5f) * effect.contrast + 0.5f + effect.brightness
            luma[value] = (normalized * 255f).roundToInt().coerceIn(0, 255).toByte()
            chroma[value] = ((value - 128) * effect.saturation + 128).roundToInt().coerceIn(0, 255).toByte()
        }
    }
}

/**
 * 카메라 프레임에 [VideoEffect]를 적용하는 WebRTC 처리기. 효과가 없으면 프레임을 그대로 넘긴다.
 *
 * 최근 [WINDOW]개 프레임의 평균 처리 시간이 [BUDGET_MS]를 넘으면 효과를 끄고 [onThrottled]를 부른다.
 * 기기가 느려도 통화는 원본 영상으로 계속된다. 처리 중 오류가 나도 원본 프레임을 넘긴다.
 */
internal class ColorEffectProcessor(private val onThrottled: () -> Unit) : NoDropVideoProcessor() {

    @Volatile
    private var sink: VideoSink? = null

    @Volatile
    private var lut: ColorLut? = null

    @Volatile
    var effect: VideoEffect = VideoEffect.None
        set(value) {
            field = value
            lut = if (value.isNone) null else ColorLut(value)
            samples = 0
            totalNs = 0
        }

    private var samples = 0
    private var totalNs = 0L
    private var row = ByteArray(0)

    override fun setSink(sink: VideoSink?) {
        this.sink = sink
    }

    override fun onCapturerStarted(success: Boolean) = Unit

    override fun onCapturerStopped() = Unit

    override fun onFrameCaptured(frame: VideoFrame) {
        val target = sink ?: return
        val table = lut
        if (table == null) {
            target.onFrame(frame)
            return
        }
        val start = System.nanoTime()
        val processed = try {
            apply(frame, table)
        } catch (_: RuntimeException) {
            null
        }
        if (processed == null) {
            target.onFrame(frame)
            return
        }
        target.onFrame(processed)
        processed.release()
        measure(System.nanoTime() - start)
    }

    private fun apply(frame: VideoFrame, table: ColorLut): VideoFrame {
        val source = frame.buffer.toI420() ?: error("toI420 failed")
        try {
            val width = source.width
            val height = source.height
            val out = JavaI420Buffer.allocate(width, height)
            mapPlane(source.dataY, source.strideY, out.dataY, out.strideY, width, height, table.luma)
            val chromaWidth = (width + 1) / 2
            val chromaHeight = (height + 1) / 2
            mapPlane(source.dataU, source.strideU, out.dataU, out.strideU, chromaWidth, chromaHeight, table.chroma)
            mapPlane(source.dataV, source.strideV, out.dataV, out.strideV, chromaWidth, chromaHeight, table.chroma)
            return VideoFrame(out, frame.rotation, frame.timestampNs)
        } finally {
            source.release()
        }
    }

    private fun mapPlane(src: ByteBuffer, srcStride: Int, dst: ByteBuffer, dstStride: Int, width: Int, height: Int, table: ByteArray) {
        if (row.size < width) row = ByteArray(width)
        val line = row
        for (y in 0 until height) {
            src.position(y * srcStride)
            src.get(line, 0, width)
            for (x in 0 until width) line[x] = table[line[x].toInt() and 0xFF]
            dst.position(y * dstStride)
            dst.put(line, 0, width)
        }
    }

    private fun measure(elapsedNs: Long) {
        samples++
        totalNs += elapsedNs
        if (samples < WINDOW) return
        val averageMs = totalNs / samples / 1_000_000.0
        samples = 0
        totalNs = 0
        if (averageMs > BUDGET_MS) {
            effect = VideoEffect.None
            onThrottled()
        }
    }

    private companion object {
        const val WINDOW = 60
        const val BUDGET_MS = 15.0
    }
}
