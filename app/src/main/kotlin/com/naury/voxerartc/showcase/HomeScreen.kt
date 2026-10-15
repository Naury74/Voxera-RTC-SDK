package com.naury.voxerartc.showcase

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.naury.voxerartc.testkit.DemoScenario
import com.naury.voxerartc.ui.component.GlassListDivider
import com.naury.voxerartc.ui.component.GlassListGroup
import com.naury.voxerartc.ui.component.GlassListRow
import com.naury.voxerartc.ui.component.StatusChip
import com.naury.voxerartc.ui.component.VoxeraAvatar
import com.naury.voxerartc.ui.component.VoxeraButton
import com.naury.voxerartc.ui.component.VoxeraIcons
import com.naury.voxerartc.ui.component.VoxeraSegmentedControl
import com.naury.voxerartc.ui.glass.GlassLevel
import com.naury.voxerartc.ui.glass.GlassSurface
import com.naury.voxerartc.ui.glass.voxeraBackdrop
import com.naury.voxerartc.ui.theme.VoxeraColors
import com.naury.voxerartc.ui.theme.VoxeraRadius
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import com.naury.voxerartc.ui.theme.VoxeraThemeConfig
import com.naury.voxerartc.ui.theme.VoxeraThemeMode
import com.naury.voxerartc.ui.theme.enterOnAppear

/** 홈 목록 한 줄. [tile]은 테마 색 중 하나를 고른다. */
private data class HomeItem(
    @StringRes val title: Int,
    @StringRes val body: Int,
    val icon: ImageVector,
    val tile: (VoxeraColors) -> Color,
    val onClick: () -> Unit,
)

/** 쇼케이스 홈(G-01). 오프라인 데모와 BYO LiveKit 연결을 서로 다른 묶음으로 나눈다. */
@Composable
fun HomeScreen(
    theme: VoxeraThemeConfig,
    onThemeChange: (VoxeraThemeMode) -> Unit,
    onStart: (DemoScenario) -> Unit,
    onDemoCall: (incoming: Boolean) -> Unit,
    onConnectLiveKit: () -> Unit,
) {
    val calls = listOf(
        HomeItem(R.string.scenario_voice, R.string.scenario_voice_body, VoxeraIcons.Call, { it.primary }) { onStart(DemoScenario.VOICE_ONE_ON_ONE) },
        HomeItem(R.string.scenario_video, R.string.scenario_video_body, VoxeraIcons.Videocam, { it.secondary }) { onStart(DemoScenario.VIDEO_ONE_ON_ONE) },
        HomeItem(R.string.scenario_group_four, R.string.scenario_group_four_body, VoxeraIcons.Group, { it.primary }) { onStart(DemoScenario.GROUP_FOUR) },
        HomeItem(R.string.scenario_group_nine, R.string.scenario_group_nine_body, VoxeraIcons.GridView, { it.secondary }) { onStart(DemoScenario.GROUP_NINE) },
        HomeItem(R.string.scenario_screen_share, R.string.scenario_screen_share_body, VoxeraIcons.ScreenShare, { it.primary }) { onStart(DemoScenario.SCREEN_SHARE) },
        HomeItem(R.string.scenario_unstable, R.string.scenario_unstable_body, VoxeraIcons.Wifi, { it.warning }) { onStart(DemoScenario.UNSTABLE_NETWORK) },
    )
    val ringing = listOf(
        HomeItem(R.string.scenario_call_incoming, R.string.scenario_call_incoming_body, VoxeraIcons.Call, { it.primary }) { onDemoCall(true) },
        HomeItem(R.string.scenario_call_outgoing, R.string.scenario_call_outgoing_body, VoxeraIcons.Call, { it.secondary }) { onDemoCall(false) },
    )
    VoxeraTheme(theme) {
        val colors = VoxeraTheme.colors
        Box(Modifier.fillMaxSize().voxeraBackdrop(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = VoxeraSpacing.md, vertical = VoxeraSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs),
            ) {
                Column(Modifier.padding(horizontal = VoxeraSpacing.xs).enterOnAppear(0)) {
                    Text(stringResource(R.string.showcase_brand), color = colors.primary, style = VoxeraTheme.typography.label)
                    Text(
                        stringResource(R.string.showcase_title),
                        color = colors.foreground,
                        style = VoxeraTheme.typography.display,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(R.string.showcase_subtitle), color = colors.foregroundSecondary, style = VoxeraTheme.typography.body)
                }
                Spacer(Modifier.height(VoxeraSpacing.xs))
                Box(Modifier.enterOnAppear(1, scale = true)) { Hero(onTry = { onStart(DemoScenario.GROUP_FOUR) }) }

                SectionHeader(R.string.showcase_offline_title, R.string.showcase_offline_body, Modifier.enterOnAppear(2))
                GlassListGroup(Modifier.fillMaxWidth().enterOnAppear(3)) {
                    calls.forEachIndexed { index, item ->
                        if (index > 0) GlassListDivider()
                        GlassListRow(stringResource(item.title), item.onClick, icon = item.icon, tileColor = item.tile(colors), subtitle = stringResource(item.body), modifier = Modifier.enterOnAppear(4 + index))
                    }
                }

                SectionHeader(R.string.showcase_section_ringing, R.string.showcase_section_ringing_body)
                GlassListGroup(Modifier.fillMaxWidth()) {
                    ringing.forEachIndexed { index, item ->
                        if (index > 0) GlassListDivider()
                        GlassListRow(stringResource(item.title), item.onClick, icon = item.icon, tileColor = item.tile(colors), subtitle = stringResource(item.body))
                    }
                }

                SectionHeader(R.string.byo_card_title, R.string.byo_card_body)
                GlassListGroup(Modifier.fillMaxWidth()) {
                    GlassListRow(
                        stringResource(R.string.byo_card_action),
                        onConnectLiveKit,
                        icon = VoxeraIcons.Code,
                        tileColor = colors.secondary,
                        subtitle = stringResource(R.string.showcase_byo_footer),
                    )
                }

                SectionHeader(R.string.showcase_theme, null)
                VoxeraSegmentedControl(
                    options = listOf(
                        VoxeraThemeMode.SYSTEM to stringResource(R.string.showcase_theme_system),
                        VoxeraThemeMode.LIGHT to stringResource(R.string.showcase_theme_light),
                        VoxeraThemeMode.DARK to stringResource(R.string.showcase_theme_dark),
                    ),
                    selected = theme.mode,
                    onSelect = onThemeChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(VoxeraSpacing.xl))
            }
        }
    }
}

@Composable
private fun SectionHeader(@StringRes title: Int, @StringRes body: Int?, modifier: Modifier = Modifier) {
    val colors = VoxeraTheme.colors
    Column(modifier.padding(start = VoxeraSpacing.xs, end = VoxeraSpacing.xs, top = VoxeraSpacing.lg, bottom = VoxeraSpacing.xxs)) {
        Text(stringResource(title), color = colors.foreground, style = VoxeraTheme.typography.title, modifier = Modifier.semantics { heading() })
        if (body != null) Text(stringResource(body), color = colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
    }
}

/** 겹쳐 놓은 이니셜 아바타, 소개 문구, 기능 칩, 바로 해 보기 버튼. 아바타 묶음은 장식이라 읽지 않는다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(onTry: () -> Unit) {
    val colors = VoxeraTheme.colors
    GlassSurface(
        level = GlassLevel.Low,
        shape = RoundedCornerShape(VoxeraRadius.xxl),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(VoxeraSpacing.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.md)) {
            Row(Modifier.clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) {
                // 겹친 아바타 사이를 카드 색 테두리로 갈라 한 장씩 구분되게 한다.
                val cut = colors.surface
                listOf("Alex", "Taylor", "Jamie", "Morgan").forEachIndexed { index, name ->
                    Box(
                        Modifier
                            .offset(x = (-14 * index).dp)
                            .enterOnAppear(2 + index, scale = true)
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(cut),
                        contentAlignment = Alignment.Center,
                    ) {
                        VoxeraAvatar(id = "hero-$index", name = name, size = 56.dp, speaking = index == 0, audioLevel = 0.7f)
                    }
                }
            }
            Text(stringResource(R.string.showcase_tagline), color = colors.foreground, style = VoxeraTheme.typography.headline)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs), verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
                listOf(R.string.feature_voice, R.string.feature_video, R.string.feature_groups, R.string.feature_share, R.string.feature_pip, R.string.feature_effects).forEach {
                    StatusChip(stringResource(it), colors.primary)
                }
            }
            VoxeraButton(stringResource(R.string.showcase_try), onClick = onTry, icon = VoxeraIcons.Videocam, modifier = Modifier.fillMaxWidth())
        }
    }
}
