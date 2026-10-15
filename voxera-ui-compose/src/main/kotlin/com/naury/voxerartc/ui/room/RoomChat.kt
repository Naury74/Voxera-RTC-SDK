package com.naury.voxerartc.ui.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naury.voxerartc.api.ChatMessage
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.ui.R
import com.naury.voxerartc.ui.component.HandBadge
import com.naury.voxerartc.ui.component.VoxeraAvatar
import com.naury.voxerartc.ui.component.VoxeraIcons
import com.naury.voxerartc.ui.glass.GlassLevel
import com.naury.voxerartc.ui.glass.GlassSurface
import com.naury.voxerartc.ui.glass.pressScale
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import kotlinx.coroutines.delay

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
internal fun ChatSheet(messages: List<ChatMessage>, participants: List<Participant>, onSend: (String) -> Unit) {
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
            modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
            contentPadding = PaddingValues(bottom = VoxeraSpacing.xs),
        ) {
            itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                val first = startsGroup(messages, index)
                ChatBubble(
                    message = message,
                    name = nameOf(people, message.participantId, someone),
                    showSender = first && !message.isLocal,
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

@Composable
private fun ChatBubble(message: ChatMessage, name: String, showSender: Boolean, modifier: Modifier = Modifier) {
    val colors = VoxeraTheme.colors
    val mine = message.isLocal
    val you = stringResource(R.string.voxera_you)
    val spoken = stringResource(R.string.voxera_chat_from, if (mine) you else name, message.text)
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!mine) {
            // 이어 보낸 메시지는 얼굴 자리를 비워 말풍선 줄을 맞춘다.
            Box(Modifier.width(36.dp)) {
                if (showSender) VoxeraAvatar(message.participantId, name, size = 28.dp)
            }
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 280.dp)) {
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
            val shape = if (mine) RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
            SelectionContainer {
                Text(
                    message.text,
                    color = if (mine) colors.onPrimary else colors.foreground,
                    style = VoxeraTheme.typography.body,
                    modifier = Modifier
                        .clip(shape)
                        .background(if (mine) colors.primary else colors.foreground.copy(alpha = if (colors.isDark) 0.12f else 0.06f))
                        .padding(horizontal = VoxeraSpacing.sm, vertical = VoxeraSpacing.xs),
                )
            }
        }
    }
}

/** 글 입력 칸과 보내기 버튼. 글이 있을 때만 버튼이 강조색으로 바뀐다. 키보드의 보내기로도 보낸다. */
@Composable
private fun ChatInput(onSend: (String) -> Unit) {
    val colors = VoxeraTheme.colors
    // 상태 기반 입력 칸은 화면이 자주 다시 그려져도(말하는 사람 표시 등) 키보드 입력을 잃지 않는다.
    val draft = rememberTextFieldState()
    val canSend = ChatMessage.sanitize(draft.text.toString()) != null
    val send = {
        if (canSend) {
            onSend(draft.text.toString())
            draft.clearText()
        }
    }
    val placeholder = stringResource(R.string.voxera_chat_placeholder)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.foreground.copy(alpha = if (colors.isDark) 0.10f else 0.05f))
                .padding(horizontal = VoxeraSpacing.md, vertical = 12.dp),
        ) {
            if (draft.text.isEmpty()) Text(placeholder, color = colors.foregroundSecondary, style = VoxeraTheme.typography.body)
            BasicTextField(
                state = draft,
                textStyle = VoxeraTheme.typography.body.copy(color = colors.foreground),
                cursorBrush = SolidColor(colors.primary),
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
                inputTransformation = InputTransformation.maxLength(ChatMessage.MAX_LENGTH),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                onKeyboardAction = { send() },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
        val interaction = remember { MutableInteractionSource() }
        val container by animateColorAsState(if (canSend) colors.primary else colors.foreground.copy(alpha = 0.08f), tween(180), label = "send")
        val tint by animateColorAsState(if (canSend) colors.onPrimary else colors.foregroundSecondary, tween(180), label = "send-tint")
        val label = stringResource(R.string.voxera_chat_send)
        Box(
            Modifier
                .size(48.dp)
                .pressScale(interaction, pressed = 0.88f)
                .clip(CircleShape)
                .background(container)
                .clickable(interactionSource = interaction, indication = null, enabled = canSend, role = Role.Button, onClick = send)
                .clearAndSetSemantics {
                    contentDescription = label
                    role = Role.Button
                    if (canSend) onClick(label = label) { send(); true }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(VoxeraIcons.Send, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
    if (draft.text.length > ChatMessage.MAX_LENGTH - 100) {
        Text(
            "${draft.text.length} / ${ChatMessage.MAX_LENGTH}",
            color = colors.foregroundSecondary,
            style = VoxeraTheme.typography.caption,
            modifier = Modifier.fillMaxWidth().padding(top = VoxeraSpacing.xxs, end = VoxeraSpacing.md),
        )
    }
}

/**
 * 시트를 닫아 둔 사이 온 채팅을 위에서 내려오는 캡슐로 잠깐 보여 준다. 누르면 채팅을 연다.
 * 화면이 처음 그려질 때 이미 있던 메시지는 띄우지 않는다. 4초 뒤 사라지고, 그사이 새 메시지가 오면 내용만 바뀐다.
 */
@Composable
internal fun ChatNotices(messages: List<ChatMessage>, participants: List<Participant>, suppressed: Boolean, onOpen: () -> Unit) {
    val latest = messages.lastOrNull { !it.isLocal }
    var baseline by remember { mutableStateOf(latest?.id) }
    var shown by remember { mutableStateOf<ChatMessage?>(null) }
    LaunchedEffect(latest?.id, suppressed) {
        if (suppressed || latest == null || latest.id == baseline) {
            if (suppressed) {
                shown = null
                baseline = latest?.id
            }
            return@LaunchedEffect
        }
        baseline = latest.id
        shown = latest
        delay(4_000)
        shown = null
    }
    val someone = stringResource(R.string.voxera_chat_someone)
    val people = participants.associateBy { it.id }
    AnimatedVisibility(
        visible = shown != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        val message = shown ?: return@AnimatedVisibility
        val name = nameOf(people, message.participantId, someone)
        val open = stringResource(R.string.voxera_chat_open)
        val interaction = remember { MutableInteractionSource() }
        GlassSurface(
            level = GlassLevel.Medium,
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(start = VoxeraSpacing.xs, end = VoxeraSpacing.md, top = VoxeraSpacing.xs, bottom = VoxeraSpacing.xs),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .pressScale(interaction, pressed = 0.97f)
                .clip(RoundedCornerShape(24.dp))
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = open, onClick = onOpen)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.sm)) {
                VoxeraAvatar(message.participantId, name, size = 32.dp)
                Column(Modifier.weight(1f, fill = false)) {
                    Text(name, color = VoxeraTheme.colors.foreground, style = VoxeraTheme.typography.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(message.text, color = VoxeraTheme.colors.foregroundSecondary, style = VoxeraTheme.typography.caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

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
