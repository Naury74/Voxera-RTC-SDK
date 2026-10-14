package com.naury.voxerartc.ui.call

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.RtcCredentials
import com.naury.voxerartc.api.RtcCredentialsProvider
import com.naury.voxerartc.core.Voxera
import com.naury.voxerartc.core.VoxeraSessionConfig
import com.naury.voxerartc.invitation.CallCoordinator
import com.naury.voxerartc.invitation.CallState
import com.naury.voxerartc.testkit.FakeInvitationGateway
import com.naury.voxerartc.testkit.FakeRtcEngineFactory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VoxeraCallScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val gateway = FakeInvitationGateway()
    private val session = Voxera.create(FakeRtcEngineFactory())
        .createSession(VoxeraSessionConfig(credentials = RtcCredentialsProvider { RtcCredentials("wss://t.livekit.cloud", "t") }))
    private val coordinator = CallCoordinator(gateway, session).also { it.start() }

    @Test
    fun `걸려 온 영상 통화를 보여 주고 거절하면 종료 사유를 알린다`() {
        compose.setContent { VoxeraCallScreen(coordinator, session, onExit = {}) }
        gateway.ring("call-1", "Alex Morgan", CallMode.VIDEO)
        compose.waitUntil(5_000) { coordinator.state.value is CallState.Ringing }

        compose.onNodeWithText("Incoming video call").assertIsDisplayed()
        compose.onNodeWithText("Alex Morgan").assertIsDisplayed()
        compose.onNodeWithContentDescription("Accept").assertExists()
        compose.onNodeWithContentDescription("Decline").performClick()

        compose.waitUntil(5_000) { coordinator.state.value is CallState.Ended }
        compose.onNodeWithText("The call was declined.").assertIsDisplayed()
        assertThat(gateway.requests).contains("decline:call-1:USER")
        coordinator.close()
        session.close()
    }
}
