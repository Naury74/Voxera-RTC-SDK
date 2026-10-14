package com.naury.voxerartc.ui.room

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.JoinResult
import com.naury.voxerartc.api.RtcCredentials
import com.naury.voxerartc.api.RtcCredentialsProvider
import com.naury.voxerartc.api.SessionState
import com.naury.voxerartc.api.VoxeraError
import com.naury.voxerartc.core.Voxera
import com.naury.voxerartc.core.VoxeraSession
import com.naury.voxerartc.core.VoxeraSessionConfig
import com.naury.voxerartc.testkit.FakeRtcEngineFactory
import com.naury.voxerartc.ui.theme.VoxeraThemeConfig
import com.naury.voxerartc.ui.theme.VoxeraThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VoxeraRoomScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val factory = FakeRtcEngineFactory()

    private fun connectedSession(mode: CallMode): VoxeraSession {
        val session = Voxera.create(factory).createSession(
            VoxeraSessionConfig(credentials = RtcCredentialsProvider { RtcCredentials("wss://test.livekit.cloud", "t") }),
        )
        assertThat(runBlocking { session.join("room", mode) }).isInstanceOf(JoinResult.Connected::class.java)
        return session
    }

    private fun stateIs(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    @Test
    fun `가짜 엔진 세션은 DEMO를 표시하고 조작 버튼 상태를 읽어 준다`() {
        val session = connectedSession(CallMode.VIDEO)
        compose.setContent { VoxeraRoomScreen(session, onExit = {}) }

        compose.onNodeWithText("DEMO").assertIsDisplayed()
        compose.onNode(hasContentDescription("Mic").and(stateIs("On"))).assertExists()
        compose.onNode(hasContentDescription("Camera").and(stateIs("On"))).assertExists()
        compose.onNodeWithContentDescription("End").assertExists()
        session.close()
    }

    @Test
    fun `마이크를 누르면 엔진 ack 뒤 꺼짐으로 바뀐다`() {
        val session = connectedSession(CallMode.VOICE)
        compose.setContent { VoxeraRoomScreen(session, onExit = {}) }

        compose.onNodeWithContentDescription("Mic").performClick()
        compose.waitUntil(5_000) { !session.localMedia.value.microphone.actual && !session.localMedia.value.microphone.isPending }
        compose.onNode(hasContentDescription("Mic").and(stateIs("Off"))).assertExists()
        assertThat(factory.last.microphoneRequests).containsExactly(false)
        session.close()
    }

    @Test
    fun `통화 중 테마를 바꿔도 다시 연결하거나 미디어를 다시 게시하지 않는다`() {
        val session = connectedSession(CallMode.VIDEO)
        var mode by mutableStateOf(VoxeraThemeMode.DARK)
        compose.setContent { VoxeraRoomScreen(session, onExit = {}, theme = VoxeraThemeConfig(mode = mode)) }
        compose.waitForIdle()
        mode = VoxeraThemeMode.LIGHT
        compose.waitForIdle()
        mode = VoxeraThemeMode.SYSTEM
        compose.waitForIdle()

        assertThat(session.state.value).isInstanceOf(SessionState.Connected::class.java)
        assertThat(factory.created).hasSize(1)
        assertThat(factory.last.connectRequests).hasSize(1)
        assertThat(factory.last.cameraRequests).isEmpty()
        assertThat(factory.last.microphoneRequests).isEmpty()
        session.close()
    }

    @Test
    fun `종료 버튼을 누르면 통화 종료 화면과 다시 입장 버튼을 보여 준다`() {
        val session = connectedSession(CallMode.VOICE)
        var exited = false
        compose.setContent { VoxeraRoomScreen(session, onExit = { exited = true }, onRejoin = {}) }

        compose.onNodeWithContentDescription("End").performClick()
        compose.waitUntil(5_000) { session.state.value is SessionState.Ended }
        compose.onNodeWithText("Call ended").assertIsDisplayed()
        compose.onNodeWithText("Join again").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        assertThat(exited).isTrue()
        assertThat(factory.last.isReleased).isTrue()
    }

    @Test
    fun `통화 중 끊기면 연결 실패가 아니라 통화가 끊겼다고 알린다`() {
        val session = connectedSession(CallMode.VOICE)
        compose.setContent { VoxeraRoomScreen(session, onExit = {}, onRejoin = {}) }
        factory.last.simulateDisconnect(VoxeraError.NetworkUnavailable)
        compose.waitUntil(5_000) { session.state.value is SessionState.Failed }
        compose.onNodeWithText("Call disconnected").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsDisplayed()
        session.close()
    }

    @Test
    fun `마이크를 켤 수 없으면 이유를 배너로 알린다`() {
        val session = connectedSession(CallMode.VOICE)
        compose.setContent { VoxeraRoomScreen(session, onExit = {}) }
        factory.last.failNextMediaAction(VoxeraError.PermissionDenied(com.naury.voxerartc.api.MediaPermission.MICROPHONE))
        compose.onNodeWithContentDescription("Mic").performClick()
        compose.waitUntil(5_000) { session.localMedia.value.microphone.lastError != null }
        compose.onNodeWithText("Allow microphone access in Settings.").assertIsDisplayed()
        session.close()
    }

    @Test
    fun `내가 화면을 공유하면 경고 배너가 나오고 중지하면 사라진다`() {
        val session = connectedSession(CallMode.VIDEO)
        compose.setContent { VoxeraRoomScreen(session, onExit = {}, onShareScreen = {}) }
        compose.onNodeWithContentDescription("Share").assertExists()
        runBlocking { session.startScreenShare(object : com.naury.voxerartc.api.ScreenShareConsent {}) }
        compose.waitUntil(5_000) { session.localMedia.value.screenShare.actual }
        compose.onNodeWithText("Stop sharing").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { !session.localMedia.value.screenShare.actual }
        compose.waitForIdle()
        compose.onNodeWithText("Stop sharing").assertDoesNotExist()
        session.close()
    }
}
