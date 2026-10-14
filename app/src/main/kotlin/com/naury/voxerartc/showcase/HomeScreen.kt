package com.naury.voxerartc.showcase

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.naury.voxerartc.testkit.DemoScenario
import com.naury.voxerartc.ui.component.VoxeraAvatar
import com.naury.voxerartc.ui.component.VoxeraButton
import com.naury.voxerartc.ui.component.VoxeraButtonTone
import com.naury.voxerartc.ui.glass.GlassLevel
import com.naury.voxerartc.ui.glass.GlassSurface
import com.naury.voxerartc.ui.theme.VoxeraRadius
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import com.naury.voxerartc.ui.theme.VoxeraThemeConfig
import com.naury.voxerartc.ui.theme.VoxeraThemeMode

private data class ScenarioItem(val scenario: DemoScenario, @StringRes val title: Int, @StringRes val body: Int)

private val scenarios = listOf(
    ScenarioItem(DemoScenario.VOICE_ONE_ON_ONE, R.string.scenario_voice, R.string.scenario_voice_body),
    ScenarioItem(DemoScenario.VIDEO_ONE_ON_ONE, R.string.scenario_video, R.string.scenario_video_body),
    ScenarioItem(DemoScenario.GROUP_FOUR, R.string.scenario_group_four, R.string.scenario_group_four_body),
    ScenarioItem(DemoScenario.GROUP_NINE, R.string.scenario_group_nine, R.string.scenario_group_nine_body),
    ScenarioItem(DemoScenario.UNSTABLE_NETWORK, R.string.scenario_unstable, R.string.scenario_unstable_body),
    ScenarioItem(DemoScenario.SCREEN_SHARE, R.string.scenario_screen_share, R.string.scenario_screen_share_body),
)

/** 쇼케이스 홈(G-01). 오프라인 데모와 BYO LiveKit 연결을 서로 다른 카드로 나눈다. */
@Composable
fun HomeScreen(
    theme: VoxeraThemeConfig,
    onThemeChange: (VoxeraThemeMode) -> Unit,
    onStart: (DemoScenario) -> Unit,
    onDemoCall: (incoming: Boolean) -> Unit,
    onConnectLiveKit: () -> Unit,
) {
    VoxeraTheme(theme) {
        val colors = VoxeraTheme.colors
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.canvas),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = VoxeraSpacing.xl, vertical = VoxeraSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.md),
            ) {
                Text(stringResource(R.string.showcase_brand), color = colors.primary, style = VoxeraTheme.typography.label)
                Column {
                    Text(
                        stringResource(R.string.showcase_title),
                        color = colors.foreground,
                        style = VoxeraTheme.typography.display,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(R.string.showcase_subtitle), color = colors.foregroundSecondary, style = VoxeraTheme.typography.body)
                }
                Hero()
                Text(stringResource(R.string.showcase_tagline), color = colors.foreground, style = VoxeraTheme.typography.title)

                GlassSurface(level = GlassLevel.Low, modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.sm)) {
                        Text(stringResource(R.string.showcase_offline_title), color = colors.foreground, style = VoxeraTheme.typography.title, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.showcase_offline_body), color = colors.foregroundSecondary, style = VoxeraTheme.typography.body)
                        scenarios.forEach { item -> ScenarioRow(item.title, item.body, onClick = { onStart(item.scenario) }) }
                        ScenarioRow(R.string.scenario_call_incoming, R.string.scenario_call_incoming_body, onClick = { onDemoCall(true) })
                        ScenarioRow(R.string.scenario_call_outgoing, R.string.scenario_call_outgoing_body, onClick = { onDemoCall(false) })
                    }
                }

                GlassSurface(level = GlassLevel.Low, modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.sm)) {
                        Text(stringResource(R.string.byo_card_title), color = colors.foreground, style = VoxeraTheme.typography.title, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.byo_card_body), color = colors.foregroundSecondary, style = VoxeraTheme.typography.body)
                        VoxeraButton(stringResource(R.string.byo_card_action), onClick = onConnectLiveKit, tone = VoxeraButtonTone.Secondary, modifier = Modifier.fillMaxWidth())
                    }
                }

                ThemeSelector(theme.mode, onThemeChange)
                Text(stringResource(R.string.showcase_byo_footer), color = colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
            }
        }
    }
}

@Composable
private fun Hero() {
    GlassSurface(level = GlassLevel.Low, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Alex", "Taylor", "Jamie", "Morgan").forEachIndexed { index, name ->
                VoxeraAvatar(id = "hero-$index", name = name, size = 64.dp, speaking = index == 0, audioLevel = 0.6f)
            }
        }
    }
}

@Composable
private fun ScenarioRow(@StringRes title: Int, @StringRes body: Int, onClick: () -> Unit) {
    val colors = VoxeraTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(VoxeraRadius.sm))
            .background(colors.primary.copy(alpha = 0.10f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = VoxeraSpacing.md, vertical = VoxeraSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.sm),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(colors.primary))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), color = colors.foreground, style = VoxeraTheme.typography.label)
            Text(stringResource(body), color = colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
        }
    }
}

@Composable
private fun ThemeSelector(current: VoxeraThemeMode, onChange: (VoxeraThemeMode) -> Unit) {
    val colors = VoxeraTheme.colors
    val options = listOf(
        VoxeraThemeMode.SYSTEM to R.string.showcase_theme_system,
        VoxeraThemeMode.LIGHT to R.string.showcase_theme_light,
        VoxeraThemeMode.DARK to R.string.showcase_theme_dark,
    )
    Column(verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
        Text(stringResource(R.string.showcase_theme), color = colors.foregroundSecondary, style = VoxeraTheme.typography.label)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.xs)) {
            options.forEach { (mode, label) ->
                val selected = mode == current
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(CircleShape)
                        .background(if (selected) colors.primary else colors.surfaceElevated)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onChange(mode) })
                        .padding(horizontal = VoxeraSpacing.md, vertical = VoxeraSpacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(label), color = if (selected) colors.onPrimary else colors.foreground, style = VoxeraTheme.typography.label)
                }
            }
        }
    }
}
