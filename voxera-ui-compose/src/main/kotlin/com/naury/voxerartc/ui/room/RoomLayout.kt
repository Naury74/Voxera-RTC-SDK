package com.naury.voxerartc.ui.room

import com.naury.voxerartc.api.Participant

/** 타일 격자 크기. */
internal data class GridSpec(val columns: Int, val rows: Int) {
    val capacity: Int get() = columns * rows
}

/** 한 화면 격자에 넣는 최대 타일 수. 넘치면 쪽을 나눈다. */
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
internal enum class TileRole { GRID, FOCUS, THUMB, FULL, FLOATING }

/**
 * 타일 하나의 자리. [key]는 참가자 id, 화면 공유는 `screen:<id>`이다.
 * 같은 key는 보기 방식이 바뀌어도 같은 타일로 이어져 자리만 움직인다.
 */
internal data class TilePlacement(
    val key: String,
    val participant: Participant?,
    val rect: TileRect,
    val role: TileRole,
    val screenShare: Boolean = false,
)

/** 작은 줄. [area]는 보이는 칸, [content]는 모든 작은 화면을 늘어놓은 길이(가로 줄이면 너비, 세로 줄이면 높이). */
internal data class StripLayout(val area: TileRect, val content: Float, val horizontal: Boolean) {
    /** 넘겨 볼 수 있는 최대 거리. 다 들어가면 0. */
    val maxScroll: Float get() = maxOf(0f, content - (if (horizontal) area.width else area.height))
}

/** 배치 결과. [paged]면 9명 넘는 갤러리라 쪽 넘김 격자로 그린다. [strip]이 있으면 작은 줄을 손가락으로 넘겨 본다. */
internal data class StagePlan(
    val tiles: List<TilePlacement>,
    val paged: Boolean = false,
    val focusKey: String? = null,
    val strip: StripLayout? = null,
)


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
 * 큰 타일 + 작은 줄. 세로 화면은 아래(가로로 넘김), 가로 화면은 오른쪽(세로로 넘김)에 둔다.
 * 작은 화면은 모두 한 줄에 늘어놓고, 화면에 다 안 들어가면 [StripLayout]으로 넘겨 본다. 순서는 들어온 순서를 지킨다.
 */
private fun withStrip(focus: TilePlacement, others: List<Participant>, area: TileRect, landscape: Boolean, gap: Float, strip: Float): StagePlan {
    if (others.isEmpty()) return StagePlan(listOf(focus.copy(rect = area)), focusKey = focus.key)
    val thumbW: Float
    val thumbH: Float
    val focusRect: TileRect
    val stripArea: TileRect
    if (landscape) {
        thumbW = strip
        thumbH = strip * 3f / 4f
        focusRect = TileRect(area.x, area.y, area.width - strip - gap, area.height)
        stripArea = TileRect(focusRect.right + gap, area.y, strip, area.height)
    } else {
        thumbH = strip
        thumbW = strip * 3f / 4f
        focusRect = TileRect(area.x, area.y, area.width, area.height - strip - gap)
        stripArea = TileRect(area.x, focusRect.bottom + gap, area.width, strip)
    }
    val step = (if (landscape) thumbH else thumbW) + gap
    val tiles = buildList {
        add(focus.copy(rect = focusRect))
        others.forEachIndexed { i, person ->
            val rect = if (landscape) TileRect(stripArea.x, stripArea.y + i * step, thumbW, thumbH) else TileRect(stripArea.x + i * step, stripArea.y, thumbW, thumbH)
            add(TilePlacement(person.id, person, rect, TileRole.THUMB))
        }
    }
    return StagePlan(tiles, focusKey = focus.key, strip = StripLayout(stripArea, others.size * step - gap, horizontal = !landscape))
}
