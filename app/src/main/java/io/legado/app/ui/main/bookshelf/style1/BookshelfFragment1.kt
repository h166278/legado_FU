@file:Suppress("DEPRECATION")

package io.legado.app.ui.main.bookshelf.style1

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.core.widget.TextViewCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import com.google.android.material.tabs.TabLayout
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.FragmentBookshelf1Binding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.BookshelfFloatingDockConfig
import io.legado.app.help.config.BookshelfFloatingDockSearchPosition
import io.legado.app.help.config.BookshelfTopBarStyle
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.about.ReadRecordActivity
import io.legado.app.ui.book.group.GroupEditDialog
import io.legado.app.ui.book.manage.BookshelfManageActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.main.bookshelf.BaseBookshelfFragment
import io.legado.app.ui.main.bookshelf.BookshelfDockGroup
import io.legado.app.ui.main.bookshelf.BookshelfContentToolbarActionButton
import io.legado.app.ui.main.bookshelf.BookshelfContentToolbarMenuButton
import io.legado.app.ui.main.bookshelf.BookshelfFloatingDock
import io.legado.app.ui.main.bookshelf.BookshelfToolbarMenuButton
import io.legado.app.ui.main.bookshelf.style1.books.BooksFragment
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.ui.widget.NgActionPopup
import io.legado.app.ui.widget.NgActionPopupItem
import io.legado.app.utils.dpToPx
import io.legado.app.utils.isCreated
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.statusBarHeight
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlin.collections.set

/**
 * 书架界面
 */
class BookshelfFragment1() : BaseBookshelfFragment(R.layout.fragment_bookshelf1),
    TabLayout.OnTabSelectedListener,
    SearchView.OnQueryTextListener {

    companion object {
        private const val SORT_MENU_ID_OFFSET = 1000
        private val sortValues = intArrayOf(4, 0, 1, 2, 3, 5)
    }

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    private val binding by viewBinding(FragmentBookshelf1Binding::bind)
    private val adapter by lazy { TabFragmentPageAdapter(childFragmentManager) }
    private val tabLayout: TabLayout by lazy {
        binding.titleBar.findViewById(R.id.tab_layout)
    }
    private val bookGroups = mutableListOf<BookGroup>()
    private val fragmentMap = hashMapOf<Long, BooksFragment>()
    private var dockGroups by mutableStateOf<List<BookshelfDockGroup>>(emptyList())
    private var dockSelectedIndex by mutableIntStateOf(0)
    private var dockTopDistancePx by mutableIntStateOf(0)
    private var dockContentTopInsetPx by mutableIntStateOf(0)
    private var dockTransparency by mutableIntStateOf(
        BookshelfFloatingDockConfig.DEFAULT_TRANSPARENCY_PERCENT
    )
    private var dockSearchPosition by mutableStateOf(
        BookshelfFloatingDockSearchPosition.LEFT
    )
    private var configuredTopBarStyle: BookshelfTopBarStyle? = null
    override val groupId: Long get() = selectedGroup?.groupId ?: 0

    override val books: List<Book>
        get() {
            val fragment = fragmentMap[groupId]
            return fragment?.getBooks() ?: emptyList()
        }

    override var onlyUpdateRead = false
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initView()
        initBookGroupData()
    }

    private val selectedGroup: BookGroup?
        get() = bookGroups.getOrNull(tabLayout.selectedTabPosition)

    private fun initView() {
        binding.titleBar.title = ""
        binding.titleBar.subtitle = ""
        animateTopBarIn()
        val searchView = binding.titleBar.findViewById<TextView>(R.id.tv_bookshelf_search)
        val moreButton = binding.titleBar.findViewById<ComposeView>(R.id.btn_bookshelf_more)
        applyTopBarBackground(searchView, moreButton)
        searchView.bindSoftPress()
        searchView.setOnClickListener {
            SearchActivity.start(requireContext(), null)
        }
        moreButton.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        moreButton.setContent {
            NgAppTheme {
                BookshelfToolbarMenuButton(
                    onMenuItemClick = ::onBookshelfMenuItemClick
                )
            }
        }
        searchView.setTextColor(ContextCompat.getColor(requireContext(), R.color.ng_search_hint))
        TextViewCompat.setCompoundDrawableTintList(
            searchView,
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.ng_search_icon))
        )
        binding.tvBookshelfSort.bindSoftPress()
        binding.tvBookshelfEdit.bindSoftPress()
        binding.tvBookshelfViewHistory.bindSoftPress()
        binding.tvBookshelfSort.setOnClickListener {
            showSortMenu(it)
        }
        binding.tvBookshelfEdit.setOnClickListener {
            openBookshelfManage()
        }
        binding.tvBookshelfViewHistory.setOnClickListener {
            startActivity<ReadRecordActivity>()
        }
        updateSortLabel()
        binding.viewPagerBookshelf.setEdgeEffectColor(primaryColor)
        tabLayout.isTabIndicatorFullWidth = false
        tabLayout.tabMode = TabLayout.MODE_SCROLLABLE
        tabLayout.setSelectedTabIndicatorColor(requireContext().accentColor)
        tabLayout.setupWithViewPager(binding.viewPagerBookshelf)
        binding.viewPagerBookshelf.offscreenPageLimit = 1
        binding.viewPagerBookshelf.adapter = adapter
        configureTopBarMode()
        applyBookshelfToolbarColors()
    }

    private fun configureTopBarMode() {
        val legacyTopBar = binding.titleBar.findViewById<View>(R.id.bookshelf_legacy_top_bar)
        val floatingDock = binding.titleBar.findViewById<ComposeView>(R.id.bookshelf_floating_dock)
        configuredTopBarStyle = AppConfig.bookshelfTopBarStyle
        if (configuredTopBarStyle == BookshelfTopBarStyle.TRADITIONAL) {
            legacyTopBar.visibility = View.VISIBLE
            floatingDock.visibility = View.GONE
            binding.btnBookshelfContentManage.visibility = View.GONE
            binding.btnBookshelfContentSort.visibility = View.GONE
            binding.btnBookshelfContentMore.visibility = View.GONE
            binding.tvBookshelfViewBooks.visibility = View.VISIBLE
            binding.tvBookshelfSort.visibility = View.VISIBLE
            binding.tvBookshelfEdit.visibility = View.VISIBLE
            binding.tvBookshelfViewBooks.setOnClickListener(null)
            binding.tvBookshelfViewBooks.setOnTouchListener(null)
            binding.tvBookshelfViewBooks.isClickable = false
            binding.tvBookshelfViewBooks.isFocusable = false
            binding.tvBookshelfViewBooks.setCompoundDrawablesRelative(null, null, null, null)
            return
        }
        legacyTopBar.visibility = View.GONE
        floatingDock.visibility = View.VISIBLE
        configureFloatingToolbar()
        updateFloatingDockSettings()
        floatingDock.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        floatingDock.setContent {
            NgAppTheme {
                BookshelfFloatingDock(
                    groups = dockGroups,
                    selectedIndex = dockSelectedIndex,
                    onSearchClick = {
                        SearchActivity.start(requireContext(), null)
                    },
                    onGroupClick = { index ->
                        tabLayout.getTabAt(index)?.select()
                    },
                    onGroupLongClick = { index ->
                        bookGroups.getOrNull(index)?.let { group ->
                            showDialogFragment(GroupEditDialog(group))
                        }
                    },
                    topDistancePx = dockTopDistancePx,
                    contentTopInsetPx = dockContentTopInsetPx,
                    transparencyPercent = dockTransparency,
                    searchPosition = dockSearchPosition
                )
            }
        }
    }

    private fun updateFloatingDockSettings() {
        val displayMetrics = resources.displayMetrics
        dockContentTopInsetPx = requireContext().statusBarHeight
        val topGapPx = BookshelfFloatingDockConfig.resolveTopDistancePx(
            storedDistancePx = AppConfig.bookshelfFloatingDockTopDistancePx,
            screenWidthPx = displayMetrics.widthPixels,
            density = displayMetrics.density,
            statusBarHeightPx = dockContentTopInsetPx
        )
        dockTopDistancePx = BookshelfFloatingDockConfig.screenTopDistancePx(
            topGapPx = topGapPx,
            statusBarHeightPx = dockContentTopInsetPx
        )
        dockTransparency = AppConfig.bookshelfFloatingDockTransparency
        dockSearchPosition = AppConfig.bookshelfFloatingDockSearchPosition
    }

    override fun onResume() {
        super.onResume()
        val appliedStyle = configuredTopBarStyle ?: return
        val currentStyle = AppConfig.bookshelfTopBarStyle
        if (currentStyle != appliedStyle) {
            activity?.recreate()
            return
        }
        if (currentStyle == BookshelfTopBarStyle.FLOATING_DOCK) {
            updateFloatingDockSettings()
        }
    }

    private fun configureFloatingToolbar() {
        binding.tvBookshelfViewHistory.visibility = View.GONE
        binding.bookshelfToolbarDivider.visibility = View.GONE
        binding.bookshelfContentToolbar.layoutParams =
            binding.bookshelfContentToolbar.layoutParams.apply {
                height = 40.dpToPx()
        }
        val actionBackgroundInset = 8.dpToPx()
        binding.tvBookshelfViewBooks.visibility = View.GONE
        binding.tvBookshelfSort.visibility = View.GONE
        binding.tvBookshelfEdit.visibility = View.GONE
        binding.btnBookshelfContentManage.apply {
            visibility = View.VISIBLE
            layoutParams = (layoutParams as LinearLayout.LayoutParams).apply {
                width = ViewGroup.LayoutParams.WRAP_CONTENT
                height = 40.dpToPx()
            }
            background = AppCompatResources.getDrawable(
                requireContext(),
                R.drawable.bg_bookshelf_compact_action
            )?.let { background ->
                InsetDrawable(
                    background,
                    0,
                    actionBackgroundInset,
                    0,
                    actionBackgroundInset
                )
            }
            bindSoftPress()
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    BookshelfContentToolbarActionButton(
                        iconRes = R.drawable.ic_settings,
                        labelRes = R.string.manage,
                        onClick = ::openBookshelfManage
                    )
                }
            }
        }
        binding.btnBookshelfContentSort.apply {
            visibility = View.VISIBLE
            layoutParams = (layoutParams as LinearLayout.LayoutParams).apply {
                width = ViewGroup.LayoutParams.WRAP_CONTENT
                height = 40.dpToPx()
            }
            background = AppCompatResources.getDrawable(
                requireContext(),
                R.drawable.bg_bookshelf_compact_action
            )?.let { background ->
                InsetDrawable(
                    background,
                    0,
                    actionBackgroundInset,
                    0,
                    actionBackgroundInset
                )
            }
            bindSoftPress()
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    BookshelfContentToolbarActionButton(
                        iconRes = R.drawable.ic_swap_vert,
                        labelRes = R.string.sort,
                        onClick = { showSortMenu(this@apply) }
                    )
                }
            }
        }
        binding.btnBookshelfContentMore.apply {
            visibility = View.VISIBLE
            layoutParams = (layoutParams as LinearLayout.LayoutParams).apply {
                height = 40.dpToPx()
                marginStart = 6.dpToPx()
            }
            background = AppCompatResources.getDrawable(
                requireContext(),
                R.drawable.bg_bookshelf_compact_action
            )?.let { background ->
                InsetDrawable(
                    background,
                    0,
                    actionBackgroundInset,
                    0,
                    actionBackgroundInset
                )
            }
            bindSoftPress()
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    BookshelfContentToolbarMenuButton(
                        onMenuItemClick = ::onBookshelfMenuItemClick
                    )
                }
            }
        }
        updateBookCountLabel()
    }

    private fun applyTopBarBackground(searchView: View, moreButton: View) {
        searchView.setBackgroundResource(R.drawable.bg_bookshelf_top_search)
        moreButton.setBackgroundResource(R.drawable.bg_bookshelf_top_action)
    }

    private fun applyBookshelfToolbarColors() {
        val snapshot = NgThemeResolver.resolve(requireContext())
        val topBarTextColor = if (
            AppConfig.bookshelfTopBarStyle == BookshelfTopBarStyle.FLOATING_DOCK
        ) {
            if (snapshot.isDark) {
                snapshot.colors.onSurface
            } else {
                ColorUtils.setAlphaComponent(snapshot.colors.onSurfaceVariant, 184)
            }
        } else {
            snapshot.colors.onTopBar
        }
        binding.tvBookshelfViewBooks.setTextColor(topBarTextColor)
        binding.tvBookshelfViewHistory.setTextColor(topBarTextColor)
        binding.tvBookshelfSort.setTextColor(topBarTextColor)
        binding.tvBookshelfEdit.setTextColor(topBarTextColor)
        TextViewCompat.setCompoundDrawableTintList(
            binding.tvBookshelfViewBooks,
            ColorStateList.valueOf(topBarTextColor)
        )
        TextViewCompat.setCompoundDrawableTintList(
            binding.tvBookshelfSort,
            ColorStateList.valueOf(topBarTextColor)
        )
        TextViewCompat.setCompoundDrawableTintList(
            binding.tvBookshelfEdit,
            ColorStateList.valueOf(topBarTextColor)
        )
    }

    internal fun onBookCountChanged(groupId: Long, _count: Int) {
        if (selectedGroup?.groupId == groupId) {
            updateBookCountLabel()
        }
    }

    private fun updateBookCountLabel() {
        if (AppConfig.bookshelfTopBarStyle != BookshelfTopBarStyle.FLOATING_DOCK) {
            binding.tvBookshelfViewBooks.setText(R.string.bookshelf)
            return
        }
        binding.tvBookshelfViewBooks.setText(R.string.manage)
    }

    private fun animateTopBarIn() {
        binding.titleBar.alpha = 0f
        binding.titleBar.translationY = (-4).dpToPx().toFloat()
        binding.titleBar.post {
            binding.titleBar.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220L)
                .start()
        }
    }

    private fun View.bindSoftPress() {
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate()
                        .scaleX(0.97f)
                        .scaleY(0.97f)
                        .alpha(0.92f)
                        .setDuration(90L)
                        .start()
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(140L)
                        .start()
                }
            }
            false
        }
    }

    private fun openBookshelfManage() {
        startActivity<BookshelfManageActivity> {
            putExtra("groupId", groupId)
        }
    }

    private fun showSortMenu(anchor: View) {
        val currentSort = currentBookSort()
        NgActionPopup(
            requireContext(),
            sortValues.map { sort ->
                NgActionPopupItem(
                    itemId = SORT_MENU_ID_OFFSET + sort,
                    title = getString(sortLabelRes(sort)),
                    iconRes = sortIconRes(sort),
                    checked = sort == currentSort,
                    payload = sort
                )
            },
            widthDp = 164
        ) { item ->
            (item.payload as? Int)?.let(::updateBookSort)
        }.show(
            anchor = anchor,
            marginDp = 2,
            verticalAnchorInsetDp = 8
        )
    }

    private fun updateBookSort(sort: Int) {
        selectedGroup?.let { group ->
            if (group.bookSort >= 0) {
                group.bookSort = sort
                appDb.bookGroupDao.update(group)
            } else {
                AppConfig.bookshelfSort = sort
            }
        } ?: run {
            AppConfig.bookshelfSort = sort
        }
        upSort()
        updateSortLabel()
    }

    private fun updateSortLabel() {
        binding.tvBookshelfSort.text = if (
            AppConfig.bookshelfTopBarStyle == BookshelfTopBarStyle.FLOATING_DOCK
        ) {
            getString(R.string.sort)
        } else {
            getString(sortLabelRes(currentBookSort()))
        }
    }

    private fun currentBookSort(): Int {
        return selectedGroup?.getRealBookSort() ?: AppConfig.bookshelfSort
    }

    private fun sortLabelRes(sort: Int): Int {
        return when (sort) {
            1 -> R.string.bookshelf_px_1
            2 -> R.string.bookshelf_px_2
            3 -> R.string.bookshelf_px_3
            4 -> R.string.bookshelf_px_4
            5 -> R.string.bookshelf_px_5
            else -> R.string.bookshelf_px_0
        }
    }

    private fun sortIconRes(sort: Int): Int {
        return when (sort) {
            0 -> R.drawable.ic_history
            1 -> R.drawable.ic_update
            2 -> R.drawable.ic_ai_capability_text
            3 -> R.drawable.ic_drag_handle
            5 -> R.drawable.ic_author
            else -> R.drawable.ic_baseline_sort_24
        }
    }

    private fun onBookshelfMenuItemClick(itemId: Int) {
        if (itemId == R.id.menu_read_record) {
            startActivity<ReadRecordActivity>()
        } else {
            handleBookshelfMenuItem(itemId)
        }
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        SearchActivity.start(requireContext(), query)
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        return false
    }

    @Synchronized
    override fun upGroup(data: List<BookGroup>) {
        if (data.isEmpty()) {
            appDb.bookGroupDao.enableGroup(BookGroup.IdAll)
        } else {
            if (data != bookGroups) {
                bookGroups.clear()
                bookGroups.addAll(data)
                dockGroups = data.map { group ->
                    BookshelfDockGroup(
                        groupId = group.groupId,
                        name = group.groupName,
                        cover = group.cover
                    )
                }
                adapter.notifyDataSetChanged()
                selectLastTab()
                tabLayout.post {
                    applyGroupTabViews()
                    updateGroupTabStyles()
                    updateSortLabel()
                }
                for (i in 0 until adapter.count) {
                    tabLayout.getTabAt(i)?.view?.setOnLongClickListener {
                        showDialogFragment(GroupEditDialog(bookGroups[i]))
                        true
                    }
                }
            }
        }
    }

    override fun upSort() {
        adapter.notifyDataSetChanged()
        updateSortLabel()
    }

    private fun selectLastTab() {
        tabLayout.post {
            tabLayout.removeOnTabSelectedListener(this)
            tabLayout.getTabAt(AppConfig.saveTabPosition)?.select()
            dockSelectedIndex = tabLayout.selectedTabPosition.coerceAtLeast(0)
            tabLayout.addOnTabSelectedListener(this)
            applyGroupTabViews()
            updateGroupTabStyles(animate = false)
            updateSortLabel()
            updateBookCountLabel()
        }
    }

    private fun applyGroupTabViews() {
        for (i in 0 until tabLayout.tabCount) {
            val tab = tabLayout.getTabAt(i) ?: continue
            val title = bookGroups.getOrNull(i)?.groupName ?: tab.text ?: ""
            val textView = tab.customView as? TextView ?: TextView(requireContext()).apply {
                gravity = Gravity.CENTER
                includeFontPadding = false
                isSingleLine = true
                setPadding(5.dpToPx(), 0, 5.dpToPx(), 0)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                tab.customView = this
            }
            textView.text = title
        }
    }

    private fun updateGroupTabStyles(animate: Boolean = true) {
        val selectedPosition = tabLayout.selectedTabPosition
        val topBarTextColor = NgThemeResolver.resolve(requireContext()).colors.onTopBar
        for (i in 0 until tabLayout.tabCount) {
            val textView = tabLayout.getTabAt(i)?.customView as? TextView ?: continue
            val selected = i == selectedPosition
            textView.setTextColor(topBarTextColor)
            textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            textView.typeface = Typeface.create(
                if (selected) "sans-serif-medium" else "sans-serif",
                if (selected) Typeface.BOLD else Typeface.NORMAL
            )
            val targetTranslationY = if (selected) (-1).dpToPx().toFloat() else 0f
            if (animate) {
                textView.animate()
                    .translationY(targetTranslationY)
                    .setDuration(160L)
                    .start()
            } else {
                textView.translationY = targetTranslationY
                textView.scaleX = 1f
                textView.scaleY = 1f
            }
        }
    }

    override fun onTabReselected(tab: TabLayout.Tab) {
        selectedGroup?.let { group ->
            fragmentMap[group.groupId]?.let {
                toastOnUi("${group.groupName}(${it.getBooksCount()})")
            }
        }
    }

    override fun onTabUnselected(tab: TabLayout.Tab) {
        updateGroupTabStyles()
    }

    override fun onTabSelected(tab: TabLayout.Tab) {
        AppConfig.saveTabPosition = tab.position
        dockSelectedIndex = tab.position
        updateGroupTabStyles()
        updateSortLabel()
        updateBookCountLabel()
    }

    override fun gotoTop() {
        fragmentMap[groupId]?.gotoTop()
    }

    private inner class TabFragmentPageAdapter(fm: FragmentManager) :
        FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getPageTitle(position: Int): CharSequence {
            return bookGroups[position].groupName
        }

        /**
         * 确定视图位置是否更改时调用
         * @return POSITION_NONE 已更改,刷新视图. POSITION_UNCHANGED 未更改,不刷新视图
         */
        override fun getItemPosition(any: Any): Int {
            val fragment = any as BooksFragment
            val position = fragment.position
            val group = bookGroups.getOrNull(position)
            if (fragment.groupId != group?.groupId) {
                return POSITION_NONE
            }
            val bookSort = group.getRealBookSort()
            fragment.setEnableRefresh(group.enableRefresh)
            if (fragment.bookSort != bookSort) {
                fragment.upBookSort(bookSort)
            }
            return POSITION_UNCHANGED
        }

        override fun getItem(position: Int): Fragment {
            val group = bookGroups[position]
            onlyUpdateRead = group.onlyUpdateRead
            return BooksFragment(position, group)
        }

        override fun getCount(): Int {
            return bookGroups.size
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            var fragment = super.instantiateItem(container, position) as BooksFragment
            val group = bookGroups[position]
            /**
             * Activity recreate 会复用之前的 Fragment，不正确的需要重新创建
             */
            if (fragment.isCreated && getItemPosition(fragment) == POSITION_NONE) {
                destroyItem(container, position, fragment)
                fragment = super.instantiateItem(container, position) as BooksFragment
            }
            fragmentMap[group.groupId] = fragment
            return fragment
        }

    }
}
