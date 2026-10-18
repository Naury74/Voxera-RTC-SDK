package com.naury.voxerartc.ui.video

import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.absoluteValue

class DemoPersonTest {

    private val ids = listOf("local", "demo-1", "demo-2", "demo-3", "demo-4", "demo-5", "demo-6", "demo-7", "demo-8", "demo-9", "demo-10", "demo-11")

    @Test
    fun `같은 참가자는 늘 같은 모습이다`() {
        ids.forEach { assertThat(demoLook(it)).isEqualTo(demoLook(it)) }
    }

    @Test
    fun `옷은 벽과 밝기가 달라 몸이 배경에 묻히지 않는다`() {
        (ids + (1..200).map { "p$it" }).forEach { id ->
            val look = demoLook(id)
            assertThat((look.shirt.luminance() - look.wall.luminance()).absoluteValue).isAtLeast(0.18f)
        }
    }

    @Test
    fun `데모 참가자들은 서로 다른 생김새를 섞어 쓴다`() {
        val looks = ids.map(::demoLook)
        assertThat(looks.map { it.skin }.toSet().size).isAtLeast(3)
        assertThat(looks.map { it.hairStyle }.toSet().size).isAtLeast(3)
        assertThat(looks.map { it.scene }.toSet().size).isAtLeast(3)
    }
}
