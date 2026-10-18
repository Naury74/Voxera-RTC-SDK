package com.naury.voxerartc.ui.component

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.naury.voxerartc.ui.theme.VoxeraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InputsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `입력 칸은 이름과 오류를 한 노드에서 읽고 위아래 글자는 따로 멈추지 않는다`() {
        compose.setContent { VoxeraTheme { VoxeraTextField(value = "", onValueChange = {}, label = "Room name", error = "Required") } }

        compose.onAllNodes(hasSetTextAction().and(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Required"))).assertCountEquals(1)
        compose.onAllNodes(hasText("Room name"), useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodes(hasText("Required"), useUnmergedTree = true).assertCountEquals(0)
    }
}
