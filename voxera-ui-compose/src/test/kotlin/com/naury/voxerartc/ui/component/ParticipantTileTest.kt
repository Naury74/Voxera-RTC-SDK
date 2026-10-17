package com.naury.voxerartc.ui.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.ui.theme.VoxeraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ParticipantTileTest {

    @get:Rule
    val compose = createComposeRule()

    private val taylor = Participant(
        id = "taylor",
        displayName = "Taylor",
        isLocal = false,
        isMicrophoneEnabled = false,
        isSpeaking = true,
        audioLevel = 0.8f,
        handRaisedAtMs = 1L,
    )

    @Test
    fun `공유 화면 타일은 말하는 중·마이크·손 상태를 읽지 않고 카메라 타일만 읽는다`() {
        compose.setContent { VoxeraTheme { VoxeraTile(taylor, renderer = null, screenShare = true) } }

        compose.onAllNodes(hasContentDescription("Speaking", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("Mic off", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("Hand raised", substring = true)).assertCountEquals(0)
    }

    @Test
    fun `카메라 타일은 말하는 중을 읽는다`() {
        compose.setContent { VoxeraTheme { VoxeraTile(taylor, renderer = null) } }

        compose.onAllNodes(hasContentDescription("Speaking", substring = true)).assertCountEquals(1)
    }
}
