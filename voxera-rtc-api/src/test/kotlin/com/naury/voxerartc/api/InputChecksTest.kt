package com.naury.voxerartc.api

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Base64

class InputChecksTest {

    private fun segment(json: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
    private fun jwt(payload: String) = "${segment("""{"alg":"HS256","typ":"JWT"}""")}.${segment(payload)}.c2ln"

    @Test
    fun `룸 이름 규칙`() {
        assertThat(InputChecks.checkRoomName("team-demo_1.a")).isNull()
        assertThat(InputChecks.checkRoomName(" ")).isEqualTo(ConfigurationProblem.ROOM_NAME_BLANK)
        assertThat(InputChecks.checkRoomName("team demo")).isEqualTo(ConfigurationProblem.ROOM_NAME_INVALID)
        assertThat(InputChecks.checkRoomName("a".repeat(129))).isEqualTo(ConfigurationProblem.ROOM_NAME_INVALID)
    }

    @Test
    fun `표시 이름 규칙`() {
        assertThat(InputChecks.checkDisplayName("지연")).isNull()
        assertThat(InputChecks.checkDisplayName("")).isEqualTo(ConfigurationProblem.DISPLAY_NAME_BLANK)
        assertThat(InputChecks.checkDisplayName("x".repeat(65))).isEqualTo(ConfigurationProblem.DISPLAY_NAME_INVALID)
    }

    @Test
    fun `유효한 JWT 모양은 통과한다`() {
        assertThat(InputChecks.checkAccessToken(jwt("""{"exp":2000,"sub":"u"}"""), nowEpochSeconds = 1000)).isNull()
        assertThat(InputChecks.checkAccessToken(jwt("""{"sub":"u"}"""), nowEpochSeconds = 1000)).isNull()
    }

    @Test
    fun `API Secret이나 Key를 토큰란에 넣으면 JWT가 아니라고 알린다`() {
        assertThat(InputChecks.checkAccessToken("Xk2s9fJq0aLm4Vb7Tz1pYw8Rc3Nd6Ge5Hu0Io2Kj", 0)).isEqualTo(ConfigurationProblem.TOKEN_NOT_JWT)
        assertThat(InputChecks.checkAccessToken("APIabcdEFGH1234", 0)).isEqualTo(ConfigurationProblem.TOKEN_NOT_JWT)
        assertThat(InputChecks.checkAccessToken("a.b.c", 0)).isEqualTo(ConfigurationProblem.TOKEN_NOT_JWT)
        assertThat(InputChecks.checkAccessToken("", 0)).isEqualTo(ConfigurationProblem.TOKEN_BLANK)
    }

    @Test
    fun `만료된 토큰을 알린다`() {
        assertThat(InputChecks.checkAccessToken(jwt("""{"exp": 999}"""), nowEpochSeconds = 1000)).isEqualTo(ConfigurationProblem.TOKEN_EXPIRED)
    }

    @Test
    fun `자격 증명 문자열에는 토큰이 드러나지 않는다`() {
        val text = RtcCredentials("wss://a.livekit.cloud", "eyJsecret-token").toString()
        assertThat(text).doesNotContain("eyJsecret-token")
        assertThat(text).contains("<redacted>")
    }
}
