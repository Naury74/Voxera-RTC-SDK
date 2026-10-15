package com.example.consumer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.naury.voxerartc.android.VoxeraForegroundCall
import com.naury.voxerartc.android.VoxeraPictureInPicture
import com.naury.voxerartc.android.VoxeraProximityScreenOff
import com.naury.voxerartc.api.Reaction
import com.naury.voxerartc.api.RtcCredentials
import com.naury.voxerartc.api.RtcCredentialsProvider
import com.naury.voxerartc.core.Voxera
import com.naury.voxerartc.core.VoxeraSessionConfig
import com.naury.voxerartc.livekit.LiveKitRtcEngineFactory
import com.naury.voxerartc.livekit.compose.asComposeRenderer
import com.naury.voxerartc.livekit.compose.rememberScreenShareLauncher
import com.naury.voxerartc.ui.room.VoxeraRoomScreen
import com.naury.voxerartc.ui.video.LocalVoxeraVideoRenderer
import kotlinx.coroutines.launch

/** docs/integration.md의 예제를 그대로 옮긴 소비자 앱. 호스트 백엔드 대신 고정 값을 쓴다. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engines = LiveKitRtcEngineFactory(applicationContext)
        val session = Voxera.create(engines).createSession(
            VoxeraSessionConfig(credentials = RtcCredentialsProvider { RtcCredentials("wss://example.invalid", "token") }),
        )
        val keepAlive = VoxeraForegroundCall.keepWhileConnected(this, session, title = "Support call")
        val proximity = VoxeraProximityScreenOff.whileOnEarpiece(this, session)
        val pip = VoxeraPictureInPicture.attach(this, session, onHangUp = { lifecycleScope.launch { session.leave() } })
        setContent {
            val renderer = remember { engines.videoTracks.asComposeRenderer() }
            val inPip by pip.isInPictureInPicture.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalVoxeraVideoRenderer provides renderer) {
                VoxeraRoomScreen(
                    session = session,
                    onExit = { finish() },
                    onShareScreen = rememberScreenShareLauncher(session),
                    pictureInPicture = inPip,
                    onEnterPictureInPicture = { pip.enter() },
                )
            }
        }
        lifecycleScope.launch { session.sendReaction(Reaction.HEART) }
        JavaHost.create(this)
        if (isFinishing) {
            keepAlive.close()
            proximity.close()
        }
    }
}
