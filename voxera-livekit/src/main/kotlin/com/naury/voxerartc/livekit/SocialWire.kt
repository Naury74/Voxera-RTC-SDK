package com.naury.voxerartc.livekit

import com.naury.voxerartc.api.ChatMessage

/**
 * 채팅을 LiveKit 데이터 메시지로 주고받는 형식. 주제는 [TOPIC], 내용은 `1:<메시지 ID>:<글>`(UTF-8)이다.
 * 메시지 ID는 영문·숫자·`-`만 쓴다. 모르는 버전, 잘못된 ID, 너무 큰 내용은 버린다. 글을 다듬는 일은 세션이 맡는다.
 */
internal object ChatWire {
    const val TOPIC = "voxera.chat"
    private const val VERSION = "1"

    // 글자 하나가 UTF-8로 최대 4바이트이므로 최대 글자 수의 4배에 머리 부분 여유를 더한다.
    private const val MAX_BYTES = ChatMessage.MAX_LENGTH * 4 + ChatMessage.MAX_ID_LENGTH + 8
    private val idPattern = Regex("[A-Za-z0-9-]{1,${ChatMessage.MAX_ID_LENGTH}}")

    fun encode(messageId: String, text: String): ByteArray = "$VERSION:$messageId:$text".toByteArray(Charsets.UTF_8)

    /** 해석한 (메시지 ID, 글). 형식이 맞지 않으면 `null`. */
    fun decode(topic: String?, data: ByteArray): Pair<String, String>? {
        if (topic != TOPIC || data.isEmpty() || data.size > MAX_BYTES) return null
        val raw = data.toString(Charsets.UTF_8)
        val parts = raw.split(':', limit = 3)
        if (parts.size != 3 || parts[0] != VERSION || !idPattern.matches(parts[1])) return null
        return parts[1] to parts[2]
    }
}

/** 손들기 형식. 주제는 [TOPIC], 내용은 `1:up` 또는 `1:down`이다. */
internal object HandWire {
    const val TOPIC = "voxera.hand"
    private const val UP = "1:up"
    private const val DOWN = "1:down"

    fun encode(raised: Boolean): ByteArray = (if (raised) UP else DOWN).toByteArray(Charsets.UTF_8)

    /** 든 상태면 `true`, 내린 상태면 `false`, 형식이 아니면 `null`. */
    fun decode(topic: String?, data: ByteArray): Boolean? {
        if (topic != TOPIC || data.size > 16) return null
        return when (data.toString(Charsets.UTF_8)) {
            UP -> true
            DOWN -> false
            else -> null
        }
    }
}
