package io.legado.app.ui.book.read.config

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AdvancedTitleConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadTipConfig
import io.legado.app.help.config.AdvancedTitlePackageManager
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.config.NgInlineColorPicker
import io.legado.app.ui.design.components.compose.NgSwitchControl
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.utils.hexString
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean

class TipConfigDialog : BaseComposeDialogFragment() {

    companion object {
        const val TIP_COLOR = 7897
        const val TIP_DIVIDER_COLOR = 7898

        private const val SECTION_TITLE = 0
        private const val SECTION_HEADER = 1
        private const val SECTION_FOOTER = 2
        private const val SECTION_STYLE = 3

        private const val POSITION_LEFT = 0
        private const val POSITION_MIDDLE = 1
        private const val POSITION_RIGHT = 2
    }

    private enum class ColorPickerTarget { TIP, DIVIDER, ADVANCED_TITLE }

    private lateinit var composeView: ComposeView
    private var externalRevision by mutableIntStateOf(0)
    private var colorEditingTemplate: AdvancedTitlePackageManager.Entry? by mutableStateOf(null)

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(marginDp = 20, dimAmount = 0.4f)
        composeView.post { repositionAboveDrawer() }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        if (ReadBookConfig.titleMode !in 0..AdvancedTitleConfig.TITLE_MODE_ADVANCED) {
            ReadBookConfig.titleMode = 0
        }
        composeView = view as ComposeView
        composeView.setBackgroundColor(AndroidColor.TRANSPARENT)
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            NgAppTheme(
                snapshot = ReadDrawerStyle.themeSnapshot(requireContext()),
                updateSystemBars = false,
            ) {
                TipConfigContent()
            }
        }
        observeEvent<String>(EventBus.TIP_COLOR) { externalRevision++ }
    }

    @Composable
    private fun TipConfigContent() {
        var section by remember { mutableIntStateOf(SECTION_TITLE) }
        var headerPosition by remember { mutableIntStateOf(POSITION_LEFT) }
        var footerPosition by remember { mutableIntStateOf(POSITION_LEFT) }
        var headerEnabled by remember { mutableStateOf(ReadTipConfig.headerMode == 1) }
        var footerEnabled by remember { mutableStateOf(ReadTipConfig.footerMode == 0) }
        var titleMode by remember { mutableIntStateOf(ReadBookConfig.titleMode) }
        var titleTop by remember { mutableIntStateOf(ReadBookConfig.titleTopSpacing) }
        var titleBottom by remember { mutableIntStateOf(ReadBookConfig.titleBottomSpacing) }
        var templateEntries by remember { mutableStateOf<List<AdvancedTitlePackageManager.Entry>>(emptyList()) }
        var appliedTemplateId by remember {
            mutableStateOf(AdvancedTitlePackageManager.activeId())
        }
        var editingTemplate by remember { mutableStateOf<AdvancedTitlePackageManager.Entry?>(null) }
        var revision by remember { mutableIntStateOf(0) }
        LaunchedEffect(titleMode, revision) {
            if (titleMode == AdvancedTitleConfig.TITLE_MODE_ADVANCED) {
                templateEntries = AdvancedTitlePackageManager.loadEntries()
                appliedTemplateId = AdvancedTitlePackageManager.activeId()
            }
        }
        var activePicker by remember { mutableStateOf<ColorPickerTarget?>(null) }
        externalRevision
        revision

        LaunchedEffect(
            section,
            activePicker,
            headerEnabled,
            footerEnabled,
            headerPosition,
            footerPosition,
            revision,
        ) {
            composeView.post { repositionAboveDrawer() }
        }

        ReadConfigDialogSurface(
            contentPadding = PaddingValues(
                start = 18.dp,
                top = 16.dp,
                end = 18.dp,
                bottom = 12.dp,
            ),
        ) {
            ReadConfigDialogTitle(getString(R.string.reading_information))
            Spacer(Modifier.height(12.dp))
            ReadConfigDock(
                labels = listOf(
                    getString(R.string.title),
                    getString(R.string.header),
                    getString(R.string.footer),
                    getString(R.string.style),
                ),
                selectedIndex = section,
                onSelected = {
                    if (it != SECTION_STYLE) activePicker = null
                    section = it
                },
                height = 40.dp,
                accessibilityLabel = getString(R.string.reading_information),
            )
            Spacer(Modifier.height(10.dp))
            if (activePicker != null) {
                val target = requireNotNull(activePicker)
                NgInlineColorPicker(
                    title = getString(
                        when (target) {
                            ColorPickerTarget.TIP -> R.string.tip_text_color
                            ColorPickerTarget.DIVIDER -> R.string.tip_divider_color
                            ColorPickerTarget.ADVANCED_TITLE -> R.string.advanced_title_text_color
                        }
                    ),
                    initialColor = initialColorFor(target),
                    onBack = { activePicker = null },
                    onColorChanged = { selected ->
                        applySelectedColor(target, selected or AndroidColor.BLACK)?.let {
                            editingTemplate = it
                        }
                        revision++
                    },
                    onReset = {
                        resetSelectedColor(target)?.let { editingTemplate = it }
                        revision++
                        activePicker = null
                    },
                )
                return@ReadConfigDialogSurface
            }

            when (section) {
                SECTION_TITLE -> {
                    ReadConfigDock(
                        labels = listOf(
                            getString(R.string.title_left),
                            getString(R.string.title_center),
                            getString(R.string.title_hide),
                            getString(R.string.advanced_title_mode_label),
                        ),
                        selectedIndex = titleMode,
                        onSelected = {
                            titleMode = it
                            ReadBookConfig.titleMode = it
                            postEvent(EventBus.UP_CONFIG, arrayListOf(5))
                        },
                        accessibilityLabel = getString(R.string.title),
                    )
                    if (titleMode == AdvancedTitleConfig.TITLE_MODE_ADVANCED) {
                        AdvancedTitleTemplates(
                            entries = templateEntries,
                            appliedId = appliedTemplateId,
                            editing = editingTemplate,
                            onEdit = { editingTemplate = it },
                            onApply = { entry ->
                                AdvancedTitlePackageManager.apply(entry)
                                appliedTemplateId = entry.id
                                revision++
                                postEvent(EventBus.UP_CONFIG, arrayListOf(5))
                            },
                            onColorEdit = { entry ->
                                colorEditingTemplate = entry
                                activePicker = ColorPickerTarget.ADVANCED_TITLE
                            },
                            onColorEnabledChanged = { entry, enabled ->
                                colorEditingTemplate = entry
                                if (enabled) {
                                    applySelectedColor(
                                        ColorPickerTarget.ADVANCED_TITLE,
                                        entry.config.normalizedTextColorOrNull()
                                            ?: AdvancedTitleConfig.effectiveTextColor()
                                            ?: ReadBookConfig.resolvedTitleColor,
                                    )
                                } else {
                                    resetSelectedColor(ColorPickerTarget.ADVANCED_TITLE)
                                }
                                revision++
                            },
                            onSave = { entry, weight, size, top, bottom ->
                                val updated = AdvancedTitlePackageManager.addOrUpdate(
                                    name = entry.name,
                                    json = AdvancedTitlePackageManager.readTemplate(entry),
                                    oldEntry = entry,
                                    fontWeight = weight,
                                    fontSizeScale = size,
                                    titleTopSpacing = top,
                                    titleBottomSpacing = bottom,
                                )
                                if (entry.id == AdvancedTitlePackageManager.activeId()) {
                                    AdvancedTitlePackageManager.apply(updated)
                                    postEvent(EventBus.UP_CONFIG, arrayListOf(5))
                                }
                                editingTemplate = updated
                                revision++
                            },
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        ReadConfigSliderRow(
                            title = getString(R.string.title_font_size),
                            value = ReadBookConfig.titleSize,
                            valueRange = 0..20,
                            onValueChange = {
                                ReadBookConfig.titleSize = it
                                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
                            },
                        )
                        ReadConfigSliderRow(
                            title = getString(R.string.title_margin_top),
                            value = titleTop,
                            valueRange = 0..100,
                            onValueChange = {
                                titleTop = it
                                ReadBookConfig.titleTopSpacing = it
                                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
                            },
                        )
                        ReadConfigSliderRow(
                            title = getString(R.string.title_margin_bottom),
                            value = titleBottom,
                            valueRange = 0..100,
                            onValueChange = {
                                titleBottom = it
                                ReadBookConfig.titleBottomSpacing = it
                                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
                            },
                        )
                    }
                }

                SECTION_HEADER -> TipAreaContent(
                    enabledTitle = getString(R.string.show_header),
                    enabled = headerEnabled,
                    onEnabledChanged = { enabled ->
                        headerEnabled = enabled
                        ReadTipConfig.headerMode = if (enabled) 1 else 2
                        if (enabled && !ReadBookConfig.hideStatusBar) {
                            ReadBookConfig.hideStatusBar = true
                            putPrefBoolean(PreferKey.hideStatusBar, true)
                            postEvent(EventBus.UP_CONFIG, arrayListOf(0, 2))
                        } else {
                            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                        }
                    },
                    position = headerPosition,
                    onPositionChanged = { headerPosition = it },
                    section = SECTION_HEADER,
                    revision = revision,
                    onContentChanged = { revision++ },
                )

                SECTION_FOOTER -> TipAreaContent(
                    enabledTitle = getString(R.string.show_footer),
                    enabled = footerEnabled,
                    onEnabledChanged = { enabled ->
                        footerEnabled = enabled
                        ReadTipConfig.footerMode = if (enabled) 0 else 1
                        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                    },
                    position = footerPosition,
                    onPositionChanged = { footerPosition = it },
                    section = SECTION_FOOTER,
                    revision = revision,
                    onContentChanged = { revision++ },
                )

                SECTION_STYLE -> StyleContent(
                    revision = revision + externalRevision,
                    onOpenPicker = { activePicker = it },
                    onChanged = { revision++ },
                )
            }
        }
    }

    @Composable
    private fun AdvancedTitleTemplates(
        entries: List<AdvancedTitlePackageManager.Entry>,
        appliedId: String,
        editing: AdvancedTitlePackageManager.Entry?,
        onEdit: (AdvancedTitlePackageManager.Entry) -> Unit,
        onApply: (AdvancedTitlePackageManager.Entry) -> Unit,
        onColorEdit: (AdvancedTitlePackageManager.Entry) -> Unit,
        onColorEnabledChanged: (AdvancedTitlePackageManager.Entry, Boolean) -> Unit,
        onSave: (AdvancedTitlePackageManager.Entry, Int, Int, Int, Int) -> Unit,
    ) {
        entries.forEach { entry ->
            val active = entry.id == appliedId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clickable { onApply(entry) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.name,
                    modifier = Modifier.weight(1f),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 16.sp,
                )
                if (active) {
                    Icon(
                        painter = painterResource(R.drawable.ng_ic_popup_selected),
                        contentDescription = getString(R.string.advanced_title_applied),
                        tint = Color(NgTheme.colors.primary),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onEdit(entry) },
                    )
                } else {
                    Spacer(Modifier.size(24.dp))
                }
            }
            if (editing?.id == entry.id) {
                val config = editing.config
                var weight by remember(entry.id) {
                    mutableIntStateOf(config.normalizedFontWeightOrNull() ?: 400)
                }
                var size by remember(entry.id) {
                    mutableIntStateOf(config.normalizedFontSizeScaleOrNull() ?: 100)
                }
                var top by remember(entry.id) {
                    mutableIntStateOf(config.normalizedTitleTopSpacingOrNull() ?: 0)
                }
                var bottom by remember(entry.id) {
                    mutableIntStateOf(config.normalizedTitleBottomSpacingOrNull() ?: 0)
                }
                ReadConfigSliderRow(
                    title = getString(R.string.advanced_title_font_weight),
                    value = weight,
                    valueRange = 100..900,
                    stepSize = 100,
                    onValueChange = { weight = it; onSave(entry, it, size, top, bottom) },
                )
                ReadConfigSliderRow(
                    title = getString(R.string.advanced_title_font_size),
                    value = size,
                    valueRange = 50..200,
                    stepSize = 10,
                    onValueChange = { size = it; onSave(entry, weight, it, top, bottom) },
                )
                ReadConfigSliderRow(
                    title = getString(R.string.title_margin_top),
                    value = top,
                    valueRange = 0..100,
                    onValueChange = { top = it; onSave(entry, weight, size, it, bottom) },
                )
                ReadConfigSliderRow(
                    title = getString(R.string.title_margin_bottom),
                    value = bottom,
                    valueRange = 0..100,
                    onValueChange = { bottom = it; onSave(entry, weight, size, top, it) },
                )
                AdvancedTitleOptionalColorRow(
                    title = getString(R.string.highlight_rule_use_text_color),
                    color = config.normalizedTextColorOrNull(),
                    onEnabledChanged = { onColorEnabledChanged(editing, it) },
                    onClick = { onColorEdit(editing) },
                )
            }
        }
    }

    @Composable
    private fun AdvancedTitleOptionalColorRow(
        title: String,
        color: Int?,
        onEnabledChanged: (Boolean) -> Unit,
        onClick: () -> Unit,
    ) {
        val contentColor = Color(NgTheme.colors.onSurface)
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, Modifier.weight(1f), contentColor, fontSize = 14.sp)
            if (color != null) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .border(0.7.dp, contentColor.copy(alpha = 0.18f), CircleShape)
                        .clickable(onClick = onClick),
                )
            }
            NgSwitchControl(
                checked = color != null,
                onCheckedChange = onEnabledChanged,
                modifier = Modifier.size(width = 52.dp, height = 36.dp),
            )
        }
    }

    @Composable
    private fun TipAreaContent(
        enabledTitle: String,
        enabled: Boolean,
        onEnabledChanged: (Boolean) -> Unit,
        position: Int,
        onPositionChanged: (Int) -> Unit,
        section: Int,
        revision: Int,
        onContentChanged: () -> Unit,
    ) {
        revision
        ReadConfigSwitchRow(
            title = enabledTitle,
            checked = enabled,
            onCheckedChange = onEnabledChanged,
        )
        if (!enabled) return
        Spacer(Modifier.height(6.dp))
        ReadConfigDock(
            labels = listOf(
                getString(R.string.left),
                getString(R.string.middle),
                getString(R.string.right),
            ),
            selectedIndex = position,
            onSelected = onPositionChanged,
            height = 38.dp,
            accessibilityLabel = getString(R.string.reading_information),
        )
        ReadTipConfig.tipValues.indices.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth()) {
                rowItems.forEach { item ->
                    val value = ReadTipConfig.tipValues[item]
                    val selected = value == currentTipValue(section, position)
                    TipOption(
                        name = ReadTipConfig.tipNames[item],
                        icon = tipIcon(value),
                        selected = selected,
                        usage = if (selected) null else usagePosition(value, section, position),
                        onClick = {
                            clearRepeat(value)
                            assignTipValue(section, position, value)
                            postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
                            onContentChanged()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    @Composable
    private fun TipOption(
        name: String,
        icon: Int,
        selected: Boolean,
        usage: String?,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        Row(
            modifier = modifier
                .heightIn(min = 40.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color(NgTheme.colors.onSurface),
                modifier = Modifier.size(18.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp),
            ) {
                Text(
                    text = name,
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (usage != null) {
                    Text(
                        text = usage,
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ng_ic_popup_selected),
                    contentDescription = null,
                    tint = Color(NgTheme.colors.primary),
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Spacer(Modifier.size(18.dp))
            }
        }
    }

    @Composable
    private fun StyleContent(
        revision: Int,
        onOpenPicker: (ColorPickerTarget) -> Unit,
        onChanged: () -> Unit,
    ) {
        revision
        ColorModeSection(
            title = getString(R.string.tip_text_color),
            labels = ReadTipConfig.tipColorNames,
            selectedIndex = if (ReadTipConfig.tipColor == 0) 0 else 1,
            valueText = ReadTipConfig.tipColor.takeUnless { it == 0 }?.let { "#${it.hexString}" },
            onSelected = { mode ->
                if (mode == 0) {
                    ReadTipConfig.tipColor = 0
                    postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                    onChanged()
                } else {
                    onOpenPicker(ColorPickerTarget.TIP)
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        ColorModeSection(
            title = getString(R.string.tip_divider_color),
            labels = ReadTipConfig.tipDividerColorNames,
            selectedIndex = dividerColorModeIndex(),
            valueText = ReadTipConfig.tipDividerColor.takeUnless { it in -1..0 }
                ?.let { "#${it.hexString}" },
            onSelected = { mode ->
                if (mode <= 1) {
                    ReadTipConfig.tipDividerColor = mode - 1
                    postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                    onChanged()
                } else {
                    onOpenPicker(ColorPickerTarget.DIVIDER)
                }
            },
        )
    }

    @Composable
    private fun ColorModeSection(
        title: String,
        labels: List<String>,
        selectedIndex: Int,
        valueText: String?,
        onSelected: (Int) -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = Color(NgTheme.colors.onSurface),
                fontSize = 14.sp,
            )
            if (valueText != null) {
                Text(
                    text = valueText,
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 13.sp,
                    textAlign = TextAlign.End,
                )
            }
        }
        ReadConfigDock(
            labels = labels,
            selectedIndex = selectedIndex,
            onSelected = onSelected,
            modifier = Modifier.padding(horizontal = 8.dp),
            accessibilityLabel = title,
        )
    }

    private fun dividerColorModeIndex(): Int = when (ReadTipConfig.tipDividerColor) {
        -1 -> 0
        0 -> 1
        else -> 2
    }

    private fun initialColorFor(target: ColorPickerTarget): Int = when (target) {
        ColorPickerTarget.TIP -> ReadTipConfig.tipColor.takeUnless { it == 0 }
            ?: ReadBookConfig.textColor

        ColorPickerTarget.DIVIDER -> when (val color = ReadTipConfig.tipDividerColor) {
            -1 -> ContextCompat.getColor(requireContext(), R.color.divider)
            0 -> ReadBookConfig.textColor
            else -> color
        }

        ColorPickerTarget.ADVANCED_TITLE -> colorEditingTemplate?.config?.normalizedTextColorOrNull()
            ?: AdvancedTitleConfig.effectiveTextColor()
            ?: ReadBookConfig.resolvedTitleColor
    }

    private fun applySelectedColor(
        target: ColorPickerTarget,
        color: Int,
    ): AdvancedTitlePackageManager.Entry? = when (target) {
            ColorPickerTarget.TIP -> {
                ReadTipConfig.tipColor = color
                null
            }
            ColorPickerTarget.DIVIDER -> {
                ReadTipConfig.tipDividerColor = color
                null
            }
            ColorPickerTarget.ADVANCED_TITLE -> colorEditingTemplate?.let { entry ->
                val config = entry.config
                AdvancedTitlePackageManager.addOrUpdate(
                    name = entry.name,
                    json = AdvancedTitlePackageManager.readTemplate(entry),
                    oldEntry = entry,
                    fontWeight = config.normalizedFontWeightOrNull(),
                    fontSizeScale = config.normalizedFontSizeScaleOrNull(),
                    titleTopSpacing = config.normalizedTitleTopSpacingOrNull(),
                    titleBottomSpacing = config.normalizedTitleBottomSpacingOrNull(),
                    textColor = color,
                ).also { updated ->
                    colorEditingTemplate = updated
                    if (updated.id == AdvancedTitlePackageManager.activeId()) {
                        AdvancedTitlePackageManager.apply(updated)
                    }
                }
            }
        }.also {
            postEvent(EventBus.TIP_COLOR, "")
            postEvent(EventBus.UP_CONFIG, arrayListOf(2, 5))
        }

    private fun resetSelectedColor(target: ColorPickerTarget): AdvancedTitlePackageManager.Entry? = when (target) {
            ColorPickerTarget.TIP -> {
                ReadTipConfig.tipColor = 0
                null
            }
            ColorPickerTarget.DIVIDER -> {
                ReadTipConfig.tipDividerColor = -1
                null
            }
            ColorPickerTarget.ADVANCED_TITLE -> colorEditingTemplate?.let { entry ->
                val config = entry.config
                AdvancedTitlePackageManager.addOrUpdate(
                    name = entry.name,
                    json = AdvancedTitlePackageManager.readTemplate(entry),
                    oldEntry = entry,
                    fontWeight = config.normalizedFontWeightOrNull(),
                    fontSizeScale = config.normalizedFontSizeScaleOrNull(),
                    titleTopSpacing = config.normalizedTitleTopSpacingOrNull(),
                    titleBottomSpacing = config.normalizedTitleBottomSpacingOrNull(),
                    textColor = null,
                ).also { updated ->
                    colorEditingTemplate = updated
                    if (updated.id == AdvancedTitlePackageManager.activeId()) {
                        AdvancedTitlePackageManager.apply(updated)
                    }
                }
            }
        }.also {
            postEvent(EventBus.UP_CONFIG, arrayListOf(2, 5))
        }

    private fun currentTipValue(section: Int, position: Int): Int = ReadTipConfig.run {
        when (section) {
            SECTION_HEADER -> when (position) {
                POSITION_LEFT -> tipHeaderLeft
                POSITION_MIDDLE -> tipHeaderMiddle
                else -> tipHeaderRight
            }

            SECTION_FOOTER -> when (position) {
                POSITION_LEFT -> tipFooterLeft
                POSITION_MIDDLE -> tipFooterMiddle
                else -> tipFooterRight
            }

            else -> none
        }
    }

    private fun assignTipValue(section: Int, position: Int, value: Int) = ReadTipConfig.run {
        when (section) {
            SECTION_HEADER -> when (position) {
                POSITION_LEFT -> tipHeaderLeft = value
                POSITION_MIDDLE -> tipHeaderMiddle = value
                POSITION_RIGHT -> tipHeaderRight = value
            }

            SECTION_FOOTER -> when (position) {
                POSITION_LEFT -> tipFooterLeft = value
                POSITION_MIDDLE -> tipFooterMiddle = value
                POSITION_RIGHT -> tipFooterRight = value
            }
        }
    }

    private fun usagePosition(value: Int, currentSection: Int, currentPosition: Int): String? {
        if (value == ReadTipConfig.none) return null
        val slots = listOf(
            Triple(SECTION_HEADER, POSITION_LEFT, ReadTipConfig.tipHeaderLeft),
            Triple(SECTION_HEADER, POSITION_MIDDLE, ReadTipConfig.tipHeaderMiddle),
            Triple(SECTION_HEADER, POSITION_RIGHT, ReadTipConfig.tipHeaderRight),
            Triple(SECTION_FOOTER, POSITION_LEFT, ReadTipConfig.tipFooterLeft),
            Triple(SECTION_FOOTER, POSITION_MIDDLE, ReadTipConfig.tipFooterMiddle),
            Triple(SECTION_FOOTER, POSITION_RIGHT, ReadTipConfig.tipFooterRight),
        )
        val usedSlot = slots.firstOrNull { (section, position, slotValue) ->
            slotValue == value && (section != currentSection || position != currentPosition)
        } ?: return null
        val sectionName = getString(
            if (usedSlot.first == SECTION_HEADER) R.string.header else R.string.footer
        )
        val positionName = getString(
            when (usedSlot.second) {
                POSITION_LEFT -> R.string.left
                POSITION_MIDDLE -> R.string.middle
                else -> R.string.right
            }
        )
        return getString(
            R.string.read_tip_used_at,
            getString(R.string.read_tip_position, sectionName, positionName),
        )
    }

    private fun clearRepeat(repeat: Int) = ReadTipConfig.apply {
        if (repeat != none) {
            if (tipHeaderLeft == repeat) tipHeaderLeft = none
            if (tipHeaderMiddle == repeat) tipHeaderMiddle = none
            if (tipHeaderRight == repeat) tipHeaderRight = none
            if (tipFooterLeft == repeat) tipFooterLeft = none
            if (tipFooterMiddle == repeat) tipFooterMiddle = none
            if (tipFooterRight == repeat) tipFooterRight = none
        }
    }

    private fun tipIcon(value: Int): Int = when (value) {
        ReadTipConfig.none -> R.drawable.ic_block_outline
        ReadTipConfig.bookName, ReadTipConfig.chapterTitle -> R.drawable.ic_book_has
        ReadTipConfig.time,
        ReadTipConfig.timeBattery,
        ReadTipConfig.timeBatteryPercentage -> R.drawable.ic_mingcute_time_line

        ReadTipConfig.battery,
        ReadTipConfig.batteryPercentage -> R.drawable.ic_battery_outline

        else -> R.drawable.ic_chapter_list
    }

    private fun repositionAboveDrawer() {
        parentFragment?.view?.let { ReadDrawerStyle.positionDialogAbove(dialog, it) }
    }
}
