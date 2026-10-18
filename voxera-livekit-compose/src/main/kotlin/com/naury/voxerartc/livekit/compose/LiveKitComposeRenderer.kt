package com.naury.voxerartc.livekit.compose

import android.app.Activity
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import com.naury.voxerartc.api.VideoTrackHandle
import com.naury.voxerartc.core.VoxeraSession
import com.naury.voxerartc.livekit.LiveKitCameraPreview
import com.naury.voxerartc.livekit.LiveKitVideoTracks
import com.naury.voxerartc.livekit.MediaProjectionConsent
import com.naury.voxerartc.ui.video.VoxeraVideoRenderer
import kotlinx.coroutines.launch

/**
 * LiveKit 영상 트랙을 그리는 [VoxeraVideoRenderer]. `LocalVoxeraVideoRenderer`로 넘기면 `VoxeraRoomScreen`이 실제 영상을 그린다.
 *
 * 트랙이 다시 게시되어 핸들이 바뀌면 이전 렌더러를 떼고 새로 붙인다. 타일이 화면에서 사라지면 렌더러와 EGL 자원을 해제한다.
 */
public fun LiveKitVideoTracks.asComposeRenderer(): VoxeraVideoRenderer = LiveKitComposeRenderer(this)

private class LiveKitComposeRenderer(private val tracks: LiveKitVideoTracks) : VoxeraVideoRenderer {
    @Composable
    override fun Render(track: VideoTrackHandle, mirror: Boolean, modifier: Modifier) = Render(track, mirror, modifier) {}

    @Composable
    override fun Render(track: VideoTrackHandle, mirror: Boolean, modifier: Modifier, onFrameSize: (IntSize) -> Unit) {
        val report by rememberUpdatedState(onFrameSize)
        key(track, mirror) {
            val context = LocalContext.current
            val video = remember { tracks.createView(context, track, mirror) { width, height -> report(IntSize(width, height)) } }
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

/**
 * 시스템 화면 공유 승인 대화상자를 띄우고, 허락하면 [session]에서 공유를 시작하는 함수를 돌려준다.
 * 사용자가 거절하면 아무것도 하지 않으며 카메라 영상은 그대로다. `VoxeraRoomScreen(onShareScreen = ...)`에 넘긴다.
 *
 * Android 14+는 공유할 때마다 새로 승인을 받아야 하므로 승인 결과를 저장하지 않는다.
 */
@Composable
public fun rememberScreenShareLauncher(session: VoxeraSession): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            scope.launch { session.startScreenShare(MediaProjectionConsent(data)) }
        }
    }
    return remember(launcher) {
        {
            val manager = context.getSystemService(MediaProjectionManager::class.java)
            launcher.launch(manager.createScreenCaptureIntent())
        }
    }
}
