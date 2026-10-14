package com.naury.voxerartc.ui.component

import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import com.naury.voxerartc.ui.glass.GlassLevel
import com.naury.voxerartc.ui.glass.voxeraGlass
import com.naury.voxerartc.ui.theme.rememberReducedMotion
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.naury.voxerartc.ui.theme.VoxeraRadius
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme

/**
 * 한 줄 입력칸. [secure]면 글자를 가리고 키보드 학습·자동 완성을 끈다(토큰 입력용). 가린 칸은 복사·잘라내기가 막힌다.
 *
 * @param error 값이 있으면 입력칸 아래에 오류로 보여 주고 TalkBack에도 오류로 알린다.
 * @param supportingText 오류가 없을 때 보여 줄 도움말.
 */
@Composable
public fun VoxeraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    supportingText: String? = null,
    secure: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
) {
    val colors = VoxeraTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(VoxeraRadius.md)
    val borderColor = when {
        error != null -> colors.danger
        focused -> colors.focusRing
        else -> colors.glassStroke
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xxs)) {
        Text(label, color = colors.foregroundSecondary, style = VoxeraTheme.typography.label)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = VoxeraTheme.typography.body.copy(color = colors.foreground),
            cursorBrush = SolidColor(colors.primary),
            interactionSource = interaction,
            visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (secure) KeyboardType.Password else keyboardType,
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = label
                    if (secure) password()
                    if (error != null) error(error)
                },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(shape)
                        .background(if (colors.isDark) colors.surfaceElevated.copy(alpha = 0.85f) else colors.surface)
                        .border(if (focused || error != null) 2.dp else 1.dp, borderColor, shape)
                        .padding(horizontal = VoxeraSpacing.md, vertical = VoxeraSpacing.sm),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, color = colors.foregroundSecondary.copy(alpha = 0.6f), style = VoxeraTheme.typography.body, maxLines = 1)
                    }
                    inner()
                }
            },
        )
        val help = error ?: supportingText
        if (help != null) {
            Text(help, color = if (error != null) colors.danger else colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
        }
    }
}

/**
 * 하나만 고르는 알약 묶음. 유리 홈 위로 밝은 선택 알약이 튕기듯 옮겨 간다. 항목 폭은 모두 같고 최소 48dp이며
 * TalkBack에서 라디오 버튼으로 읽힌다.
 */
@Composable
public fun <T> VoxeraSegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VoxeraTheme.colors
    val reduced = rememberReducedMotion()
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    BoxWithConstraints(
        modifier
            .heightIn(min = 48.dp)
            .voxeraGlass(CircleShape, GlassLevel.Low)
            .padding(VoxeraSpacing.xxs),
    ) {
        val segment = maxWidth / options.size.coerceAtLeast(1)
        val offset by animateDpAsState(
            segment * index,
            if (reduced) snap() else spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
            label = "segment",
        )
        // 선택 알약. 밝은 면과 그림자로 홈 위에 떠 있는 것처럼 보인다.
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .offset { IntOffset(offset.roundToPx(), 0) }
                    .width(segment)
                    .fillMaxHeight()
                    .shadow(3.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                    .clip(CircleShape)
                    .background(if (colors.isDark) colors.primary.copy(alpha = 0.32f) else colors.surface)
                    .border(1.dp, if (colors.isDark) colors.primary.copy(alpha = 0.5f) else colors.glassStroke, CircleShape),
            )
        }
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(CircleShape)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(value) })
                        .padding(horizontal = VoxeraSpacing.sm, vertical = VoxeraSpacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (isSelected) colors.foreground else colors.foregroundSecondary,
                        style = VoxeraTheme.typography.label,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** 켜고 끄는 한 줄 설정. 줄 전체를 누를 수 있고 상태는 글자로도 보여 준다. */
@Composable
public fun VoxeraToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val colors = VoxeraTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(VoxeraRadius.sm))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = VoxeraSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = colors.foreground, style = VoxeraTheme.typography.body)
            if (description != null) Text(description, color = colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
        }
        VoxeraSwitch(checked)
    }
}

/** 켜짐/꺼짐 스위치 그림. 색과 함께 손잡이 위치로도 상태를 보여 준다. 누르기·읽기는 감싼 줄이 맡는다. */
@Composable
private fun VoxeraSwitch(checked: Boolean) {
    val colors = VoxeraTheme.colors
    val reduced = rememberReducedMotion()
    val knob by animateDpAsState(if (checked) 20.dp else 0.dp, if (reduced) snap() else spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium), label = "knob")
    val track by animateColorAsState(if (checked) colors.primary else colors.foreground.copy(alpha = 0.18f), label = "track")
    Box(
        Modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(CircleShape)
            .background(track)
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset { IntOffset(knob.roundToPx(), 0) }
                .size(27.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
