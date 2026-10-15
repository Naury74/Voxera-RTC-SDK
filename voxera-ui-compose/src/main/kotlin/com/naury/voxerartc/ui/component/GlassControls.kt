package com.naury.voxerartc.ui.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.naury.voxerartc.ui.glass.GlassLevel
import com.naury.voxerartc.ui.glass.GlassSurface
import com.naury.voxerartc.ui.glass.pressScale
import com.naury.voxerartc.ui.theme.VoxeraRadius
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import com.naury.voxerartc.ui.theme.readableOn

/** 둥근 사각형 단색 바탕에 아이콘을 얹은 타일. 목록 행 앞이나 기능 소개에 쓴다. 아이콘은 장식이므로 TalkBack이 읽지 않는다. */
@Composable
public fun VoxeraIconTile(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val tint = readableOn(color)
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.6f))
    }
}

/** 여러 행을 유리 카드 한 장에 모은다. 행 사이 구분선은 아이콘 뒤에서 시작하는 가는 선이다. */
@Composable
public fun GlassListGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(level = GlassLevel.Low, shape = RoundedCornerShape(VoxeraRadius.xl), contentPadding = PaddingValues(vertical = VoxeraSpacing.xxs), modifier = modifier) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

/** [GlassListGroup] 안의 행 구분선. [inset]은 왼쪽 들여쓰기(보통 아이콘 폭만큼). */
@Composable
public fun GlassListDivider(inset: androidx.compose.ui.unit.Dp = 60.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(1.dp)
            .background(VoxeraTheme.colors.glassStroke),
    )
}

/**
 * 목록 한 줄. 앞에 색 타일, 가운데 제목과 설명, 끝에 화살표(또는 [trailing])를 둔다. 최소 높이는 56dp이고 누르면 살짝 눌린다.
 *
 * @param tileColor 앞쪽 타일 색. `null`이면 [VoxeraTheme] 강조색.
 */
@Composable
public fun GlassListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tileColor: Color? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = VoxeraTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .pressScale(interaction, pressed = 0.98f)
            .heightIn(min = 56.dp)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(horizontal = VoxeraSpacing.md, vertical = VoxeraSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.sm),
    ) {
        if (icon != null) VoxeraIconTile(icon, tileColor ?: colors.primary)
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.foreground, style = VoxeraTheme.typography.body.copy(fontWeight = VoxeraTheme.typography.label.fontWeight))
            if (subtitle != null) Text(subtitle, color = colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
        }
        if (trailing != null) trailing()
        else Icon(VoxeraIcons.ChevronRight, contentDescription = null, tint = colors.foregroundSecondary.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
    }
}

/** 바텀 시트 위쪽 손잡이. 장식이며, 닫기는 바깥 누르기·뒤로 가기·아래로 끌기로 한다. */
@Composable
internal fun SheetGrabber(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 36.dp, height = 5.dp)
            .clip(CircleShape)
            .background(VoxeraTheme.colors.foregroundSecondary.copy(alpha = 0.4f)),
    )
}
