package com.naury.voxerartc.ui.component

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KSegmentSizing
import com.naury.kinetica.ui.KSegmentedControl
import com.naury.kinetica.ui.KSwitch
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.shake
import com.naury.voxerartc.ui.theme.VoxeraRadius
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme

/**
 * 한 줄 입력칸. [secure]면 토큰 입력용으로 글자를 가리고 키보드 학습과 자동 완성을 끈다. 가린 칸에서는 복사와 잘라내기를 할 수 없다.
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
    // 새 오류가 생길 때마다 입력란이 좌우로 짧게 흔들린다. 오류가 사라질 때는 흔들지 않는다.
    var errorShakes by remember { mutableStateOf(0) }
    LaunchedEffect(error) { if (error != null) errorShakes++ }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xxs)) {
        // 이름과 오류는 입력 칸이 읽으므로("편집 상자, 이름, 오류: …") 위아래 글자는 TalkBack이 따로 멈추지 않게 한다.
        Text(label, color = colors.foregroundSecondary, style = VoxeraTheme.typography.label, modifier = Modifier.clearAndSetSemantics {})
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
                .shake(errorShakes, distance = 8.dp)
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
            Text(
                help,
                color = if (error != null) colors.danger else colors.foregroundSecondary,
                style = VoxeraTheme.typography.caption,
                modifier = if (error != null) Modifier.clearAndSetSemantics {} else Modifier,
            )
        }
    }
}

/**
 * 하나만 고르는 알약 모양 선택 묶음. 고르면 선택 표시가 액체처럼 늘어났다 줄며 옮겨 간다. 항목 폭은 모두 같다.
 * TalkBack에서는 라디오 버튼으로 읽힌다. 항목이 하나뿐이면 고를 것이 없으므로 이름만 보여 준다.
 */
@Composable
public fun <T> VoxeraSegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.size < 2) {
        options.firstOrNull()?.let { Text(it.second, color = VoxeraTheme.colors.foreground, style = VoxeraTheme.typography.label, modifier = modifier) }
        return
    }
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    // 이름 길이가 크게 달라도 짧은 칸의 남는 폭을 긴 칸에 주고, 글꼴을 키우면 칸 높이가 늘며 세 줄까지 넘긴다.
    KSegmentedControl(
        options.map { it.second },
        index,
        onSelect = { onSelect(options[it].first) },
        modifier = modifier,
        tone = KTone.Accent,
        maxLines = 3,
        sizing = KSegmentSizing.Proportional,
    )
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
    KSwitch(checked, onCheckedChange = null)
}
