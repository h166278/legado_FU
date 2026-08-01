package io.legado.app.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.NgManagedTheme
import io.legado.app.help.config.NgThemeBackground
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsItem
import io.legado.app.ui.design.components.compose.NgSettingsSectionLabel
import io.legado.app.ui.design.theme.NgTheme
import java.io.File

@Composable
internal fun ThemeEditScreen(
    theme: NgManagedTheme,
    copyOnSave: Boolean,
    onThemeChanged: (NgManagedTheme) -> Unit,
    onEditBackground: (Boolean) -> Unit
) {
    ThemeColorConfigScreen(
        colors = theme.colors,
        onColorsChanged = { onThemeChanged(theme.copy(colors = it)) },
        headerContent = {
            item(key = "theme-info") {
                NgSettingsSectionLabel(stringResource(R.string.ng_theme_details))
                NgSettingsGroup {
                    ThemeNameInput(
                        value = theme.name,
                        onValueChange = { onThemeChanged(theme.copy(name = it)) },
                        isError = theme.name.isBlank()
                    )
                    if (copyOnSave) {
                        NgSettingsItem(
                            title = stringResource(R.string.ng_theme_built_in_copy),
                            summary = stringResource(R.string.ng_theme_built_in_copy_summary),
                            enabled = false,
                            trailing = NgSettingsTrailing.NONE
                        )
                    }
                    NgSettingsItem(
                        title = stringResource(R.string.transparent_app_bars),
                        summary = stringResource(R.string.transparent_app_bars_summary),
                        trailing = NgSettingsTrailing.SWITCH,
                        checked = theme.transparentAppBars,
                        onCheckedChange = {
                            onThemeChanged(theme.copy(transparentAppBars = it))
                        }
                    )
                    ThemeBackgroundItem(
                        dark = false,
                        background = theme.lightBackground,
                        onClick = { onEditBackground(false) }
                    )
                    ThemeBackgroundItem(
                        dark = true,
                        background = theme.darkBackground,
                        onClick = { onEditBackground(true) }
                    )
                }
            }
            item(key = "theme-colors-title") {
                NgSettingsSectionLabel(stringResource(R.string.ng_theme_colors))
            }
        }
    )
}

@Composable
private fun ThemeNameInput(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean
) {
    val shape = RoundedCornerShape(18.dp)
    val colors = NgTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = TextStyle(
            color = Color(colors.onSurface),
            fontSize = 16.sp,
            lineHeight = 20.sp
        ),
        cursorBrush = SolidColor(Color(colors.primary)),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clip(shape)
                    .background(colorResource(R.color.ng_settings_item))
                    .border(
                        width = if (isError) 1.5.dp else 0.6.dp,
                        color = if (isError) {
                            Color(colors.error)
                        } else {
                            colorResource(R.color.ng_settings_item_stroke)
                        },
                        shape = shape
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                innerTextField()
            }
        }
    )
}

@Composable
private fun ThemeBackgroundItem(
    dark: Boolean,
    background: NgThemeBackground,
    onClick: () -> Unit
) {
    val path = background.path
    val source = when {
        path.isNullOrBlank() -> stringResource(R.string.ng_theme_background_none)
        path.startsWith("asset://") -> path.substringAfterLast('/')
        else -> File(path).name.ifBlank { path }
    }
    NgSettingsItem(
        title = stringResource(
            if (dark) R.string.ng_theme_dark_background else R.string.ng_theme_light_background
        ),
        summary = stringResource(R.string.ng_theme_background_summary, source, background.blur),
        onClick = onClick
    )
}
