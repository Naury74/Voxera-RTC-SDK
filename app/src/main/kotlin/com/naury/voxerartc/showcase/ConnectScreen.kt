package com.naury.voxerartc.showcase

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.naury.voxerartc.api.CallMode
import com.naury.voxerartc.ui.component.VoxeraButton
import com.naury.voxerartc.ui.component.VoxeraSegmentedControl
import com.naury.voxerartc.ui.component.VoxeraTextField
import com.naury.voxerartc.ui.component.VoxeraToggleRow
import com.naury.voxerartc.ui.glass.GlassLevel
import com.naury.voxerartc.ui.glass.GlassSurface
import com.naury.voxerartc.ui.theme.VoxeraSpacing
import com.naury.voxerartc.ui.theme.VoxeraTheme
import com.naury.voxerartc.ui.theme.VoxeraThemeConfig

@StringRes
private fun FieldProblem.messageRes(): Int = when (this) {
    FieldProblem.REQUIRED -> R.string.byo_problem_required
    FieldProblem.URL_MALFORMED -> R.string.byo_problem_malformed
    FieldProblem.URL_SCHEME -> R.string.byo_problem_scheme
    FieldProblem.URL_INSECURE -> R.string.byo_problem_insecure
    FieldProblem.ROOM_INVALID -> R.string.byo_problem_room
    FieldProblem.NAME_TOO_LONG -> R.string.byo_problem_name
    FieldProblem.TOKEN_NOT_JWT -> R.string.byo_problem_not_jwt
    FieldProblem.TOKEN_EXPIRED -> R.string.byo_problem_expired
}

/**
 * BYO LiveKit 설정(U-02/G-02). API Secret 입력칸은 없다. 오류는 사용자가 입력을 건드린 칸이나 연결을 누른 뒤에만 보여 준다.
 */
@Composable
fun ConnectScreen(
    theme: VoxeraThemeConfig,
    form: ConnectForm,
    onFormChange: (ConnectForm) -> Unit,
    onBack: () -> Unit,
    onConnect: () -> Unit,
) {
    var touched by remember { mutableStateOf(emptySet<FormField>()) }
    var submitted by remember { mutableStateOf(false) }
    val problems = form.problems(System.currentTimeMillis() / 1000)

    VoxeraTheme(theme) {
        val colors = VoxeraTheme.colors
        @Composable
        fun errorOf(field: FormField): String? =
            problems[field]?.takeIf { submitted || field in touched }?.let { stringResource(it.messageRes()) }
        fun update(field: FormField, next: ConnectForm) {
            touched = touched + field
            onFormChange(next)
        }

        Box(Modifier.fillMaxSize().background(colors.canvas), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = VoxeraSpacing.xl, vertical = VoxeraSpacing.md),
                verticalArrangement = Arrangement.spacedBy(VoxeraSpacing.md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.byo_back), tint = colors.foreground)
                    }
                    Column {
                        Text(stringResource(R.string.byo_title), color = colors.foreground, style = VoxeraTheme.typography.headline, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.byo_subtitle), color = colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
                    }
                }

                Text(stringResource(R.string.byo_method), color = colors.foregroundSecondary, style = VoxeraTheme.typography.label)
                VoxeraSegmentedControl(
                    options = listOf(
                        CredentialMethod.DEV_TOKEN_SERVER to stringResource(R.string.byo_method_dev),
                        CredentialMethod.EXISTING_TOKEN to stringResource(R.string.byo_method_token),
                        CredentialMethod.TOKEN_ENDPOINT to stringResource(R.string.byo_method_endpoint),
                    ),
                    selected = form.method,
                    onSelect = { method ->
                        submitted = false
                        touched = emptySet()
                        onFormChange(form.copy(method = method))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (form.method == CredentialMethod.DEV_TOKEN_SERVER) {
                    GlassSurface(level = GlassLevel.Low, modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(VoxeraSpacing.sm)) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(20.dp))
                            Column {
                                Text(stringResource(R.string.byo_dev_warning_title), color = colors.warning, style = VoxeraTheme.typography.label)
                                Text(stringResource(R.string.byo_dev_warning_body), color = colors.foregroundSecondary, style = VoxeraTheme.typography.caption)
                            }
                        }
                    }
                }

                form.fields.forEach { field ->
                    when (field) {
                        FormField.TOKEN_SERVER_ID -> VoxeraTextField(
                            value = form.tokenServerId,
                            onValueChange = { update(field, form.copy(tokenServerId = it)) },
                            label = stringResource(R.string.byo_field_token_server_id),
                            placeholder = stringResource(R.string.byo_field_token_server_id_hint),
                            error = errorOf(field),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FormField.SERVER_URL -> VoxeraTextField(
                            value = form.serverUrl,
                            onValueChange = { update(field, form.copy(serverUrl = it)) },
                            label = stringResource(R.string.byo_field_server_url),
                            placeholder = "wss://your-project.livekit.cloud",
                            error = errorOf(field),
                            supportingText = stringResource(R.string.byo_trust_note),
                            keyboardType = KeyboardType.Uri,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FormField.ACCESS_TOKEN -> VoxeraTextField(
                            value = form.accessToken,
                            onValueChange = { update(field, form.copy(accessToken = it)) },
                            label = stringResource(R.string.byo_field_access_token),
                            error = errorOf(field),
                            supportingText = stringResource(R.string.byo_field_access_token_help),
                            secure = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FormField.ENDPOINT_URL -> VoxeraTextField(
                            value = form.endpointUrl,
                            onValueChange = { update(field, form.copy(endpointUrl = it)) },
                            label = stringResource(R.string.byo_field_endpoint_url),
                            placeholder = "https://example.com/livekit/token",
                            error = errorOf(field),
                            supportingText = stringResource(R.string.byo_field_endpoint_help),
                            keyboardType = KeyboardType.Uri,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FormField.ROOM_NAME -> VoxeraTextField(
                            value = form.roomName,
                            onValueChange = { update(field, form.copy(roomName = it)) },
                            label = stringResource(R.string.byo_field_room),
                            placeholder = "team-demo",
                            error = errorOf(field),
                            supportingText = stringResource(R.string.byo_field_room_help),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FormField.DISPLAY_NAME -> VoxeraTextField(
                            value = form.displayName,
                            onValueChange = { update(field, form.copy(displayName = it)) },
                            label = stringResource(R.string.byo_field_name),
                            error = errorOf(field),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Text(stringResource(R.string.byo_mode), color = colors.foregroundSecondary, style = VoxeraTheme.typography.label)
                VoxeraSegmentedControl(
                    options = listOf(CallMode.VOICE to stringResource(R.string.byo_mode_voice), CallMode.VIDEO to stringResource(R.string.byo_mode_video)),
                    selected = form.mode,
                    onSelect = { onFormChange(form.copy(mode = it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                VoxeraToggleRow(
                    label = stringResource(R.string.byo_local_dev),
                    description = stringResource(R.string.byo_local_dev_body),
                    checked = form.allowLocalDev,
                    onCheckedChange = { onFormChange(form.copy(allowLocalDev = it)) },
                )
                VoxeraButton(
                    text = stringResource(R.string.byo_connect),
                    onClick = {
                        submitted = true
                        if (problems.isEmpty()) onConnect()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
