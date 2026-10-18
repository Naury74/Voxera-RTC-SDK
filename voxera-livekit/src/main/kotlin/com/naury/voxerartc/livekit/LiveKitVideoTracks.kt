package com.naury.voxerartc.livekit

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.annotation.MainThread
import com.naury.voxerartc.api.VideoSource
import com.naury.voxerartc.api.VideoTrackHandle
import io.livekit.android.renderer.TextureViewRenderer
import livekit.org.webrtc.RendererCommon
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [VideoTrackHandle]로 LiveKit 영상 트랙을 찾아 화면에 붙인다. LiveKit 트랙 객체는 밖으로 내보내지 않고 View만 준다.
 *
 * [LiveKitRtcEngineFactory.videoTracks]로 얻는다. 트랙은 그 factory가 만든 엔진 중 연결되어 있는 것에서만 찾는다.
 */
public class LiveKitVideoTracks internal constructor() {

    private val engines = CopyOnWriteArraySet<LiveKitRtcEngine>()

    internal fun register(engine: LiveKitRtcEngine) {
        engines += engine
    }

    internal fun unregister(engine: LiveKitRtcEngine) {
        engines -= engine
    }

    /**
     * [handle] 트랙을 그리는 View를 만든다. 트랙을 찾지 못하면(이미 끝난 통화, 재게시 등) `null`.
     * 다 쓰면 [LiveKitVideoView.close]를 불러 렌더러를 트랙에서 떼고 EGL 자원을 해제해야 한다.
     *
     * @param mirror 로컬 전면 카메라처럼 좌우를 뒤집어 보여 줄지.
     * @param onFrameSize 받은 영상의 가로·세로(회전을 반영한 픽셀)가 처음 정해지거나 바뀔 때 메인 스레드에서 부른다.
     *   상대가 휴대전화를 돌리거나 화질 단계가 바뀌면 다시 불린다. 화면에서 영상을 채울지, 전체를 보여 줄지 정하는 데 쓴다.
     */
    @MainThread
    public fun createView(
        context: Context,
        handle: VideoTrackHandle,
        mirror: Boolean,
        onFrameSize: ((width: Int, height: Int) -> Unit)? = null,
    ): LiveKitVideoView? {
        for (engine in engines) {
            val found = engine.findVideoTrack(handle) ?: continue
            val (room, track) = found
            val renderer = SizedTextureViewRenderer(context, onFrameSize)
            room.initVideoRenderer(renderer)
            // 화면 공유는 글자가 잘리지 않게 전체를 보여 주고, 카메라는 타일을 채운다.
            val scaling = if (handle.source == VideoSource.SCREEN_SHARE) RendererCommon.ScalingType.SCALE_ASPECT_FIT else RendererCommon.ScalingType.SCALE_ASPECT_FILL
            renderer.setScalingType(scaling)
            renderer.setMirror(mirror)
            track.addRenderer(renderer)
            return LiveKitVideoView(renderer) {
                track.removeRenderer(renderer)
                renderer.release()
            }
        }
        return null
    }
}

/** 프레임 크기가 바뀌면 [onFrameSize]로 알리는 렌더러. 렌더 스레드에서 오는 알림을 메인 스레드로 넘긴다. */
private class SizedTextureViewRenderer(
    context: Context,
    private val onFrameSize: ((width: Int, height: Int) -> Unit)?,
) : TextureViewRenderer(context) {
    private val main = Handler(Looper.getMainLooper())

    override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
        super.onFrameResolutionChanged(videoWidth, videoHeight, rotation)
        val listener = onFrameSize ?: return
        val turned = rotation % 180 != 0
        val width = if (turned) videoHeight else videoWidth
        val height = if (turned) videoWidth else videoHeight
        if (width > 0 && height > 0) main.post { listener(width, height) }
    }
}

/** 영상 트랙이 붙은 View. [close]는 여러 번 불러도 안전하다. */
public class LiveKitVideoView internal constructor(
    public val view: View,
    private val release: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) release()
    }
}
