package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.dpToPx
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import kotlin.math.max

@SuppressLint("RestrictedApi")
class TextActionMenu(private val context: ComponentActivity, private val callBack: CallBack) :
    PopupWindow(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {

    private val menuItems: List<MenuItemImpl> = buildMenuItems()
    private val primaryMenuItemIds = setOf(
        R.id.menu_replace,
        R.id.menu_ai_purify,
        R.id.menu_copy,
        R.id.menu_bookmark,
    )
    private val currentPageState = mutableIntStateOf(0)
    private val moreMenuVisibleState = mutableStateOf(false)
    private val themeSnapshotState = mutableStateOf(ReadDrawerStyle.themeSnapshot(context))
    private var moreMenuPopup: PopupWindow? = null
    private var popupParentView: View? = null
    private var toolbarX = 0
    private var toolbarY = 0
    private var toolbarWidth = 0
    private var toolbarHeight = 0
    private var menuSafeLeft = 0
    private var menuSafeRight = 0
    private var menuSafeTop = 0
    private var menuSafeBottom = 0
    private val actions: List<TextSelectionAction> by lazy {
        menuItems.map { item ->
            TextSelectionAction(
                title = item.title.toString(),
                iconRes = menuIcon(item.itemId),
                iconBitmap = item.icon?.let { drawable ->
                    val iconSize = 24.dpToPx()
                    runCatching {
                        drawable.toBitmap(iconSize, iconSize).asImageBitmap()
                    }.getOrNull()
                },
                onClick = { onActionClick(item) },
            )
        }
    }
    private val primaryActions: List<TextSelectionAction> by lazy {
        menuItems.zip(actions)
            .filter { (item) -> item.itemId in primaryMenuItemIds }
            .map { (_, action) -> action }
    }
    private val moreActions: List<TextSelectionAction> by lazy {
        menuItems.zip(actions)
            .filterNot { (item) -> item.itemId in primaryMenuItemIds }
            .map { (_, action) -> action }
    }

    init {
        contentView = ComposeView(context).apply {
            attachViewTreeOwners()
            setBackgroundColor(Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme(
                    snapshot = themeSnapshotState.value,
                    updateSystemBars = false,
                ) {
                    TextSelectionToolbar(
                        primaryActions = primaryActions,
                        currentPage = currentPageState.intValue,
                        onPageChange = {
                            currentPageState.intValue = it
                            setMoreMenuVisible(false)
                        },
                        moreMenuVisible = moreMenuVisibleState.value,
                        onMoreMenuVisibleChange = ::setMoreMenuVisible,
                        onLongClick = ::toggleSelectionReadMode,
                    )
                }
            }
        }
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        elevation = 0f
        isTouchable = true
        isOutsideTouchable = false
        isFocusable = false
        setOnDismissListener {
            dismissMoreMenu()
            currentPageState.intValue = 0
            popupParentView = null
        }
    }

    fun show(
        view: View,
        windowHeight: Int,
        startTopY: Int,
        startBottomY: Int,
        endBottomY: Int,
    ) {
        themeSnapshotState.value = ReadDrawerStyle.themeSnapshot(context)
        currentPageState.intValue = 0
        dismissMoreMenu()
        popupParentView = view

        val rootWidth = view.rootView.width.takeIf { it > 0 }
            ?: context.resources.displayMetrics.widthPixels
        val insets = ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
        val leftInset = insets?.left ?: 0
        val rightInset = insets?.right ?: 0
        val topInset = insets?.top ?: 0
        val bottomInset = insets?.bottom ?: 0

        val horizontalMargin = 16.dpToPx()
        val safeLeft = leftInset + horizontalMargin
        val safeRight = rootWidth - rightInset - horizontalMargin
        val safeWidth = (safeRight - safeLeft).coerceAtLeast(1)
        val desiredWidth = textSelectionToolbarWidthDp(primaryActions.size).dpToPx()
        val popupWidth = desiredWidth.coerceAtMost(safeWidth)

        val desiredHeight = TEXT_SELECTION_TOOLBAR_HEIGHT_DP.dpToPx()
        val verticalMargin = 8.dpToPx()
        val availableHeight = (
            windowHeight - topInset - bottomInset - verticalMargin * 2
        ).coerceAtLeast(1)
        val popupHeight = desiredHeight.coerceAtMost(availableHeight)

        width = popupWidth
        height = popupHeight

        val gap = 8.dpToPx()
        val minTop = topInset + verticalMargin
        val maxTop = (
            windowHeight - bottomInset - verticalMargin - popupHeight
        ).coerceAtLeast(minTop)
        val above = startTopY - popupHeight - gap
        val selectionSpan = (endBottomY - startBottomY).coerceAtLeast(0)
        val below = if (selectionSpan > popupHeight * 2) {
            startBottomY + gap
        } else {
            max(startBottomY, endBottomY) + gap
        }
        val popupY = when {
            above >= minTop -> above
            below <= maxTop -> below
            else -> above.coerceIn(minTop, maxTop)
        }
        val popupX = safeLeft + (safeWidth - popupWidth) / 2

        toolbarX = popupX
        toolbarY = popupY
        toolbarWidth = popupWidth
        toolbarHeight = popupHeight
        menuSafeLeft = leftInset + TEXT_SELECTION_MORE_PANEL_SCREEN_MARGIN_DP.dpToPx()
        menuSafeRight = rootWidth - rightInset -
            TEXT_SELECTION_MORE_PANEL_SCREEN_MARGIN_DP.dpToPx()
        menuSafeTop = topInset + TEXT_SELECTION_MORE_PANEL_SCREEN_MARGIN_DP.dpToPx()
        menuSafeBottom = windowHeight - bottomInset -
            TEXT_SELECTION_MORE_PANEL_SCREEN_MARGIN_DP.dpToPx()

        showAtLocation(
            view,
            Gravity.TOP or Gravity.START,
            popupX,
            popupY,
        )
        // PopupWindow 会创建独立 DecorView，必须在下一帧 Compose attach 前补齐 owners。
        var popupView: View? = contentView
        while (popupView != null) {
            popupView.attachViewTreeOwners()
            popupView = popupView.parent as? View
        }
    }

    private fun View.attachViewTreeOwners() {
        setViewTreeLifecycleOwner(this@TextActionMenu.context)
        setViewTreeViewModelStoreOwner(this@TextActionMenu.context)
        setViewTreeSavedStateRegistryOwner(this@TextActionMenu.context)
    }

    private fun buildMenuItems(): List<MenuItemImpl> {
        val appMenu = MenuBuilder(context)
        val processTextMenu = MenuBuilder(context)
        SupportMenuInflater(context).inflate(R.menu.content_select_action, appMenu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            onInitializeMenu(processTextMenu)
        }
        return appMenu.visibleItems + processTextMenu.visibleItems
    }

    private fun onActionClick(item: MenuItemImpl) {
        dismissMoreMenu()
        if (!callBack.onMenuItemSelected(item.itemId)) {
            onMenuItemSelected(item)
        }
        callBack.onMenuActionFinally()
    }

    private fun setMoreMenuVisible(visible: Boolean) {
        if (!visible) {
            dismissMoreMenu()
            return
        }
        val parentView = popupParentView ?: return
        if (moreActions.isEmpty() || !isShowing) return

        dismissMoreMenu()
        moreMenuVisibleState.value = true

        val gap = TEXT_SELECTION_MORE_PANEL_GAP_DP.dpToPx()
        val desiredHeight = textSelectionMoreMenuHeightDp(moreActions.size).dpToPx()
        val availableAbove = (toolbarY - gap - menuSafeTop).coerceAtLeast(0)
        val availableBelow = (
            menuSafeBottom - toolbarY - toolbarHeight - gap
        ).coerceAtLeast(0)
        val placeAbove = availableAbove >= desiredHeight || availableAbove >= availableBelow
        val availableHeight = if (placeAbove) availableAbove else availableBelow
        val panelHeight = desiredHeight.coerceAtMost(availableHeight).coerceAtLeast(1)
        val safeWidth = (menuSafeRight - menuSafeLeft).coerceAtLeast(1)
        val panelWidth = TEXT_SELECTION_MORE_PANEL_WIDTH_DP.dpToPx()
            .coerceAtMost(safeWidth)
        val maxX = (menuSafeRight - panelWidth).coerceAtLeast(menuSafeLeft)
        val panelX = (toolbarX + toolbarWidth - panelWidth)
            .coerceIn(menuSafeLeft, maxX)
        val panelY = if (placeAbove) {
            toolbarY - gap - panelHeight
        } else {
            toolbarY + toolbarHeight + gap
        }

        val menuView = ComposeView(context).apply {
            attachViewTreeOwners()
            setBackgroundColor(Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme(
                    snapshot = themeSnapshotState.value,
                    updateSystemBars = false,
                ) {
                    TextSelectionMoreMenu(
                        actions = moreActions,
                        onLongClick = ::toggleSelectionReadMode,
                    )
                }
            }
        }
        val popup = PopupWindow(menuView, panelWidth, panelHeight).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 0f
            isTouchable = true
            isOutsideTouchable = false
            isFocusable = false
        }
        moreMenuPopup = popup
        popup.setOnDismissListener {
            if (moreMenuPopup === popup) {
                moreMenuPopup = null
                moreMenuVisibleState.value = false
            }
        }
        popup.showAtLocation(
            parentView,
            Gravity.TOP or Gravity.START,
            panelX,
            panelY,
        )
        var popupView: View? = menuView
        while (popupView != null) {
            popupView.attachViewTreeOwners()
            popupView = popupView.parent as? View
        }
    }

    private fun dismissMoreMenu() {
        val popup = moreMenuPopup
        moreMenuPopup = null
        popup?.setOnDismissListener(null)
        popup?.dismiss()
        moreMenuVisibleState.value = false
    }

    private fun toggleSelectionReadMode() {
        if (AppConfig.contentSelectSpeakMod == 0) {
            AppConfig.contentSelectSpeakMod = 1
            context.toastOnUi("切换为从选择的地方开始一直朗读")
        } else {
            AppConfig.contentSelectSpeakMod = 0
            context.toastOnUi("切换为朗读选择内容")
        }
    }

    @DrawableRes
    private fun menuIcon(itemId: Int): Int = when (itemId) {
        R.id.menu_replace -> R.drawable.ic_cfg_replace
        R.id.menu_ai_purify -> R.drawable.ic_ai_purify
        R.id.menu_copy -> R.drawable.ic_copy
        R.id.menu_bookmark -> R.drawable.ic_bookmark
        R.id.menu_aloud -> R.drawable.ic_read_aloud
        R.id.menu_dict -> R.drawable.ic_translate
        R.id.menu_search_content -> R.drawable.ic_search
        R.id.menu_browser -> R.drawable.ic_web_outline
        R.id.menu_share_str -> R.drawable.ic_share
        else -> R.drawable.ic_ai_capability_text
    }

    private fun onMenuItemSelected(item: MenuItemImpl) {
        when (item.itemId) {
            R.id.menu_copy -> context.sendToClip(callBack.selectedText)
            R.id.menu_share_str -> context.share(callBack.selectedText)
            R.id.menu_browser -> {
                kotlin.runCatching {
                    val intent = if (callBack.selectedText.isAbsUrl()) {
                        Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse(callBack.selectedText)
                        }
                    } else {
                        Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra(SearchManager.QUERY, callBack.selectedText)
                        }
                    }
                    context.startActivity(intent)
                }.onFailure {
                    it.printOnDebug()
                    context.toastOnUi(it.localizedMessage ?: "ERROR")
                }
            }

            else -> item.intent?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    kotlin.runCatching {
                        it.putExtra(Intent.EXTRA_PROCESS_TEXT, callBack.selectedText)
                        context.startActivity(it)
                    }.onFailure { error ->
                        AppLog.put("执行文本菜单操作出错\n$error", error, true)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntent(): Intent {
        return Intent()
            .setAction(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getSupportedActivities(): List<ResolveInfo> {
        return context.packageManager
            .queryIntentActivities(createProcessTextIntent(), 0)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntentForResolveInfo(info: ResolveInfo): Intent {
        return createProcessTextIntent()
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
            .setClassName(info.activityInfo.packageName, info.activityInfo.name)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun onInitializeMenu(menu: Menu) {
        kotlin.runCatching {
            var menuItemOrder = 100
            for (resolveInfo in getSupportedActivities()) {
                menu.add(
                    Menu.NONE,
                    Menu.NONE,
                    menuItemOrder++,
                    resolveInfo.loadLabel(context.packageManager),
                ).apply {
                    intent = createProcessTextIntentForResolveInfo(resolveInfo)
                    icon = resolveInfo.loadIcon(context.packageManager)
                }
            }
        }.onFailure {
            context.toastOnUi("获取文字操作菜单出错:${it.localizedMessage}")
        }
    }

    interface CallBack {
        val selectedText: String

        fun onMenuItemSelected(itemId: Int): Boolean

        fun onMenuActionFinally()
    }
}
