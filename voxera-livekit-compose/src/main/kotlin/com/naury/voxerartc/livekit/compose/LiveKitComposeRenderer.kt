package com.naury.voxerartc.livekit.compose

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.naury.voxerartc.livekit.LiveKitVideoTracks
import com.naury.voxerartc.ui.video.VoxeraVideoRenderer

/**
 * LiveKit 영상 트랙을 그리는 [VoxeraVideoRenderer]. `LocalVoxeraVideoRenderer`로 넘기면 `VoxeraRoomScreen`이 실제 영상을 그린다.
 *
 * 트랙 핸들이 바뀌면(재게시) 이전 렌더러를 떼고 새로 붙이며, 타일이 화면에서 사라지면 렌더러와 EGL 자원을 해제한다.
 */
public fun LiveKitVideoTracks.asComposeRenderer(): VoxeraVideoRenderer {
    val tracks = this
    return VoxeraVideoRenderer { track, mirror, modifier ->
        key(track, mirror) {
            val context = LocalContext.current
            val video = remember { tracks.createView(context, track, mirror) }
            DisposableEffect(Unit) { onDispose { video?.close() } }
            if (video != null) AndroidView(factory = { video.view }, modifier = modifier)
        }
    }
}
