package com.naury.voxerartc.api

/**
 * 룸 연결에 쓰는 서버 주소와 접근 토큰.
 *
 * 토큰은 호스트 서버가 짧은 유효기간으로 발급한 값이어야 한다. [toString]은 토큰을 가리므로 로그에 그대로 남겨도
 * 토큰이 새지 않는다. SDK는 이 값을 메모리에만 두고 저장하지 않는다.
 *
 * @property serverUrl LiveKit 호환 서버의 `wss://` 주소. 로컬 개발에서만 `ws://`를 허용한다([ServerUrlPolicy]).
 * @property accessToken 서버가 검증할 JWT 접근 토큰. 비어 있으면 연결 전에 거부된다.
 */
public class RtcCredentials(
    public val serverUrl: String,
    public val accessToken: String,
) {
    override fun equals(other: Any?): Boolean =
        other is RtcCredentials && other.serverUrl == serverUrl && other.accessToken == accessToken

    override fun hashCode(): Int = 31 * serverUrl.hashCode() + accessToken.hashCode()

    override fun toString(): String = "RtcCredentials(serverUrl=$serverUrl, accessToken=<redacted>)"
}

/** 자격 증명을 다시 요청하는 이유. 호스트는 이 값으로 캐시 사용 여부를 정할 수 있다. */
public enum class CredentialReason {
    /** 처음 입장. */
    INITIAL_JOIN,

    /** 연결이 끊겨 새로 연결해야 함. */
    RECONNECT,

    /** 서버가 토큰을 만료·거부해 새 토큰이 필요함. 같은 토큰을 다시 돌려주면 다시 실패한다. */
    REAUTHENTICATE,
}

/** 자격 증명 요청. [roomId]는 호스트가 권한을 검증할 룸 이름이다. */
public data class CredentialRequest(val roomId: String, val reason: CredentialReason)

/**
 * 호스트가 구현하는 자격 증명 공급자. 보통 호스트 백엔드의 인증된 token endpoint를 부른다.
 *
 * 실패는 [CredentialsUnavailableException]으로 알리면 오류 종류가 정확히 분류된다. 그 밖의 예외는
 * [VoxeraError.InternalFailure]로 처리한다. 호출은 SDK 세션 코루틴에서 일어나고 사용자가 나가면 취소된다.
 */
public fun interface RtcCredentialsProvider {
    public suspend fun getCredentials(request: CredentialRequest): RtcCredentials
}

/**
 * 자격 증명 공급자가 실패 이유를 알릴 때 던지는 예외. [message]는 사용자에게 보이지 않으며 토큰을 담지 않아야 한다.
 */
public class CredentialsUnavailableException(
    public val kind: Kind,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    public enum class Kind {
        /** 호스트 로그인·권한 문제(401/403). SDK는 자동으로 다시 시도하지 않는다. */
        UNAUTHORIZED,

        /** 일시적인 서버·망 문제(5xx, 시간 초과). 제한된 횟수만 다시 시도한다. */
        TRANSIENT,
    }
}
