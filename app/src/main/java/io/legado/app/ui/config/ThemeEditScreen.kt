package io.legado.app.ui.config

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import io.legado.app.help.config.BookshelfFloatingDockConfig
import io.legado.app.help.config.BookshelfFloatingDockSearchPosition
import io.legado.app.help.config.BookshelfTopBarStyle
import io.legado.app.help.config.FloatingBottomBarConfig
import io.legado.app.help.config.NgManagedTheme
import io.legado.app.help.config.NgThemeBarProfile
import io.legado.app.help.config.NgThemeBackground
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgDockSlider
import io.legado.app.ui.design.components.compose.NgExpandableSettingsItem
import io.legado.app.ui.design.components.compose.NgFloatingTabBar
import io.legado.app.ui.design.components.compose.NgFloatingTabSpec
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsItem
import io.legado.app.ui.design.components.compose.NgSettingsSectionLabel
import io.legado.app.ui.design.theme.NgTheme
import java.io.File
import kotlin.math.roundToInt

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
            item(key = "theme-bars") {
                NgSettingsSectionLabel(stringResource(R.string.ng_theme_bars))
                NgSettingsGroup {
                    ThemeBarProfileEditor(
                        profile = theme.barProfile ?: NgThemeBarProfile(),
                        onProfileChanged = {
                            onThemeChanged(theme.copy(barProfile = it))
                        }
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
private fun ThemeBarProfileEditor(
    profile: NgThemeBarProfile,
    onProfileChanged: (NgThemeBarProfile) -> Unit
) {
    var bottomBarExpanded by rememberSaveable { mutableStateOf(false) }
    var topBarExpanded by rememberSaveable { mutableStateOf(false) }
    val useFloatingBottomBar = profile.useFloatingBottomBar ?: false
    val bottomDistancePx = profile.floatingBottomBarBottomDistancePx
        ?: NgThemeBarProfile.EDITOR_DEFAULT_BOTTOM_DISTANCE_PX
    val bottomTransparency = profile.floatingBottomBarTransparency
        ?: FloatingBottomBarConfig.DEFAULT_TRANSPARENCY_PERCENT
    val topBarStyle = BookshelfTopBarStyle.fromValue(
        profile.bookshelfTopBarStyle ?: BookshelfTopBarStyle.TRADITIONAL.value
    )
    val topDistancePx = profile.bookshelfFloatingDockTopDistancePx
        ?: NgThemeBarProfile.EDITOR_DEFAULT_TOP_DISTANCE_PX
    val topTransparency = profile.bookshelfFloatingDockTransparency
        ?: BookshelfFloatingDockConfig.DEFAULT_TRANSPARENCY_PERCENT
    val searchPosition = BookshelfFloatingDockSearchPosition.fromValue(
        profile.bookshelfFloatingDockSearchPosition
            ?: BookshelfFloatingDockSearchPosition.LEFT.value
    )

    NgExpandableSettingsItem(
        title = stringResource(R.string.main_bottom_bar_style),
        summary = stringResource(
            if (useFloatingBottomBar) {
                R.string.floating_bottom_bar
            } else {
                R.string.traditional_bottom_bar
            }
        ),
        expanded = bottomBarExpanded,
        onExpandedChange = { bottomBarExpanded = it }
    ) {
        NgFloatingTabBar(
            items = listOf(
                NgFloatingTabSpec(
                    text = stringResource(R.string.traditional_bottom_bar),
                    iconRes = R.drawable.ic_bookshelf_top_bar_traditional
                ),
                NgFloatingTabSpec(
                    text = stringResource(R.string.floating_bottom_bar),
                    iconRes = R.drawable.ic_bookshelf_top_bar_floating
                )
            ),
            selectedIndex = if (useFloatingBottomBar) 1 else 0,
            onTabSelected = { index ->
                onProfileChanged(
                    profile.copy(
                        useFloatingBottomBar = index == 1,
                        floatingBottomBarBottomDistancePx =
                            profile.floatingBottomBarBottomDistancePx
                                ?: NgThemeBarProfile.EDITOR_DEFAULT_BOTTOM_DISTANCE_PX,
                        floatingBottomBarTransparency =
                            profile.floatingBottomBarTransparency
                                ?: FloatingBottomBarConfig.DEFAULT_TRANSPARENCY_PERCENT,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        AnimatedVisibility(visible = useFloatingBottomBar) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NgDockSlider(
                    title = stringResource(R.string.floating_bottom_bar_bottom_distance),
                    valueText = stringResource(
                        R.string.bookshelf_floating_dock_top_distance_value,
                        bottomDistancePx
                    ),
                    minimumText = stringResource(
                        R.string.bookshelf_floating_dock_top_distance_value,
                        FloatingBottomBarConfig.MIN_BOTTOM_DISTANCE_PX
                    ),
                    maximumText = stringResource(
                        R.string.bookshelf_floating_dock_top_distance_value,
                        FloatingBottomBarConfig.MAX_BOTTOM_DISTANCE_PX
                    ),
                    value = bottomDistancePx.toFloat(),
                    valueRange = FloatingBottomBarConfig.MIN_BOTTOM_DISTANCE_PX.toFloat()..
                        FloatingBottomBarConfig.MAX_BOTTOM_DISTANCE_PX.toFloat(),
                    steps = FloatingBottomBarConfig.BOTTOM_DISTANCE_SLIDER_STEPS,
                    onValueChange = { value ->
                        onProfileChanged(
                            profile.copy(
                                floatingBottomBarBottomDistancePx = value.roundToInt()
                            )
                        )
                    },
                    onValueChangeFinished = {}
                )
                NgDockSlider(
                    title = stringResource(R.string.floating_bottom_bar_transparency),
                    valueText = stringResource(
                        R.string.bookshelf_floating_dock_transparency_value,
                        bottomTransparency
                    ),
                    minimumText = stringResource(
                        R.string.bookshelf_floating_dock_transparency_value,
                        FloatingBottomBarConfig.MIN_TRANSPARENCY_PERCENT
                    ),
                    maximumText = stringResource(
                        R.string.bookshelf_floating_dock_transparency_value,
                        FloatingBottomBarConfig.MAX_TRANSPARENCY_PERCENT
                    ),
                    value = bottomTransparency.toFloat(),
                    valueRange = FloatingBottomBarConfig.MIN_TRANSPARENCY_PERCENT.toFloat()..
                        FloatingBottomBarConfig.MAX_TRANSPARENCY_PERCENT.toFloat(),
                    onValueChange = { value ->
                        onProfileChanged(
                            profile.copy(floatingBottomBarTransparency = value.roundToInt())
                        )
                    },
                    onValueChangeFinished = {}
                )
            }
        }
    }

    NgExpandableSettingsItem(
        title = stringResource(R.string.bookshelf_top_bar_style),
        summary = stringResource(
            when (topBarStyle) {
                BookshelfTopBarStyle.TRADITIONAL -> R.string.bookshelf_top_bar_traditional
                BookshelfTopBarStyle.FLOATING_DOCK -> R.string.bookshelf_top_bar_floating_dock
            }
        ),
        expanded = topBarExpanded,
        onExpandedChange = { topBarExpanded = it }
    ) {
        NgFloatingTabBar(
            items = listOf(
                NgFloatingTabSpec(
                    text = stringResource(R.string.bookshelf_top_bar_traditional),
                    iconRes = R.drawable.ic_bookshelf_top_bar_traditional
                ),
                NgFloatingTabSpec(
                    text = stringResource(R.string.bookshelf_top_bar_floating_dock),
                    iconRes = R.drawable.ic_bookshelf_top_bar_floating
                )
            ),
            selectedIndex = BookshelfTopBarStyle.entries.indexOf(topBarStyle),
            onTabSelected = { index ->
                onProfileChanged(
                    profile.copy(
                        bookshelfTopBarStyle = BookshelfTopBarStyle.entries[index].value,
                        bookshelfFloatingDockTopDistancePx =
                            profile.bookshelfFloatingDockTopDistancePx
                                ?: NgThemeBarProfile.EDITOR_DEFAULT_TOP_DISTANCE_PX,
                        bookshelfFloatingDockTransparency =
                            profile.bookshelfFloatingDockTransparency
                                ?: BookshelfFloatingDockConfig.DEFAULT_TRANSPARENCY_PERCENT,
                        bookshelfFloatingDockSearchPosition =
                            profile.bookshelfFloatingDockSearchPosition
                                ?: BookshelfFloatingDockSearchPosition.LEFT.value,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        AnimatedVisibility(visible = topBarStyle == BookshelfTopBarStyle.FLOATING_DOCK) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NgSettingsItem(
                    title = stringResource(R.string.bookshelf_floating_dock_search_position),
                    trailing = NgSettingsTrailing.CUSTOM,
                    customTrailing = {
                        NgFloatingTabBar(
                            items = listOf(
                                NgFloatingTabSpec(text = stringResource(R.string.left)),
                                NgFloatingTabSpec(text = stringResource(R.string.right))
                            ),
                            selectedIndex =
                                BookshelfFloatingDockSearchPosition.entries.indexOf(
                                    searchPosition
                                ),
                            onTabSelected = { index ->
                                onProfileChanged(
                                    profile.copy(
                                        bookshelfFloatingDockSearchPosition =
                                            BookshelfFloatingDockSearchPosition.entries[index].value
                                    )
                                )
                            },
                            modifier = Modifier.width(132.dp)
                        )
                    }
                )
                NgDockSlider(
                    title = stringResource(R.string.bookshelf_floating_dock_top_distance),
                    valueText = stringResource(
                        R.string.bookshelf_floating_dock_top_distance_value,
                        topDistancePx
                    ),
                    minimumText = stringResource(
                        R.string.bookshelf_floating_dock_top_distance_value,
                        BookshelfFloatingDockConfig.MIN_TOP_DISTANCE_PX
                    ),
                    maximumText = stringResource(
                        R.string.bookshelf_floating_dock_top_distance_value,
                        BookshelfFloatingDockConfig.MAX_TOP_DISTANCE_PX
                    ),
                    value = topDistancePx.toFloat(),
                    valueRange = BookshelfFloatingDockConfig.MIN_TOP_DISTANCE_PX.toFloat()..
                        BookshelfFloatingDockConfig.MAX_TOP_DISTANCE_PX.toFloat(),
                    steps = BookshelfFloatingDockConfig.TOP_DISTANCE_SLIDER_STEPS,
                    onValueChange = { value ->
                        onProfileChanged(
                            profile.copy(
                                bookshelfFloatingDockTopDistancePx = value.roundToInt()
                            )
                        )
                    },
                    onValueChangeFinished = {}
                )
                NgDockSlider(
                    title = stringResource(R.string.bookshelf_floating_dock_transparency),
                    valueText = stringResource(
                        R.string.bookshelf_floating_dock_transparency_value,
                        topTransparency
                    ),
                    minimumText = stringResource(
                        R.string.bookshelf_floating_dock_transparency_value,
                        BookshelfFloatingDockConfig.MIN_TRANSPARENCY_PERCENT
                    ),
                    maximumText = stringResource(
                        R.string.bookshelf_floating_dock_transparency_value,
                        BookshelfFloatingDockConfig.MAX_TRANSPARENCY_PERCENT
                    ),
                    value = topTransparency.toFloat(),
                    valueRange = BookshelfFloatingDockConfig.MIN_TRANSPARENCY_PERCENT.toFloat()..
                        BookshelfFloatingDockConfig.MAX_TRANSPARENCY_PERCENT.toFloat(),
                    onValueChange = { value ->
                        onProfileChanged(
                            profile.copy(
                                bookshelfFloatingDockTransparency = value.roundToInt()
                            )
                        )
                    },
                    onValueChangeFinished = {}
                )
            }
        }
    }
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
