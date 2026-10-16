package com.naury.voxerartc.ui.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KChatBubble
import com.naury.kinetica.ui.KChatGroupPosition
import com.naury.kinetica.ui.KChatInput
import com.naury.kinetica.ui.KChatSide
import com.naury.kinetica.ui.KIslandContent
import com.naury.kinetica.ui.KIslandResult
import com.naury.kinetica.ui.KIslandState
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.voxerartc.api.ChatMessage
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.ui.R
import com.naury.voxerartc.ui.component.HandBadge
import com.naury.voxerartc.ui.component.VoxeraAvatar
import com.naury.voxerartc.ui.glass.GlassLevel
import com.naury.voxerartc.ui.glass.GlassSurface
import com.naury.voxerartc.ui.glass.pressScale
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * [lastReadId] 뒤에 온, 내가 보내지 않은 메시지 수. [lastReadId]가 `null`이거나 목록에서 밀려나 없으면 남의 메시지 전부를 센다.
 */
internal fun unreadCount(messages: List<ChatMessage>, lastReadId: String?): Int {
    val from = if (lastReadId == null) 0 else messages.indexOfLast { it.id == lastReadId } + 1
    return messages.drop(from).count { !it.isLocal }
}

/** 같은 사람이 [CHAT_GROUP_MS] 안에 이어 보낸 메시지는 이름과 얼굴을 다시 그리지 않는다. */
internal fun startsGroup(messages: List<ChatMessage>, index: Int): Boolean {
    val previous = messages.getOrNull(index - 1) ?: return true
    val current = messages[index]
    return previous.participantId != current.participantId || current.atMs - previous.atMs > CHAT_GROUP_MS
}

internal const val CHAT_GROUP_MS = 120_000L

/** 사람 이름. 나간 사람은 목록에 없으므로 [fallback]을 쓴다. */
private fun nameOf(people: Map<String, Participant>, id: String, fallback: String): String = people[id]?.displayName ?: fallback

/**
 * 통화 중 채팅 시트. 남의 메시지는 왼쪽, 내 메시지는 오른쪽 말풍선이다. 새 메시지가 오면 맨 아래로 부드럽게 내려간다.
 * 받은 글은 일반 글자로만 그리고(링크·서식 해석 없음), 길게 눌러 복사할 수 있다.
 */
@Composable
internal fun ColumnScope.ChatSheet(messages: List<ChatMessage>, participants: List<Participant>, onSend: (String) -> Unit) {
    val colors = VoxeraTheme.colors
    val people = participants.associateBy { it.id }
    val someone = stringResource(R.string.voxera_chat_someone)
    SheetTitle(stringResource(R.string.voxera_chat_title))
    val list = rememberLazyListState()
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex)
    }
    if (messages.isEmpty()) {
        Text(
            stringResource(R.string.voxera_chat_empty),
            color = colors.foregroundSecondary,
            style = VoxeraTheme.typography.body,
            modifier = Modifier.fillMaxWidth().padding(vertical = VoxeraSpacing.xl),
        )
    } else {
        LazyColumn(
            state = list,
            // 키보드가 올라와 시트 높이가 줄면 목록이 먼저 줄어들어, 입력 칸은 늘 키보드 위에 보인다.
            modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).weight(1f, fill = false),
            contentPadding = PaddingValues(bottom = VoxeraSpacing.xs),
        ) {
            itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                val first = startsGroup(messages, index)
                val last = index == messages.lastIndex || startsGroup(messages, index + 1)
                ChatBubble(
                    message = message,
                    name = nameOf(people, message.participantId, someone),
                    showSender = first && !message.isLocal,
                    position = when {
                        first && last -> KChatGroupPosition.Single
                        first -> KChatGroupPosition.First
                        last -> KChatGroupPosition.Last
                        else -> KChatGroupPosition.Middle
                    },
                    modifier = Modifier
                        .animateItem()
                        .padding(top = if (first && index > 0) VoxeraSpacing.sm else 2.dp),
                )
            }
        }
    }
    Spacer(Modifier.size(VoxeraSpacing.sm))
    ChatInput(onSend)
}

/**
 * 말풍선 하나. 같은 사람이 이어 보낸 묶음은 붙은 쪽 모서리를 덜 둥글게 하고, 묶음의 마지막 말풍선에만 꼬리를 단다.
 * 받은 글은 일반 글자로만 그리고 길게 눌러 골라 복사할 수 있다.
 */
@Composable
private fun ChatBubble(message: ChatMessage, name: String, showSender: Boolean, position: KChatGroupPosition, modifier: Modifier = Modifier) {
    val colors = VoxeraTheme.colors
    val mine = message.isLocal
    val you = stringResource(R.string.voxera_you)
    val spoken = stringResource(R.string.voxera_chat_from, if (mine) you else name, message.text)
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.Top,
    ) {
        if (!mine) {
            // 이어 보낸 메시지는 얼굴 자리를 비워 말풍선 줄을 맞춘다.
            Box(Modifier.width(36.dp)) {
                if (showSender) VoxeraAvatar(message.participantId, name, size = 28.dp)
            }
        }
        Column(Modifier.weight(1f)) {
            if (showSender) {
                Text(
                    name,
                    color = colors.foregroundSecondary,
                    style = VoxeraTheme.typography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = VoxeraSpacing.xs, bottom = 2.dp),
                )
            }
            KChatBubble(side = if (mine) KChatSide.Sent else KChatSide.Received, position = position, maxWidthFraction = 0.82f) {
                SelectionContainer {
                    Text(message.text, color = LocalKineticaContentColor.current, style = VoxeraTheme.typography.body)
                }
            }
        }
    }
}

/**
 * 글 입력 칸과 보내기 버튼. 글이 있을 때만 버튼이 강조색으로 바뀌고, 여러 줄이면 네 줄까지 늘어난다. 키보드의 보내기로도 보낸다.
 * 상태 기반 입력 칸이라 화면이 자주 다시 그려져도(말하는 사람 표시 등) 키보드 입력을 잃지 않는다.
 */
@Composable
private fun ChatInput(onSend: (String) -> Unit) {
    val draft = rememberTextFieldState()
    KChatInput(
        draft,
        onSend = {
            val text = draft.text.toString()
            if (ChatMessage.sanitize(text) != null) {
                onSend(text)
                draft.clearText()
            }
        },
        placeholder = stringResource(R.string.voxera_chat_placeholder),
        // 화면 판독기가 입력 칸 이름으로 읽는다.
        label = stringResource(R.string.voxera_chat_placeholder),
        maxLines = 4,
        maxLength = ChatMessage.MAX_LENGTH,
        // 글자 수는 남은 글자가 얼마 안 될 때만 보여 준다.
        counterThreshold = COUNTER_MARGIN,
    )
}

/**
 * 시트를 닫아 둔 사이 온 채팅을 화면 위 아일랜드에 잠깐 보여 준다. 누르면 채팅을 연다.
 * 화면이 처음 그려질 때 이미 있던 메시지는 띄우지 않는다. 4초 뒤 줄어들며 사라지고, 다른 알림이 있으면 차례로 이어진다.
 */
@Composable
internal fun ChatNotices(messages: List<ChatMessage>, participants: List<Participant>, suppressed: Boolean, island: KIslandState, onOpen: () -> Unit) {
    val latest = messages.lastOrNull { !it.isLocal }
    var baseline by remember { mutableStateOf(latest?.id) }
    val someone = stringResource(R.string.voxera_chat_someone)
    val people by rememberUpdatedState(participants.associateBy { it.id })
    val open by rememberUpdatedState(onOpen)
    val scope = rememberCoroutineScope()
    var showing by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(latest?.id, suppressed) {
        if (suppressed || latest == null || latest.id == baseline) {
            if (suppressed) {
                baseline = latest?.id
                // 채팅 시트를 열면 같은 글을 위에 또 보여 줄 필요가 없으므로 미리보기를 닫는다.
                showing?.cancel()
            }
            return@LaunchedEffect
        }
        baseline = latest.id
        val name = nameOf(people, latest.participantId, someone)
        val content = KIslandContent(
            title = name,
            subtitle = latest.text,
            leading = { VoxeraAvatar(latest.participantId, name, size = 32.dp) },
        )
        // 채팅 미리보기는 하나만 둔다. 보이는 중에 새 메시지가 오면 쌓지 않고 내용만 바꾸고 시간을 다시 잰다.
        showing = scope.launch {
            if (island.show(content, CHAT_NOTICE_MS, key = CHAT_NOTICE_KEY, replaceShowing = true) == KIslandResult.Clicked) open()
        }
    }
}

private const val CHAT_NOTICE_KEY = "voxera.chat"

private const val COUNTER_MARGIN = 100
private const val CHAT_NOTICE_MS = 4_000L

/** 내가 손을 든 동안 위에 남아 있는 알약. 누르면 손을 내린다. */
@Composable
internal fun RaisedHandChip(visible: Boolean, onLower: () -> Unit) {
    AnimatedVisibility(visible, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
        val interaction = remember { MutableInteractionSource() }
        val lower = stringResource(R.string.voxera_hand_lower)
        GlassSurface(
            level = GlassLevel.Medium,
            shape = CircleShape,
            contentPadding = PaddingValues(start = VoxeraSpacing.xxs, end = VoxeraSpacing.md, top = VoxeraSpacing.xxs, bottom = VoxeraSpacing.xxs),
            modifier = Modifier
                .pressScale(interaction, pressed = 0.95f)
                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = lower, onClick = onLower),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
                HandBadge(size = 32.dp)
                Text(stringResource(R.string.voxera_hand_you_raised), color = VoxeraTheme.colors.foreground, style = VoxeraTheme.typography.label)
                Text(lower, color = VoxeraTheme.colors.primary, style = VoxeraTheme.typography.label)
            }
        }
    }
}
