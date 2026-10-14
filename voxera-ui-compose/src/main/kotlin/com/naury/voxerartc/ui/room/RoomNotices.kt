package com.naury.voxerartc.ui.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
