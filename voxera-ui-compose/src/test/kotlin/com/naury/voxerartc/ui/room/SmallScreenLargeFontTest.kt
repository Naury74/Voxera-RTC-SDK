package com.naury.voxerartc.ui.room

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.JoinResult
import com.naury.voxerartc.api.RtcCredentials
import com.naury.voxerartc.api.RtcCredentialsProvider
import com.naury.voxerartc.api.SessionState
import com.naury.voxerartc.core.Voxera
import com.naury.voxerartc.core.VoxeraSession
import com.naury.voxerartc.core.VoxeraSessionConfig
import com.naury.voxerartc.testkit.FakeRtcEngineFactory
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 320dp 폭 + 글꼴 200%에서도 조작이 화면 안에 있고 48dp 터치 영역을 지키는지 확인한다(VOX-026). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h640dp")
class SmallScreenLargeFontTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun largeFont() {
        RuntimeEnvironment.setFontScale(2f)
    }

    private fun session(mode: CallMode): VoxeraSession {
        val session = Voxera.create(FakeRtcEngineFactory()).createSession(
            VoxeraSessionConfig(credentials = RtcCredentialsProvider { RtcCredentials("wss://t.livekit.cloud", "t") }),
        )
        assertThat(runBlocking { session.join("room", mode) }).isInstanceOf(JoinResult.Connected::class.java)
        return session
    }

    @Test
    fun `영상 통화의 모든 조작은 화면 안에 있고 48dp 이상이다`() {
        val session = session(CallMode.VIDEO)
        compose.setContent { VoxeraRoomScreen(session, onExit = {}, onShareScreen = {}) }
        compose.waitForIdle()
        val width = 320.dp
        listOf("Mic", "Camera", "End", "People", "Quality").forEach { label ->
            val node = compose.onNode(hasContentDescription(label))
            node.assertIsDisplayed()
            val bounds = node.getBoundsInRoot()
            assertWithMessage("$label right edge").that(bounds.right.value).isAtMost(width.value)
            assertWithMessage("$label width").that((bounds.right - bounds.left).value).isAtLeast(47.9f)
            assertWithMessage("$label height").that((bounds.bottom - bounds.top).value).isAtLeast(47.9f)
        }
        // 막대에 들어가지 못한 조작은 더보기에 있다.
        compose.onNode(hasContentDescription("More")).assertIsDisplayed()
        session.close()
    }

    @Test
    fun `종료 화면의 버튼은 큰 글꼴에서도 닿을 수 있다`() {
        val session = session(CallMode.VOICE)
        runBlocking { session.leave() }
        compose.setContent { VoxeraRoomScreen(session, onExit = {}, onRejoin = {}, onSaveReport = {}) }
        compose.waitUntil(5_000) { session.state.value is SessionState.Ended }
        compose.onNodeWithText("Close").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save diagnostic report").performScrollTo().assertIsDisplayed()
    }
}
