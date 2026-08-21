package io.legado.app.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsItem
import io.legado.app.ui.design.components.compose.NgSettingsSectionLabel
import io.legado.app.ui.design.theme.NgBuiltInColorPresets
import io.legado.app.ui.design.theme.NgColorGenerationMode
import io.legado.app.ui.design.theme.NgColorSpec
import io.legado.app.ui.design.theme.NgColorSystem
import io.legado.app.ui.design.theme.NgContrastLevel
import io.legado.app.ui.design.theme.NgManualColorSet
import io.legado.app.ui.design.theme.NgPaletteStyle
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.NgTopBarTextMode
import io.legado.app.ui.design.theme.formatNgColor

@Composable
internal fun ThemeColorConfigScreen(
    colors: NgColorSystem,
    onColorsChanged: (NgColorSystem) -> Unit,
    headerContent: LazyListScope.() -> Unit = {}
) {
    val context = LocalContext.current
    var activePicker by remember { mutableStateOf<NgColorPickerTarget?>(null) }
    val generated = colors.mode == NgColorGenerationMode.PALETTE
    val selectedPreset = NgBuiltInColorPresets.matching(colors)
    val themeColors = NgTheme.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        headerContent()
        item {
            NgSettingsGroup {
                NgSettingsItem(
                    title = stringResource(R.string.ng_color_presets),
                    summary = selectedPreset?.let { stringResource(it.nameRes) }
                        ?: stringResource(R.string.ng_color_preset_customized),
                    onClick = {
                        showNgColorPresetSheet(
                            context = context,
                            current = colors,
                            accentColor = themeColors.primary,
                            onAccentColor = themeColors.onPrimary,
                            onSurfaceColor = themeColors.onSurface,
                            onSelected = onColorsChanged
                        )
                    }
                )
                NgSettingsItem(
                    title = stringResource(R.string.ng_use_palette_colors),
                    summary = stringResource(R.string.ng_use_palette_colors_summary),
                    trailing = NgSettingsTrailing.SWITCH,
                    checked = generated,
                    onCheckedChange = { enabled ->
                        onColorsChanged(
                            colors.copy(
                                mode = if (enabled) {
                                    NgColorGenerationMode.PALETTE
                                } else {
                                    NgColorGenerationMode.MANUAL
                                }
                            )
                        )
                    }
                )
            }
        }

        if (generated) {
            item {
                NgSettingsSectionLabel(stringResource(R.string.ng_palette_generation))
                NgSettingsGroup {
                    NgColorSettingItem(
                        title = stringResource(R.string.ng_seed_color),
                        summary = stringResource(R.string.day),
                        color = colors.lightSeed,
                        onClick = { activePicker = NgColorPickerTarget.DaySeed }
                    )
                    NgColorSettingItem(
                        title = stringResource(R.string.ng_seed_color),
                        summary = stringResource(R.string.night),
                        color = colors.darkSeed,
                        onClick = { activePicker = NgColorPickerTarget.NightSeed }
                    )
                    NgDropdownSettingItem(
                        title = stringResource(R.string.ng_palette_style),
                        selectedValue = colors.paletteStyle.name,
                        options = paletteOptions(),
                        onValueSelected = {
                            onColorsChanged(colors.copy(paletteStyle = NgPaletteStyle.valueOf(it)))
                        }
                    )
                    NgDropdownSettingItem(
                        title = stringResource(R.string.ng_preferred_contrast),
                        selectedValue = colors.contrast.name,
                        options = contrastOptions(),
                        onValueSelected = {
                            onColorsChanged(colors.copy(contrast = NgContrastLevel.valueOf(it)))
                        }
                    )
                    NgDropdownSettingItem(
                        title = stringResource(R.string.ng_color_spec),
                        selectedValue = colors.colorSpec.name,
                        options = colorSpecOptions(),
                        onValueSelected = {
                            onColorsChanged(colors.copy(colorSpec = NgColorSpec.valueOf(it)))
                        }
                    )
                }
            }
        } else {
            item {
                ManualColorGroup(
                    title = stringResource(R.string.day),
                    value = colors.manualLight,
                    dark = false,
                    onSelect = { activePicker = NgColorPickerTarget.Manual(false, it) }
                )
            }
            item {
                ManualColorGroup(
                    title = stringResource(R.string.night),
                    value = colors.manualDark,
                    dark = true,
                    onSelect = { activePicker = NgColorPickerTarget.Manual(true, it) }
                )
            }
        }
    }

    val picker = activePicker
    NgColorPickerSheet(
        show = picker != null,
        initialColor = picker?.color(colors) ?: 0,
        initialTopBarTextMode = picker?.topBarTextMode(colors),
        onDismissRequest = { activePicker = null },
        onSelectionConfirmed = { selected, selectedTopBarTextMode ->
            when (picker) {
                NgColorPickerTarget.DaySeed -> onColorsChanged(
                    colors.copy(
                        mode = NgColorGenerationMode.PALETTE,
                        lightSeed = selected
                    )
                )

                NgColorPickerTarget.NightSeed -> onColorsChanged(
                    colors.copy(
                        mode = NgColorGenerationMode.PALETTE,
                        darkSeed = selected
                    )
                )

                is NgColorPickerTarget.Manual -> {
                    val source = colors.manualColors(picker.dark)
                    val updated = picker.slot.update(source, selected)
                    val updatedColors = colors.copy(
                        mode = NgColorGenerationMode.MANUAL,
                        manualLight = if (picker.dark) colors.manualLight else updated,
                        manualDark = if (picker.dark) updated else colors.manualDark
                    ).let { value ->
                        if (
                            picker.slot == NgManualColorSlot.Secondary &&
                            selectedTopBarTextMode != null
                        ) {
                            value.copy(
                                lightTopBarTextMode = if (picker.dark) {
                                    value.lightTopBarTextMode
                                } else {
                                    selectedTopBarTextMode
                                },
                                darkTopBarTextMode = if (picker.dark) {
                                    selectedTopBarTextMode
                                } else {
                                    value.darkTopBarTextMode
                                }
                            )
                        } else {
                            value
                        }
                    }
                    onColorsChanged(updatedColors)
                }

                null -> Unit
            }
            activePicker = null
        }
    )
}

@Composable
private fun ManualColorGroup(
    title: String,
    value: NgManualColorSet,
    dark: Boolean,
    onSelect: (NgManualColorSlot) -> Unit
) {
    NgSettingsSectionLabel(title)
    NgSettingsGroup {
        NgColorSettingItem(stringResource(R.string.ng_primary_color), null, value.primary) {
            onSelect(NgManualColorSlot.Primary)
        }
        NgColorSettingItem(stringResource(R.string.ng_secondary_color), null, value.secondary) {
            onSelect(NgManualColorSlot.Secondary)
        }
        NgColorSettingItem(
            stringResource(R.string.ng_primary_text_color),
            null,
            value.primaryText
        ) {
            onSelect(NgManualColorSlot.PrimaryText)
        }
        NgColorSettingItem(
            stringResource(R.string.ng_secondary_text_color),
            null,
            value.secondaryText
        ) {
            onSelect(NgManualColorSlot.SecondaryText)
        }
        NgColorSettingItem(stringResource(R.string.background_color), null, value.background) {
            onSelect(NgManualColorSlot.Background)
        }
        NgColorSettingItem(
            stringResource(R.string.ng_label_container_color),
            null,
            value.labelContainer
        ) {
            onSelect(NgManualColorSlot.LabelContainer)
        }
    }
}

@Composable
private fun NgColorSettingItem(
    title: String,
    summary: String?,
    color: Int,
    onClick: () -> Unit
) {
    NgSettingsItem(
        title = title,
        summary = listOfNotNull(summary, formatNgColor(color)).joinToString(" · "),
        trailing = NgSettingsTrailing.CUSTOM,
        onClick = onClick,
        customTrailing = {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(Color(color), CircleShape)
                    .border(1.dp, Color(NgTheme.colors.outlineVariant), CircleShape)
            )
        }
    )
}

@Composable
private fun NgDropdownSettingItem(
    title: String,
    selectedValue: String,
    options: List<NgSettingOption>,
    onValueSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label ?: selectedValue
    Box {
        NgSettingsItem(
            title = title,
            value = selectedLabel,
            trailing = NgSettingsTrailing.VALUE,
            onClick = { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color(NgTheme.colors.dialogContainer)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, color = Color(NgTheme.colors.onSurface)) },
                    onClick = {
                        expanded = false
                        onValueSelected(option.value)
                    }
                )
            }
        }
    }
}

private data class NgSettingOption(val label: String, val value: String)

@Composable
private fun paletteOptions() = listOf(
    NgSettingOption(stringResource(R.string.ng_palette_tonal_spot), NgPaletteStyle.TONAL_SPOT.name),
    NgSettingOption(stringResource(R.string.ng_palette_neutral), NgPaletteStyle.NEUTRAL.name),
    NgSettingOption(stringResource(R.string.ng_palette_vibrant), NgPaletteStyle.VIBRANT.name),
    NgSettingOption(stringResource(R.string.ng_palette_expressive), NgPaletteStyle.EXPRESSIVE.name),
    NgSettingOption(stringResource(R.string.ng_palette_rainbow), NgPaletteStyle.RAINBOW.name),
    NgSettingOption(stringResource(R.string.ng_palette_fruit_salad), NgPaletteStyle.FRUIT_SALAD.name),
    NgSettingOption(stringResource(R.string.ng_palette_monochrome), NgPaletteStyle.MONOCHROME.name),
    NgSettingOption(stringResource(R.string.ng_palette_fidelity), NgPaletteStyle.FIDELITY.name),
    NgSettingOption(stringResource(R.string.ng_palette_content), NgPaletteStyle.CONTENT.name)
)

@Composable
private fun contrastOptions() = listOf(
    NgSettingOption(stringResource(R.string.ng_contrast_default), NgContrastLevel.DEFAULT.name),
    NgSettingOption(stringResource(R.string.ng_contrast_medium), NgContrastLevel.MEDIUM.name),
    NgSettingOption(stringResource(R.string.ng_contrast_high), NgContrastLevel.HIGH.name)
)

@Composable
private fun colorSpecOptions() = listOf(
    NgSettingOption(
        stringResource(R.string.ng_material_3_2021),
        NgColorSpec.MATERIAL_3_2021.name
    ),
    NgSettingOption(
        stringResource(R.string.ng_material_3_expressive_2025),
        NgColorSpec.MATERIAL_3_EXPRESSIVE_2025.name
    )
)

private sealed interface NgColorPickerTarget {
    data object DaySeed : NgColorPickerTarget
    data object NightSeed : NgColorPickerTarget
    data class Manual(val dark: Boolean, val slot: NgManualColorSlot) : NgColorPickerTarget
}

private enum class NgManualColorSlot {
    Primary,
    Secondary,
    PrimaryText,
    SecondaryText,
    Background,
    LabelContainer
}

private fun NgColorPickerTarget.color(colors: NgColorSystem): Int = when (this) {
    NgColorPickerTarget.DaySeed -> colors.lightSeed
    NgColorPickerTarget.NightSeed -> colors.darkSeed
    is NgColorPickerTarget.Manual -> slot.read(colors.manualColors(dark))
}

private fun NgColorPickerTarget.topBarTextMode(
    colors: NgColorSystem
): NgTopBarTextMode? = when (this) {
    is NgColorPickerTarget.Manual -> {
        if (slot == NgManualColorSlot.Secondary) colors.topBarTextMode(dark) else null
    }

    else -> null
}

private fun NgManualColorSlot.read(colors: NgManualColorSet): Int = when (this) {
    NgManualColorSlot.Primary -> colors.primary
    NgManualColorSlot.Secondary -> colors.secondary
    NgManualColorSlot.PrimaryText -> colors.primaryText
    NgManualColorSlot.SecondaryText -> colors.secondaryText
    NgManualColorSlot.Background -> colors.background
    NgManualColorSlot.LabelContainer -> colors.labelContainer
}

private fun NgManualColorSlot.update(colors: NgManualColorSet, selected: Int) = when (this) {
    NgManualColorSlot.Primary -> colors.copy(primary = selected)
    NgManualColorSlot.Secondary -> colors.copy(secondary = selected)
    NgManualColorSlot.PrimaryText -> colors.copy(primaryText = selected)
    NgManualColorSlot.SecondaryText -> colors.copy(secondaryText = selected)
    NgManualColorSlot.Background -> colors.copy(background = selected)
    NgManualColorSlot.LabelContainer -> colors.copy(labelContainer = selected)
}
