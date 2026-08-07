package io.legado.app.ui.book.read.config

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.DialogTipConfigBinding
import io.legado.app.databinding.ItemReadTipOptionBinding
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadTipConfig
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.config.NgInlineColorPicker
import io.legado.app.ui.design.components.view.NgFloatingTabItem
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.hexString
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.viewbindingdelegate.viewBinding


class TipConfigDialog : BaseDialogFragment(R.layout.dialog_tip_config) {

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

    private val binding by viewBinding(DialogTipConfigBinding::bind)

    private var currentSection = SECTION_TITLE
    private var headerPosition = POSITION_LEFT
    private var footerPosition = POSITION_LEFT
    private var dialogContentColor = Color.WHITE
    private var dialogAccentColor = Color.WHITE
    private lateinit var tipContentAdapter: TipContentAdapter
    private var activeColorPickerTarget by mutableStateOf<ColorPickerTarget?>(null)

    private enum class ColorPickerTarget {
        TIP,
        DIVIDER,
    }

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(marginDp = 20, dimAmount = 0.4f)
        repositionAboveDrawer()
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initDialogStyle()
        initView()
        initEvent()
        initColorPicker()
        showSection(SECTION_TITLE)
        observeEvent<String>(EventBus.TIP_COLOR) {
            updateStyleControls()
        }
    }

    private fun initView() = binding.run {
        if (ReadBookConfig.titleMode !in 0..2) {
            ReadBookConfig.titleMode = 0
        }

        switchHeaderEnabled.isChecked = ReadTipConfig.headerMode == 1
        switchFooterEnabled.isChecked = ReadTipConfig.footerMode == 0

        tipContentAdapter = TipContentAdapter().apply {
            setItems(ReadTipConfig.tipValues.indices.toList())
            bindToRecyclerView(rvTipContent)
        }
        rvTipContent.layoutManager = object : GridLayoutManager(requireContext(), 2) {
            override fun canScrollVertically(): Boolean = false
        }

        tipPositionTabs.setItems(
            items = listOf(
                NgFloatingTabItem(text = getString(R.string.left)),
                NgFloatingTabItem(text = getString(R.string.middle)),
                NgFloatingTabItem(text = getString(R.string.right)),
            ),
            selectedIndex = POSITION_LEFT,
        ) { position ->
            when (currentSection) {
                SECTION_HEADER -> headerPosition = position
                SECTION_FOOTER -> footerPosition = position
            }
            tipContentAdapter.notifyDataSetChanged()
        }

        infoSectionTabs.setItems(
            items = listOf(
                NgFloatingTabItem(text = getString(R.string.title)),
                NgFloatingTabItem(text = getString(R.string.header)),
                NgFloatingTabItem(text = getString(R.string.footer)),
                NgFloatingTabItem(text = getString(R.string.style)),
            ),
            selectedIndex = SECTION_TITLE,
        ) { section ->
            showSection(section)
        }
        titleModeTabs.setItems(
            items = listOf(
                NgFloatingTabItem(text = getString(R.string.title_left)),
                NgFloatingTabItem(text = getString(R.string.title_center)),
                NgFloatingTabItem(text = getString(R.string.title_hide)),
            ),
            selectedIndex = ReadBookConfig.titleMode,
        ) { titleMode ->
            ReadBookConfig.titleMode = titleMode
            postEvent(EventBus.UP_CONFIG, arrayListOf(5))
        }

        tipColorModeTabs.setItems(
            items = ReadTipConfig.tipColorNames.map { NgFloatingTabItem(text = it) },
            selectedIndex = if (ReadTipConfig.tipColor == 0) 0 else 1,
        ) { mode ->
            if (mode == 0) {
                ReadTipConfig.tipColor = 0
                updateStyleControls()
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            } else {
                openColorPicker(ColorPickerTarget.TIP)
            }
        }
        tipDividerColorModeTabs.setItems(
            items = ReadTipConfig.tipDividerColorNames.map { NgFloatingTabItem(text = it) },
            selectedIndex = dividerColorModeIndex(),
        ) { mode ->
            when (mode) {
                0, 1 -> {
                    ReadTipConfig.tipDividerColor = mode - 1
                    updateStyleControls()
                    postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                }

                else -> openColorPicker(ColorPickerTarget.DIVIDER)
            }
        }
        dsbTitleSize.progress = ReadBookConfig.titleSize
        dsbTitleTop.progress = ReadBookConfig.titleTopSpacing
        dsbTitleBottom.progress = ReadBookConfig.titleBottomSpacing

        updateStyleControls()
    }

    private fun updateStyleControls() = binding.run {
        val tipColor = ReadTipConfig.tipColor
        val tipDividerColor = ReadTipConfig.tipDividerColor
        tipColorModeTabs.select(if (tipColor == 0) 0 else 1, notify = false)
        tipDividerColorModeTabs.select(dividerColorModeIndex(), notify = false)
        tvTipColor.visibility = if (tipColor == 0) View.GONE else View.VISIBLE
        tvTipColor.text = "#${tipColor.hexString}"
        tvTipDividerColor.visibility = if (tipDividerColor in -1..0) View.GONE else View.VISIBLE
        tvTipDividerColor.text = "#${tipDividerColor.hexString}"
    }

    private fun dividerColorModeIndex(): Int = when (ReadTipConfig.tipDividerColor) {
        -1 -> 0
        0 -> 1
        else -> 2
    }

    private fun initEvent() = binding.run {
        dsbTitleSize.onChanged = {
            ReadBookConfig.titleSize = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        dsbTitleTop.onChanged = {
            ReadBookConfig.titleTopSpacing = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        dsbTitleBottom.onChanged = {
            ReadBookConfig.titleBottomSpacing = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }

        llHeaderEnabled.setOnClickListener { switchHeaderEnabled.performClick() }
        switchHeaderEnabled.setOnUserCheckedChangeListener { enabled ->
            ReadTipConfig.headerMode = if (enabled) 1 else 2
            if (enabled && !ReadBookConfig.hideStatusBar) {
                ReadBookConfig.hideStatusBar = true
                putPrefBoolean(PreferKey.hideStatusBar, true)
                postEvent(EventBus.UP_CONFIG, arrayListOf(0, 2))
            } else {
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }
            updateTipContentVisibility()
        }

        llFooterEnabled.setOnClickListener { switchFooterEnabled.performClick() }
        switchFooterEnabled.setOnUserCheckedChangeListener { enabled ->
            ReadTipConfig.footerMode = if (enabled) 0 else 1
            updateTipContentVisibility()
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }

    }

    private fun initColorPicker() = binding.colorPickerHost.apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            NgAppTheme(
                updateSystemBars = false,
                darkModeOverride = ReadBookConfig.isNightTheme,
            ) {
                val target = activeColorPickerTarget
                if (target != null) {
                    NgInlineColorPicker(
                        title = getString(
                            when (target) {
                                ColorPickerTarget.TIP -> R.string.tip_text_color
                                ColorPickerTarget.DIVIDER -> R.string.tip_divider_color
                            }
                        ),
                        initialColor = initialColorFor(target),
                        onBack = ::closeColorPicker,
                        onColorChanged = { selected ->
                            applySelectedColor(target, selected or Color.BLACK)
                        },
                        onReset = {
                            resetSelectedColor(target)
                            closeColorPicker()
                        },
                    )
                }
            }
        }
    }

    private fun openColorPicker(target: ColorPickerTarget) = binding.run {
        activeColorPickerTarget = target
        styleModeContent.visibility = View.GONE
        colorPickerHost.visibility = View.VISIBLE
        rootView.post { repositionAboveDrawer() }
    }

    private fun closeColorPicker() = binding.run {
        activeColorPickerTarget = null
        colorPickerHost.visibility = View.GONE
        styleModeContent.visibility = View.VISIBLE
        updateStyleControls()
        rootView.post { repositionAboveDrawer() }
    }

    private fun initialColorFor(target: ColorPickerTarget): Int = when (target) {
        ColorPickerTarget.TIP -> ReadTipConfig.tipColor.takeUnless { it == 0 }
            ?: ReadBookConfig.textColor

        ColorPickerTarget.DIVIDER -> when (val color = ReadTipConfig.tipDividerColor) {
            -1 -> ContextCompat.getColor(requireContext(), R.color.divider)
            0 -> ReadBookConfig.textColor
            else -> color
        }
    }

    private fun applySelectedColor(target: ColorPickerTarget, color: Int) {
        when (target) {
            ColorPickerTarget.TIP -> ReadTipConfig.tipColor = color
            ColorPickerTarget.DIVIDER -> ReadTipConfig.tipDividerColor = color
        }
        postEvent(EventBus.TIP_COLOR, "")
        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
    }

    private fun resetSelectedColor(target: ColorPickerTarget) {
        when (target) {
            ColorPickerTarget.TIP -> ReadTipConfig.tipColor = 0
            ColorPickerTarget.DIVIDER -> ReadTipConfig.tipDividerColor = -1
        }
        updateStyleControls()
        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
    }

    private fun initDialogStyle() = binding.run {
        dialogContentColor = ReadDrawerStyle.contentColor(requireContext())
        rootView.setBackgroundColor(Color.TRANSPARENT)
        ReadDrawerStyle.applyGlassBackground(
            view = ngDialogBackground,
            radiusDp = 24,
        )
        applyTextColor(dialogContent, dialogContentColor)
        dialogAccentColor = ReadDrawerStyle.accentColor(requireContext())
        val selectedContentColor = if (ColorUtils.isColorLight(dialogAccentColor)) {
            Color.BLACK
        } else {
            Color.WHITE
        }
        listOf(
            infoSectionTabs,
            titleModeTabs,
            tipPositionTabs,
            tipColorModeTabs,
            tipDividerColorModeTabs,
        ).forEach {
            it.setSurfaceAlpha(0.28f)
            it.setContentColors(
                unselectedContentColor = dialogContentColor,
                selectedContentColor = selectedContentColor,
                selectedContainerColor = dialogAccentColor,
            )
        }
        listOf(dsbTitleSize, dsbTitleTop, dsbTitleBottom).forEach {
            it.setContentColor(dialogContentColor)
            it.useSliderOnlyLayout()
        }
    }

    private fun applyTextColor(view: View, color: Int) {
        when (view) {
            is TextView -> view.setTextColor(color)
            is ViewGroup -> repeat(view.childCount) { index ->
                applyTextColor(view.getChildAt(index), color)
            }
        }
    }

    private fun showSection(section: Int) = binding.run {
        if (section != SECTION_STYLE && activeColorPickerTarget != null) {
            closeColorPicker()
        }
        currentSection = section
        llTitleConfig.visibility = if (section == SECTION_TITLE) View.VISIBLE else View.GONE
        llHeaderConfig.visibility = if (section == SECTION_HEADER) View.VISIBLE else View.GONE
        llFooterConfig.visibility = if (section == SECTION_FOOTER) View.VISIBLE else View.GONE
        llInfoStyleConfig.visibility = if (section == SECTION_STYLE) View.VISIBLE else View.GONE
        updateTipContentVisibility()
    }

    private fun updateTipContentVisibility() = binding.run {
        val showTipContent = when (currentSection) {
            SECTION_HEADER -> switchHeaderEnabled.isChecked
            SECTION_FOOTER -> switchFooterEnabled.isChecked
            else -> false
        }
        llTipContentConfig.visibility = if (showTipContent) View.VISIBLE else View.GONE
        if (showTipContent) {
            val selectedPosition = if (currentSection == SECTION_HEADER) {
                headerPosition
            } else {
                footerPosition
            }
            tipPositionTabs.select(selectedPosition, notify = false)
            tipContentAdapter.notifyDataSetChanged()
        }
        rootView.post { repositionAboveDrawer() }
    }

    private fun repositionAboveDrawer() {
        parentFragment?.view?.let {
            ReadDrawerStyle.positionDialogAbove(dialog, it)
        }
    }

    private fun currentTipValue(): Int = ReadTipConfig.run {
        when (currentSection) {
            SECTION_HEADER -> when (headerPosition) {
                POSITION_LEFT -> tipHeaderLeft
                POSITION_MIDDLE -> tipHeaderMiddle
                else -> tipHeaderRight
            }

            SECTION_FOOTER -> when (footerPosition) {
                POSITION_LEFT -> tipFooterLeft
                POSITION_MIDDLE -> tipFooterMiddle
                else -> tipFooterRight
            }

            else -> none
        }
    }

    private fun assignCurrentTipValue(value: Int) = ReadTipConfig.run {
        when (currentSection) {
            SECTION_HEADER -> when (headerPosition) {
                POSITION_LEFT -> tipHeaderLeft = value
                POSITION_MIDDLE -> tipHeaderMiddle = value
                POSITION_RIGHT -> tipHeaderRight = value
            }

            SECTION_FOOTER -> when (footerPosition) {
                POSITION_LEFT -> tipFooterLeft = value
                POSITION_MIDDLE -> tipFooterMiddle = value
                POSITION_RIGHT -> tipFooterRight = value
            }
        }
    }

    private fun usagePosition(value: Int): String? {
        if (value == ReadTipConfig.none) return null
        val slots = listOf(
            Triple(SECTION_HEADER, POSITION_LEFT, ReadTipConfig.tipHeaderLeft),
            Triple(SECTION_HEADER, POSITION_MIDDLE, ReadTipConfig.tipHeaderMiddle),
            Triple(SECTION_HEADER, POSITION_RIGHT, ReadTipConfig.tipHeaderRight),
            Triple(SECTION_FOOTER, POSITION_LEFT, ReadTipConfig.tipFooterLeft),
            Triple(SECTION_FOOTER, POSITION_MIDDLE, ReadTipConfig.tipFooterMiddle),
            Triple(SECTION_FOOTER, POSITION_RIGHT, ReadTipConfig.tipFooterRight),
        )
        val currentPosition = if (currentSection == SECTION_HEADER) {
            headerPosition
        } else {
            footerPosition
        }
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
        val position = getString(R.string.read_tip_position, sectionName, positionName)
        return getString(R.string.read_tip_used_at, position)
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

    private inner class TipContentAdapter :
        RecyclerAdapter<Int, ItemReadTipOptionBinding>(requireContext()) {

        override fun getViewBinding(parent: ViewGroup): ItemReadTipOptionBinding {
            return ItemReadTipOptionBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemReadTipOptionBinding,
            item: Int,
            payloads: MutableList<Any>,
        ) = binding.run {
            val value = ReadTipConfig.tipValues[item]
            val selected = value == currentTipValue()
            tvOption.text = ReadTipConfig.tipNames[item]
            tvOption.setTextColor(dialogContentColor)
            ivOption.setImageResource(tipIcon(value))
            ivOption.setColorFilter(dialogContentColor)
            ivChecked.setColorFilter(dialogAccentColor)
            ivChecked.visibility = if (selected) View.VISIBLE else View.GONE

            val usage = if (selected) null else usagePosition(value)
            tvUsage.text = usage
            tvUsage.setTextColor(ColorUtils.withAlpha(dialogContentColor, 0.62f))
            tvUsage.visibility = if (usage == null) View.GONE else View.VISIBLE
        }

        override fun registerListener(
            holder: ItemViewHolder,
            binding: ItemReadTipOptionBinding,
        ) {
            binding.root.setOnClickListener {
                getItemByLayoutPosition(holder.layoutPosition)?.let { item ->
                    val value = ReadTipConfig.tipValues[item]
                    clearRepeat(value)
                    assignCurrentTipValue(value)
                    postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
                    notifyDataSetChanged()
                }
            }
        }
    }
}
