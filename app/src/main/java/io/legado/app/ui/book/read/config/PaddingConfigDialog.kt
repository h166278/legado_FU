package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.View
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogReadPaddingBinding
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.design.components.view.NgFloatingTabItem
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.postEvent
import io.legado.app.utils.viewbindingdelegate.viewBinding

class PaddingConfigDialog : BaseDialogFragment(R.layout.dialog_read_padding) {

    private companion object {
        const val SECTION_HEADER = 0
        const val SECTION_BODY = 1
        const val SECTION_FOOTER = 2
    }

    private val binding by viewBinding(DialogReadPaddingBinding::bind)

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(marginDp = 20, dimAmount = 0.4f)
        binding.rootView.post { repositionAboveDrawer() }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initDialogStyle()
        initData()
        initView()
        showSection(SECTION_BODY)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        ReadBookConfig.save()
    }

    private fun initData() = binding.run {
        //正文
        dsbPaddingTop.progress = ReadBookConfig.paddingTop
        dsbPaddingBottom.progress = ReadBookConfig.paddingBottom
        dsbPaddingLeft.progress = ReadBookConfig.paddingLeft
        dsbPaddingRight.progress = ReadBookConfig.paddingRight
        //页眉
        dsbHeaderPaddingTop.progress = ReadBookConfig.headerPaddingTop
        dsbHeaderPaddingBottom.progress = ReadBookConfig.headerPaddingBottom
        dsbHeaderPaddingLeft.progress = ReadBookConfig.headerPaddingLeft
        dsbHeaderPaddingRight.progress = ReadBookConfig.headerPaddingRight
        //页脚
        dsbFooterPaddingTop.progress = ReadBookConfig.footerPaddingTop
        dsbFooterPaddingBottom.progress = ReadBookConfig.footerPaddingBottom
        dsbFooterPaddingLeft.progress = ReadBookConfig.footerPaddingLeft
        dsbFooterPaddingRight.progress = ReadBookConfig.footerPaddingRight
        switchHeaderLine.isChecked = ReadBookConfig.showHeaderLine
        switchFooterLine.isChecked = ReadBookConfig.showFooterLine
    }

    private fun initView() = binding.run {
        paddingSectionTabs.setItems(
            items = listOf(
                NgFloatingTabItem(text = getString(R.string.header)),
                NgFloatingTabItem(text = getString(R.string.main_body)),
                NgFloatingTabItem(text = getString(R.string.footer)),
            ),
            selectedIndex = SECTION_BODY,
        ) { section ->
            showSection(section)
        }
        //正文
        dsbPaddingTop.onChanged = {
            ReadBookConfig.paddingTop = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(10, 5))
        }
        dsbPaddingBottom.onChanged = {
            ReadBookConfig.paddingBottom = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(10, 5))
        }
        dsbPaddingLeft.onChanged = {
            ReadBookConfig.paddingLeft = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(10, 5))
        }
        dsbPaddingRight.onChanged = {
            ReadBookConfig.paddingRight = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(10, 5))
        }
        //页眉
        dsbHeaderPaddingTop.onChanged = {
            ReadBookConfig.headerPaddingTop = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
        dsbHeaderPaddingBottom.onChanged = {
            ReadBookConfig.headerPaddingBottom = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
        dsbHeaderPaddingLeft.onChanged = {
            ReadBookConfig.headerPaddingLeft = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
        dsbHeaderPaddingRight.onChanged = {
            ReadBookConfig.headerPaddingRight = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
        //页脚
        dsbFooterPaddingTop.onChanged = {
            ReadBookConfig.footerPaddingTop = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
        dsbFooterPaddingBottom.onChanged = {
            ReadBookConfig.footerPaddingBottom = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
        dsbFooterPaddingLeft.onChanged = {
            ReadBookConfig.footerPaddingLeft = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
        dsbFooterPaddingRight.onChanged = {
            ReadBookConfig.footerPaddingRight = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
        switchHeaderLine.setOnUserCheckedChangeListener { isChecked ->
            ReadBookConfig.showHeaderLine = isChecked
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
        switchFooterLine.setOnUserCheckedChangeListener { isChecked ->
            ReadBookConfig.showFooterLine = isChecked
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
    }

    private fun initDialogStyle() = binding.run {
        val contentColor = ReadDrawerStyle.contentColor(requireContext())
        rootView.setBackgroundColor(Color.TRANSPARENT)
        ReadDrawerStyle.applyGlassBackground(
            view = ngDialogBackground,
            radiusDp = 24,
        )
        tvTitle.setTextColor(contentColor)
        tvHeaderLine.setTextColor(contentColor)
        tvFooterLine.setTextColor(contentColor)
        val selectedColor = ReadDrawerStyle.accentColor(requireContext())
        paddingSectionTabs.setSurfaceAlpha(0.28f)
        paddingSectionTabs.setContentColors(
            unselectedContentColor = contentColor,
            selectedContentColor = if (ColorUtils.isColorLight(selectedColor)) {
                Color.BLACK
            } else {
                Color.WHITE
            },
            selectedContainerColor = selectedColor,
        )
        listOf(
            dsbPaddingTop,
            dsbPaddingBottom,
            dsbPaddingLeft,
            dsbPaddingRight,
            dsbHeaderPaddingTop,
            dsbHeaderPaddingBottom,
            dsbHeaderPaddingLeft,
            dsbHeaderPaddingRight,
            dsbFooterPaddingTop,
            dsbFooterPaddingBottom,
            dsbFooterPaddingLeft,
            dsbFooterPaddingRight,
        ).forEach {
            it.setContentColor(contentColor)
            it.useSliderOnlyLayout()
        }
    }

    private fun showSection(section: Int) = binding.run {
        llHeaderPadding.visibility = if (section == SECTION_HEADER) {
            View.VISIBLE
        } else {
            View.GONE
        }
        llBodyPadding.visibility = if (section == SECTION_BODY) {
            View.VISIBLE
        } else {
            View.GONE
        }
        llFooterPadding.visibility = if (section == SECTION_FOOTER) {
            View.VISIBLE
        } else {
            View.GONE
        }
        rootView.post { repositionAboveDrawer() }
    }

    private fun repositionAboveDrawer() {
        parentFragment?.view?.let {
            ReadDrawerStyle.positionDialogAbove(dialog, it)
        }
    }

}
