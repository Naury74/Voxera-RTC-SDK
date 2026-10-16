package com.naury.voxerartc.ui.component

import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import com.naury.kinetica.ui.jellyPress
import com.naury.voxerartc.ui.theme.rememberReducedMotion
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naury.voxerartc.ui.glass.voxeraGlass
import com.naury.voxerartc.ui.glass.GlassLevel
import com.naury.voxerartc.ui.glass.pressScale
import com.naury.voxerartc.ui.theme.VoxeraRadius
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme

/** 버튼 강조 단계. [Danger]는 종료와 거절에만 쓴다. */
public enum class VoxeraButtonTone { Primary, Secondary, Danger }

/**
 * 높이 56dp(최소 48dp) 캡슐 버튼. 글자가 커져도 높이가 늘어나 잘리지 않는다. 누르면 살짝 작아졌다 돌아온다.
 *
 * @param icon 글자 앞에 둘 아이콘. 뜻은 [text]가 전하므로 TalkBack은 아이콘을 읽지 않는다.
 */
@Composable
public fun VoxeraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: VoxeraButtonTone = VoxeraButtonTone.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = VoxeraTheme.colors
    val shape = CircleShape
    val interaction = remember { MutableInteractionSource() }
    val (container, content) = when (tone) {
        VoxeraButtonTone.Primary -> colors.primary to colors.onPrimary
        VoxeraButtonTone.Danger -> colors.danger to colors.onDanger
        VoxeraButtonTone.Secondary -> Color.Transparent to colors.foreground
    }
    Row(
        modifier = modifier
            .pressScale(interaction)
            .heightIn(min = 56.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .then(
                if (tone == VoxeraButtonTone.Secondary) Modifier.voxeraGlass(shape, GlassLevel.Low)
                else Modifier.clip(shape).background(container)
            )
            .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = VoxeraSpacing.lg, vertical = VoxeraSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(text, color = content, style = VoxeraTheme.typography.label.copy(fontSize = VoxeraTheme.typography.body.fontSize), textAlign = TextAlign.Center)
    }
}

/**
 * 통화 조작 버튼(원 52dp + 아래 이름표). 켜짐/꺼짐은 아이콘, 글자, stateDescription으로 함께 알린다.
 *
 * @param stateLabel TalkBack이 읽을 현재 상태. 예: "꺼짐", "적용 중…". `null`이면 상태가 없는 동작 버튼이다.
 * @param active `true`면 강조색으로 채운다(예: 마이크 켜짐). 엔진이 결과를 확인하기 전에는 [pending]으로 표시한다.
 * @param accent `true`면 주 강조색을 쓴다(예: 통화 수락).
 * @param circleSize 원 크기. 버튼이 많아 폭이 좁으면 48dp(최소 터치 크기)까지 줄인다.
 */
@Composable
public fun CallControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stateLabel: String? = null,
    active: Boolean = false,
    pending: Boolean = false,
    danger: Boolean = false,
    accent: Boolean = false,
    enabled: Boolean = true,
    circleSize: Dp = 52.dp,
    badge: Int = 0,
) {
    val colors = VoxeraTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    // 누를 때 짧은 진동으로 조작이 먹었음을 알린다. 시스템 터치 진동을 끈 기기에서는 울리지 않는다.
    val click = {
        haptics.performHapticFeedback(if (danger) HapticFeedbackType.Confirm else HapticFeedbackType.ContextClick)
        onClick()
    }
    val (containerTarget, tintTarget) = when {
        danger -> colors.danger to colors.onDanger
        accent -> colors.primary to colors.onPrimary
        active -> colors.foreground.copy(alpha = 0.92f) to colors.canvas
        else -> colors.foreground.copy(alpha = if (colors.isDark) 0.14f else 0.08f) to colors.foreground
    }
    // 켜짐/꺼짐이 바뀌면 색이 부드럽게 넘어가고 원이 한 번 통 튄다.
    val container by animateColorAsState(containerTarget, tween(220), label = "container")
    val tint by animateColorAsState(tintTarget, tween(220), label = "tint")
    val reduced = rememberReducedMotion()
    val bounce = remember { Animatable(1f) }
    val seen = remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        // 처음 그릴 때는 튀지 않는다. 사용자가 바꾼 뒤부터.
        if (!seen.value) {
            seen.value = true
            return@LaunchedEffect
        }
        if (reduced) return@LaunchedEffect
        bounce.snapTo(0.86f)
        bounce.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
    }
    // 진행 중이면 테두리가 맥박처럼 옅어졌다 진해진다.
    val pulse = if (pending && !reduced) {
        val transition = rememberInfiniteTransition(label = "pending")
        val value by transition.animateFloat(0.35f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse")
        value
    } else 1f
    Column(
        modifier = modifier
            .width(circleSize.coerceAtLeast(48.dp) + 8.dp)
            .sizeIn(minHeight = 48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(VoxeraRadius.sm))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = click)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
                stateLabel?.let { stateDescription = it }
                if (!enabled) disabled()
                onClick(label = label) { click(); true }
            }
            .padding(VoxeraSpacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xxs),
    ) {
        // 원 밖으로 조금 나오는 숫자 배지(읽지 않은 메시지 등)를 위해 한 겹 더 감싼다. 배지 내용은 stateLabel로 읽힌다.
        Box {
        Box(
            modifier = Modifier
                // 누르는 동안 원이 젤리처럼 옆으로 퍼지며 눌리고, 놓으면 출렁이며 돌아온다.
                .jellyPress(0.1f)
                .graphicsLayer {
                    scaleX = bounce.value
                    scaleY = bounce.value
                }
                .size(circleSize.coerceAtLeast(48.dp))
                .clip(CircleShape)
                .background(container)
                .then(if (pending) Modifier.border(2.dp, colors.secondary.copy(alpha = pulse), CircleShape) else Modifier)
                .indication(interaction, LocalIndication.current),
            contentAlignment = Alignment.Center,
        ) {
            // 아이콘이 바뀌면(마이크 ↔ 마이크 꺼짐) 작아지며 사라지고 새 아이콘이 커지며 나온다.
            AnimatedContent(
                targetState = icon,
                transitionSpec = {
                    (scaleIn(tween(180), initialScale = 0.6f) + fadeIn(tween(180))) togetherWith (scaleOut(tween(120), targetScale = 0.6f) + fadeOut(tween(120)))
                },
                label = "icon",
            ) { shown ->
                Icon(shown, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp).alpha(if (pending) 0.6f else 1f))
            }
        }
        // 배지는 생길 때 통 튀어나오고 없어질 때 줄어들며 사라진다. 사라지는 동안에는 마지막 숫자를 보여 준다.
        val badgeScale by animateFloatAsState(if (badge > 0) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "badge")
        var lastBadge by remember { mutableStateOf(badge) }
        if (badge > 0) lastBadge = badge
        if (badgeScale > 0.01f) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-2).dp)
                    .graphicsLayer {
                        scaleX = badgeScale
                        scaleY = badgeScale
                    }
                    .sizeIn(minWidth = 20.dp, minHeight = 20.dp)
                    .clip(CircleShape)
                    .background(colors.danger)
                    .border(1.5.dp, colors.canvas, CircleShape)
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (lastBadge > 99) "99+" else lastBadge.toString(), color = colors.onDanger, style = VoxeraTheme.typography.caption, maxLines = 1)
            }
        }
        }
        // 막대 폭은 고정이라 이름표 글꼴만 최대 1.3배까지 키운다. 전체 이름은 contentDescription으로 읽힌다.
        val fontScale = LocalDensity.current.fontScale
        val caption = VoxeraTheme.typography.caption
        val labelStyle = if (fontScale > MAX_LABEL_SCALE) caption.copy(fontSize = caption.fontSize * (MAX_LABEL_SCALE / fontScale)) else caption
        Text(
            label,
            color = colors.foreground,
            style = labelStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private const val MAX_LABEL_SCALE = 1.3f

/** 작은 상태 칩(예: DEMO, 연결 약함). 색만으로 뜻을 전하지 않도록 항상 글자를 담는다. */
@Composable
public fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = VoxeraSpacing.sm, vertical = VoxeraSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xxs),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, color = color, style = VoxeraTheme.typography.caption)
    }
}
