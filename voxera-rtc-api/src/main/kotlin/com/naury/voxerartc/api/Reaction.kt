package com.naury.voxerartc.api

/**
 * 통화 중 보내는 짧은 반응. 정해진 종류만 주고받아 임의 문자열이 화면에 그려지지 않게 한다.
 *
 * @property wireName 엔진이 주고받을 때 쓰는 이름. 바꾸면 이전 버전과 서로 알아보지 못한다.
 */
public enum class Reaction(public val wireName: String) {
    THUMBS_UP("thumbs_up"),
    HEART("heart"),
    LAUGH("laugh"),
    CLAP("clap"),
    SURPRISED("surprised"),
    CELEBRATE("celebrate"),
    ;

    public companion object {
        /** 모르는 이름이면 `null`. 새 버전이 보낸 반응은 조용히 버린다. */
        public fun fromWireName(name: String): Reaction? = entries.firstOrNull { it.wireName == name }
    }
}

/** 받은(또는 내가 보낸) 반응 하나. [atMs]는 `VoxeraSession` 시계 기준이다. */
public data class ReactionEvent(val participantId: String, val reaction: Reaction, val atMs: Long)
