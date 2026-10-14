package com.naury.voxerartc.livekit

import com.naury.voxerartc.api.Reaction

/**
 * 반응을 LiveKit 데이터 메시지로 주고받는 형식. 주제는 [TOPIC], 내용은 `1:<wireName>`(UTF-8)이다.
 * 앞의 숫자는 형식 버전이며, 모르는 버전·이름·너무 긴 내용은 버린다.
 */
internal object ReactionWire {
    const val TOPIC = "voxera.reaction"
    private const val VERSION = "1"
    private const val MAX_BYTES = 64

    fun encode(reaction: Reaction): ByteArray = "$VERSION:${reaction.wireName}".toByteArray(Charsets.UTF_8)

    fun decode(topic: String?, data: ByteArray): Reaction? {
        if (topic != TOPIC || data.isEmpty() || data.size > MAX_BYTES) return null
        val text = data.toString(Charsets.UTF_8)
        val version = text.substringBefore(':', missingDelimiterValue = "")
        if (version != VERSION) return null
        return Reaction.fromWireName(text.substringAfter(':'))
    }
}
