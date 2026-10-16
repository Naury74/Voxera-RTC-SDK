package com.naury.voxerartc.ui.room

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.naury.kinetica.ui.KIslandContent
import com.naury.kinetica.ui.KIslandState
import com.naury.voxerartc.ui.component.VoxeraIcons
import com.naury.voxerartc.api.AudioRoute
import com.naury.voxerartc.api.AudioRouteType
import com.naury.voxerartc.api.LocalMediaState
import com.naury.voxerartc.ui.component.VoxeraIconTile
import com.naury.voxerartc.ui.glass.pressScale
import com.naury.voxerartc.ui.theme.VoxeraRadius
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.ui.R
import com.naury.voxerartc.ui.component.HandBadge
import com.naury.voxerartc.ui.component.VoxeraAvatar
import com.naury.voxerartc.ui.glass.GlassLevel
import com.naury.voxerartc.ui.glass.GlassSurface
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import kotlinx.coroutines.delay

internal enum class NoticeKind { JOINED, LEFT, HAND_RAISED }

/** 참가자 입장·퇴장·손들기 알림 하나. */
internal data class RoomNotice(val id: Long, val participantId: String, val name: String, val kind: NoticeKind)

/**
 * 이전·현재 원격 참가자 목록을 비교해 들어온 사람, 나간 사람, 새로 손을 든 사람을 알림으로 만든다.
 * 처음 목록(입장 직후)은 알리지 않는다. 로컬 참가자는 알리지 않는다. 손을 든 채로 들어온 사람은 입장만 알린다.
 */
internal fun noticesBetween(previous: List<Participant>?, current: List<Participant>, nextId: () -> Long): List<RoomNotice> {
    if (previous == null) return emptyList()
    val before = previous.filterNot { it.isLocal }.associateBy { it.id }
    val after = current.filterNot { it.isLocal }.associateBy { it.id }
    val joined = after.values.filter { it.id !in before }.map { RoomNotice(nextId(), it.id, it.displayName, NoticeKind.JOINED) }
    val left = before.values.filter { it.id !in after }.map { RoomNotice(nextId(), it.id, it.displayName, NoticeKind.LEFT) }
    val hands = after.values.filter { it.isHandRaised && before[it.id]?.isHandRaised == false }
        .map { RoomNotice(nextId(), it.id, it.displayName, NoticeKind.HAND_RAISED) }
    return joined + left + hands
}

/**
 * 누가 들어오고 나가거나 손을 들면 화면 위 아일랜드에 잠깐 알린다. 화면에 들어온 뒤 1.5초 동안의 변화는 알리지 않는다.
 * 한 번에 하나씩 2.5초 보여 주고, 여러 명이 한꺼번에 바뀌면 캡슐이 줄었다 부풀며 차례로 보여 준다(최대 3건, 그 이상은 버린다).
 */
@Composable
internal fun ParticipantNotices(participants: List<Participant>, island: KIslandState) {
    var previous by remember { mutableStateOf<List<Participant>?>(null) }
    var queue by remember { mutableStateOf(emptyList<RoomNotice>()) }
    var counter by remember { mutableStateOf(0L) }
    // 입장 직후 목록이 채워지는 동안(이미 방에 있던 사람들)은 알리지 않는다.
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1_500)
        armed = true
    }
    LaunchedEffect(participants, armed) {
        val fresh = if (armed) noticesBetween(previous, participants) { ++counter } else emptyList()
        previous = participants
        if (fresh.isNotEmpty()) queue = (queue + fresh).takeLast(3)
    }
    val current = queue.firstOrNull()
    val joined = stringResource(R.string.voxera_notice_joined, current?.name.orEmpty())
    val left = stringResource(R.string.voxera_notice_left, current?.name.orEmpty())
    val hand = stringResource(R.string.voxera_notice_hand, current?.name.orEmpty())
    val colors = VoxeraTheme.colors
    LaunchedEffect(current?.id) {
        val notice = current ?: return@LaunchedEffect
        val content = when (notice.kind) {
            NoticeKind.JOINED -> KIslandContent(joined, icon = VoxeraIcons.Person, accent = colors.primary)
            NoticeKind.LEFT -> KIslandContent(left, icon = VoxeraIcons.Person, accent = colors.foregroundSecondary)
            NoticeKind.HAND_RAISED -> KIslandContent(hand, icon = VoxeraIcons.Hand, accent = colors.warning)
        }
        island.show(content, PARTICIPANT_NOTICE_MS)
        queue = queue.drop(1)
    }
}

private const val PARTICIPANT_NOTICE_MS = 2_500L

/** 오디오 경로가 바뀌었을 때 띄울 알림. */
internal sealed interface RouteNotice {
    /** 새로 연결된 이어폰으로 자동으로 옮겼다. [previous]가 있으면 되돌리기를 보여 준다. */
    data class SwitchedTo(val route: AudioRoute, val previous: AudioRoute?) : RouteNotice

    /** 새 이어폰이 연결됐지만 엔진이 옮기지 않았다. 사용자가 옮길지 고른다. */
    data class Available(val route: AudioRoute) : RouteNotice

    /** 쓰던 이어폰이 끊겨 [now]로 옮겨졌다. */
    data class Lost(val route: AudioRoute, val now: AudioRoute?) : RouteNotice
}

private val AudioRouteType.isHeadset: Boolean
    get() = this == AudioRouteType.BLUETOOTH || this == AudioRouteType.WIRED_HEADSET || this == AudioRouteType.USB

/**
 * 이전·현재 미디어 상태를 비교해 알림을 고른다. 사용자가 메뉴에서 직접 고른 변화([userInitiated])는 알리지 않는다.
 * 경로 목록이 처음 채워질 때(이전 목록이 비어 있음)도 알리지 않는다.
 */
internal fun routeNoticeBetween(previous: LocalMediaState?, current: LocalMediaState, userInitiated: Boolean): RouteNotice? {
    if (previous == null || previous.audioRoutes.isEmpty() || userInitiated) return null
    val before = previous.audioRoutes.associateBy { it.id }
    val after = current.audioRoutes.associateBy { it.id }
    val selected = after[current.selectedAudioRouteId]
    val wasSelected = before[previous.selectedAudioRouteId]
    val added = current.audioRoutes.filter { it.id !in before && it.type.isHeadset && it.isSelectable }
    val removed = previous.audioRoutes.filter { it.id !in after && it.type.isHeadset }
    return when {
        selected != null && selected.id in added.map { it.id } -> RouteNotice.SwitchedTo(selected, wasSelected?.takeIf { it.id in after })
        wasSelected != null && removed.any { it.id == wasSelected.id } -> RouteNotice.Lost(wasSelected, selected)
        added.isNotEmpty() -> RouteNotice.Available(added.last())
        else -> null
    }
}

/** 되돌리기·전환 버튼이 있는 오디오 알림이 보이는 시간. */
internal const val ROUTE_NOTICE_ACTION_MS = 8_000L

/** 끊김처럼 알리기만 하는 오디오 알림이 보이는 시간. */
internal const val ROUTE_NOTICE_INFO_MS = 6_000L

/**
 * 이어폰 연결·해제 알림. 오디오 버튼 위에 꼬리 달린 말풍선으로 기기 이름과 상황, 되돌리기·전환 버튼을 보여 준다.
 * 화면 위쪽은 연결 순간 시스템의 이어폰 팝업과 겹치므로 버튼 쪽에 띄운다. 꼬리 끝에서 커지며 나타난다.
 * 버튼이 있는 알림은 8초, 끊김 알림은 6초 보인다. 접근성 설정의 "조치를 취할 시간"이 더 길면 그 값을 따른다.
 * TalkBack에는 조용히 읽힌다. 사용자가 경로 메뉴에서 고른 지 3초 안의 변화는 알리지 않는다.
 *
 * 이 함수는 화면 전체를 차지하는 상자 안에 두며, 말풍선 밖은 터치를 막지 않는다.
 *
 * @param lastUserSelectionMs 사용자가 메뉴에서 경로를 마지막으로 고른 시각([System.nanoTime] 기준 ms).
 * @param anchor 말풍선이 가리킬 버튼의 영역(이 상자 기준 px). 모르면 아래 가운데를 가리킨다.
 * @param onVisibleChange 말풍선이 보이기 시작하거나 사라질 때 불린다. 보이는 동안 조작 막대를 숨기지 않을 때 쓴다.
 */
@Composable
internal fun AudioRouteNotices(
    media: LocalMediaState,
    lastUserSelectionMs: Long,
    onSelect: (String) -> Unit,
    anchor: Rect?,
    onVisibleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var previous by remember { mutableStateOf<LocalMediaState?>(null) }
    var notice by remember { mutableStateOf<RouteNotice?>(null) }
    var serial by remember { mutableStateOf(0) }
    LaunchedEffect(media.audioRoutes, media.selectedAudioRouteId) {
        val userInitiated = System.nanoTime() / 1_000_000 - lastUserSelectionMs < 3_000
        routeNoticeBetween(previous, media, userInitiated)?.let {
            notice = it
            serial++
        }
        previous = media
    }
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(serial) {
        val shown = notice ?: return@LaunchedEffect
        val hasAction = shown !is RouteNotice.Lost
        val base = if (hasAction) ROUTE_NOTICE_ACTION_MS else ROUTE_NOTICE_INFO_MS
        val timeout = accessibility?.calculateRecommendedTimeoutMillis(base, containsIcons = true, containsText = true, containsControls = hasAction) ?: base
        // 접근성 설정이 "사라지지 않음"이면 다음 알림이 올 때까지 둔다.
        if (timeout == Long.MAX_VALUE) return@LaunchedEffect
        delay(timeout)
        notice = null
    }
    val visible = notice != null
    LaunchedEffect(visible) { onVisibleChange(visible) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val margin = VoxeraSpacing.sm
        val width = minOf(360.dp, maxWidth - margin * 2)
        // 가리킬 곳: 버튼 위쪽 가운데. 버튼을 모르면 화면 아래 가운데.
        val target = with(density) {
            anchor?.let { DpOffset(it.center.x.toDp(), it.top.toDp()) } ?: DpOffset(maxWidth / 2, maxHeight - 120.dp)
        }
        val left = (target.x - width / 2).coerceIn(margin, maxWidth - margin - width)
        val tailX = (target.x - left).coerceIn(VoxeraRadius.xl, width - VoxeraRadius.xl)
        val origin = TransformOrigin(tailX / width, 1f)
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.6f, transformOrigin = origin) + fadeIn(),
            exit = scaleOut(tween(160), targetScale = 0.8f, transformOrigin = origin) + fadeOut(tween(160)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = left)
                // 버튼은 막대 안쪽에 있으므로, 꼬리 끝이 막대 윗선보다 조금 위에 오도록 막대 안쪽 여백만큼 더 띄운다.
                .padding(bottom = (maxHeight - target.y + BubbleGap).coerceAtLeast(0.dp))
                .width(width),
        ) {
            val shown = notice ?: return@AnimatedVisibility
            RouteBubble(shown, tailX, onSelect = { id ->
                onSelect(id)
                notice = null
            })
        }
    }
}

@Composable
private fun RouteBubble(shown: RouteNotice, tailX: Dp, onSelect: (String) -> Unit) {
    val colors = VoxeraTheme.colors
    val (route, title, body) = when (shown) {
        is RouteNotice.SwitchedTo -> Triple(shown.route, shown.route.label, stringResource(R.string.voxera_route_notice_switched))
        is RouteNotice.Available -> Triple(shown.route, stringResource(R.string.voxera_route_notice_available, shown.route.label), stringResource(R.string.voxera_route_notice_available_body))
        is RouteNotice.Lost -> Triple(
            shown.route,
            stringResource(R.string.voxera_route_notice_lost, shown.route.label),
            shown.now?.let { stringResource(R.string.voxera_route_notice_now, stringResource(it.type.labelRes())) }.orEmpty(),
        )
    }
    val action: Pair<String, () -> Unit>? = when (shown) {
        is RouteNotice.SwitchedTo -> shown.previous?.let { prev -> stringResource(R.string.voxera_route_notice_undo) to { onSelect(prev.id) } }
        is RouteNotice.Available -> stringResource(R.string.voxera_route_notice_switch) to { onSelect(shown.route.id) }
        is RouteNotice.Lost -> null
    }
    // 꼬리까지 한 모양으로 그려 유리 채움·테두리·그림자가 끊기지 않게 한다.
    val density = LocalDensity.current
    val shape = remember(tailX, density) { with(density) { BubbleShape(VoxeraRadius.xl.toPx(), tailX.toPx(), TailWidth.toPx(), TailHeight.toPx()) } }
    GlassSurface(
        level = GlassLevel.High,
        shape = shape,
        contentPadding = PaddingValues(start = VoxeraSpacing.sm, end = VoxeraSpacing.xs, top = VoxeraSpacing.xs, bottom = VoxeraSpacing.xs + TailHeight),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.sm)) {
            VoxeraIconTile(route.type.icon(), if (shown is RouteNotice.Lost) colors.warning else colors.primary, size = 36.dp)
            Column(Modifier.weight(1f)) {
                Text(title, color = colors.foreground, style = VoxeraTheme.typography.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (body.isNotEmpty()) Text(body, color = colors.foregroundSecondary, style = VoxeraTheme.typography.caption, maxLines = 2)
            }
            if (action != null) {
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .pressScale(interaction)
                        .heightIn(min = 40.dp)
                        .clip(CircleShape)
                        .background(colors.primary.copy(alpha = 0.14f))
                        .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = action.second)
                        .padding(horizontal = VoxeraSpacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(action.first, color = colors.primary, style = VoxeraTheme.typography.label)
                }
            }
        }
    }
}

/** 아래쪽 [tailX] 위치에 꼬리가 달린 둥근 사각형. 꼬리 높이만큼 아래가 비어 있으므로 내용 아래 여백을 그만큼 더 준다. */
private class BubbleShape(private val radius: Float, private val tailX: Float, private val tailWidth: Float, private val tailHeight: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val bodyHeight = size.height - tailHeight
        val body = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, bodyHeight, CornerRadius(radius))) }
        val x = tailX.coerceIn(radius + tailWidth / 2, size.width - radius - tailWidth / 2)
        val tail = Path().apply {
            moveTo(x - tailWidth / 2, bodyHeight - 1f)
            lineTo(x + tailWidth / 2, bodyHeight - 1f)
            lineTo(x, size.height)
            close()
        }
        return Outline.Generic(Path.combine(PathOperation.Union, body, tail))
    }
}

private val TailWidth = 22.dp
private val TailHeight = 10.dp
private val BubbleGap = 18.dp
