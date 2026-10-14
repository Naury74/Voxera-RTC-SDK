package com.naury.voxerartc.livekit

import android.media.AudioManager
import android.os.PowerManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceConditionsTest {

    @Test
    fun `SEVERE 이상의 발열에서만 효과를 끈다`() {
        assertThat(DeviceConditions.isOverheated(PowerManager.THERMAL_STATUS_NONE)).isFalse()
        assertThat(DeviceConditions.isOverheated(PowerManager.THERMAL_STATUS_MODERATE)).isFalse()
        assertThat(DeviceConditions.isOverheated(PowerManager.THERMAL_STATUS_SEVERE)).isTrue()
        assertThat(DeviceConditions.isOverheated(PowerManager.THERMAL_STATUS_SHUTDOWN)).isTrue()
    }

    @Test
    fun `휴대전화 통화와 벨소리만 다른 통화로 본다`() {
        assertThat(DeviceConditions.isInterrupting(AudioManager.MODE_IN_CALL)).isTrue()
        assertThat(DeviceConditions.isInterrupting(AudioManager.MODE_RINGTONE)).isTrue()
        assertThat(DeviceConditions.isInterrupting(AudioManager.MODE_IN_COMMUNICATION)).isFalse()
        assertThat(DeviceConditions.isInterrupting(AudioManager.MODE_NORMAL)).isFalse()
    }
}
