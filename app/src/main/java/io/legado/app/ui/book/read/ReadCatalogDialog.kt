package io.legado.app.ui.book.read

import android.content.DialogInterface
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.theme.NgAppTheme
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
                    onDismissRequest = { dismissAllowingStateLoss() },
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
            isDraggable = false
            isHideable = false
            skipCollapsed = true
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
    onDismissRequest: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(CatalogTab.Chapters) }
    var searchVisible by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var descending by remember { mutableStateOf(false) }
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
    val filteredBookmarks = remember(bookmarks, query) {
        bookmarks.filter {
            query.isBlank() || it.chapterName.contains(query, ignoreCase = true) ||
                it.bookText.contains(query, ignoreCase = true) ||
                it.content.contains(query, ignoreCase = true)
        }
    }
    val chapterListState = rememberLazyListState()
    val bookmarkListState = rememberLazyListState()
    LaunchedEffect(query, chapterCount) {
        visibleChapterCount = if (query.isBlank()) chapterCount else 0
    }

    NgGlassSurface(
        modifier = Modifier.fillMaxSize(),
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
            CatalogDragHandle(
                mutedColor = mutedColor,
                onDismissRequest = onDismissRequest,
            )
            if (searchVisible) {
                CatalogSearchField(
                    query = query,
                    hint = if (selectedTab == CatalogTab.Chapters) {
                        stringResource(R.string.read_catalog_search_chapters)
                    } else {
                        stringResource(R.string.read_catalog_search_bookmarks)
                    },
                    contentColor = contentColor,
                    mutedColor = mutedColor,
                    accentColor = accentColor,
                    dockColor = dockColor,
                    onQueryChange = { query = it },
                    onClose = {
                        query = ""
                        searchVisible = false
                    },
                )
            } else {
                CatalogTopActions(
                    contentColor = contentColor,
                    dockColor = dockColor,
                    onSearch = { searchVisible = true },
                )
            }
            Spacer(Modifier.height(4.dp))
            CatalogTabDock(
                selectedTab = selectedTab,
                contentColor = contentColor,
                accentColor = accentColor,
                selectedContentColor = selectedContentColor,
                dockColor = dockColor,
                onTabSelected = {
                    selectedTab = it
                    query = ""
                },
            )
            Spacer(Modifier.height(4.dp))
            CatalogSummaryRow(
                selectedTab = selectedTab,
                itemCount = if (selectedTab == CatalogTab.Chapters) {
                    visibleChapterCount
                } else {
                    filteredBookmarks.size
                },
                descending = descending,
                contentColor = contentColor,
                mutedColor = mutedColor,
                onSort = { descending = !descending },
            )
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = accentColor,
                        strokeWidth = 2.dp,
                    )
                }
            } else if (selectedTab == CatalogTab.Chapters) {
                CatalogChapterList(
                    chapterCount = chapterCount,
                    query = query,
                    descending = descending,
                    cachedChapterFiles = cachedChapterFiles,
                    isLocalBook = isLocalBook,
                    currentChapterIndex = currentChapterIndex,
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
                )
            }
        }
    }
}

@Composable
private fun CatalogDragHandle(
    mutedColor: Color,
    onDismissRequest: () -> Unit,
) {
    var draggedDownPx by remember { mutableStateOf(0f) }
    val dismissThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .pointerInput(dismissThresholdPx) {
                detectVerticalDragGestures(
                    onDragStart = { draggedDownPx = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        draggedDownPx = (draggedDownPx + dragAmount).coerceAtLeast(0f)
                    },
                    onDragCancel = { draggedDownPx = 0f },
                    onDragEnd = {
                        if (draggedDownPx >= dismissThresholdPx) {
                            onDismissRequest()
                        }
                        draggedDownPx = 0f
                    },
                )
            },
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
            .height(40.dp)
            .padding(horizontal = 20.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(40.dp)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(40.dp)
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
                    .clip(RoundedCornerShape(10.dp))
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
            .height(40.dp)
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
                .size(36.dp)
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
    itemCount: Int,
    descending: Boolean,
    contentColor: Color,
    mutedColor: Color,
    onSort: () -> Unit,
) {
    val listBackgroundColor = when {
        NgTheme.snapshot.isEInk -> Color(NgTheme.colors.inputContainer)
        NgTheme.snapshot.isDark -> Color(NgTheme.colors.surface)
        else -> mutedColor.copy(alpha = 0.035f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(
                if (selectedTab == CatalogTab.Chapters) listBackgroundColor
                else Color.Transparent
            )
            .padding(start = 24.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (selectedTab == CatalogTab.Chapters) {
                stringResource(R.string.all_chapter_num, itemCount)
            } else {
                stringResource(R.string.read_catalog_bookmark_count, itemCount)
            },
            color = mutedColor,
            fontSize = 13.sp,
        )
        Spacer(Modifier.weight(1f))
        if (selectedTab == CatalogTab.Chapters) {
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onSort),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(
                        if (descending) R.drawable.ic_catalog_sort_descending
                        else R.drawable.ic_catalog_sort_ascending
                    ),
                    contentDescription = stringResource(R.string.swap_sort),
                    modifier = Modifier.size(17.dp),
                    tint = contentColor,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(
                        if (descending) R.string.read_catalog_descending
                        else R.string.read_catalog_ascending
                    ),
                    color = contentColor,
                    fontSize = 13.sp,
                )
            }
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
        accentColor = accentColor,
    ) {
        val listBackgroundColor = when {
            NgTheme.snapshot.isEInk -> Color(NgTheme.colors.inputContainer)
            NgTheme.snapshot.isDark -> Color(NgTheme.colors.surface)
            else -> mutedColor.copy(alpha = 0.035f)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(listBackgroundColor),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 4.dp,
                end = 20.dp,
                bottom = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                        cached = isLocalBook || item.chapter.isVolume ||
                            cachedChapterFiles.contains(item.chapter.getFileName()),
                        contentColor = contentColor,
                        mutedColor = mutedColor,
                        accentColor = accentColor,
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
    cached: Boolean,
    contentColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val cardColor = if (NgTheme.snapshot.isDark) {
        Color(NgTheme.colors.surfaceContainerLow)
    } else {
        Color(NgTheme.colors.inputContainer)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (current) accentColor.copy(alpha = 0.12f)
                else cardColor
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        val wordCount = if (cached && AppConfig.tocCountWords) {
            item.chapter.wordCount?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.displayTitle,
                modifier = Modifier.weight(1f),
                color = if (current) accentColor else contentColor,
                fontSize = if (item.chapter.isVolume) 16.sp else 15.sp,
                fontWeight = if (current || item.chapter.isVolume) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!cached) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_outline_cloud_24),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = mutedColor.copy(alpha = 0.72f),
                )
            } else if (wordCount != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = wordCount,
                    color = if (current) accentColor else mutedColor.copy(alpha = 0.82f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
        val chapterTag = item.chapter.tag
            ?.takeIf { it.isNotBlank() }
            ?.let(::formatCatalogChapterTag)
        if (chapterTag != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = chapterTag,
                color = mutedColor.copy(alpha = 0.78f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CatalogChapterPlaceholder(mutedColor: Color) {
    val cardColor = if (NgTheme.snapshot.isDark) {
        Color(NgTheme.colors.surfaceContainerLow)
    } else {
        Color(NgTheme.colors.inputContainer)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.58f)
                .height(14.dp)
                .clip(CircleShape)
                .background(mutedColor.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.42f)
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
) {
    if (bookmarks.isEmpty()) {
        CatalogEmptyState(stringResource(R.string.read_catalog_no_bookmarks), mutedColor)
        return
    }
    val currentPosition = remember(bookmarks, currentChapterIndex) {
        bookmarks.indexOfLast { it.chapterIndex <= currentChapterIndex }.coerceAtLeast(0)
    }
    LaunchedEffect(bookmarks.isNotEmpty()) {
        listState.scrollToItem((currentPosition - 1).coerceAtLeast(0))
    }
    CatalogScrollableList(
        itemCount = bookmarks.size,
        listState = listState,
        accentColor = accentColor,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            items(bookmarks, key = { it.time }) { bookmark ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onBookmarkClick(bookmark) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = bookmark.chapterName,
                        color = if (bookmark.chapterIndex == currentChapterIndex) {
                            accentColor
                        } else {
                            contentColor
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val excerpt = bookmark.bookText.ifBlank { bookmark.content }
                    if (excerpt.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = excerpt.replace('\n', ' '),
                            color = mutedColor.copy(alpha = 0.82f),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = mutedColor.copy(alpha = 0.10f),
                )
            }
        }
    }
}

@Composable
private fun CatalogScrollableList(
    itemCount: Int,
    listState: LazyListState,
    accentColor: Color,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        CatalogFastScrollbar(
            itemCount = itemCount,
            listState = listState,
            accentColor = accentColor,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 2.dp)
                .width(20.dp)
                .fillMaxHeight(),
        )
        val firstVisible = listState.firstVisibleItemIndex
        val jumpToTop = firstVisible > itemCount / 2
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 10.dp)
                .size(40.dp)
                .clickable {
                    scope.launch {
                        listState.scrollToItem(
                            if (jumpToTop) 0 else (itemCount - 1).coerceAtLeast(0)
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (jumpToTop) {
                        Icons.Rounded.KeyboardArrowUp
                    } else {
                        Icons.Rounded.KeyboardArrowDown
                    },
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                    tint = accentColor,
                )
            }
        }
    }
}

@Composable
private fun CatalogFastScrollbar(
    itemCount: Int,
    listState: LazyListState,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    if (itemCount <= 1) return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var trackHeightPx by remember { mutableStateOf(0) }
    val thumbHeight = 48.dp
    val thumbHeightPx = with(density) { thumbHeight.toPx() }
    val maxOffsetPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
    val visibleCount = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    val maxFirstVisibleIndex = (itemCount - visibleCount).coerceAtLeast(1)
    val scrollFraction = listState.firstVisibleItemIndex.toFloat() /
        maxFirstVisibleIndex
    val jumpTo = { y: Float ->
        if (trackHeightPx > 0) {
            val fraction = ((y - thumbHeightPx / 2f) / maxOffsetPx.coerceAtLeast(1f))
                .coerceIn(0f, 1f)
            scope.launch { listState.scrollToItem((maxFirstVisibleIndex * fraction).toInt()) }
        }
    }
    Box(
        modifier = modifier
            .onSizeChanged { trackHeightPx = it.height }
            .pointerInput(itemCount, trackHeightPx, maxFirstVisibleIndex) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> jumpTo(offset.y) },
                    onVerticalDrag = { change, _ -> jumpTo(change.position.y) },
                )
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .width(2.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.09f)),
        )
        Box(
            modifier = Modifier
                .offset(y = with(density) { (maxOffsetPx * scrollFraction).toDp() })
                .width(4.dp)
                .height(thumbHeight)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.76f)),
        )
    }
}

@Composable
private fun CatalogEmptyState(text: String, color: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = color.copy(alpha = 0.72f), fontSize = 15.sp)
    }
}
