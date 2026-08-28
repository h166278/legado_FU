package io.legado.app.ui.book.read

import android.content.DialogInterface
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.Bookmark
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuVariant
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuWidthVariant
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgLazyListFastScroller
import io.legado.app.ui.design.components.compose.NgLazyListFastScrollerVariant
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgColorMath
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReadCatalogDialog : BottomSheetDialogFragment() {

    private var chapterCount by mutableStateOf(0)
    private var bookmarks by mutableStateOf<List<Bookmark>>(emptyList())
    private var cachedChapterFiles by mutableStateOf<Set<String>>(emptySet())
    private var loading by mutableStateOf(true)
    private var bottomDialogRegistered = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(AndroidColor.TRANSPARENT)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val readActivity = activity as? ReadBookActivity ?: run {
            dismissAllowingStateLoss()
            return
        }
        val book = ReadBook.book ?: run {
            dismissAllowingStateLoss()
            return
        }
        if (!bottomDialogRegistered) {
            if (readActivity.bottomDialog > 0) {
                dismissAllowingStateLoss()
                return
            }
            readActivity.bottomDialog += 1
            bottomDialogRegistered = true
        }
        val snapshot = ReadDrawerStyle.themeSnapshot(requireContext())
        (view as ComposeView).setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                ReadCatalogPanel(
                    chapterCount = chapterCount,
                    bookmarks = bookmarks,
                    cachedChapterFiles = cachedChapterFiles,
                    isLocalBook = ReadBook.isLocalBook,
                    currentChapterIndex = ReadBook.durChapterIndex,
                    loading = loading,
                    loadChapterCount = { query ->
                        loadChapterCount(book.bookUrl, query)
                    },
                    loadChapterPosition = { descending, totalCount ->
                        loadChapterPosition(
                            bookUrl = book.bookUrl,
                            chapterIndex = ReadBook.durChapterIndex,
                            descending = descending,
                            totalCount = totalCount,
                        )
                    },
                    loadChapterPage = { query, descending, offset, limit ->
                        loadChapterPage(
                            bookUrl = book.bookUrl,
                            query = query,
                            descending = descending,
                            offset = offset,
                            limit = limit,
                        )
                    },
                    onChapterClick = { chapter ->
                        ReadBook.openChapter(chapter.index)
                        dismissAllowingStateLoss()
                    },
                    onBookmarkClick = { bookmark ->
                        ReadBook.openChapter(
                            bookmark.chapterIndex,
                            bookmark.chapterPos,
                        )
                        dismissAllowingStateLoss()
                    },
                    onBookmarkDelete = ::deleteBookmark,
                )
            }
        }
        loadCatalogData(book)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.18f }
            decorView.setPadding(0, 0, 0, 0)
        }
        val sheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        sheet.setBackgroundColor(AndroidColor.TRANSPARENT)
        sheet.layoutParams = sheet.layoutParams.apply {
            height = (resources.displayMetrics.heightPixels * 0.82f).toInt()
        }
        BottomSheetBehavior.from(sheet).apply {
            skipCollapsed = true
            isDraggable = true
            isDraggableOnNestedScroll = true
            isHideable = true
            state = BottomSheetBehavior.STATE_EXPANDED
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

    override fun show(manager: FragmentManager, tag: String?) {
        runCatching { super.show(manager, tag) }
            .onFailure { AppLog.put("显示阅读目录抽屉失败 tag:$tag", it) }
    }

    private fun loadCatalogData(book: Book) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    Triple(
                        appDb.bookChapterDao.getChapterCount(book.bookUrl),
                        appDb.bookmarkDao.getByBook(book.name, book.author),
                        BookHelp.getChapterFiles(book).toSet(),
                    )
                }
            }.onSuccess { (totalCount, bookmarkItems, cacheFiles) ->
                chapterCount = totalCount
                bookmarks = bookmarkItems
                cachedChapterFiles = cacheFiles
            }.onFailure {
                AppLog.put("阅读目录抽屉加载失败\n${it.localizedMessage}", it)
            }
            loading = false
        }
    }

    private suspend fun loadChapterCount(bookUrl: String, query: String): Int =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) {
                appDb.bookChapterDao.getChapterCount(bookUrl)
            } else {
                appDb.bookChapterDao.getChapterCount(bookUrl, query)
            }
        }

    private fun deleteBookmark(bookmark: Bookmark) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    appDb.bookmarkDao.delete(bookmark)
                }
            }.onSuccess {
                bookmarks = bookmarks.filterNot { it.time == bookmark.time }
            }.onFailure {
                AppLog.put("阅读目录抽屉删除书签失败\n${it.localizedMessage}", it)
            }
        }
    }

    private suspend fun loadChapterPosition(
        bookUrl: String,
        chapterIndex: Int,
        descending: Boolean,
        totalCount: Int,
    ): Int = withContext(Dispatchers.IO) {
        val ascendingPosition = appDb.bookChapterDao
            .getChapterPosition(bookUrl, chapterIndex)
            .coerceIn(0, (totalCount - 1).coerceAtLeast(0))
        if (descending) {
            (totalCount - 1 - ascendingPosition).coerceAtLeast(0)
        } else {
            ascendingPosition
        }
    }

    private suspend fun loadChapterPage(
        bookUrl: String,
        query: String,
        descending: Boolean,
        offset: Int,
        limit: Int,
    ): List<CatalogChapter> = withContext(Dispatchers.IO) {
        val chapters = when {
            query.isBlank() && descending -> appDb.bookChapterDao
                .getChapterPageDescending(bookUrl, offset, limit)

            query.isBlank() -> appDb.bookChapterDao.getChapterPage(bookUrl, offset, limit)
            descending -> appDb.bookChapterDao
                .searchPageDescending(bookUrl, query, offset, limit)

            else -> appDb.bookChapterDao.searchPage(bookUrl, query, offset, limit)
        }
        chapters.map { CatalogChapter(it, it.getDisplayTitle()) }
    }
}

private data class CatalogChapter(
    val chapter: BookChapter,
    val displayTitle: String,
)

private data class CatalogStyle(
    val showOriginalIndex: Boolean = false,
    val titleMaxLines: Int = 2,
    val looseSpacing: Boolean = true,
    val infoDisplay: Int = CATALOG_INFO_PAGE,
    val infoBelowTitle: Boolean = false,
)

private const val CATALOG_INFO_NONE = 0
private const val CATALOG_INFO_WORD_COUNT = 1
private const val CATALOG_INFO_PAGE = 2
private const val CATALOG_INFO_PERCENT = 3
private const val CATALOG_INFO_WORD_COUNT_AND_PAGE = 4

private enum class CatalogTab { Chapters, Bookmarks }

private const val CATALOG_PAGE_SIZE = 64
private const val CATALOG_PRELOAD_ITEMS = 16
private const val CATALOG_RETAINED_PAGE_RADIUS = 2
private val catalogUpdateTimeRegex = Regex(
    """(?:更新)?时间\s*[:：]\s*(\d{4}[-/.]\d{1,2}[-/.]\d{1,2}(?:\s+\d{1,2}:\d{2}(?::\d{2})?)?)"""
)
private val catalogSourceWordCountRegex = Regex(
    """(?:章节)?字数\s*[:：]\s*([0-9万千百.]+)\s*字?"""
)

@Composable
private fun ReadCatalogPanel(
    chapterCount: Int,
    bookmarks: List<Bookmark>,
    cachedChapterFiles: Set<String>,
    isLocalBook: Boolean,
    currentChapterIndex: Int,
    loading: Boolean,
    loadChapterCount: suspend (String) -> Int,
    loadChapterPosition: suspend (Boolean, Int) -> Int,
    loadChapterPage: suspend (String, Boolean, Int, Int) -> List<CatalogChapter>,
    onChapterClick: (BookChapter) -> Unit,
    onBookmarkClick: (Bookmark) -> Unit,
    onBookmarkDelete: (Bookmark) -> Unit,
) {
    var searchVisible by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var descending by remember { mutableStateOf(false) }
    var tocCollapsed by remember { mutableStateOf(false) }
    var menuVisible by remember { mutableStateOf(false) }
    var styleDialogVisible by remember { mutableStateOf(false) }
    var tocStyle by remember {
        mutableStateOf(
            CatalogStyle(
                showOriginalIndex = AppConfig.tocShowOriginalIndex,
                titleMaxLines = AppConfig.tocTitleMaxLines,
                looseSpacing = AppConfig.tocLooseSpacing,
                infoDisplay = AppConfig.tocInfoDisplay,
                infoBelowTitle = AppConfig.tocInfoBelowTitle,
            ),
        )
    }
    var visibleChapterCount by remember(chapterCount) { mutableStateOf(chapterCount) }
    val contentColor = Color(NgTheme.colors.onSurface)
    val mutedColor = Color(NgTheme.colors.onSurfaceVariant)
    val accentColor = Color(NgTheme.colors.primary)
    val selectedContentColor = Color(NgTheme.colors.onPrimary)
    val drawerSurfaceColor = Color(
        if (NgTheme.snapshot.isDark) NgTheme.colors.surface else NgTheme.colors.inputContainer
    )
    val dockColor = if (NgTheme.snapshot.isDark || NgTheme.snapshot.isEInk) {
        Color(NgTheme.colors.surfaceContainerLow)
    } else {
        contentColor.copy(alpha = 0.025f)
    }
    val listBackgroundColor = catalogListBackgroundColor(mutedColor)
    val filteredBookmarks = remember(bookmarks, query) {
        bookmarks.filter {
            query.isBlank() || it.chapterName.contains(query, ignoreCase = true) ||
                it.bookText.contains(query, ignoreCase = true) ||
                it.content.contains(query, ignoreCase = true)
        }
    }
    val chapterListState = rememberLazyListState()
    val bookmarkListState = rememberLazyListState()
    val pagerState = rememberPagerState(pageCount = { CatalogTab.entries.size })
    val pagerScope = rememberCoroutineScope()
    val selectedTab = CatalogTab.entries[pagerState.currentPage]
    val nestedScrollInteropConnection = rememberNestedScrollInteropConnection()
    LaunchedEffect(query, chapterCount) {
        visibleChapterCount = if (query.isBlank()) chapterCount else 0
    }
    LaunchedEffect(pagerState.currentPage) {
        query = ""
    }

    NgGlassSurface(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollInteropConnection),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        style = NgGlassDefaults.style(
            containerAlpha = 1f,
        ).copy(
            containerTop = drawerSurfaceColor,
            containerBottom = drawerSurfaceColor,
            accentGlow = Color.Transparent,
            surfaceGloss = Color.Transparent,
            depthEdge = Color.Transparent,
            shadowElevation = 0.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(top = 8.dp),
        ) {
            CatalogDragHandle(mutedColor = mutedColor)
            Spacer(Modifier.height(4.dp))
            CatalogTabDock(
                selectedTab = selectedTab,
                contentColor = contentColor,
                accentColor = accentColor,
                selectedContentColor = selectedContentColor,
                dockColor = dockColor,
                onTabSelected = {
                    query = ""
                    pagerScope.launch {
                        pagerState.animateScrollToPage(it.ordinal)
                    }
                },
            )
            Spacer(Modifier.height(4.dp))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val pageTab = CatalogTab.entries[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(listBackgroundColor),
                ) {
                    CatalogSummaryRow(
                        selectedTab = pageTab,
                        currentChapterIndex = currentChapterIndex,
                        chapterCount = chapterCount,
                        itemCount = if (pageTab == CatalogTab.Chapters) {
                            visibleChapterCount
                        } else {
                            filteredBookmarks.size
                        },
                        searchVisible = searchVisible,
                        query = query,
                        contentColor = contentColor,
                        mutedColor = mutedColor,
                        accentColor = accentColor,
                        dockColor = dockColor,
                        onQueryChange = { query = it },
                        onSearchToggle = { searchVisible = !searchVisible },
                        onMore = { menuVisible = true },
                        menuVisible = menuVisible,
                        tocExpanded = !tocCollapsed,
                        descending = descending,
                        onDismissMenu = { menuVisible = false },
                        onToggleCollapsed = {
                            tocCollapsed = !tocCollapsed
                            menuVisible = false
                        },
                        onToggleSort = {
                            descending = !descending
                            menuVisible = false
                        },
                        onStyle = {
                            menuVisible = false
                            styleDialogVisible = true
                        },
                    )
                    if (styleDialogVisible) {
                        CatalogStyleDialog(
                            style = tocStyle,
                            onDismiss = { styleDialogVisible = false },
                            onConfirm = { style ->
                                AppConfig.tocShowOriginalIndex = style.showOriginalIndex
                                AppConfig.tocTitleMaxLines = style.titleMaxLines
                                AppConfig.tocLooseSpacing = style.looseSpacing
                                AppConfig.tocInfoDisplay = style.infoDisplay
                                AppConfig.tocInfoBelowTitle = style.infoBelowTitle
                                tocStyle = style
                                styleDialogVisible = false
                            },
                        )
                    }
                    if (loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = accentColor,
                                strokeWidth = 2.dp,
                            )
                        }
                    } else if (pageTab == CatalogTab.Chapters) {
                        CatalogChapterList(
                            chapterCount = chapterCount,
                            query = query,
                            descending = descending,
                            cachedChapterFiles = cachedChapterFiles,
                            isLocalBook = isLocalBook,
                            currentChapterIndex = currentChapterIndex,
                            style = tocStyle,
                            listState = chapterListState,
                            contentColor = contentColor,
                            mutedColor = mutedColor,
                            accentColor = accentColor,
                            loadChapterCount = loadChapterCount,
                            loadChapterPosition = loadChapterPosition,
                            loadChapterPage = loadChapterPage,
                            onChapterCountChanged = { visibleChapterCount = it },
                            onChapterClick = onChapterClick,
                        )
                    } else {
                        CatalogBookmarkList(
                            bookmarks = filteredBookmarks,
                            currentChapterIndex = currentChapterIndex,
                            listState = bookmarkListState,
                            contentColor = contentColor,
                            mutedColor = mutedColor,
                            accentColor = accentColor,
                            onBookmarkClick = onBookmarkClick,
                            onBookmarkDelete = onBookmarkDelete,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogDragHandle(
    mutedColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(mutedColor.copy(alpha = 0.32f)),
        )
    }
}

@Composable
private fun CatalogTopActions(
    contentColor: Color,
    dockColor: Color,
    onSearch: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 20.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(44.dp)
                .clickable(onClick = onSearch),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(dockColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.search),
                    modifier = Modifier.size(20.dp),
                    tint = contentColor,
                )
            }
        }
    }
}

@Composable
private fun CatalogTabDock(
    selectedTab: CatalogTab,
    contentColor: Color,
    accentColor: Color,
    selectedContentColor: Color,
    dockColor: Color,
    onTabSelected: (CatalogTab) -> Unit,
) {
    val selectedShape = RoundedCornerShape(10.dp)
    val selectedShadowColor = Color.Black.copy(
        alpha = if (NgTheme.snapshot.isDark) 0.32f else 0.16f
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(dockColor),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CatalogTab.values().forEach { tab ->
            val selected = selectedTab == tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(3.dp)
                    .then(
                        if (selected && !NgTheme.snapshot.isEInk) {
                            Modifier.shadow(
                                elevation = 4.dp,
                                shape = selectedShape,
                                clip = false,
                                ambientColor = selectedShadowColor,
                                spotColor = selectedShadowColor,
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clip(selectedShape)
                    .background(
                        if (selected) accentColor.copy(alpha = 0.86f) else Color.Transparent
                    )
                    .clickable { onTabSelected(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(
                        if (tab == CatalogTab.Chapters) R.string.chapter_list else R.string.bookmark
                    ),
                    color = if (selected) selectedContentColor else contentColor,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun CatalogSearchField(
    query: String,
    hint: String,
    contentColor: Color,
    mutedColor: Color,
    accentColor: Color,
    dockColor: Color,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(48.dp)
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(dockColor)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = accentColor,
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            singleLine = true,
            textStyle = TextStyle(color = contentColor, fontSize = 14.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(text = hint, color = mutedColor.copy(alpha = 0.72f), fontSize = 14.sp)
                }
                inner()
            },
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable {
                    keyboard?.hide()
                    onClose()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.close),
                modifier = Modifier.size(18.dp),
                tint = mutedColor,
            )
        }
    }
}

@Composable
private fun CatalogSummaryRow(
    selectedTab: CatalogTab,
    currentChapterIndex: Int,
    chapterCount: Int,
    itemCount: Int,
    searchVisible: Boolean,
    query: String,
    contentColor: Color,
    mutedColor: Color,
    accentColor: Color,
    dockColor: Color,
    onQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    onMore: () -> Unit,
    menuVisible: Boolean,
    tocExpanded: Boolean,
    descending: Boolean,
    onDismissMenu: () -> Unit,
    onToggleCollapsed: () -> Unit,
    onToggleSort: () -> Unit,
    onStyle: () -> Unit,
) {
    val currentChapterNumber = if (chapterCount > 0) {
        (currentChapterIndex + 1).coerceIn(1, chapterCount)
    } else {
        0
    }
    val readingProgress = if (chapterCount > 0) {
        currentChapterNumber.toDouble() / chapterCount * 100
    } else {
        0.0
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (selectedTab == CatalogTab.Chapters) {
                stringResource(
                    R.string.read_catalog_reading_progress,
                    currentChapterNumber,
                    chapterCount,
                    readingProgress,
                )
            } else {
                stringResource(R.string.read_catalog_bookmark_count, itemCount)
            },
            color = mutedColor,
            fontSize = 13.sp,
        )
        Spacer(Modifier.weight(1f))
        if (selectedTab == CatalogTab.Chapters) {
            if (searchVisible) {
                CatalogInlineSearchField(
                    query = query,
                    contentColor = contentColor,
                    mutedColor = mutedColor,
                    accentColor = accentColor,
                    dockColor = dockColor,
                    onQueryChange = onQueryChange,
                    onClose = onSearchToggle,
                )
            } else {
                CatalogIconAction(
                    icon = Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.search),
                    contentColor = contentColor,
                    dockColor = dockColor,
                    onClick = onSearchToggle,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box {
                CatalogIconAction(
                    icon = null,
                    painter = painterResource(R.drawable.ic_more_horiz),
                    contentDescription = stringResource(R.string.more),
                    contentColor = contentColor,
                    dockColor = dockColor,
                    onClick = onMore,
                )
                if (menuVisible) {
                    CatalogMoreMenu(
                        expanded = tocExpanded,
                        descending = descending,
                        onDismiss = onDismissMenu,
                        onToggleCollapsed = onToggleCollapsed,
                        onToggleSort = onToggleSort,
                        onStyle = onStyle,
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    painter: androidx.compose.ui.graphics.painter.Painter? = null,
    contentDescription: String,
    contentColor: Color,
    dockColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(dockColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription, Modifier.size(20.dp), contentColor)
        } else if (painter != null) {
            Icon(painter, contentDescription, Modifier.size(20.dp), contentColor)
        }
    }
}

@Composable
private fun catalogMenuContainerColor(): Color =
    if (NgTheme.snapshot.isDark) Color(NgTheme.colors.surface) else Color(NgTheme.colors.inputContainer)

@Composable
private fun CatalogMoreMenu(
    expanded: Boolean,
    descending: Boolean,
    onDismiss: () -> Unit,
    onToggleCollapsed: () -> Unit,
    onToggleSort: () -> Unit,
    onStyle: () -> Unit,
) {
    NgExpandableActionMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        items = listOf(
            NgExpandableActionMenuItem(
                itemId = 0x7510,
                titleRes = if (expanded) R.string.expand_toc else R.string.collapse_toc,
                iconRes = if (expanded) R.drawable.ic_catalog_expand else R.drawable.ic_catalog_collapse,
            ),
            NgExpandableActionMenuItem(
                itemId = 0x7503,
                titleRes = if (descending) R.string.forward_toc else R.string.reverse_toc,
                iconRes = if (descending) R.drawable.ic_catalog_sort_ascending else R.drawable.ic_catalog_sort_descending,
            ),
            NgExpandableActionMenuItem(
                itemId = 0x7511,
                titleRes = R.string.toc_style,
                iconRes = R.drawable.ic_catalog_style,
                dividerBefore = true,
            ),
        ),
        onItemClick = { item ->
            when (item.itemId) {
                0x7510 -> onToggleCollapsed()
                0x7503 -> onToggleSort()
                0x7511 -> onStyle()
            }
        },
        variant = NgExpandableActionMenuVariant.DROPDOWN,
        widthVariant = NgExpandableActionMenuWidthVariant.GROUPED_LABELS,
        rowMinHeight = 44.dp,
        offset = DpOffset(0.dp, 4.dp),
        menuContainerColor = catalogMenuContainerColor(),
        properties = PopupProperties(focusable = true, clippingEnabled = false),
    )
}

@Composable
private fun CatalogStyleDialog(
    style: CatalogStyle,
    onDismiss: () -> Unit,
    onConfirm: (CatalogStyle) -> Unit,
) {
    var draft by remember(style) { mutableStateOf(style) }
    var densityOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var positionOpen by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .widthIn(max = 480.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        shape = RoundedCornerShape(16.dp),
        containerColor = catalogMenuContainerColor(),
        tonalElevation = 0.dp,
        text = {
            Column {
            Text(
                "目录样式",
                color = Color(NgTheme.colors.onSurface),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "调整目录的标题、统计信息和显示密度。",
                modifier = Modifier.padding(top = 6.dp),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 14.sp,
            )
            Text(
                "标题",
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(NgTheme.colors.surfaceContainerLow))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("显示原始章节序号", fontSize = 16.sp)
                        Text(
                            "按书籍原始章节顺序显示序号",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = Color(NgTheme.colors.onSurfaceVariant),
                        )
                    }
                    Switch(checked = draft.showOriginalIndex, onCheckedChange = { draft = draft.copy(showOriginalIndex = it) })
                }
                Text("标题最大行数", modifier = Modifier.padding(top = 16.dp), fontSize = 16.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "控制长标题在目录中最多显示几行",
                        Modifier.weight(1f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Color(NgTheme.colors.onSurfaceVariant),
                    )
                    CatalogStepperButton("−") {
                        draft = draft.copy(titleMaxLines = (draft.titleMaxLines - 1).coerceAtLeast(1))
                    }
                    Text(
                        draft.titleMaxLines.toString(),
                        modifier = Modifier.padding(horizontal = 14.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    CatalogStepperButton("+") {
                        draft = draft.copy(titleMaxLines = (draft.titleMaxLines + 1).coerceAtMost(3))
                    }
                }
                CatalogStyleChoice(
                    "排列密度",
                    "调整目录项的垂直留白",
                    if (draft.looseSpacing) "宽松模式" else "紧凑模式",
                    { densityOpen = true },
                )
                DropdownMenu(
                    densityOpen,
                    { densityOpen = false },
                    containerColor = catalogMenuContainerColor(),
                ) {
                    DropdownMenuItem({ Text("紧凑模式") }, { draft = draft.copy(looseSpacing = false); densityOpen = false })
                    DropdownMenuItem({ Text("宽松模式") }, { draft = draft.copy(looseSpacing = true); densityOpen = false })
                }
            }
            Text(
                "信息",
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(NgTheme.colors.surfaceContainerLow))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                CatalogStyleChoice(
                    "信息显示",
                    "选择标题旁展示的章节信息",
                    catalogInfoLabel(draft.infoDisplay),
                    { infoOpen = true },
                )
                DropdownMenu(
                    infoOpen,
                    { infoOpen = false },
                    containerColor = catalogMenuContainerColor(),
                ) {
                    listOf(
                        CATALOG_INFO_NONE to "不显示",
                        CATALOG_INFO_WORD_COUNT to "字数",
                        CATALOG_INFO_PAGE to "页码",
                        CATALOG_INFO_PERCENT to "百分比",
                        CATALOG_INFO_WORD_COUNT_AND_PAGE to "字数和页码",
                    ).forEach { (v, t) ->
                        DropdownMenuItem({ Text(t) }, { draft = draft.copy(infoDisplay = v); infoOpen = false })
                    }
                }
                CatalogStyleChoice(
                    "信息位置",
                    "选择信息显示在标题末尾或标题下方",
                    if (draft.infoBelowTitle) "标题下方" else "标题末尾",
                    { positionOpen = true },
                )
                DropdownMenu(
                    positionOpen,
                    { positionOpen = false },
                    containerColor = catalogMenuContainerColor(),
                ) {
                    DropdownMenuItem({ Text("标题末尾") }, { draft = draft.copy(infoBelowTitle = false); positionOpen = false })
                    DropdownMenuItem({ Text("标题下方") }, { draft = draft.copy(infoBelowTitle = true); positionOpen = false })
                }
            }
            }
        },
        confirmButton = {
            NgFormActionButton(
                text = "确定",
                onClick = { onConfirm(draft) },
                variant = NgButtonVariant.PRIMARY,
            )
        },
    )
}

@Composable
private fun CatalogStepperButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 32.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color(NgTheme.colors.outlineVariant), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 16.sp)
    }
}

@Composable
private fun CatalogStyleChoice(title: String, subtitle: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            Text(
                subtitle,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = Color(NgTheme.colors.onSurfaceVariant),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(value, fontSize = 15.sp)
        Text(" ›", fontSize = 18.sp, color = Color(NgTheme.colors.onSurfaceVariant))
    }
}

private fun catalogInfoLabel(value: Int) = when (value) { CATALOG_INFO_WORD_COUNT -> "字数"; CATALOG_INFO_PAGE -> "页码"; CATALOG_INFO_PERCENT -> "百分比"; CATALOG_INFO_WORD_COUNT_AND_PAGE -> "字数和页码"; else -> "不显示" }

@Composable
private fun CatalogCompactAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    painter: androidx.compose.ui.graphics.painter.Painter? = null,
    label: String,
    contentColor: Color,
    dockColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .width(96.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(dockColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(17.dp), tint = contentColor)
        } else if (painter != null) {
            Icon(painter, contentDescription = label, modifier = Modifier.size(17.dp), tint = contentColor)
        }
        Spacer(Modifier.width(4.dp))
        Text(text = label, color = contentColor, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun CatalogInlineSearchField(
    query: String,
    contentColor: Color,
    mutedColor: Color,
    accentColor: Color,
    dockColor: Color,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(40.dp)
            .width(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(dockColor)
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, Modifier.size(17.dp), accentColor)
        Spacer(Modifier.width(6.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(color = contentColor, fontSize = 13.sp),
            decorationBox = { inner ->
                if (query.isEmpty()) Text(stringResource(R.string.search), color = mutedColor, fontSize = 13.sp)
                inner()
            },
        )
        Box(
            modifier = Modifier.size(32.dp).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, stringResource(R.string.close), Modifier.size(16.dp), mutedColor)
        }
    }
}

@Composable
private fun CatalogChapterList(
    chapterCount: Int,
    query: String,
    descending: Boolean,
    cachedChapterFiles: Set<String>,
    isLocalBook: Boolean,
    currentChapterIndex: Int,
    style: CatalogStyle,
    listState: LazyListState,
    contentColor: Color,
    mutedColor: Color,
    accentColor: Color,
    loadChapterCount: suspend (String) -> Int,
    loadChapterPosition: suspend (Boolean, Int) -> Int,
    loadChapterPage: suspend (String, Boolean, Int, Int) -> List<CatalogChapter>,
    onChapterCountChanged: (Int) -> Unit,
    onChapterClick: (BookChapter) -> Unit,
) {
    var itemCount by remember(query, descending) { mutableStateOf(0) }
    var datasetLoading by remember(query, descending) { mutableStateOf(true) }
    val loadedPages = remember(query, descending) {
        mutableStateMapOf<Int, List<CatalogChapter>>()
    }
    LaunchedEffect(query, descending, chapterCount) {
        datasetLoading = true
        if (query.isNotBlank()) {
            delay(220)
        }
        val totalCount = if (query.isBlank()) chapterCount else loadChapterCount(query)
        itemCount = totalCount
        onChapterCountChanged(totalCount)
        if (totalCount > 0) {
            withFrameNanos { }
            val currentPosition = if (query.isBlank()) {
                loadChapterPosition(descending, totalCount)
            } else {
                0
            }
            listState.scrollToItem((currentPosition - 1).coerceAtLeast(0))
        }
        datasetLoading = false
    }
    LaunchedEffect(query, descending, itemCount) {
        if (itemCount <= 0) return@LaunchedEffect
        snapshotFlow {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val first = visibleItems.firstOrNull()?.index ?: listState.firstVisibleItemIndex
            val last = visibleItems.lastOrNull()?.index ?: first
            first to last
        }.distinctUntilChanged().collectLatest { (firstVisible, lastVisible) ->
            val preloadStart = (firstVisible - CATALOG_PRELOAD_ITEMS).coerceAtLeast(0)
            val preloadEnd = (lastVisible + CATALOG_PRELOAD_ITEMS)
                .coerceAtMost(itemCount - 1)
            val firstPage = preloadStart / CATALOG_PAGE_SIZE
            val lastPage = preloadEnd / CATALOG_PAGE_SIZE
            for (pageIndex in firstPage..lastPage) {
                if (loadedPages[pageIndex] == null) {
                    loadedPages[pageIndex] = loadChapterPage(
                        query,
                        descending,
                        pageIndex * CATALOG_PAGE_SIZE,
                        CATALOG_PAGE_SIZE,
                    )
                }
            }
            val centerPage = ((firstVisible + lastVisible) / 2) / CATALOG_PAGE_SIZE
            loadedPages.keys.toList()
                .filter { kotlin.math.abs(it - centerPage) > CATALOG_RETAINED_PAGE_RADIUS }
                .forEach(loadedPages::remove)
        }
    }
    if (itemCount == 0) {
        if (datasetLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    color = accentColor,
                    strokeWidth = 2.dp,
                )
            }
            return
        }
        CatalogEmptyState(stringResource(R.string.chapter_list_empty), mutedColor)
        return
    }
    CatalogScrollableList(
        itemCount = itemCount,
        listState = listState,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(catalogListBackgroundColor(mutedColor)),
            contentPadding = PaddingValues(
                start = 8.dp,
                top = 6.dp,
                end = 8.dp,
                bottom = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(count = itemCount, key = { it }) { position ->
                val pageIndex = position / CATALOG_PAGE_SIZE
                val pageOffset = position % CATALOG_PAGE_SIZE
                val item = loadedPages[pageIndex]?.getOrNull(pageOffset)
                if (item == null) {
                    CatalogChapterPlaceholder(mutedColor)
                } else {
                    CatalogChapterRow(
                        item = item,
                        current = item.chapter.index == currentChapterIndex,
                        style = style,
                        chapterCount = chapterCount,
                        cached = isLocalBook || item.chapter.isVolume ||
                            cachedChapterFiles.contains(item.chapter.getFileName()),
                        contentColor = contentColor,
                        mutedColor = mutedColor,
                        onClick = { onChapterClick(item.chapter) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogChapterRow(
    item: CatalogChapter,
    current: Boolean,
    style: CatalogStyle,
    chapterCount: Int,
    cached: Boolean,
    contentColor: Color,
    mutedColor: Color,
    onClick: () -> Unit,
) {
    val currentChapterColor = Color(NgTheme.colors.primary)
    val currentContainerColor = currentChapterColor.copy(alpha = 0.08f)
    val outlineColor = Color(NgTheme.colors.outlineVariant)
    val wordCount = if (cached && AppConfig.tocCountWords) {
        item.chapter.wordCount?.takeIf { it.isNotBlank() }
    } else {
        null
    }
    val infoText = when (style.infoDisplay) {
        CATALOG_INFO_WORD_COUNT -> wordCount
        CATALOG_INFO_PAGE -> (item.chapter.index + 1).toString()
        CATALOG_INFO_PERCENT -> if (chapterCount > 0) {
            "${((item.chapter.index + 1) * 100 / chapterCount).coerceIn(0, 100)}%"
        } else null
        CATALOG_INFO_WORD_COUNT_AND_PAGE -> listOfNotNull(
            wordCount,
            (item.chapter.index + 1).toString(),
        ).joinToString(" · ")
        else -> null
    }
    val chapterTag = item.chapter.tag
        ?.takeIf { it.isNotBlank() }
        ?.let(::formatCatalogChapterTag)
    if (item.chapter.isVolume) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 2.dp),
        ) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = outlineColor.copy(alpha = 0.45f),
            )
            Text(
                text = item.displayTitle,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                color = contentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .then(
                if (current) Modifier.background(currentContainerColor) else Modifier,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (current) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(currentChapterColor),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(
                    horizontal = 12.dp,
                    vertical = if (style.looseSpacing) 16.dp else 8.dp,
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = buildString {
                    if (style.showOriginalIndex) append("${item.chapter.index + 1}. ")
                    append(item.displayTitle)
                },
                modifier = Modifier.weight(1f),
                color = if (current) currentChapterColor else contentColor,
                fontSize = 15.sp,
                maxLines = style.titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (!style.infoBelowTitle && infoText != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = infoText,
                    color = if (current) currentChapterColor else mutedColor.copy(alpha = 0.82f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
            if (!cached) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_outline_cloud_24),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = mutedColor.copy(alpha = 0.72f),
                )
            }
        }
        if (style.infoBelowTitle && infoText != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = infoText,
                color = mutedColor.copy(alpha = 0.78f),
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        if (chapterTag != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = chapterTag,
                modifier = Modifier,
                color = mutedColor.copy(alpha = 0.78f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    }
    HorizontalDivider(
        thickness = 1.dp,
        color = outlineColor.copy(alpha = 0.52f),
    )
}

@Composable
private fun CatalogChapterPlaceholder(mutedColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .clip(CircleShape)
                .background(mutedColor.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(10.dp)
                .clip(CircleShape)
                .background(mutedColor.copy(alpha = 0.05f)),
        )
    }
}

private fun formatCatalogChapterTag(tag: String): String {
    val updateTime = catalogUpdateTimeRegex.find(tag)?.groupValues?.getOrNull(1)?.trim()
    val sourceWordCount = catalogSourceWordCountRegex.find(tag)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
    if (updateTime == null && sourceWordCount == null) return tag
    return listOfNotNull(
        updateTime,
        sourceWordCount?.let { "字数：$it" },
    ).joinToString("  ")
}

@Composable
private fun CatalogBookmarkList(
    bookmarks: List<Bookmark>,
    currentChapterIndex: Int,
    listState: LazyListState,
    contentColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onBookmarkClick: (Bookmark) -> Unit,
    onBookmarkDelete: (Bookmark) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        CatalogEmptyState(stringResource(R.string.read_catalog_no_bookmarks), mutedColor)
        return
    }
    val currentPosition = remember(bookmarks, currentChapterIndex) {
        bookmarks.indexOfLast { it.chapterIndex <= currentChapterIndex }.coerceAtLeast(0)
    }
    var pendingDeleteTime by remember { mutableStateOf<Long?>(null) }
    val expandedNoteTimes = remember { mutableStateMapOf<Long, Boolean>() }
    LaunchedEffect(bookmarks.isNotEmpty()) {
        listState.scrollToItem((currentPosition - 1).coerceAtLeast(0))
    }
    CatalogScrollableList(
        itemCount = bookmarks.size,
        listState = listState,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(catalogListBackgroundColor(mutedColor)),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 6.dp,
                end = 12.dp,
                bottom = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(bookmarks, key = { it.time }) { bookmark ->
                val deleteConfirmationVisible = pendingDeleteTime == bookmark.time
                CatalogBookmarkCard(
                    bookmark = bookmark,
                    deleteConfirmationVisible = deleteConfirmationVisible,
                    noteExpanded = expandedNoteTimes[bookmark.time] == true,
                    contentColor = contentColor,
                    mutedColor = mutedColor,
                    accentColor = accentColor,
                    onClick = { onBookmarkClick(bookmark) },
                    onLongClick = { pendingDeleteTime = bookmark.time },
                    onNoteToggle = {
                        expandedNoteTimes[bookmark.time] =
                            expandedNoteTimes[bookmark.time] != true
                    },
                    onDeleteCancel = { pendingDeleteTime = null },
                    onDeleteConfirm = {
                        pendingDeleteTime = null
                        onBookmarkDelete(bookmark)
                    },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CatalogBookmarkCard(
    bookmark: Bookmark,
    deleteConfirmationVisible: Boolean,
    noteExpanded: Boolean,
    contentColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onNoteToggle: () -> Unit,
    onDeleteCancel: () -> Unit,
    onDeleteConfirm: () -> Unit,
) {
    val cardColor = catalogCardColor()
    val errorColor = Color(NgTheme.colors.error)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor)
            .combinedClickable(
                onClick = onClick,
                onLongClickLabel = stringResource(R.string.delete),
                onLongClick = onLongClick,
            )
            .padding(start = 12.dp, top = 6.dp, end = 8.dp, bottom = 6.dp),
    ) {
        Text(
            text = bookmark.chapterName,
            modifier = Modifier.fillMaxWidth(),
            color = contentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (bookmark.bookText.isNotBlank()) {
            Text(
                text = bookmark.bookText.replace('\n', ' '),
                modifier = Modifier.padding(end = 4.dp),
                color = mutedColor.copy(alpha = 0.84f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (bookmark.content.isNotBlank() || deleteConfirmationVisible) {
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp, end = 4.dp),
                thickness = 0.5.dp,
                color = mutedColor.copy(alpha = 0.16f),
            )
            if (deleteConfirmationVisible) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.read_catalog_delete_bookmark_confirmation),
                        modifier = Modifier.weight(1f),
                        color = mutedColor,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    CatalogBookmarkInlineAction(
                        text = stringResource(R.string.cancel),
                        color = mutedColor,
                        onClick = onDeleteCancel,
                    )
                    CatalogBookmarkInlineAction(
                        text = stringResource(R.string.delete),
                        color = errorColor,
                        onClick = onDeleteConfirm,
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clickable(onClick = onNoteToggle)
                        .padding(start = 2.dp, end = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ai_chat_suggestion),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = accentColor,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.bookmark_note),
                        color = Color(NgTheme.colors.secondary),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = if (noteExpanded) {
                            Icons.Rounded.KeyboardArrowUp
                        } else {
                            Icons.Rounded.KeyboardArrowDown
                        },
                        contentDescription = stringResource(
                            if (noteExpanded) {
                                R.string.read_catalog_collapse_note
                            } else {
                                R.string.read_catalog_expand_note
                            }
                        ),
                        modifier = Modifier.size(15.dp),
                        tint = mutedColor,
                    )
                }
                if (noteExpanded) {
                    Text(
                        text = bookmark.content.trim(),
                        modifier = Modifier.padding(start = 20.dp, end = 8.dp, bottom = 6.dp),
                        color = contentColor.copy(alpha = 0.88f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogBookmarkInlineAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun catalogListBackgroundColor(mutedColor: Color): Color = when {
    NgTheme.snapshot.isEInk -> Color(NgTheme.colors.inputContainer)
    NgTheme.snapshot.isDark -> Color(NgTheme.colors.surface)
    else -> mutedColor.copy(alpha = 0.035f)
}

@Composable
private fun catalogCardColor(): Color = if (NgTheme.snapshot.isDark) {
    Color(NgTheme.colors.surfaceContainerLow)
} else {
    Color(NgTheme.colors.inputContainer)
}

@Composable
private fun CatalogScrollableList(
    itemCount: Int,
    listState: LazyListState,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        NgLazyListFastScroller(
            state = listState,
            itemCount = itemCount,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
            variant = NgLazyListFastScrollerVariant.FLOATING_HANDLE,
        )
    }
}

@Composable
private fun CatalogEmptyState(text: String, color: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = color.copy(alpha = 0.72f), fontSize = 15.sp)
    }
}
