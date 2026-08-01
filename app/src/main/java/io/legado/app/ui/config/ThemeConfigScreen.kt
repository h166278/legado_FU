package io.legado.app.ui.config

import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MonochromePhotos
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgFloatingTabBar
import io.legado.app.ui.design.components.compose.NgFloatingTabSpec
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsItem
import io.legado.app.ui.design.components.compose.NgSettingsSectionLabel

internal data class ThemeConfigScreenState(
    val themeMode: String = "0",
    val showLauncherIcon: Boolean = true,
    @param:DrawableRes val launcherIconRes: Int = R.mipmap.ic_launcher,
    val floatingBottomBar: Boolean = false,
    val transparentAppBars: Boolean = false,
    val fontScaleSummary: String = "",
    val dayBackgroundSummary: String = "",
    val nightBackgroundSummary: String = ""
)

@Composable
internal fun ThemeConfigScreen(
    state: ThemeConfigScreenState,
    onThemeModeSelected: (String) -> Unit,
    onLauncherIconClick: () -> Unit,
    onFloatingBottomBarChanged: (Boolean) -> Unit,
    onTransparentAppBarsChanged: (Boolean) -> Unit,
    onOpenCustomColors: () -> Unit,
    onOpenFontScale: () -> Unit,
    onOpenCoverConfig: () -> Unit,
    onOpenThemeManager: () -> Unit,
    onOpenDayBackground: () -> Unit,
    onOpenNightBackground: () -> Unit
) {
    val selectedMode = THEME_MODES.indexOf(state.themeMode).coerceAtLeast(0)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        NgFloatingTabBar(
            items = listOf(
                NgFloatingTabSpec(
                    text = stringResource(R.string.theme_mode_follow_short),
                    iconVector = Icons.Rounded.BrightnessAuto
                ),
                NgFloatingTabSpec(
                    text = stringResource(R.string.theme_mode_day_short),
                    iconVector = Icons.Rounded.LightMode
                ),
                NgFloatingTabSpec(
                    text = stringResource(R.string.theme_mode_night_short),
                    iconVector = Icons.Rounded.DarkMode
                ),
                NgFloatingTabSpec(
                    text = stringResource(R.string.theme_mode_eink_short),
                    iconVector = Icons.Rounded.MonochromePhotos
                )
            ),
            selectedIndex = selectedMode,
            onTabSelected = { index -> onThemeModeSelected(THEME_MODES[index]) },
            modifier = Modifier.fillMaxWidth()
        )

        NgSettingsGroup {
            if (state.showLauncherIcon) {
                NgSettingsItem(
                    title = stringResource(R.string.change_icon),
                    summary = stringResource(R.string.change_icon_summary),
                    trailing = NgSettingsTrailing.CUSTOM,
                    onClick = onLauncherIconClick,
                    customTrailing = {
                        LauncherIconPreview(
                            iconRes = state.launcherIconRes,
                            contentDescription = stringResource(R.string.change_icon)
                        )
                    }
                )
            }
            NgSettingsItem(
                title = stringResource(R.string.floating_bottom_bar),
                summary = stringResource(R.string.floating_bottom_bar_summary),
                trailing = NgSettingsTrailing.SWITCH,
                checked = state.floatingBottomBar,
                onCheckedChange = onFloatingBottomBarChanged,
                onClick = { onFloatingBottomBarChanged(!state.floatingBottomBar) }
            )
            NgSettingsItem(
                title = stringResource(R.string.transparent_app_bars),
                summary = stringResource(R.string.transparent_app_bars_summary),
                trailing = NgSettingsTrailing.SWITCH,
                checked = state.transparentAppBars,
                onCheckedChange = onTransparentAppBarsChanged,
                onClick = { onTransparentAppBarsChanged(!state.transparentAppBars) }
            )
            NgSettingsItem(
                title = stringResource(R.string.ng_custom_colors),
                summary = stringResource(R.string.ng_custom_colors_summary),
                onClick = onOpenCustomColors
            )
            NgSettingsItem(
                title = stringResource(R.string.font_scale),
                summary = state.fontScaleSummary,
                onClick = onOpenFontScale
            )
            NgSettingsItem(
                title = stringResource(R.string.cover_config),
                summary = stringResource(R.string.cover_config_summary),
                onClick = onOpenCoverConfig
            )
            NgSettingsItem(
                title = stringResource(R.string.theme_list),
                summary = stringResource(R.string.theme_list_summary),
                onClick = onOpenThemeManager
            )
        }

        Spacer(Modifier.height(4.dp))
        NgSettingsSectionLabel(stringResource(R.string.day))
        NgSettingsGroup {
            NgSettingsItem(
                title = stringResource(R.string.background_image),
                summary = state.dayBackgroundSummary,
                onClick = onOpenDayBackground
            )
        }

        NgSettingsSectionLabel(stringResource(R.string.night))
        NgSettingsGroup {
            NgSettingsItem(
                title = stringResource(R.string.background_image),
                summary = state.nightBackgroundSummary,
                onClick = onOpenNightBackground
            )
        }
    }
}

@Composable
private fun LauncherIconPreview(
    @DrawableRes iconRes: Int,
    contentDescription: String
) {
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        },
        update = { imageView ->
            imageView.setImageResource(iconRes)
            imageView.contentDescription = contentDescription
        },
        modifier = Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(12.dp))
    )
}

private val THEME_MODES = listOf("0", "1", "2", "3")
