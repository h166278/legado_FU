package io.legado.app.ui.config

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsIcon
import io.legado.app.ui.design.components.compose.NgSettingsItem
import io.legado.app.ui.design.components.compose.NgSettingsSectionLabel

internal data class ReadAloudConfigScreenState(
    val multiRoleEngineSummary: String = ""
)

@Composable
internal fun ReadAloudConfigScreen(
    state: ReadAloudConfigScreenState,
    onOpenTtsEngine: () -> Unit,
    onOpenMultiRoleEngine: () -> Unit,
    onOpenDefaultVoice: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
    ) {
        NgSettingsSectionLabel(stringResource(R.string.read_aloud_settings_section_features))
        NgSettingsGroup {
            ReadAloudConfigEntry(
                title = stringResource(R.string.tts_engine_settings),
                summary = stringResource(R.string.tts_engine_settings_summary),
                iconRes = R.drawable.ic_ai_capability_tts,
                onClick = onOpenTtsEngine
            )
            ReadAloudConfigEntry(
                title = stringResource(R.string.multi_role_tts_engine),
                summary = state.multiRoleEngineSummary,
                iconRes = R.drawable.ic_groups,
                onClick = onOpenMultiRoleEngine
            )
            ReadAloudConfigEntry(
                title = stringResource(R.string.default_tts_voice),
                summary = stringResource(R.string.default_tts_voice_summary),
                iconRes = R.drawable.ic_tts_tab_voice,
                onClick = onOpenDefaultVoice
            )
        }
    }
}

@Composable
private fun ReadAloudConfigEntry(
    title: String,
    summary: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit
) {
    NgSettingsItem(
        title = title,
        summary = summary,
        onClick = onClick,
        leading = {
            NgSettingsIcon(
                painter = painterResource(iconRes),
                contentDescription = null
            )
        }
    )
}
