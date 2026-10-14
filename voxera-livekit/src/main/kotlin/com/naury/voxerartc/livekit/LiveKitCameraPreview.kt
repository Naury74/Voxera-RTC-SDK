package com.naury.voxerartc.livekit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import io.livekit.android.LiveKit
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrackOptions
import livekit.org.webrtc.RendererCommon

/**
 * 입장 전 카메라 미리보기. 서버에 연결하지 않는 미리보기 전용 Room으로 로컬 카메라만 연다.
 * 통화용 카메라와 동시에 열지 않도록 입장하기 전에 [LiveKitVideoView.close]로 닫는다.
 */
public object LiveKitCameraPreview {

    /**
     * 전면 카메라 미리보기 View. 카메라 권한이 없거나 다른 앱이 카메라를 쓰는 중이면 `null`.
     */
    @MainThread
    public fun create(context: Context): LiveKitVideoView? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return null
        val room = LiveKit.create(context.applicationContext)
        return try {
            val track = room.localParticipant.createVideoTrack("preview", LocalVideoTrackOptions(position = CameraPosition.FRONT))
            track.startCapture()
            val renderer = TextureViewRenderer(context)
            room.initVideoRenderer(renderer)
            renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            renderer.setMirror(true)
            track.addRenderer(renderer)
            LiveKitVideoView(renderer) {
                track.removeRenderer(renderer)
                track.stopCapture()
                track.dispose()
                renderer.release()
                room.release()
            }
        } catch (_: Exception) {
            // 카메라를 열지 못함(점유·장치 없음). 미리보기 없이 입장할 수 있다.
            room.release()
            null
        }
    }
}
