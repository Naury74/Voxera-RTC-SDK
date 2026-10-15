package com.naury.voxerartc.ui.room

import com.naury.voxerartc.ui.component.VoxeraIcons
import com.naury.voxerartc.ui.component.VoxeraButtonTone
import com.naury.voxerartc.ui.component.VoxeraButton
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.api.Reaction
import com.naury.voxerartc.api.ReactionEvent
import com.naury.voxerartc.core.VoxeraSession
import com.naury.voxerartc.ui.R
import com.naury.voxerartc.ui.glass.GlassPill
import com.naury.voxerartc.ui.glass.pressScale
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import com.naury.voxerartc.ui.theme.rememberReducedMotion
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** 반응 그림 문자. 화면에 그리는 것은 이 표의 값뿐이다. */
internal val Reaction.emoji: String
    get() = when (this) {
        Reaction.THUMBS_UP -> "👍"
        Reaction.HEART -> "❤️"
        Reaction.LAUGH -> "😂"
        Reaction.CLAP -> "👏"
        Reaction.SURPRISED -> "😮"
        Reaction.CELEBRATE -> "🎉"
    }

internal fun Reaction.labelRes(): Int = when (this) {
    Reaction.THUMBS_UP -> R.string.voxera_reaction_thumbs_up
    Reaction.HEART -> R.string.voxera_reaction_heart
    Reaction.LAUGH -> R.string.voxera_reaction_laugh
    Reaction.CLAP -> R.string.voxera_reaction_clap
    Reaction.SURPRISED -> R.string.voxera_reaction_surprised
    Reaction.CELEBRATE -> R.string.voxera_reaction_celebrate
}

/** 반응 고르기(시트 안). 큰 그림 문자 여섯 개를 한 줄에 두고, 누르면 [onPick]. */
@Composable
internal fun ReactionPicker(onPick: (Reaction) -> Unit, handRaised: Boolean? = null, onToggleHand: () -> Unit = {}) {
    Text(
        stringResource(R.string.voxera_reactions_title),
        color = VoxeraTheme.colors.foreground,
        style = VoxeraTheme.typography.title,
        modifier = Modifier.padding(bottom = VoxeraSpacing.sm),
    )
    // 손들기는 반응과 달리 내릴 때까지 남는다. 반응 위에 따로 둔다.
    if (handRaised != null) {
        VoxeraButton(
            stringResource(if (handRaised) R.string.voxera_hand_lower else R.string.voxera_hand_raise),
            onClick = onToggleHand,
            tone = if (handRaised) VoxeraButtonTone.Secondary else VoxeraButtonTone.Primary,
            icon = VoxeraIcons.Hand,
            modifier = Modifier.fillMaxWidth().padding(bottom = VoxeraSpacing.md),
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Reaction.entries.forEach { reaction ->
            val label = stringResource(reaction.labelRes())
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .size(52.dp)
                    .pressScale(interaction, pressed = 0.85f)
                    .clip(CircleShape)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onPick(reaction) }
                    .clearAndSetSemantics {
                        contentDescription = label
                        role = Role.Button
                        onClick(label = label) { onPick(reaction); true }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(reaction.emoji, fontSize = 30.sp)
            }
        }
    }
}

/** 화면에 떠오르는 반응 하나. [lane]은 0~1 가로 위치. */
private data class FloatingReaction(val id: Long, val event: ReactionEvent, val name: String, val lane: Float)

/**
 * 받은 반응이 아래에서 위로 떠오르며 사라지는 층. 한 번에 최대 8개만 그리고 넘치면 오래된 것부터 지운다.
 * TalkBack에는 마지막 반응 하나를 조용히 읽어 준다("Alex: 하트"). 움직임 줄이기 설정이면 제자리에서 나타났다 사라진다.
 */
@Composable
internal fun ReactionOverlay(session: VoxeraSession, participants: List<Participant>, modifier: Modifier = Modifier, bottomInset: Dp = BottomReserve) {
    val names by rememberUpdatedState(participants.associate { it.id to it })
    var floating by remember(session) { mutableStateOf(emptyList<FloatingReaction>()) }
    var announce by remember(session) { mutableStateOf<String?>(null) }
    var counter by remember(session) { mutableStateOf(0L) }
    val random = remember { Random(System.nanoTime()) }
    val you = stringResource(R.string.voxera_you)
    LaunchedEffect(session) {
        session.reactions.collect { event ->
            val participant = names[event.participantId]
            val name = when {
                participant == null -> ""
                participant.isLocal -> you
                else -> participant.displayName
            }
            counter++
            floating = (floating + FloatingReaction(counter, event, name, 0.08f + random.nextFloat() * 0.5f)).takeLast(8)
            announce = "$name: ${event.reaction.emoji}"
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val height = maxHeight
        val width = maxWidth
        floating.forEach { item ->
            key(item.id) {
                FloatingReactionView(
                    item,
                    travel = height * 0.45f,
                    startX = width * item.lane,
                    bottom = bottomInset,
                    onDone = { floating = floating.filterNot { it.id == item.id } },
                )
            }
        }
        // 읽기 전용 알림. 화면에는 보이지 않는다.
        val text = announce
        if (text != null) {
            Box(Modifier.size(1.dp).alpha(0f).semantics { contentDescription = text; liveRegion = LiveRegionMode.Polite })
        }
    }
}

@Composable
private fun FloatingReactionView(item: FloatingReaction, travel: Dp, startX: Dp, bottom: Dp, onDone: () -> Unit) {
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(if (reduced) 1_600 else 2_600, easing = if (reduced) LinearEasing else FastOutSlowInEasing))
        onDone()
    }
    val p = progress.value
    // 처음 15%는 커지며 나타나고, 마지막 30%는 흐려지며 사라진다.
    val appear = (p / 0.15f).coerceAtMost(1f)
    val fade = if (p > 0.7f) 1f - (p - 0.7f) / 0.3f else 1f
    val rise = if (reduced) 0f else p
    val sway = if (reduced) 0f else sin(p * 6f + item.id) * 10f
    Box(Modifier.fillMaxSize().clearAndSetSemantics { hideFromAccessibility() }, contentAlignment = Alignment.BottomStart) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(bottom = bottom)
                .offset { IntOffset((startX.toPx() + sway.dp.toPx()).roundToInt(), -(travel.toPx() * rise).roundToInt()) }
                .graphicsLayer {
                    alpha = appear * fade
                    val scale = 0.6f + 0.4f * appear
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            Text(item.event.reaction.emoji, fontSize = 40.sp)
            if (item.name.isNotEmpty()) {
                GlassPill {
                    Text(item.name, color = VoxeraTheme.colors.onScrim, style = VoxeraTheme.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
