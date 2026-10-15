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

/** 격자 한 쪽의 최대 타일 수. */
internal const val PAGE_SIZE = 9

/** 영상 격자의 쪽 나누기. 첫 쪽 맨 앞에 로컬 참가자를 두고 나머지는 원래 순서를 지킨다. 참가자가 없으면 빈 쪽 하나. */
internal fun gridPages(participants: List<Participant>, pageSize: Int = PAGE_SIZE): List<List<Participant>> {
    val ordered = participants.filter { it.isLocal } + participants.filterNot { it.isLocal }
    return ordered.chunked(pageSize).ifEmpty { listOf(emptyList()) }
}

/** 영상 통화 보기 방식. [GALLERY]는 모두 같은 크기, [SPEAKER]는 말하는 사람을 크게. */
internal enum class StageView { GALLERY, SPEAKER }

/** 화면 모서리 네 곳. 1:1 통화의 작은 화면이 붙는 자리. */
internal enum class Corner { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

/** 무대 위 사각형(px). */
internal data class TileRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height
}

/** 타일 역할. 모서리 모양과 누를 때 동작이 달라진다. */
internal enum class TileRole { GRID, FOCUS, THUMB, FULL, FLOATING, OVERFLOW }

/**
 * 타일 하나의 자리. [key]는 참가자 id, 화면 공유는 `screen:<id>`, 넘친 인원 칸은 [OVERFLOW_KEY]이다.
 * 같은 key는 보기 방식이 바뀌어도 같은 타일로 이어져 자리만 움직인다.
 */
internal data class TilePlacement(
    val key: String,
    val participant: Participant?,
    val rect: TileRect,
    val role: TileRole,
    val screenShare: Boolean = false,
)

/** 배치 결과. [paged]면 9명 넘는 갤러리라 쪽 넘김 격자로 그린다. [hidden]은 작은 줄에 다 못 넣은 인원. */
internal data class StagePlan(val tiles: List<TilePlacement>, val hidden: Int = 0, val paged: Boolean = false, val focusKey: String? = null)

internal const val OVERFLOW_KEY = "voxera:overflow"

/**
 * 영상 무대 배치를 계산한다. 결과는 사각형 목록이며 화면은 이 자리로 타일을 부드럽게 옮긴다.
 *
 * - 다른 사람이 화면을 공유하면 공유 화면이 크게, 나머지는 작은 줄.
 * - 고정한 사람이 있거나 [StageView.SPEAKER]면 그 사람(또는 말하는 사람)이 크게, 나머지는 작은 줄.
 * - 2명(나 + 1명)이면 큰 사람은 화면 전체([full]), 다른 한 명은 [corner] 모서리의 작은 화면.
 * - 그 밖에는 9명까지 격자. 마지막 줄이 덜 차면 가운데로 모은다. 9명을 넘으면 [StagePlan.paged].
 *
 * @param area 상단 줄·조작 막대를 뺀 영역. @param full 화면 전체. @param gap 타일 사이 간격.
 * @param strip 작은 줄 두께(세로 화면은 높이, 가로 화면은 너비). @param floatingWidth 1:1 작은 화면 너비(높이는 4:3).
 */
internal fun planStage(
    participants: List<Participant>,
    pinnedId: String?,
    view: StageView,
    lastSpeakerId: String?,
    area: TileRect,
    full: TileRect,
    landscape: Boolean,
    gap: Float,
    strip: Float,
    floatingWidth: Float,
    floatingMargin: Float,
    corner: Corner,
): StagePlan {
    if (participants.isEmpty()) return StagePlan(emptyList())
    val local = participants.firstOrNull { it.isLocal }
    val remotes = participants.filterNot { it.isLocal }
    val sharing = remotes.firstOrNull { it.screenShare != null }
    val pinned = participants.firstOrNull { it.id == pinnedId }
    // 발표자 보기는 화면이 정한 "마지막 발화자"를 먼저 따른다(잠깐 끼어든 소리에 크게 보이는 사람이 깜빡이며 바뀌지 않게).
    val speaker = remotes.firstOrNull { it.id == lastSpeakerId }
        ?: remotes.filter { it.isSpeaking }.maxByOrNull { it.audioLevel }
        ?: remotes.firstOrNull()

    if (sharing != null) {
        val focus = TilePlacement("screen:${sharing.id}", sharing, area, TileRole.FOCUS, screenShare = true)
        return withStrip(focus, participants, area, landscape, gap, strip)
    }
    // 1:1은 고정 여부로 큰 화면과 작은 화면을 바꾼다.
    if (remotes.size == 1 && local != null) {
        val big = if (pinned?.isLocal == true) local else remotes.single()
        val small = if (big.isLocal) remotes.single() else local
        val w = floatingWidth
        val h = floatingWidth * 4f / 3f
        val left = area.x + floatingMargin
        val right = area.right - floatingMargin - w
        val top = area.y
        val bottom = area.bottom - h
        val (x, y) = when (corner) {
            Corner.TOP_START -> left to top
            Corner.TOP_END -> right to top
            Corner.BOTTOM_START -> left to bottom
            Corner.BOTTOM_END -> right to bottom
        }
        return StagePlan(
            listOf(
                TilePlacement(big.id, big, full, TileRole.FULL),
                TilePlacement(small.id, small, TileRect(x, y, w, h), TileRole.FLOATING),
            ),
            focusKey = big.id,
        )
    }
    val focusPerson = pinned ?: if (view == StageView.SPEAKER && remotes.isNotEmpty()) speaker else null
    if (focusPerson != null) {
        return withStrip(TilePlacement(focusPerson.id, focusPerson, area, TileRole.FOCUS), participants.filterNot { it.id == focusPerson.id }, area, landscape, gap, strip)
    }
    if (participants.size > MAX_VISIBLE_TILES) return StagePlan(emptyList(), paged = true)
    return StagePlan(grid(participants.filter { it.isLocal } + remotes, area, landscape, gap))
}

/** 격자 배치. 마지막 줄이 덜 차면 가운데로 모은다. */
internal fun grid(people: List<Participant>, area: TileRect, landscape: Boolean, gap: Float): List<TilePlacement> {
    val spec = gridFor(people.size, landscape)
    val cellW = (area.width - gap * (spec.columns - 1)) / spec.columns
    val rows = (people.size + spec.columns - 1) / spec.columns
    val cellH = (area.height - gap * (rows - 1)) / rows
    return people.mapIndexed { index, person ->
        val row = index / spec.columns
        val col = index % spec.columns
        val inRow = minOf(spec.columns, people.size - row * spec.columns)
        val offset = (spec.columns - inRow) * (cellW + gap) / 2f
        TilePlacement(person.id, person, TileRect(area.x + offset + col * (cellW + gap), area.y + row * (cellH + gap), cellW, cellH), TileRole.GRID)
    }
}

/**
 * 큰 타일 + 작은 줄. 세로 화면은 아래, 가로 화면은 오른쪽에 둔다. 다 못 넣으면 말하는 사람을 먼저 넣고
 * 마지막 칸을 "+N"으로 쓴다.
 */
private fun withStrip(focus: TilePlacement, others: List<Participant>, area: TileRect, landscape: Boolean, gap: Float, strip: Float): StagePlan {
    if (others.isEmpty()) return StagePlan(listOf(focus.copy(rect = area)), focusKey = focus.key)
    val ordered = others
    val thumbW: Float
    val thumbH: Float
    val focusRect: TileRect
    if (landscape) {
        thumbW = strip
        thumbH = strip * 3f / 4f
        focusRect = TileRect(area.x, area.y, area.width - strip - gap, area.height)
    } else {
        thumbH = strip
        thumbW = strip * 3f / 4f
        focusRect = TileRect(area.x, area.y, area.width, area.height - strip - gap)
    }
    val length = if (landscape) area.height else area.width
    val step = (if (landscape) thumbH else thumbW) + gap
    val capacity = maxOf(1, ((length + gap) / step).toInt())
    val needsOverflow = ordered.size > capacity
    // 다 들어가면 순서를 지켜 작은 화면이 자리를 바꾸지 않게 한다. 넘칠 때만 말하는 사람을 앞쪽 칸에 넣는다.
    val shown = if (needsOverflow) {
        ordered.withIndex()
            .sortedWith(compareByDescending<IndexedValue<Participant>> { it.value.isSpeaking }.thenBy { it.index })
            .take(capacity - 1)
            .sortedBy { it.index }
            .map { it.value }
    } else ordered
    val hidden = ordered.size - shown.size
    fun slot(i: Int): TileRect = if (landscape) {
        TileRect(focusRect.right + gap, area.y + i * step, thumbW, thumbH)
    } else {
        TileRect(area.x + i * step, focusRect.bottom + gap, thumbW, thumbH)
    }
    val tiles = buildList {
        add(focus.copy(rect = focusRect))
        shown.forEachIndexed { i, person -> add(TilePlacement(person.id, person, slot(i), TileRole.THUMB)) }
        if (hidden > 0) add(TilePlacement(OVERFLOW_KEY, null, slot(shown.size), TileRole.OVERFLOW))
    }
    return StagePlan(tiles, hidden = hidden, focusKey = focus.key)
}
