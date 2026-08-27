package io.legado.app.ui.book.toc

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.Bookmark
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgLazyListFastScroller
import io.legado.app.ui.design.components.compose.NgLazyListFastScrollerVariant
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.components.compose.NgSearchBarVariant
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal const val TOC_TAB_CHAPTERS = 0
internal const val TOC_TAB_BOOKMARKS = 1

internal data class TocChapterUiItem(
    val chapter: BookChapter,
    val displayTitle: String = chapter.title,
)

internal data class TocUiState(
    val book: Book? = null,
    val selectedTab: Int = TOC_TAB_CHAPTERS,
    val searchExpanded: Boolean = false,
    val query: String = "",
    val chapters: List<TocChapterUiItem> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val cachedFileNames: Set<String> = emptySet(),
    val chapterScrollIndex: Int = 0,
    val chapterScrollToken: Int = 0,
    val bookmarkScrollIndex: Int = 0,
    val bookmarkScrollToken: Int = 0,
    val useReplace: Boolean = false,
    val loadWordCount: Boolean = false,
    val splitLongChapter: Boolean = false,
    val isLocalTxt: Boolean = false,
    val tocCollapsed: Boolean = false,
    val tocStyle: TocStyle = TocStyle(),
)

internal data class TocStyle(
    val showOriginalIndex: Boolean = false,
    val titleMaxLines: Int = 1,
    val looseSpacing: Boolean = false,
    val infoDisplay: Int = TOC_INFO_DEFAULT,
    val infoBelowTitle: Boolean = false,
)

internal const val TOC_INFO_DEFAULT = -1
internal const val TOC_INFO_NONE = 0
internal const val TOC_INFO_WORD_COUNT = 1
internal const val TOC_INFO_PAGE = 2
internal const val TOC_INFO_PERCENT = 3
internal const val TOC_INFO_WORD_COUNT_AND_PAGE = 4

internal enum class TocMenuAction(val itemId: Int) {
    TocRegex(0x7501),
    SplitLongChapter(0x7502),
    ReverseToc(0x7503),
    UseReplace(0x7504),
    LoadWordCount(0x7505),
    ExportBookmark(0x7506),
    ExportMarkdown(0x7507),
    Log(0x7508),
    NetworkLog(0x7509),
    ToggleCollapsedToc(0x7510),
    TocStyle(0x7511),
    ;

    companion object {
        fun fromItemId(itemId: Int): TocMenuAction? = entries.firstOrNull {
            it.itemId == itemId
        }
    }
}

internal sealed interface TocUiEvent {
    data object Back : TocUiEvent
    data class TabChange(val tab: Int) : TocUiEvent
    data class SearchExpandedChange(val expanded: Boolean) : TocUiEvent
    data class QueryChange(val query: String) : TocUiEvent
    data class Menu(val action: TocMenuAction) : TocUiEvent
    data class TocStyleChange(val style: TocStyle) : TocUiEvent
    data class ChapterClick(val chapter: BookChapter) : TocUiEvent
    data class ChapterLongClick(val title: String) : TocUiEvent
    data class BookmarkClick(val bookmark: Bookmark) : TocUiEvent
    data class BookmarkLongClick(val bookmark: Bookmark, val position: Int) : TocUiEvent
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TocScreen(
    state: TocUiState,
    onEvent: (TocUiEvent) -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = state.selectedTab,
        pageCount = { 2 },
    )
    LaunchedEffect(state.selectedTab) {
        if (pagerState.currentPage != state.selectedTab) {
            pagerState.animateScrollToPage(state.selectedTab)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page != state.selectedTab) {
                    onEvent(TocUiEvent.TabChange(page))
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(NgTheme.colors.surface).copy(alpha = 0.54f)),
    ) {
        Column(Modifier.fillMaxSize()) {
            TocTopBar(state = state, onEvent = onEvent)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    TOC_TAB_BOOKMARKS -> TocBookmarkPage(state = state, onEvent = onEvent)
                    else -> TocChapterPage(state = state, onEvent = onEvent)
                }
            }
        }
    }
}

@Composable
private fun TocTopBar(
    state: TocUiState,
    onEvent: (TocUiEvent) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(NgTheme.colors.surface).copy(alpha = 0.26f))
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TocToolbarIcon(
            iconRes = R.drawable.ic_arrow_back,
            contentDescription = stringResource(R.string.back),
            onClick = { onEvent(TocUiEvent.Back) },
        )
        if (state.searchExpanded) {
            NgSearchBar(
                query = state.query,
                onQueryChange = { onEvent(TocUiEvent.QueryChange(it)) },
                hint = stringResource(R.string.search),
                variant = NgSearchBarVariant.TOOLBAR,
                onSearch = {},
                modifier = Modifier.weight(1f),
            )
            TocToolbarIcon(
                iconRes = R.drawable.ic_baseline_close,
                contentDescription = stringResource(R.string.close),
                onClick = { onEvent(TocUiEvent.SearchExpandedChange(false)) },
            )
        } else {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
            ) {
                TocTab(
                    title = stringResource(R.string.chapter_list),
                    selected = state.selectedTab == TOC_TAB_CHAPTERS,
                    onClick = { onEvent(TocUiEvent.TabChange(TOC_TAB_CHAPTERS)) },
                )
                TocTab(
                    title = stringResource(R.string.bookmark),
                    selected = state.selectedTab == TOC_TAB_BOOKMARKS,
                    onClick = { onEvent(TocUiEvent.TabChange(TOC_TAB_BOOKMARKS)) },
                )
            }
            TocToolbarIcon(
                iconRes = R.drawable.ic_search,
                contentDescription = stringResource(R.string.search),
                onClick = { onEvent(TocUiEvent.SearchExpandedChange(true)) },
            )
            TocMoreMenu(state = state, onEvent = onEvent)
        }
    }
}

@Composable
private fun TocTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            color = colorResource(R.color.primaryText),
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(2.dp)
                .background(
                    if (selected) Color(NgTheme.colors.primary) else Color.Transparent,
                ),
        )
    }
}

@Composable
private fun TocToolbarIcon(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = Color(NgTheme.colors.onSurface),
        )
    }
}

@Composable
private fun TocMoreMenu(
    state: TocUiState,
    onEvent: (TocUiEvent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showStyleDialog by remember { mutableStateOf(false) }
    Box {
        TocToolbarIcon(
            iconRes = R.drawable.ic_more_horiz,
            contentDescription = stringResource(R.string.more),
            onClick = { expanded = true },
        )
        NgExpandableActionMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            items = tocMenuItems(state),
            rowMinHeight = 44.dp,
            offset = DpOffset(0.dp, 4.dp),
            menuContainerColor = colorResource(R.color.ng_surface_card),
            properties = PopupProperties(focusable = true, clippingEnabled = false),
            onItemClick = { item ->
                expanded = false
                val action = TocMenuAction.fromItemId(item.itemId)
                when (action) {
                    TocMenuAction.TocStyle -> showStyleDialog = true
                    null -> Unit
                    else -> onEvent(TocUiEvent.Menu(action))
                }
            },
        )
    }
    if (showStyleDialog) {
        TocStyleDialog(
            style = state.tocStyle,
            onDismiss = { showStyleDialog = false },
            onConfirm = {
                onEvent(TocUiEvent.TocStyleChange(it))
                showStyleDialog = false
            },
        )
    }
}

@Composable
private fun tocMenuItems(state: TocUiState): List<NgExpandableActionMenuItem> = buildList {
    if (state.selectedTab == TOC_TAB_BOOKMARKS) {
        add(TocMenuAction.ExportBookmark.item(R.string.export, R.drawable.ic_export))
        add(TocMenuAction.ExportMarkdown.item(R.string.export_md, R.drawable.ic_code))
    } else {
        if (state.chapters.any { it.chapter.isVolume }) {
            add(
                TocMenuAction.ToggleCollapsedToc.item(
                    if (state.tocCollapsed) R.string.expand_toc else R.string.collapse_toc,
                    R.drawable.ic_catalog_sort_descending,
                ),
            )
        }
        add(
            TocMenuAction.ReverseToc.item(
                R.string.reverse_toc,
                R.drawable.ic_catalog_sort_descending,
                dividerBefore = isNotEmpty(),
            ),
        )
        add(
            TocMenuAction.TocStyle.item(
                R.string.toc_style,
                R.drawable.ic_cfg_about,
            ),
        )
        if (state.isLocalTxt) {
            add(TocMenuAction.TocRegex.item(R.string.txt_toc_rule, R.drawable.ic_code))
            add(
                TocMenuAction.SplitLongChapter.item(
                    R.string.split_long_chapter,
                    R.drawable.ic_chapter_list,
                    checked = state.splitLongChapter,
                ),
            )
        }
        add(
            TocMenuAction.UseReplace.item(
                R.string.use_replace,
                R.drawable.ic_find_replace,
                checked = state.useReplace,
            ),
        )
        add(
            TocMenuAction.LoadWordCount.item(
                R.string.load_word_count,
                R.drawable.ic_cfg_about,
                checked = state.loadWordCount,
            ),
        )
    }
    add(TocMenuAction.Log.item(R.string.log, R.drawable.ic_history, dividerBefore = true))
    add(
        TocMenuAction.NetworkLog.item(
            R.string.network_request_log,
            R.drawable.ic_network_check,
        ),
    )
}

private fun TocMenuAction.item(
    titleRes: Int,
    iconRes: Int,
    checked: Boolean = false,
    dividerBefore: Boolean = false,
): NgExpandableActionMenuItem = NgExpandableActionMenuItem(
    itemId = itemId,
    titleRes = titleRes,
    iconRes = iconRes,
    checked = checked,
    dividerBefore = dividerBefore,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TocChapterPage(
    state: TocUiState,
    onEvent: (TocUiEvent) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val chapters = remember(state.chapters, state.tocCollapsed) {
        if (!state.tocCollapsed) state.chapters else state.chapters.filterCollapsedToc()
    }
    LaunchedEffect(state.chapterScrollToken, state.tocCollapsed) {
        if (chapters.isNotEmpty()) {
            val currentIndex = chapters.indexOfLast {
                it.chapter.index < (state.book?.durChapterIndex ?: -1)
            }.coerceAtLeast(0)
            listState.scrollToItem(currentIndex)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = chapters,
                    key = { it.chapter.primaryStr() },
                ) { item ->
                    TocChapterRow(
                        item = item,
                        current = item.chapter.index == state.book?.durChapterIndex,
                        cached = state.book?.isLocal == true ||
                            item.chapter.isVolume ||
                            item.chapter.getFileName() in state.cachedFileNames,
                        showWordCount = state.loadWordCount,
                        style = state.tocStyle,
                        chapterCount = state.chapters.count { !it.chapter.isVolume },
                        onClick = { onEvent(TocUiEvent.ChapterClick(item.chapter)) },
                        onLongClick = {
                            onEvent(TocUiEvent.ChapterLongClick(item.displayTitle))
                        },
                    )
                }
            }
            NgLazyListFastScroller(
                state = listState,
                itemCount = chapters.size,
                variant = NgLazyListFastScrollerVariant.TRACK,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        TocChapterBottomBar(
            book = state.book,
            onCurrent = {
                if (chapters.isNotEmpty()) {
                    scope.launch {
                        val currentIndex = chapters.indexOfLast {
                            it.chapter.index < (state.book?.durChapterIndex ?: -1)
                        }.coerceAtLeast(0)
                        listState.scrollToItem(currentIndex)
                    }
                }
            },
            onTop = {
                if (chapters.isNotEmpty()) scope.launch { listState.scrollToItem(0) }
            },
            onBottom = {
                if (chapters.isNotEmpty()) {
                    scope.launch { listState.scrollToItem(chapters.lastIndex) }
                }
            },
        )
    }
}

private fun List<TocChapterUiItem>.filterCollapsedToc(): List<TocChapterUiItem> {
    var hasVolume = false
    return filter { item ->
        if (item.chapter.isVolume) {
            hasVolume = true
            true
        } else {
            !hasVolume
        }
    }
}

private fun BookChapter.tocInfoText(
    style: TocStyle,
    chapterCount: Int,
    showWordCount: Boolean,
): String? {
    if (isVolume) return null
    val wordCount = wordCount?.takeIf { showWordCount && it.isNotBlank() }
    val page = (index + 1).toString()
    val percent = if (chapterCount > 0) "${((index + 1) * 100 / chapterCount).coerceIn(0, 100)}%" else null
    return when (style.infoDisplay) {
        TOC_INFO_DEFAULT -> null
        TOC_INFO_NONE -> null
        TOC_INFO_WORD_COUNT -> wordCount
        TOC_INFO_PAGE -> page
        TOC_INFO_PERCENT -> percent
        TOC_INFO_WORD_COUNT_AND_PAGE -> listOfNotNull(wordCount, page).joinToString(" · ")
        else -> null
    }
}

@Composable
private fun TocStyleDialog(
    style: TocStyle,
    onDismiss: () -> Unit,
    onConfirm: (TocStyle) -> Unit,
) {
    var draft by remember(style) { mutableStateOf(style) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.toc_style)) },
        text = {
            Column {
                Text("调整目录标题、统计信息和显示密度。", color = colorResource(R.color.secondaryText))
                TocStyleSwitchRow(
                    title = "显示原始章节序号",
                    checked = draft.showOriginalIndex,
                    onCheckedChange = { draft = draft.copy(showOriginalIndex = it) },
                )
                Text("标题最大行数", modifier = Modifier.padding(top = 16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        draft = draft.copy(titleMaxLines = (draft.titleMaxLines - 1).coerceAtLeast(1))
                    }) { Text("−") }
                    Text(
                        text = draft.titleMaxLines.toString(),
                        modifier = Modifier.width(56.dp).wrapContentWidth(Alignment.CenterHorizontally),
                    )
                    OutlinedButton(onClick = {
                        draft = draft.copy(titleMaxLines = (draft.titleMaxLines + 1).coerceAtMost(3))
                    }) { Text("+") }
                }
                TocStyleSwitchRow(
                    title = "宽松模式",
                    checked = draft.looseSpacing,
                    onCheckedChange = { draft = draft.copy(looseSpacing = it) },
                )
                Text("信息显示", modifier = Modifier.padding(top = 16.dp))
                TocStyleChoiceRow(
                    labels = listOf("默认", "不显示", "字数", "页码", "百分比", "字数和页码"),
                    selected = draft.infoDisplay + 1,
                    onSelected = { draft = draft.copy(infoDisplay = it - 1) },
                )
                TocStyleSwitchRow(
                    title = "信息显示在标题下方",
                    checked = draft.infoBelowTitle,
                    onCheckedChange = { draft = draft.copy(infoBelowTitle = it) },
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(draft) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun TocStyleChoiceRow(
    labels: List<String>,
    selected: Int,
    onSelected: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        labels.forEachIndexed { index, label ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onSelected(index) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (index == selected) "✓" else "", modifier = Modifier.width(24.dp))
                Text(label)
            }
        }
    }
}

@Composable
private fun TocStyleSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TocChapterRow(
    item: TocChapterUiItem,
    current: Boolean,
    cached: Boolean,
    showWordCount: Boolean,
    style: TocStyle,
    chapterCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val chapter = item.chapter
    val infoText = chapter.tocInfoText(style, chapterCount, showWordCount)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (chapter.isVolume) colorResource(R.color.btn_bg_press)
                else Color.Transparent,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = if (style.looseSpacing) 18.dp else 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (chapter.isVip && !chapter.isPay) {
                Icon(
                    painter = painterResource(R.drawable.ic_lock_outline),
                    contentDescription = "VIP",
                    modifier = Modifier.size(16.dp),
                    tint = colorResource(R.color.secondaryText),
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        if (style.showOriginalIndex) append("${chapter.index + 1}. ")
                        append(item.displayTitle)
                    },
                    color = if (current) {
                        Color(NgTheme.colors.primary)
                    } else {
                        colorResource(R.color.primaryText)
                    },
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    maxLines = style.titleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
                val tag = chapter.tag?.takeIf { it.isNotBlank() }
                val wordCount = chapter.wordCount?.takeIf {
                    showWordCount && !chapter.isVolume && it.isNotBlank()
                }
                if (style.infoBelowTitle && infoText != null) {
                    Text(
                        text = infoText,
                        modifier = Modifier.padding(top = 4.dp),
                        color = colorResource(R.color.secondaryText),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                } else if (tag != null || wordCount != null) {
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        tag?.let {
                            Text(
                                text = it,
                                color = colorResource(R.color.secondaryText),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (tag != null && wordCount != null) Spacer(Modifier.width(18.dp))
                        wordCount?.let {
                            Text(
                                text = it,
                                color = colorResource(R.color.secondaryText),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            if (!style.infoBelowTitle && infoText != null) {
                Text(
                    text = infoText,
                    modifier = Modifier.padding(start = 12.dp),
                    color = colorResource(R.color.secondaryText),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                )
            }
            when {
                current -> Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.success),
                    modifier = Modifier.size(16.dp),
                    tint = colorResource(R.color.secondaryText),
                )
                !cached -> Icon(
                    painter = painterResource(R.drawable.ic_outline_cloud_24),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colorResource(R.color.secondaryText),
                )
            }
        }
    }
    HorizontalDivider(
        thickness = 0.6.dp,
        color = colorResource(R.color.bg_divider_line),
    )
}

@Composable
private fun TocChapterBottomBar(
    book: Book?,
    onCurrent: () -> Unit,
    onTop: () -> Unit,
    onBottom: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(NgTheme.colors.surface),
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(36.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = book?.let {
                    "${it.durChapterTitle}(${it.durChapterIndex + 1}/${it.simulatedTotalChapterNum()})"
                }.orEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClick = onCurrent)
                    .padding(horizontal = 10.dp),
                color = colorResource(R.color.primaryText),
                fontSize = 12.sp,
                lineHeight = 36.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TocBottomIcon(
                iconRes = R.drawable.ic_arrow_drop_up,
                contentDescription = stringResource(R.string.go_to_top),
                onClick = onTop,
            )
            TocBottomIcon(
                iconRes = R.drawable.ic_arrow_drop_down,
                contentDescription = stringResource(R.string.go_to_bottom),
                onClick = onBottom,
            )
        }
    }
}

@Composable
private fun TocBottomIcon(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(36.dp),
            tint = colorResource(R.color.primaryText),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TocBookmarkPage(
    state: TocUiState,
    onEvent: (TocUiEvent) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.bookmarkScrollToken) {
        if (state.bookmarks.isNotEmpty()) {
            listState.scrollToItem(state.bookmarkScrollIndex.coerceIn(state.bookmarks.indices))
        }
    }
    Box(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(
                items = state.bookmarks,
                key = { _, item -> item.time },
            ) { position, bookmark ->
                TocBookmarkRow(
                    bookmark = bookmark,
                    onClick = { onEvent(TocUiEvent.BookmarkClick(bookmark)) },
                    onLongClick = {
                        onEvent(TocUiEvent.BookmarkLongClick(bookmark, position))
                    },
                )
            }
        }
        NgLazyListFastScroller(
            state = listState,
            itemCount = state.bookmarks.size,
            variant = NgLazyListFastScrollerVariant.TRACK,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TocBookmarkRow(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
    ) {
        Text(
            text = bookmark.chapterName,
            modifier = Modifier.padding(4.dp),
            color = colorResource(R.color.primaryText),
            fontSize = 16.sp,
            lineHeight = 20.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        bookmark.bookText.takeIf { it.isNotEmpty() }?.let {
            Text(
                text = it,
                modifier = Modifier.padding(4.dp),
                color = colorResource(R.color.secondaryText),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        bookmark.content.takeIf { it.isNotEmpty() }?.let {
            Text(
                text = it,
                modifier = Modifier.padding(4.dp),
                color = colorResource(R.color.secondaryText),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    HorizontalDivider(
        thickness = 0.6.dp,
        color = colorResource(R.color.bg_divider_line),
    )
}
