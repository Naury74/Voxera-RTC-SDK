package com.naury.voxerartc.showcase

import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.api.ConfigurationProblem
import com.naury.voxerartc.api.InputChecks
import com.naury.voxerartc.api.ServerUrlCheck
import com.naury.voxerartc.api.ServerUrlPolicy
import java.net.URI
import java.net.URISyntaxException

/** BYO LiveKit 자격 증명을 얻는 방법. API Secret을 받는 방법은 없다. */
enum class CredentialMethod { DEV_TOKEN_SERVER, EXISTING_TOKEN, TOKEN_ENDPOINT }

enum class FormField { TOKEN_SERVER_ID, SERVER_URL, ACCESS_TOKEN, ENDPOINT_URL, ROOM_NAME, DISPLAY_NAME }

/** 입력칸 하나의 문제. 화면 문구는 [ConnectScreen]이 고른다. */
enum class FieldProblem { REQUIRED, URL_MALFORMED, URL_SCHEME, URL_INSECURE, ROOM_INVALID, NAME_TOO_LONG, TOKEN_NOT_JWT, TOKEN_EXPIRED }

/**
 * BYO 설정 입력값. 토큰과 토큰 서버 ID는 화면이 메모리에만 들고 있으며 저장하지 않는다. [toString]은 비밀 값을 가린다.
 *
 * @property allowLocalDev 에뮬레이터·로컬 개발 서버(`10.0.2.2`, `localhost`)에 한해 `ws://`·`http://`를 허용한다.
 */
data class ConnectForm(
    val method: CredentialMethod = CredentialMethod.DEV_TOKEN_SERVER,
    val tokenServerId: String = "",
    val serverUrl: String = "",
    val accessToken: String = "",
    val endpointUrl: String = "",
    val roomName: String = "",
    val displayName: String = "",
    val mode: CallMode = CallMode.VOICE,
    val allowLocalDev: Boolean = false,
) {
    /** 이 방법에서 보이는 입력칸. 기존 토큰에는 이름이 이미 들어 있으므로 이름 칸이 없다. */
    val fields: List<FormField>
        get() = when (method) {
            CredentialMethod.DEV_TOKEN_SERVER -> listOf(FormField.TOKEN_SERVER_ID, FormField.ROOM_NAME, FormField.DISPLAY_NAME)
            CredentialMethod.EXISTING_TOKEN -> listOf(FormField.SERVER_URL, FormField.ACCESS_TOKEN, FormField.ROOM_NAME)
            CredentialMethod.TOKEN_ENDPOINT -> listOf(FormField.ENDPOINT_URL, FormField.ROOM_NAME, FormField.DISPLAY_NAME)
        }

    val serverUrlPolicy: ServerUrlPolicy get() = ServerUrlPolicy(allowInsecureLocalhost = allowLocalDev)

    /** 보이는 입력칸의 문제. 비어 있으면 연결할 수 있다. */
    fun problems(nowEpochSeconds: Long): Map<FormField, FieldProblem> = fields.mapNotNull { field ->
        problemOf(field, nowEpochSeconds)?.let { field to it }
    }.toMap()

    private fun problemOf(field: FormField, now: Long): FieldProblem? = when (field) {
        FormField.TOKEN_SERVER_ID -> when {
            tokenServerId.isBlank() -> FieldProblem.REQUIRED
            tokenServerId.trim().any { it.isWhitespace() } || tokenServerId.trim().length > 128 -> FieldProblem.URL_MALFORMED
            else -> null
        }
        FormField.SERVER_URL -> when (val check = serverUrlPolicy.check(serverUrl)) {
            is ServerUrlCheck.Valid -> null
            is ServerUrlCheck.Invalid -> check.problem.toFieldProblem()
        }
        FormField.ACCESS_TOKEN -> InputChecks.checkAccessToken(accessToken, now)?.toFieldProblem()
        FormField.ENDPOINT_URL -> checkEndpointUrl(endpointUrl, allowLocalDev)
        FormField.ROOM_NAME -> InputChecks.checkRoomName(roomName)?.toFieldProblem()
        FormField.DISPLAY_NAME -> InputChecks.checkDisplayName(displayName)?.toFieldProblem()
    }

    override fun toString(): String =
        "ConnectForm(method=$method, serverUrl=$serverUrl, endpointUrl=$endpointUrl, roomName=$roomName, mode=$mode, " +
            "tokenServerId=<redacted>, accessToken=<redacted>)"
}

private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

/** token endpoint 주소. 기본은 `https://`만, 로컬 개발 허용 시 로컬 주소에 한해 `http://`. */
internal fun checkEndpointUrl(raw: String, allowLocalDev: Boolean): FieldProblem? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return FieldProblem.REQUIRED
    val uri = try {
        URI(trimmed)
    } catch (_: URISyntaxException) {
        return FieldProblem.URL_MALFORMED
    }
    val host = uri.host?.lowercase()
    if (host.isNullOrEmpty() || uri.userInfo != null) return FieldProblem.URL_MALFORMED
    return when (uri.scheme?.lowercase()) {
        "https" -> null
        "http" -> if (allowLocalDev && host in LOCAL_HOSTS) null else FieldProblem.URL_INSECURE
        else -> FieldProblem.URL_SCHEME
    }
}

private fun ConfigurationProblem.toFieldProblem(): FieldProblem = when (this) {
    ConfigurationProblem.SERVER_URL_BLANK, ConfigurationProblem.ROOM_NAME_BLANK, ConfigurationProblem.DISPLAY_NAME_BLANK,
    ConfigurationProblem.TOKEN_BLANK -> FieldProblem.REQUIRED
    ConfigurationProblem.SERVER_URL_MALFORMED, ConfigurationProblem.SERVER_URL_NOT_ALLOWED,
    ConfigurationProblem.TOKEN_SOURCE_REJECTED -> FieldProblem.URL_MALFORMED
    ConfigurationProblem.SERVER_URL_SCHEME -> FieldProblem.URL_SCHEME
    ConfigurationProblem.SERVER_URL_INSECURE -> FieldProblem.URL_INSECURE
    ConfigurationProblem.ROOM_NAME_INVALID -> FieldProblem.ROOM_INVALID
    ConfigurationProblem.DISPLAY_NAME_INVALID -> FieldProblem.NAME_TOO_LONG
    ConfigurationProblem.TOKEN_NOT_JWT -> FieldProblem.TOKEN_NOT_JWT
    ConfigurationProblem.TOKEN_EXPIRED -> FieldProblem.TOKEN_EXPIRED
}
