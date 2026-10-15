package com.naury.voxerartc.android

import com.google.common.truth.Truth.assertThat
import com.naury.voxerartc.api.LocalMediaState
import com.naury.voxerartc.api.MediaToggle
import org.junit.Test

class CallNotificationStatusTest {

    private val on = MediaToggle(desired = true, actual = true)
    private val voice = LocalMediaState(microphone = on)

    @Test
    fun `마이크만 켠 음성 통화는 배지 없이 보여 준다`() {
        assertThat(callNotificationStatus(reconnecting = false, media = voice)).isEqualTo(CallNotificationStatus.ACTIVE)
    }

    @Test
    fun `카메라를 켜면 영상 통화, 마이크를 끄면 마이크 꺼짐이 먼저다`() {
        assertThat(callNotificationStatus(false, voice.copy(camera = on))).isEqualTo(CallNotificationStatus.VIDEO)
        assertThat(callNotificationStatus(false, voice.copy(camera = on, microphone = MediaToggle.Off))).isEqualTo(CallNotificationStatus.MUTED)
    }

    @Test
    fun `통화가 끊길 수 있는 상황을 화면 공유와 마이크보다 먼저 알린다`() {
        val sharingMuted = LocalMediaState(screenShare = on)
        assertThat(callNotificationStatus(false, sharingMuted)).isEqualTo(CallNotificationStatus.SHARING)
        assertThat(callNotificationStatus(false, sharingMuted.copy(audioInterrupted = true))).isEqualTo(CallNotificationStatus.ON_HOLD)
        assertThat(callNotificationStatus(true, sharingMuted.copy(audioInterrupted = true))).isEqualTo(CallNotificationStatus.RECONNECTING)
    }

    @Test
    fun `상황마다 다른 아이콘과 문구를 쓴다`() {
        val entries = CallNotificationStatus.entries
        assertThat(entries.map { it.icon }.toSet()).hasSize(entries.size)
        assertThat(entries.map { it.text }.toSet()).hasSize(entries.size)
    }
}
