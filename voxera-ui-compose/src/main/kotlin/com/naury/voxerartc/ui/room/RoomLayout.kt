package com.naury.voxerartc.ui.room

import com.naury.voxerartc.api.Participant

/** 영상 통화 보기 방식. [GALLERY]는 모두 같은 크기, [SPEAKER]는 말하는 사람을 크게. */
internal enum class StageView { GALLERY, SPEAKER }

/**
 * 무대 위 타일 하나. [key]는 참가자 id, 화면 공유는 `screen:<id>`이다.
 * 같은 key는 보기 방식이 바뀌어도 같은 타일로 이어져 자리만 움직인다(영상이 끊기지 않는다).
 */
internal data class StageItem(val key: String, val participant: Participant, val screenShare: Boolean = false)

/**
 * 무대에 무엇을 어떻게 놓을지. 자리 계산은 격자 컴포넌트가 하고, 여기서는 누구를 크게 볼지만 정한다.
 *
 * @property items 놓을 순서. 맨 앞이 나, 나머지는 들어온 순서다.
 * @property focusKey 크게 볼 타일. `null`이면 격자(2명이면 1:1)다.
 * @property selfKey 1:1에서 모서리 작은 화면이 될 타일.
 */
internal data class StageModel(val items: List<StageItem>, val focusKey: String?, val selfKey: String?)

/**
 * 누구를 크게 볼지 정한다.
 *
 * - 다른 사람이 화면을 공유하면 공유 화면이 크게, 나머지(공유한 사람의 카메라 포함)는 작은 줄.
 * - 1:1은 상대가 화면 전체, 나는 모서리 작은 화면. 나를 고정하면 서로 바뀐다.
 * - 고정한 사람이 있거나 [StageView.SPEAKER]면 그 사람(또는 말하는 사람)이 크게, 나머지는 작은 줄.
 * - 그 밖에는 모두 같은 크기 격자. 9명을 넘으면 격자가 쪽을 나눈다.
 *
 * @param lastSpeakerId 발표자 보기에서 크게 볼 사람. 화면이 잠깐 끼어든 소리에 흔들리지 않게 늦춰 정한 값이다.
 */
internal fun stageModel(participants: List<Participant>, pinnedId: String?, view: StageView, lastSpeakerId: String?): StageModel {
    val local = participants.firstOrNull { it.isLocal }
    val remotes = participants.filterNot { it.isLocal }
    val people = listOfNotNull(local) + remotes
    val items = people.map { StageItem(it.id, it) }
    val sharing = remotes.firstOrNull { it.screenShare != null }
    if (sharing != null) {
        val screen = StageItem("screen:${sharing.id}", sharing, screenShare = true)
        return StageModel(listOf(screen) + items, focusKey = screen.key, selfKey = local?.id)
    }
    if (remotes.size == 1 && local != null) {
        val small = if (pinnedId == local.id) remotes.single() else local
        return StageModel(items, focusKey = null, selfKey = small.id)
    }
    val pinned = people.firstOrNull { it.id == pinnedId }
    val speaker = remotes.firstOrNull { it.id == lastSpeakerId }
        ?: remotes.filter { it.isSpeaking }.maxByOrNull { it.audioLevel }
        ?: remotes.firstOrNull()
    val focus = pinned ?: speaker.takeIf { view == StageView.SPEAKER }
    return StageModel(items, focusKey = focus?.id, selfKey = local?.id)
}

/** 발표자 보기에서 지금 가장 크게 말하는 상대. 아무도 말하지 않으면 `null`. */
internal fun loudestRemote(participants: List<Participant>): String? =
    participants.filter { !it.isLocal && it.isSpeaking }.maxByOrNull { it.audioLevel }?.id

/**
 * 격자 열 수: 2명은 위아래(가로 화면은 좌우), 3~4명 2열, 5~6명 2열(가로 화면 3열), 7명부터 3열.
 *
 * [shortScreen](가로로 눕힌 휴대전화처럼 높이가 낮은 창)에서는 줄을 쌓으면 칸이 납작해져 세로 영상이 가는 띠로 보이므로,
 * 4명까지는 한 줄에, 그보다 많으면 두 줄에 나눠 놓는다.
 */
internal fun stageColumns(count: Int, landscape: Boolean, shortScreen: Boolean = false): Int = when {
    landscape && shortScreen -> if (count <= 4) count.coerceAtLeast(1) else (count + 1) / 2
    count <= 2 -> if (landscape) 2 else 1
    count <= 4 -> 2
    count <= 6 -> if (landscape) 3 else 2
    else -> 3
}

/** [stageColumns]의 낮은 창 기준. 창 높이가 이보다 낮으면(가로로 눕힌 휴대전화) 줄 수를 줄인다. */
internal const val SHORT_SCREEN_DP = 480
