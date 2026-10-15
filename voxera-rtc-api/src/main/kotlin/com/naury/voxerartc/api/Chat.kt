package com.naury.voxerartc.api

/**
 * 통화 중 주고받은 채팅 메시지 하나. 통화가 끝나면 사라지며, 늦게 들어온 사람은 그 전 메시지를 받지 못한다.
 *
 * [text]는 네트워크에서 온 그대로의 사용자 입력이다. 화면에는 일반 글자로만 그리고 링크·서식으로 해석하지 않는다.
 * 로그에도 남기지 않는다.
 *
 * @property id 보낸 쪽이 만든 메시지 ID. 같은 메시지가 두 번 도착하면 이 값으로 걸러 낸다.
 * @property atMs 받은(보낸) 시각. [VoxeraSession] 시계 기준이다.
 */
public data class ChatMessage(
    val id: String,
    val participantId: String,
    val text: String,
    val atMs: Long,
    val isLocal: Boolean,
) {
    public companion object {
        /** 메시지 한 개의 최대 글자 수. 넘으면 보내지 않고, 받은 메시지는 버린다. */
        public const val MAX_LENGTH: Int = 1_000

        /** 메시지 ID 최대 길이. */
        public const val MAX_ID_LENGTH: Int = 64

        /**
         * 받은 글을 화면에 그려도 되는 모양으로 다듬는다. 줄바꿈과 탭 말고는 제어 문자를 지우고 앞뒤 공백을 자른다.
         * 비었거나 [MAX_LENGTH]를 넘으면 `null`.
         */
        public fun sanitize(text: String): String? {
            val cleaned = text.filter { it == '\n' || it == '\t' || !it.isISOControl() }.trim()
            return cleaned.takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH }
        }
    }
}
