package com.naury.voxerartc.ui.room

import com.naury.voxerartc.api.Participant

/** 타일 격자 크기. */
internal data class GridSpec(val columns: Int, val rows: Int) {
    val capacity: Int get() = columns * rows
}

/** 한 화면에 보여 줄 최대 타일 수. 넘치면 마지막 칸에 남은 인원을 보여 준다. */
internal const val MAX_VISIBLE_TILES = 9

/**
 * 참가자 수에 맞는 격자. 1명은 전체, 2명은 세로로 위아래(가로 화면은 좌우), 3~4명 2×2, 5~6명 2×3(가로 3×2),
 * 7명 이상 3×3.
 */
internal fun gridFor(count: Int, landscape: Boolean): GridSpec = when {
    count <= 1 -> GridSpec(1, 1)
    count == 2 -> if (landscape) GridSpec(2, 1) else GridSpec(1, 2)
    count <= 4 -> GridSpec(2, 2)
    count <= 6 -> if (landscape) GridSpec(3, 2) else GridSpec(2, 3)
    else -> GridSpec(3, 3)
}

/**
 * 보여 줄 타일과 숨겨진 인원 수. 많을 때는 로컬 참가자와 말하는 참가자를 먼저 보여 주고, 나머지는 원래 순서를 지킨다.
 * 넘치면 마지막 칸 하나를 "+N" 칸으로 비워 둔다.
 */
internal fun visibleTiles(participants: List<Participant>, capacity: Int = MAX_VISIBLE_TILES): Pair<List<Participant>, Int> {
    if (participants.size <= capacity) return participants to 0
    val prioritized = participants.withIndex()
        .sortedWith(compareByDescending<IndexedValue<Participant>> { it.value.isLocal }.thenByDescending { it.value.isSpeaking }.thenBy { it.index })
        .map { it.value }
    val shown = prioritized.take(capacity - 1)
    val order = participants.map { it.id }
    return shown.sortedBy { order.indexOf(it.id) } to participants.size - shown.size
}
