package com.naury.voxerartc.ui.prejoin

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaPermissionsTest {

    @Test
    fun `요청 전에는 rationale이 없어도 물어볼 수 있다`() {
        assertThat(resolvePermission(granted = false, requestedBefore = false, shouldShowRationale = false)).isEqualTo(PermissionStatus.REQUESTABLE)
    }

    @Test
    fun `한 번 거부하면 다시 물을 수 있고, 이유 표시가 사라지면 막힌 것으로 본다`() {
        assertThat(resolvePermission(granted = false, requestedBefore = true, shouldShowRationale = true)).isEqualTo(PermissionStatus.DENIED)
        assertThat(resolvePermission(granted = false, requestedBefore = true, shouldShowRationale = false)).isEqualTo(PermissionStatus.BLOCKED)
    }

    @Test
    fun `허용되어 있으면 요청 기록과 관계없이 GRANTED`() {
        assertThat(resolvePermission(granted = true, requestedBefore = true, shouldShowRationale = false)).isEqualTo(PermissionStatus.GRANTED)
    }
}
