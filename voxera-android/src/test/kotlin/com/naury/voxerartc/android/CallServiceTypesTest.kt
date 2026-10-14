package com.naury.voxerartc.android

import android.content.pm.ServiceInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CallServiceTypesTest {

    @Test
    fun `허용된 권한의 타입만 켠다`() {
        assertThat(callServiceTypes(microphoneGranted = true, cameraGranted = false, cameraActive = true))
            .isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        assertThat(callServiceTypes(microphoneGranted = true, cameraGranted = true, cameraActive = true))
            .isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
    }

    @Test
    fun `카메라가 꺼져 있으면 카메라 타입을 빼고, 권한이 하나도 없으면 서비스를 띄우지 않는다`() {
        assertThat(callServiceTypes(microphoneGranted = true, cameraGranted = true, cameraActive = false))
            .isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        assertThat(callServiceTypes(microphoneGranted = false, cameraGranted = false, cameraActive = true)).isEqualTo(0)
    }
}
