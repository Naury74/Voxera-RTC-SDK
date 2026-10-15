package com.naury.voxerartc.api

import java.net.URI
import java.net.URISyntaxException

/** 서버 주소 검사 결과. */
public sealed interface ServerUrlCheck {
    /** 사용할 수 있는 주소. [normalized]는 앞뒤 공백과 끝의 `/`를 정리한 값이다. */
    public data class Valid(val normalized: String) : ServerUrlCheck

    /** 사용할 수 없는 주소. */
    public data class Invalid(val problem: ConfigurationProblem) : ServerUrlCheck
}

/**
 * 토큰을 보내도 되는 서버 주소인지 가리는 규칙. 기본은 TLS(`wss://`)만 허용한다.
 *
 * @property allowInsecureLocalhost `true`면 에뮬레이터와 로컬 개발 서버(`localhost`, `127.0.0.1`, `10.0.2.2`)에 한해
 *   `ws://`를 허용한다. 공개 배포 앱에서는 켜지 않는다.
 * @property allowedHosts 비어 있지 않으면 이 호스트 이름(정확히 일치하거나 하위 도메인)만 허용한다.
 */
public data class ServerUrlPolicy(
    val allowInsecureLocalhost: Boolean = false,
    val allowedHosts: Set<String> = emptySet(),
) {
    /** [raw]를 검사한다. 네트워크에 접속하지 않으며, 서버가 실제로 LiveKit 호환인지는 연결할 때 확인한다. */
    public fun check(raw: String): ServerUrlCheck {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ServerUrlCheck.Invalid(ConfigurationProblem.SERVER_URL_BLANK)
        if (trimmed.length > MAX_URL_LENGTH) return ServerUrlCheck.Invalid(ConfigurationProblem.SERVER_URL_MALFORMED)
        val uri = try {
            URI(trimmed)
        } catch (_: URISyntaxException) {
            return ServerUrlCheck.Invalid(ConfigurationProblem.SERVER_URL_MALFORMED)
        }
        val host = uri.host?.lowercase()
        if (host.isNullOrEmpty() || uri.userInfo != null) {
            return ServerUrlCheck.Invalid(ConfigurationProblem.SERVER_URL_MALFORMED)
        }
        when (uri.scheme?.lowercase()) {
            "wss" -> Unit
            "ws" -> if (!allowInsecureLocalhost || host !in LOCAL_HOSTS) {
                return ServerUrlCheck.Invalid(ConfigurationProblem.SERVER_URL_INSECURE)
            }
            else -> return ServerUrlCheck.Invalid(ConfigurationProblem.SERVER_URL_SCHEME)
        }
        if (allowedHosts.isNotEmpty() && allowedHosts.none { host == it.lowercase() || host.endsWith(".${it.lowercase()}") }) {
            return ServerUrlCheck.Invalid(ConfigurationProblem.SERVER_URL_NOT_ALLOWED)
        }
        return ServerUrlCheck.Valid(trimmed)
    }

    private companion object {
        const val MAX_URL_LENGTH = 2048
        val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")
    }
}
