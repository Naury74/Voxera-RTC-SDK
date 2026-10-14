package com.naury.voxerartc.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naury.voxerartc.ui.glass.voxeraGlass
import com.naury.voxerartc.ui.glass.GlassLevel
import com.naury.voxerartc.ui.theme.VoxeraRadius
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme

/** 버튼 강조 단계. [Danger]는 종료·거절에만 쓴다. */
public enum class VoxeraButtonTone { Primary, Secondary, Danger }

/** 높이 56dp(최소 48dp) 버튼. 글자가 커져도 높이가 늘어나 잘리지 않는다. */
@Composable
public fun VoxeraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: VoxeraButtonTone = VoxeraButtonTone.Primary,
    enabled: Boolean = true,
) {
    val colors = VoxeraTheme.colors
    val shape = RoundedCornerShape(VoxeraRadius.md)
    val (container, content) = when (tone) {
        VoxeraButtonTone.Primary -> colors.primary to colors.onPrimary
        VoxeraButtonTone.Danger -> colors.danger to colors.onDanger
        VoxeraButtonTone.Secondary -> Color.Transparent to colors.foreground
    }
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .then(if (tone == VoxeraButtonTone.Secondary) Modifier.voxeraGlass(shape, GlassLevel.Low) else Modifier.clip(shape).background(container))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = VoxeraSpacing.lg, vertical = VoxeraSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = content, style = VoxeraTheme.typography.label.copy(fontSize = VoxeraTheme.typography.body.fontSize), textAlign = TextAlign.Center)
    }
}

/**
 * 통화 조작 버튼(원 52dp + 아래 이름표). 켜짐/꺼짐은 아이콘·글자·stateDescription으로 함께 알린다.
 *
 * @param stateLabel TalkBack이 읽을 현재 상태. 예: "꺼짐", "적용 중…". `null`이면 상태가 없는 동작 버튼이다.
 * @param active `true`면 강조 채움(예: 마이크 켜짐). 동작의 결과가 엔진에서 확인되기 전에는 [pending]으로 표시한다.
 * @param accent `true`면 주 강조색(예: 통화 수락).
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
) {
    val colors = VoxeraTheme.colors
    val (container, tint) = when {
        danger -> colors.danger to colors.onDanger
        accent -> colors.primary to colors.onPrimary
        active -> colors.foreground.copy(alpha = 0.92f) to colors.canvas
        else -> colors.foreground.copy(alpha = 0.10f) to colors.foreground
    }
    Column(
        modifier = modifier
            .sizeIn(minWidth = 56.dp, minHeight = 48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(VoxeraRadius.sm))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
                stateLabel?.let { stateDescription = it }
                if (!enabled) disabled()
                onClick(label = label) { onClick(); true }
            }
            .padding(VoxeraSpacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xxs),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(container)
                .then(if (pending) Modifier.border(2.dp, colors.secondary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp).alpha(if (pending) 0.6f else 1f))
        }
        Text(
            label,
            color = colors.foreground,
            style = VoxeraTheme.typography.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

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
