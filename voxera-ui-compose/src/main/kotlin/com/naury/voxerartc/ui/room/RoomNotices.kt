package com.naury.voxerartc.ui.room

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.ui.R
import com.naury.voxerartc.ui.component.VoxeraAvatar
import com.naury.voxerartc.ui.glass.GlassLevel
import com.naury.voxerartc.ui.glass.GlassSurface
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import kotlinx.coroutines.delay

/** 참가자 입장·퇴장 알림 하나. */
internal data class RoomNotice(val id: Long, val participantId: String, val name: String, val joined: Boolean)

/**
 * 이전·현재 원격 참가자 목록을 비교해 들어온 사람과 나간 사람을 알림으로 만든다. 처음 목록(입장 직후)은 알리지 않는다.
 * 로컬 참가자는 알리지 않는다.
 */
internal fun noticesBetween(previous: List<Participant>?, current: List<Participant>, nextId: () -> Long): List<RoomNotice> {
    if (previous == null) return emptyList()
    val before = previous.filterNot { it.isLocal }.associateBy { it.id }
    val after = current.filterNot { it.isLocal }.associateBy { it.id }
    val joined = after.values.filter { it.id !in before }.map { RoomNotice(nextId(), it.id, it.displayName, joined = true) }
    val left = before.values.filter { it.id !in after }.map { RoomNotice(nextId(), it.id, it.displayName, joined = false) }
    return joined + left
}

/**
 * 위에서 내려오는 유리 캡슐 알림. 화면에 들어온 뒤 1.5초 동안의 변화는 알리지 않는다. 한 번에 하나씩 2.5초 보여 주며, TalkBack에는 조용히 읽힌다.
 * 여러 명이 한꺼번에 바뀌면 차례로 보여 준다(최대 3건, 그 이상은 버린다).
 */
@Composable
internal fun ParticipantNotices(participants: List<Participant>, modifier: Modifier = Modifier) {
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
    LaunchedEffect(current?.id) {
        if (current != null) {
            delay(2_500)
            queue = queue.drop(1)
        }
    }
    AnimatedVisibility(
        visible = current != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        val notice = current ?: return@AnimatedVisibility
        val text = stringResource(if (notice.joined) R.string.voxera_notice_joined else R.string.voxera_notice_left, notice.name)
        GlassSurface(
            level = GlassLevel.Medium,
            shape = CircleShape,
            contentPadding = PaddingValues(start = VoxeraSpacing.xxs, end = VoxeraSpacing.md, top = VoxeraSpacing.xxs, bottom = VoxeraSpacing.xxs),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
                VoxeraAvatar(notice.participantId, notice.name, size = 32.dp)
                Text(text, color = VoxeraTheme.colors.foreground, style = VoxeraTheme.typography.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

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

/**
 * 이어폰 연결·해제 알림. 위에서 내려오는 유리 캡슐에 기기 이름과 상황, 되돌리기·전환 버튼을 보여 주고 5초 뒤 사라진다.
 * TalkBack에는 조용히 읽힌다. 사용자가 경로 메뉴에서 고른 지 3초 안의 변화는 알리지 않는다.
 *
 * @param lastUserSelectionMs 사용자가 메뉴에서 경로를 마지막으로 고른 시각([System.nanoTime] 기준 ms).
 */
@Composable
internal fun AudioRouteNotices(media: LocalMediaState, lastUserSelectionMs: Long, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
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
    LaunchedEffect(serial) {
        if (notice != null) {
            delay(5_000)
            notice = null
        }
    }
    AnimatedVisibility(
        visible = notice != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        val shown = notice ?: return@AnimatedVisibility
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
            is RouteNotice.SwitchedTo -> shown.previous?.let { prev -> stringResource(R.string.voxera_route_notice_undo) to { onSelect(prev.id); notice = null } }
            is RouteNotice.Available -> stringResource(R.string.voxera_route_notice_switch) to { onSelect(shown.route.id); notice = null }
            is RouteNotice.Lost -> null
        }
        GlassSurface(
            level = GlassLevel.High,
            shape = RoundedCornerShape(VoxeraRadius.xl),
            contentPadding = PaddingValues(start = VoxeraSpacing.sm, end = VoxeraSpacing.xs, top = VoxeraSpacing.xs, bottom = VoxeraSpacing.xs),
            modifier = Modifier.widthIn(max = 420.dp).semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.sm)) {
                VoxeraIconTile(route.type.icon(), if (shown is RouteNotice.Lost) colors.warning else colors.primary, size = 36.dp)
                Column(Modifier.weight(1f, fill = false)) {
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
}
