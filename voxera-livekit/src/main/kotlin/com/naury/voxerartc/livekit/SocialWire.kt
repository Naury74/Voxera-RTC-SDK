package com.naury.voxerartc.livekit

import com.naury.voxerartc.api.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile

/**
 * 채팅은 LiveKit 표준 채팅과 같은 텍스트 스트림(주제 [TOPIC])으로 주고받는다. LiveKit Meet 같은 다른 클라이언트와도 서로 읽힌다.
 * 메시지 ID는 스트림 ID다. 받는 쪽은 [MAX_BYTES]를 넘는 스트림을 끝까지 읽지 않고 버린다. 글을 다듬는 일은 세션이 맡는다.
 */
internal object ChatStream {
    const val TOPIC = "lk.chat"

    // 글자 하나가 UTF-8로 최대 4바이트다.
    const val MAX_BYTES = ChatMessage.MAX_LENGTH * 4

    /** [chunks]를 이어 붙인다. 합이 [maxBytes]를 넘으면 그 자리에서 멈추고 `null`. */
    suspend fun read(chunks: Flow<String>, declaredBytes: Long?, maxBytes: Int = MAX_BYTES): String? {
        if (declaredBytes != null && declaredBytes > maxBytes) return null
        val text = StringBuilder()
        var bytes = 0
        var overflow = false
        chunks.takeWhile { chunk ->
            bytes += chunk.toByteArray(Charsets.UTF_8).size
            overflow = bytes > maxBytes
            if (!overflow) text.append(chunk)
            !overflow
        }.collect()
        return if (overflow) null else text.toString()
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
