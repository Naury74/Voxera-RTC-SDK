package com.naury.voxerartc.livekit.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.naury.voxerartc.livekit.LiveKitCameraPreview
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

/**
 * 입장 전 전면 카메라 미리보기. 카메라를 열 수 없으면 [fallback]을 그린다. 화면에서 사라지면 카메라를 닫는다.
 * `VoxeraPrejoinScreen`의 `preview`로 넘긴다.
 */
@Composable
public fun LiveKitCameraPreviewContent(modifier: Modifier = Modifier, fallback: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val video = remember { LiveKitCameraPreview.create(context) }
    DisposableEffect(Unit) { onDispose { video?.close() } }
    if (video != null) AndroidView(factory = { video.view }, modifier = modifier) else fallback()
}
