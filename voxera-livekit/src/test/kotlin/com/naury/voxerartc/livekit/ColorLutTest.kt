package com.naury.voxerartc.livekit

import com.google.common.truth.Truth.assertThat
import com.naury.voxerartc.api.VideoEffect
import org.junit.Test

class ColorLutTest {

    private fun ByteArray.at(index: Int) = this[index].toInt() and 0xFF

    @Test
    fun `효과가 없으면 값이 그대로다`() {
        val lut = ColorLut(VideoEffect.None)
        listOf(0, 16, 128, 200, 255).forEach { value ->
            assertThat(lut.luma.at(value)).isEqualTo(value)
            assertThat(lut.chroma.at(value)).isEqualTo(value)
        }
    }

    @Test
    fun `흑백은 색 성분을 가운데로 모은다`() {
        val lut = ColorLut(VideoEffect(saturation = 0f))
        assertThat(lut.chroma.at(0)).isEqualTo(128)
        assertThat(lut.chroma.at(255)).isEqualTo(128)
        assertThat(lut.luma.at(77)).isEqualTo(77)
    }

    @Test
    fun `밝기는 올리고 범위를 넘지 않는다`() {
        val lut = ColorLut(VideoEffect(brightness = 0.2f))
        assertThat(lut.luma.at(100)).isEqualTo(151)
        assertThat(lut.luma.at(250)).isEqualTo(255)
    }

    @Test
    fun `대비를 올리면 가운데에서 멀어진다`() {
        val lut = ColorLut(VideoEffect(contrast = 1.5f))
        assertThat(lut.luma.at(200)).isGreaterThan(200)
        assertThat(lut.luma.at(50)).isLessThan(50)
    }

    @Test
    fun `범위를 벗어난 효과는 만들 수 없다`() {
        val result = runCatching { VideoEffect(saturation = 3f) }
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }
}
