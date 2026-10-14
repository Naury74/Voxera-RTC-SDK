package com.naury.voxerartc.livekit

import com.naury.voxerartc.api.CredentialsUnavailableException
import com.naury.voxerartc.api.RtcCredentials
import com.naury.voxerartc.api.RtcCredentialsProvider
import io.livekit.android.token.TokenRequestOptions
import io.livekit.android.token.TokenSource
import io.livekit.android.token.TokenSourceResponse
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * LiveKit 표준 토큰 공급 방식을 [RtcCredentialsProvider]로 감싼다. 받은 토큰은 메모리에만 두며 저장하지 않는다.
 *
 * 운영 환경에서는 호스트가 자기 인증을 거치는 [RtcCredentialsProvider]를 직접 구현하는 것이 기본이다.
 */
public object LiveKitTokenSources {

    /**
     * LiveKit Cloud 개발 토큰 서버(Sandbox). **개발·테스트 전용**이며 누구나 ID만 알면 토큰을 받을 수 있다.
     * 공개 앱에 기본 ID를 넣지 않는다.
     *
     * @param tokenServerId 사용자가 직접 입력한 토큰 서버 ID.
     * @param participantName 다른 참가자에게 보일 이름.
     */
    public fun developmentTokenServer(tokenServerId: String, participantName: String): RtcCredentialsProvider {
        val source = TokenSource.fromDevelopmentTokenServer(tokenServerId.trim())
        return RtcCredentialsProvider { request ->
            source.fetch(TokenRequestOptions(roomName = request.roomId, participantName = participantName)).toCredentials()
        }
    }

    /**
     * LiveKit 표준 token endpoint 형식(`room_name`, `participant_name`을 POST하고 `server_url`, `participant_token`을 받음)을
     * 따르는 호스트 endpoint.
     *
     * @param headers 호스트 인증 헤더 등. 로그에 남기지 않는다.
     */
    public fun endpoint(url: String, participantName: String, headers: Map<String, String> = emptyMap()): RtcCredentialsProvider {
        val source = TokenSource.fromEndpoint(url.trim(), headers = headers)
        return RtcCredentialsProvider { request ->
            source.fetch(TokenRequestOptions(roomName = request.roomId, participantName = participantName)).toCredentials()
        }
    }

    /** 이미 발급받은 토큰. 새 토큰을 받을 수 없으므로 만료되면 다시 입력해야 한다. */
    public fun literal(serverUrl: String, accessToken: String): RtcCredentialsProvider {
        val credentials = RtcCredentials(serverUrl.trim(), accessToken.trim())
        return RtcCredentialsProvider { credentials }
    }
}

/** LiveKit 토큰 공급 실패를 Voxera 분류로 바꾼다. 망 오류만 다시 시도할 수 있다. */
internal fun Result<TokenSourceResponse>.toCredentials(): RtcCredentials {
    val response = getOrElse { failure -> throw classifyTokenFailure(failure) }
    return RtcCredentials(response.serverUrl, response.participantToken)
}

internal fun classifyTokenFailure(failure: Throwable): Exception = when (failure) {
    is CancellationException -> failure
    is IOException -> CredentialsUnavailableException(CredentialsUnavailableException.Kind.TRANSIENT, "token request failed", failure)
    // LiveKit 구현은 HTTP 상태를 보지 않고 본문을 해석하므로, 거절 응답은 해석 실패로 온다.
    else -> CredentialsUnavailableException(CredentialsUnavailableException.Kind.REJECTED, "token source rejected the request", failure)
}
