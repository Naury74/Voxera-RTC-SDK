package com.naury.voxerartc.ui.room

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performImeAction
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.api.Reaction
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
import org.robolectric.annotation.Config

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
    @Config(qualifiers = "w480dp-h900dp")
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

    @Test
    fun `영상 통화에서 조작이 없으면 조작 막대를 숨기고 화면을 누르면 다시 보여 준다`() {
        val session = connectedSession(CallMode.VIDEO)
        factory.last.upsertRemote(Participant(id = "alex", displayName = "Alex", isLocal = false))
        compose.mainClock.autoAdvance = false
        compose.setContent { VoxeraRoomScreen(session, onExit = {}) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription("End").assertExists()
        compose.mainClock.advanceTimeBy(5_000)
        compose.onNodeWithContentDescription("End").assertDoesNotExist()
        compose.onRoot().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription("End").assertExists()
    }

    @Test
    fun `자동 숨김을 끄면 조작 막대가 계속 보인다`() {
        val session = connectedSession(CallMode.VIDEO)
        factory.last.upsertRemote(Participant(id = "alex", displayName = "Alex", isLocal = false))
        compose.mainClock.autoAdvance = false
        compose.setContent { VoxeraRoomScreen(session, onExit = {}, autoHideControls = false) }
        compose.mainClock.advanceTimeBy(10_000)
        compose.onNodeWithContentDescription("End").assertExists()
    }

    @Test
    fun `PiP 창에서는 조작 없이 상대 한 명만 보여 준다`() {
        val session = connectedSession(CallMode.VIDEO)
        factory.last.upsertRemote(Participant(id = "alex", displayName = "Alex", isLocal = false))
        compose.setContent { VoxeraRoomScreen(session, onExit = {}, pictureInPicture = true) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("End").assertDoesNotExist()
        compose.onNodeWithContentDescription("People").assertDoesNotExist()
        compose.onNode(hasContentDescription("Alex", substring = true)).assertExists()
    }

    @Test
    fun `다른 통화가 오디오를 가져가면 안내하고 끝나면 사라진다`() {
        val session = connectedSession(CallMode.VOICE)
        compose.setContent { VoxeraRoomScreen(session, onExit = {}) }
        // 엔진 이벤트는 세션 쪽 스레드에서 오므로 화면이 바뀔 때까지 기다린다.
        val banner = "Another call is using the audio"
        factory.last.simulateLocalMedia { it.copy(audioInterrupted = true) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(banner).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(banner).assertIsDisplayed()
        factory.last.simulateLocalMedia { it.copy(audioInterrupted = false) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(banner).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `반응을 고르면 엔진으로 보낸다`() {
        val session = connectedSession(CallMode.VOICE)
        compose.setContent { VoxeraRoomScreen(session, onExit = {}) }
        compose.onNodeWithContentDescription("React").performClick()
        compose.onNodeWithContentDescription("Heart").performClick()
        compose.waitForIdle()
        assertThat(factory.last.sentReactions).containsExactly(Reaction.HEART)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `채팅을 보내면 엔진으로 가고 받은 메시지는 배지로 센 뒤 시트를 열면 읽은 것으로 친다`() {
        val session = connectedSession(CallMode.VOICE)
        factory.last.upsertRemote(Participant(id = "alex", displayName = "Alex", isLocal = false))
        compose.setContent { VoxeraRoomScreen(session, onExit = {}) }
        factory.last.simulateChat("alex", "hello there")
        compose.waitUntil(5_000) { session.chat.value.isNotEmpty() }
        compose.waitForIdle()
        compose.onNode(hasContentDescription("Chat").and(stateIs("1 unread message"))).assertExists()
        compose.onNodeWithContentDescription("Chat").performClick()
        compose.onNodeWithText("hello there").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("hi Alex")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(5_000) { factory.last.sentChatMessages.isNotEmpty() }
        assertThat(factory.last.sentChatMessages.map { it.second }).containsExactly("hi Alex")
        compose.waitForIdle()
        compose.onNodeWithText("hi Alex").assertIsDisplayed()
        session.close()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `반응 시트에서 손을 들면 위에 알약이 남고 누르면 내린다`() {
        val session = connectedSession(CallMode.VOICE)
        compose.setContent { VoxeraRoomScreen(session, onExit = {}) }
        compose.onNodeWithContentDescription("React").performClick()
        compose.onNodeWithText("Raise hand").performClick()
        compose.waitUntil(5_000) { session.participants.value.first { it.isLocal }.isHandRaised }
        assertThat(factory.last.localHandRaised).isTrue()
        compose.waitForIdle()
        compose.onNodeWithText("Your hand is up").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { !session.participants.value.first { it.isLocal }.isHandRaised }
        assertThat(factory.last.localHandRaised).isFalse()
        session.close()
    }
}
