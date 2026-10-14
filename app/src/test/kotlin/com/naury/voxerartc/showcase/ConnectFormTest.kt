package com.naury.voxerartc.showcase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ConnectFormTest {

    private fun jwt(exp: Long): String {
        fun seg(json: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
        return "${seg("""{"alg":"HS256"}""")}.${seg("""{"exp":$exp}""")}.c2ln"
    }

    @Test
    fun `개발 토큰 서버는 ID·룸·이름만 검사한다`() {
        val form = ConnectForm(tokenServerId = "my-sandbox-1x2y", roomName = "team-demo", displayName = "지연")
        assertTrue(form.problems(0).isEmpty())
        assertEquals(mapOf(FormField.TOKEN_SERVER_ID to FieldProblem.REQUIRED), form.copy(tokenServerId = " ").problems(0))
    }

    @Test
    fun `기존 토큰에 API Secret을 넣으면 JWT가 아니라고 알린다`() {
        val form = ConnectForm(
            method = CredentialMethod.EXISTING_TOKEN,
            serverUrl = "wss://p.livekit.cloud",
            accessToken = "Xk2s9fJq0aLm4Vb7Tz1pYw8Rc3Nd6Ge5Hu0Io2Kj",
            roomName = "team-demo",
        )
        assertEquals(FieldProblem.TOKEN_NOT_JWT, form.problems(0)[FormField.ACCESS_TOKEN])
        assertTrue(form.copy(accessToken = jwt(2_000)).problems(1_000).isEmpty())
        assertEquals(FieldProblem.TOKEN_EXPIRED, form.copy(accessToken = jwt(10)).problems(1_000)[FormField.ACCESS_TOKEN])
    }

    @Test
    fun `평문 주소는 로컬 개발 허용 시 로컬 호스트에만 열린다`() {
        val token = ConnectForm(method = CredentialMethod.EXISTING_TOKEN, serverUrl = "ws://10.0.2.2:7880", accessToken = jwt(2_000), roomName = "r")
        assertEquals(FieldProblem.URL_INSECURE, token.problems(0)[FormField.SERVER_URL])
        assertFalse(FormField.SERVER_URL in token.copy(allowLocalDev = true).problems(0))

        assertEquals(FieldProblem.URL_INSECURE, checkEndpointUrl("http://10.0.2.2:3000/token", allowLocalDev = false))
        assertEquals(null, checkEndpointUrl("http://10.0.2.2:3000/token", allowLocalDev = true))
        assertEquals(FieldProblem.URL_INSECURE, checkEndpointUrl("http://example.com/token", allowLocalDev = true))
        assertEquals(null, checkEndpointUrl("https://example.com/token", allowLocalDev = false))
        assertEquals(FieldProblem.URL_SCHEME, checkEndpointUrl("wss://example.com", allowLocalDev = false))
    }

    @Test
    fun `문자열로 바꿔도 토큰과 토큰 서버 ID가 드러나지 않는다`() {
        val text = ConnectForm(tokenServerId = "sandbox-secret-id", accessToken = "eyJsecret").toString()
        assertFalse("sandbox-secret-id" in text)
        assertFalse("eyJsecret" in text)
    }

    @Test
    fun `기존 토큰 방식에는 이름 칸이 없다`() {
        assertFalse(FormField.DISPLAY_NAME in ConnectForm(method = CredentialMethod.EXISTING_TOKEN).fields)
    }
}
