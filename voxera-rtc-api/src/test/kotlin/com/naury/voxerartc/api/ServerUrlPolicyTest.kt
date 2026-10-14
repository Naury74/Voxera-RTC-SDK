package com.naury.voxerartc.api

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServerUrlPolicyTest {

    private val strict = ServerUrlPolicy()

    @Test
    fun `wss 주소는 공백과 끝 슬래시를 정리해 허용한다`() {
        assertThat(strict.check("  wss://my-project.livekit.cloud/ "))
            .isEqualTo(ServerUrlCheck.Valid("wss://my-project.livekit.cloud"))
    }

    @Test
    fun `빈 주소와 잘못된 주소를 구분한다`() {
        assertThat(strict.check("  ")).isEqualTo(ServerUrlCheck.Invalid(ConfigurationProblem.SERVER_URL_BLANK))
        assertThat(strict.check("wss://")).isEqualTo(ServerUrlCheck.Invalid(ConfigurationProblem.SERVER_URL_MALFORMED))
        assertThat(strict.check("wss://exa mple.com")).isEqualTo(ServerUrlCheck.Invalid(ConfigurationProblem.SERVER_URL_MALFORMED))
    }

    @Test
    fun `https와 평문 ws는 기본으로 막는다`() {
        assertThat(strict.check("https://my.livekit.cloud")).isEqualTo(ServerUrlCheck.Invalid(ConfigurationProblem.SERVER_URL_SCHEME))
        assertThat(strict.check("ws://my.livekit.cloud")).isEqualTo(ServerUrlCheck.Invalid(ConfigurationProblem.SERVER_URL_INSECURE))
        assertThat(strict.check("ws://10.0.2.2:7880")).isEqualTo(ServerUrlCheck.Invalid(ConfigurationProblem.SERVER_URL_INSECURE))
    }

    @Test
    fun `로컬 개발 허용 시에도 원격 호스트의 ws는 막는다`() {
        val local = ServerUrlPolicy(allowInsecureLocalhost = true)
        assertThat(local.check("ws://10.0.2.2:7880")).isEqualTo(ServerUrlCheck.Valid("ws://10.0.2.2:7880"))
        assertThat(local.check("ws://localhost:7880")).isEqualTo(ServerUrlCheck.Valid("ws://localhost:7880"))
        assertThat(local.check("ws://my.livekit.cloud")).isEqualTo(ServerUrlCheck.Invalid(ConfigurationProblem.SERVER_URL_INSECURE))
    }

    @Test
    fun `사용자 정보가 든 주소는 거부한다`() {
        assertThat(strict.check("wss://user:secret@my.livekit.cloud"))
            .isEqualTo(ServerUrlCheck.Invalid(ConfigurationProblem.SERVER_URL_MALFORMED))
    }

    @Test
    fun `허용 목록은 정확한 호스트와 하위 도메인만 통과시킨다`() {
        val policy = ServerUrlPolicy(allowedHosts = setOf("livekit.cloud"))
        assertThat(policy.check("wss://a.livekit.cloud")).isInstanceOf(ServerUrlCheck.Valid::class.java)
        assertThat(policy.check("wss://livekit.cloud")).isInstanceOf(ServerUrlCheck.Valid::class.java)
        assertThat(policy.check("wss://evil-livekit.cloud")).isEqualTo(ServerUrlCheck.Invalid(ConfigurationProblem.SERVER_URL_NOT_ALLOWED))
    }
}
