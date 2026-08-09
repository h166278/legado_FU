package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.ComponentDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.widget.dialog.applyNgWindow
import io.legado.app.utils.putPrefInt

/**
 * 点击区域设置
 */
class ClickActionConfigDialog : BaseComposeDialogFragment() {
    private val actions by lazy {
        linkedMapOf(
            Pair(-1, getString(R.string.non_action)),
            Pair(0, getString(R.string.menu)),
            Pair(1, getString(R.string.next_page)),
            Pair(2, getString(R.string.prev_page)),
            Pair(3, getString(R.string.next_chapter)),
            Pair(4, getString(R.string.previous_chapter)),
            Pair(5, getString(R.string.read_aloud_prev_paragraph)),
            Pair(6, getString(R.string.read_aloud_next_paragraph)),
            Pair(7, getString(R.string.bookmark_add)),
            Pair(8, getString(R.string.edit_content)),
            Pair(9, getString(R.string.replace_state_change)),
            Pair(10, getString(R.string.chapter_list)),
            Pair(11, getString(R.string.search_content)),
            Pair(12, getString(R.string.sync_book_progress_t)),
            Pair(13, getString(R.string.read_aloud_pause_resume))
        )
    }
    private val preferenceKeys = listOf(
        PreferKey.clickActionTL,
        PreferKey.clickActionTC,
        PreferKey.clickActionTR,
        PreferKey.clickActionML,
        PreferKey.clickActionMC,
        PreferKey.clickActionMR,
        PreferKey.clickActionBL,
        PreferKey.clickActionBC,
        PreferKey.clickActionBR,
    )
    private var currentActions by mutableStateOf<List<Int>>(emptyList())
    private var actionSelectorDialog: ComponentDialog? = null
    private var bottomDialogRegistered = false

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            setBackgroundDrawableResource(R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (bottomDialogRegistered) {
            (activity as? ReadBookActivity)?.let {
                it.bottomDialog = (it.bottomDialog - 1).coerceAtLeast(0)
            }
            bottomDialogRegistered = false
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        if (!bottomDialogRegistered) {
            (activity as? ReadBookActivity)?.let {
                it.bottomDialog++
                bottomDialogRegistered = true
            }
        }
        currentActions = readCurrentActions()
        (view as ComposeView).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme(
                    snapshot = ReadDrawerStyle.themeSnapshot(requireContext()),
                    updateSystemBars = false,
                ) {
                    ClickActionConfigScreen(
                        actionLabels = currentActions.map { actions[it].orEmpty() },
                        onCellClick = ::selectAction,
                        onClose = { dismissAllowingStateLoss() },
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        actionSelectorDialog?.dismiss()
        actionSelectorDialog = null
        super.onDestroyView()
    }

    private fun readCurrentActions() = listOf(
        AppConfig.clickActionTL,
        AppConfig.clickActionTC,
        AppConfig.clickActionTR,
        AppConfig.clickActionML,
        AppConfig.clickActionMC,
        AppConfig.clickActionMR,
        AppConfig.clickActionBL,
        AppConfig.clickActionBC,
        AppConfig.clickActionBR,
    )

    private fun selectAction(cellIndex: Int) {
        if (cellIndex !in preferenceKeys.indices) return
        actionSelectorDialog?.dismiss()
        val context = requireContext()
        var selectorDialog: ComponentDialog? = null
        val contentView = ComposeView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme(
                    snapshot = ReadDrawerStyle.themeSnapshot(context),
                    updateSystemBars = false,
                ) {
                    ClickActionSelectorDialog(
                        title = getString(R.string.select_action),
                        options = actions.map { (value, label) ->
                            ClickActionOption(value, label)
                        },
                        onSelected = { action ->
                            putPrefInt(preferenceKeys[cellIndex], action)
                            currentActions = currentActions.toMutableList().apply {
                                this[cellIndex] = action
                            }
                            selectorDialog?.dismiss()
                        },
                    )
                }
            }
        }
        selectorDialog = ComponentDialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(contentView)
            setCanceledOnTouchOutside(true)
            setOnDismissListener {
                if (actionSelectorDialog === this) actionSelectorDialog = null
            }
            show()
            applyNgWindow(marginDp = 20, dimAmount = 0.14f)
        }
        actionSelectorDialog = selectorDialog
    }

    override fun onDestroy() {
        super.onDestroy()
        AppConfig.detectClickArea()
    }

}
