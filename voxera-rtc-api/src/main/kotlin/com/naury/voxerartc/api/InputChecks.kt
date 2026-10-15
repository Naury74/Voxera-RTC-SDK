package com.naury.voxerartc.api

import java.util.Base64

/** 설정 화면에서 받은 룸 이름과 토큰을 연결 전에 검사한다. 값 자체는 결과나 로그에 담지 않는다. */
public object InputChecks {
    /** 룸 이름 최대 길이(문자 수). */
    public const val MAX_ROOM_NAME_LENGTH: Int = 128

    /** 표시 이름 최대 길이(문자 수). */
    public const val MAX_DISPLAY_NAME_LENGTH: Int = 64

    private val roomNamePattern = Regex("^[A-Za-z0-9._\\-]+$")

    /** 룸 이름은 1~[MAX_ROOM_NAME_LENGTH]자이고 영문, 숫자, `.`, `_`, `-`만 쓸 수 있다. 문제가 없으면 `null`. */
    public fun checkRoomName(raw: String): ConfigurationProblem? {
        val name = raw.trim()
        return when {
            name.isEmpty() -> ConfigurationProblem.ROOM_NAME_BLANK
            name.length > MAX_ROOM_NAME_LENGTH || !roomNamePattern.matches(name) -> ConfigurationProblem.ROOM_NAME_INVALID
            else -> null
        }
    }

    /** 표시 이름은 앞뒤 공백을 뺀 길이가 1~[MAX_DISPLAY_NAME_LENGTH]자여야 한다. 문제가 없으면 `null`. */
    public fun checkDisplayName(raw: String): ConfigurationProblem? {
        val name = raw.trim()
        return when {
            name.isEmpty() -> ConfigurationProblem.DISPLAY_NAME_BLANK
            name.length > MAX_DISPLAY_NAME_LENGTH -> ConfigurationProblem.DISPLAY_NAME_INVALID
            else -> null
        }
    }

    /**
     * 붙여 넣은 접근 토큰의 모양을 검사한다. 서명은 검증하지 않는다(서버만 할 수 있다).
     *
     * - JWT 세 조각이 아니면 [ConfigurationProblem.TOKEN_NOT_JWT]. API Secret이나 API Key를 토큰 칸에 잘못 넣으면 여기서 걸린다.
     * - `exp`가 [nowEpochSeconds]보다 이전이면 [ConfigurationProblem.TOKEN_EXPIRED].
     */
    public fun checkAccessToken(raw: String, nowEpochSeconds: Long): ConfigurationProblem? {
        val token = raw.trim()
        if (token.isEmpty()) return ConfigurationProblem.TOKEN_BLANK
        val parts = token.split('.')
        if (parts.size != 3 || parts.any { it.isEmpty() }) return ConfigurationProblem.TOKEN_NOT_JWT
        val payload = decodeSegment(parts[1]) ?: return ConfigurationProblem.TOKEN_NOT_JWT
        if (decodeSegment(parts[0])?.trimStart()?.startsWith("{") != true || !payload.trimStart().startsWith("{")) {
            return ConfigurationProblem.TOKEN_NOT_JWT
        }
        val exp = expPattern.find(payload)?.groupValues?.get(1)?.toLongOrNull()
        if (exp != null && exp <= nowEpochSeconds) return ConfigurationProblem.TOKEN_EXPIRED
        return null
    }

    private val expPattern = Regex("\"exp\"\\s*:\\s*(\\d+)")

    private fun decodeSegment(segment: String): String? = try {
        String(Base64.getUrlDecoder().decode(segment), Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        null
    }
}
