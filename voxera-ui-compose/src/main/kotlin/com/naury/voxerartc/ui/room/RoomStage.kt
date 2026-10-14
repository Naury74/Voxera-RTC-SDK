package com.naury.voxerartc.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naury.voxerartc.api.Participant
import com.naury.voxerartc.ui.R
import com.naury.voxerartc.ui.component.ParticipantTile
import com.naury.voxerartc.ui.component.VoxeraAvatar
import com.naury.voxerartc.ui.theme.VoxeraRadius
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import com.naury.voxerartc.ui.video.VoxeraVideoRenderer

/** 상단 상태 줄과 하단 조작 막대가 가리지 않도록 비워 두는 높이. */
private val TopReserve = 64.dp
private val BottomReserve = 116.dp

/**
 * 영상 무대. 2명은 상대 전체 화면 + 내 영상 모서리(1:1), 고정한 타일이 있으면 큰 타일 + 아래 줄, 그 밖에는 격자.
 */
@Composable
internal fun VideoStage(
    participants: List<Participant>,
    renderer: VoxeraVideoRenderer?,
    pinnedId: String?,
    landscape: Boolean,
    onTogglePin: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pinned = participants.firstOrNull { it.id == pinnedId }
    val local = participants.firstOrNull { it.isLocal }
    val remotes = participants.filterNot { it.isLocal }
    when {
        pinned != null -> PinnedStage(pinned, participants.filterNot { it.id == pinned.id }, renderer, onTogglePin, modifier)
        remotes.size == 1 && local != null -> OneOnOneStage(remotes.single(), local, renderer, onTogglePin, modifier)
        else -> GridStage(participants, renderer, landscape, onTogglePin, modifier)
    }
}

@Composable
private fun OneOnOneStage(remote: Participant, local: Participant, renderer: VoxeraVideoRenderer?, onTogglePin: (String) -> Unit, modifier: Modifier) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    Box(modifier) {
        ParticipantTile(
            remote,
            renderer,
            Modifier.fillMaxSize(),
            onTogglePin = { onTogglePin(remote.id) },
            labelPadding = PaddingValues(
                start = insets.calculateStartPadding(direction) + VoxeraSpacing.xs,
                bottom = insets.calculateBottomPadding() + BottomReserve,
            ),
        )
        ParticipantTile(
            local,
            renderer,
            Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = TopReserve, end = VoxeraSpacing.md)
                .width(112.dp)
                .aspectRatio(3f / 4f),
        )
    }
}

@Composable
private fun PinnedStage(pinned: Participant, others: List<Participant>, renderer: VoxeraVideoRenderer?, onTogglePin: (String) -> Unit, modifier: Modifier) {
    Column(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = TopReserve, bottom = BottomReserve, start = VoxeraSpacing.xs, end = VoxeraSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs),
    ) {
        ParticipantTile(pinned, renderer, Modifier.fillMaxWidth().weight(1f), pinned = true, onTogglePin = { onTogglePin(pinned.id) })
        LazyRow(horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
            items(others, key = { it.id }) { participant ->
                ParticipantTile(participant, renderer, Modifier.size(width = 96.dp, height = 128.dp), onTogglePin = { onTogglePin(participant.id) })
            }
        }
    }
}

@Composable
private fun GridStage(participants: List<Participant>, renderer: VoxeraVideoRenderer?, landscape: Boolean, onTogglePin: (String) -> Unit, modifier: Modifier) {
    val (shown, hidden) = visibleTiles(participants)
    val spec = gridFor(shown.size + if (hidden > 0) 1 else 0, landscape)
    Column(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = TopReserve, bottom = BottomReserve, start = VoxeraSpacing.xs, end = VoxeraSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs),
    ) {
        val cells: List<Participant?> = shown + if (hidden > 0) listOf(null) else emptyList()
        cells.chunked(spec.columns).forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
                row.forEach { participant ->
                    val cell = Modifier.weight(1f).fillMaxHeight()
                    if (participant == null) OverflowCell(hidden, cell)
                    else ParticipantTile(participant, renderer, cell, onTogglePin = { onTogglePin(participant.id) })
                }
                repeat(spec.columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun OverflowCell(hidden: Int, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(VoxeraRadius.md)).background(VoxeraTheme.colors.surfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        Text(pluralStringResource(R.plurals.voxera_more_participants, hidden, hidden), color = VoxeraTheme.colors.foreground, style = VoxeraTheme.typography.title)
    }
}

/** 음성 그룹 통화: 아바타 격자. 말하는 사람은 고리로 표시한다. */
@Composable
internal fun VoiceGroupGrid(participants: List<Participant>, modifier: Modifier = Modifier) {
    val (shown, hidden) = visibleTiles(participants)
    val columns = if (shown.size <= 4) 2 else 3
    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = VoxeraSpacing.md)) {
        val avatar = ((maxWidth - VoxeraSpacing.md * (columns - 1)) / columns * 0.6f).coerceIn(56.dp, 120.dp)
        Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.lg)) {
            shown.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { participant ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            VoxeraAvatar(participant.id, participant.displayName, size = avatar, speaking = participant.isSpeaking, audioLevel = participant.audioLevel)
                            Text(
                                if (participant.isLocal) stringResource(R.string.voxera_you) else participant.displayName,
                                color = VoxeraTheme.colors.foreground,
                                style = VoxeraTheme.typography.label,
                                maxLines = 1,
                                modifier = Modifier.padding(top = VoxeraSpacing.xs),
                            )
                        }
                    }
                    repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                }
            }
            if (hidden > 0) {
                Text(
                    pluralStringResource(R.plurals.voxera_more_participants, hidden, hidden),
                    color = VoxeraTheme.colors.foregroundSecondary,
                    style = VoxeraTheme.typography.body,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}
