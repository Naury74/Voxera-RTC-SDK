package com.naury.voxerartc.ui.component

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.naury.voxerartc.ui.theme.VoxeraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AvatarTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `이니셜은 화면에만 그리고 TalkBack에는 내놓지 않는다`() {
        compose.setContent { VoxeraTheme { VoxeraAvatar("p1", "Alex Morgan") } }

        // 알림 섬처럼 묶어 읽는 곳에서 "capital A M"이 이름 앞에 끼어들지 않게 한다.
        compose.onNodeWithText("AM", useUnmergedTree = true).assertDoesNotExist()
    }
}
