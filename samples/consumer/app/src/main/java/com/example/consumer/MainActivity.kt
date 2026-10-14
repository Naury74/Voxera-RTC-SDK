package com.example.consumer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.naury.voxerartc.api.RtcCredentials
import com.naury.voxerartc.api.RtcCredentialsProvider
import com.naury.voxerartc.core.Voxera
import com.naury.voxerartc.core.VoxeraSessionConfig
import com.naury.voxerartc.livekit.LiveKitRtcEngineFactory
import com.naury.voxerartc.livekit.compose.asComposeRenderer
import com.naury.voxerartc.ui.room.VoxeraRoomScreen
import com.naury.voxerartc.ui.video.LocalVoxeraVideoRenderer

/** README의 "실제 통화" 예제를 그대로 옮긴 소비자 앱. 호스트 백엔드 대신 고정 값을 쓴다. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engines = LiveKitRtcEngineFactory(applicationContext)
        val session = Voxera.create(engines).createSession(
            VoxeraSessionConfig(credentials = RtcCredentialsProvider { RtcCredentials("wss://example.invalid", "token") }),
        )
        setContent {
            val renderer = remember { engines.videoTracks.asComposeRenderer() }
            CompositionLocalProvider(LocalVoxeraVideoRenderer provides renderer) {
                VoxeraRoomScreen(session = session, onExit = { finish() })
            }
        }
        JavaHost.create(this)
    }
}
