package io.legado.app.ui.book.explore

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.entities.rule.ExploreKind.Type
import io.legado.app.ui.book.search.SearchBookCover
import io.legado.app.ui.book.search.SearchResultCard
import io.legado.app.ui.design.components.compose.NgPullRefreshBox
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.login.SourceLoginJsExtensions
import io.legado.app.ui.main.explore.ExploreInfoStore
import io.legado.app.ui.main.explore.ExploreKindItem
import io.legado.app.ui.main.explore.rememberExploreKindLabel
import io.legado.app.ui.main.explore.sourceTileColor
import io.legado.app.ui.main.explore.sourceTileContentColor
import io.legado.app.utils.InfoMap
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val ExploreCategoryTileHeight = 62.dp
private val ExploreCategoryRowSpacing = 6.dp
private val ExploreCategoryViewportHeight =
    ExploreCategoryTileHeight * 2f + ExploreCategoryRowSpacing

@Composable
internal fun ExploreShowScreen(
    state: ExploreShowUiState,
    layoutMode: ExploreShowLayoutMode,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshKinds: () -> Unit,
    onSelectKind: (ExploreKind) -> Unit,
    onLayoutModeChange: (ExploreShowLayoutMode) -> Unit,
    onSelectPage: () -> Unit,
    onLoadPrevious: () -> Unit,
    onLoadNext: () -> Unit,
    onRetryContent: () -> Unit,
    onOpenBook: (SearchBook) -> Unit,
    onShowError: (String) -> Unit
) {
    val kindSections = remember(state.kinds) { buildExploreKindSections(state.kinds) }
    val activeSectionIndex = kindSections.sectionIndexFor(state.selectedKind)
    val visibleSections =
        if (kindSections.useTopLevelGroups) {
            kindSections.sections.getOrNull(activeSectionIndex)?.let(::listOf).orEmpty()
        } else {
            kindSections.sections
        }
    val sectionRows = visibleSections.map { section ->
        section to calculateExploreDetailKindRows(section.items)
    }
    val controlRows = calculateExploreDetailKindRows(kindSections.controls)
    val gridState = rememberLazyGridState()
    val columns = if (layoutMode == ExploreShowLayoutMode.LIST) 1 else 3
    val resultBackground = colorResource(R.color.ng_explore_result_background)
    val shouldLoadNext by remember(gridState, state.books, state.hasMore, state.isContentLoading) {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            state.books.isNotEmpty() &&
                    state.hasMore &&
                    !state.isContentLoading &&
                    lastVisible >= gridState.layoutInfo.totalItemsCount - 4
        }
    }

    LaunchedEffect(shouldLoadNext) {
        if (shouldLoadNext) onLoadNext()
    }
    LaunchedEffect(state.selectionRevision) {
        if (state.selectionRevision > 0) {
            gridState.scrollToItem(0)
        }
    }

    NgPullRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.ng_background))
            .statusBarsPadding(),
        enabled = !state.isKindsLoading,
        showIndicator = false
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ExploreShowTopBar(
                sourceName = state.sourceName,
                isLoading = state.isKindsLoading && !state.isRefreshing,
                onBack = onBack,
                onRefresh = onRefresh
            )

            ExploreCategoryPanel(
                state = state,
                kindSections = kindSections,
                sectionRows = sectionRows,
                controlRows = controlRows,
                activeSectionIndex = activeSectionIndex,
                onSelectKind = onSelectKind,
                onRefreshKinds = onRefreshKinds,
                onShowError = onShowError
            )

            ExploreContentToolbar(
                selectedKindLabel = state.selectedKind?.title
                    ?.let(::sanitizeExploreDetailLabel)
                    .orEmpty(),
                page = state.displayPage,
                layoutMode = layoutMode,
                onSelectPage = onSelectPage,
                onLayoutModeChange = onLayoutModeChange
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier
                    .weight(1f)
                    .background(resultBackground)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    top = 8.dp,
                    end = 8.dp,
                    bottom = 24.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            if (state.firstLoadedPage > 1) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ExplorePreviousPageRow(
                        isLoading = state.isLoadingPrevious,
                        onClick = onLoadPrevious
                    )
                }
            }

            if (layoutMode == ExploreShowLayoutMode.LIST) {
                items(
                    items = state.books,
                    key = { "list_${it.bookUrl}" },
                    span = { GridItemSpan(maxLineSpan) }
                ) { book ->
                    SearchResultCard(
                        book = book,
                        inBookshelf = state.isBookInShelf(book),
                        originCount = 0,
                        onClick = { onOpenBook(book) },
                        onLongClick = { onOpenBook(book) },
                        outerHorizontalPadding = 0.dp,
                        outerVerticalPadding = 0.dp,
                        cardCornerRadius = 10.dp,
                        cardHeight = 120.dp,
                        cardContentPadding = 8.dp,
                        coverWidth = 68.dp,
                        coverHeight = 92.dp,
                        contentStartPadding = 78.dp,
                        cardBackgroundColorRes = R.color.ng_surface,
                        cardBorderWidth = 0.dp
                    )
                }
            } else {
                items(
                    items = state.books,
                    key = { "grid_${it.bookUrl}" }
                ) { book ->
                    ExploreBookGridCard(
                        book = book,
                        inBookshelf = state.isBookInShelf(book),
                        onClick = { onOpenBook(book) }
                    )
                }
            }

            when {
                state.isContentLoading && !state.isLoadingPrevious -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ExploreStatusRow(text = stringResource(R.string.is_loading), loading = true)
                    }
                }

                state.contentError != null -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ExploreContentErrorRow(
                            error = state.contentError,
                            onRetry = onRetryContent,
                            onShowError = onShowError
                        )
                    }
                }

                state.selectedKind == null && !state.isKindsLoading -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ExploreStatusRow(stringResource(R.string.explore_category_empty))
                    }
                }

                state.books.isEmpty() && state.selectedKind != null -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ExploreStatusRow(stringResource(R.string.empty))
                    }
                }

                !state.hasMore -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ExploreStatusRow(stringResource(R.string.explore_no_more))
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun ExploreCategoryPanel(
    state: ExploreShowUiState,
    kindSections: ExploreKindSections,
    sectionRows: List<Pair<ExploreKindSection, List<List<Pair<ExploreKind, Int>>>>>,
    controlRows: List<List<Pair<ExploreKind, Int>>>,
    activeSectionIndex: Int,
    onSelectKind: (ExploreKind) -> Unit,
    onRefreshKinds: () -> Unit,
    onShowError: (String) -> Unit
) {
    val fallbackLabel = stringResource(R.string.explore_category_default)
    val ungroupedLabel = stringResource(R.string.no_group)
    val scrollState = rememberScrollState()
    val scrollThumbColor = colorResource(R.color.secondaryText).copy(alpha = 0.32f)
    val rowSnapStepPx = with(LocalDensity.current) {
        (ExploreCategoryTileHeight + ExploreCategoryRowSpacing).roundToPx()
    }
    val shouldSnapToRows = remember(
        sectionRows,
        controlRows,
        kindSections.useTopLevelGroups
    ) {
        controlRows.isEmpty() && sectionRows.all { (section, rows) ->
            (kindSections.useTopLevelGroups || section.header == null) &&
                    rows.flatten().all { (kind, _) ->
                        kind.type == Type.url && !kind.url.isNullOrBlank()
                    }
        }
    }
    LaunchedEffect(activeSectionIndex, state.kinds) {
        scrollState.scrollTo(0)
    }
    LaunchedEffect(scrollState, shouldSnapToRows, rowSnapStepPx) {
        snapshotFlow { scrollState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (!isScrolling && shouldSnapToRows && scrollState.maxValue > 0) {
                    val nearestRow = (scrollState.value + rowSnapStepPx / 2) / rowSnapStepPx
                    val target = (nearestRow * rowSnapStepPx)
                        .coerceIn(0, scrollState.maxValue)
                    if (target != scrollState.value) {
                        scrollState.animateScrollTo(target)
                    }
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorResource(R.color.ng_explore_result_background))
            .padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
    ) {
        if (kindSections.useTopLevelGroups) {
            val sectionLabels = kindSections.sections.map { section ->
                section.header?.let { header ->
                    sanitizeExploreDetailLabel(header.displaySectionLabel())
                        .ifBlank { fallbackLabel }
                } ?: ungroupedLabel
            }
            ExploreSectionTabs(
                labels = sectionLabels,
                selectedIndex = activeSectionIndex,
                onSelectSection = { index ->
                    kindSections.kindForSectionSelection(index, state.selectedKind)
                        ?.takeIf { nextKind -> nextKind != state.selectedKind }
                        ?.let(onSelectKind)
                }
            )
            Spacer(Modifier.height(4.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = ExploreCategoryViewportHeight)
                .clipToBounds()
                .drawWithContent {
                    drawContent()
                    if (scrollState.maxValue > 0 && size.height > 0f) {
                        val thumbWidth = 3.dp.toPx()
                        val thumbHeight = 40.dp.toPx().coerceAtMost(size.height)
                        val progress = scrollState.value.toFloat() / scrollState.maxValue.toFloat()
                        val offsetY = (size.height - thumbHeight) * progress
                        drawRoundRect(
                            color = scrollThumbColor,
                            topLeft = Offset(size.width - thumbWidth, offsetY),
                            size = Size(thumbWidth, thumbHeight),
                            cornerRadius = CornerRadius(thumbWidth / 2f)
                        )
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = ExploreCategoryViewportHeight)
                    .verticalScroll(scrollState)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(ExploreCategoryRowSpacing)
            ) {
                controlRows.forEach { row ->
                    ExploreCategoryRow(
                        row = row,
                        source = state.source,
                        selectedKind = state.selectedKind,
                        onSelectKind = onSelectKind,
                        onRefreshKinds = onRefreshKinds,
                        onShowError = onShowError
                    )
                }

                sectionRows.forEach { (_, rows) ->
                    rows.forEach { row ->
                        ExploreCategoryRow(
                            row = row,
                            source = state.source,
                            selectedKind = state.selectedKind,
                            onSelectKind = onSelectKind,
                            onRefreshKinds = onRefreshKinds,
                            onShowError = onShowError
                        )
                    }
                }

                if (state.isKindsLoading && state.kinds.isEmpty()) {
                    ExploreStatusRow(text = stringResource(R.string.is_loading), loading = true)
                }

                state.kindsError?.let { error ->
                    ExploreErrorRow(error = error, onShowError = onShowError)
                }
            }

        }
    }
}

private fun ExploreShowUiState.isBookInShelf(book: SearchBook): Boolean {
    return "${book.name}-${book.author}" in bookshelfKeys ||
            book.name in bookshelfKeys ||
            book.bookUrl in bookshelfKeys
}

@Composable
private fun ExploreShowTopBar(
    sourceName: String,
    isLoading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    val contentColor = colorResource(R.color.primaryText)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorResource(R.color.ng_explore_result_background))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .background(colorResource(R.color.ng_surface))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            ExploreToolbarButton(
                iconRes = R.drawable.ic_arrow_back,
                description = stringResource(R.string.back),
                tint = contentColor,
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Text(
                text = sourceName,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 46.dp),
                color = contentColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colorResource(R.color.ng_surface_card)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = contentColor
                    )
                }
            } else {
                ExploreToolbarButton(
                    iconRes = R.drawable.ic_refresh_black_24dp,
                    description = stringResource(R.string.refresh_sort),
                    tint = contentColor,
                    onClick = onRefresh,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}

@Composable
private fun ExploreToolbarButton(
    iconRes: Int,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colorResource(R.color.ng_surface_card))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            modifier = Modifier.size(18.dp),
            tint = tint
        )
    }
}

@Composable
private fun ExploreSectionTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelectSection: (Int) -> Unit
) {
    val primary = Color(NgTheme.colors.primary)
    val listState = rememberLazyListState()
    LaunchedEffect(labels, selectedIndex) {
        if (labels.size > 1 && selectedIndex in labels.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(colorResource(R.color.ng_surface_card)),
        contentPadding = PaddingValues(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(
            items = labels,
            key = { index, label -> "$index:$label" }
        ) { index, label ->
            val selected = index == selectedIndex
            Text(
                text = label,
                modifier = (if (labels.size == 1) {
                    Modifier.fillParentMaxWidth()
                } else {
                    Modifier.widthIn(min = 84.dp, max = 160.dp)
                })
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) primary.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelectSection(index) }
                    .padding(horizontal = 14.dp, vertical = 5.dp),
                color = if (selected) primary else colorResource(R.color.primaryText),
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ExploreCategoryRow(
    row: List<Pair<ExploreKind, Int>>,
    source: BookSource?,
    selectedKind: ExploreKind?,
    onSelectKind: (ExploreKind) -> Unit,
    onRefreshKinds: () -> Unit,
    onShowError: (String) -> Unit
) {
    val fallbackLabel = stringResource(R.string.explore_category_default)
    val context = androidx.compose.ui.platform.LocalContext.current
    val infoMap = remember(source?.bookSourceUrl) {
        val sourceUrl = source?.bookSourceUrl.orEmpty()
        ExploreInfoStore.infoMapList[sourceUrl] ?: InfoMap(sourceUrl).also {
            ExploreInfoStore.infoMapList.put(sourceUrl, it)
        }
    }
    val scope = rememberCoroutineScope()
    val sourceJsExtensions = remember(source, infoMap) {
        SourceLoginJsExtensions(
            context as? AppCompatActivity,
            source,
            callback = object : SourceLoginJsExtensions.Callback {
                override fun upUiData(data: Map<String, Any?>?) = Unit

                override fun reUiView(deltaUp: Boolean) {
                    scope.launch { onRefreshKinds() }
                }
            }
        )
    }
    val usedSpan = row.sumOf { it.second }.coerceAtMost(EXPLORE_DETAIL_MAX_SPAN)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        row.forEach { (kind, span) ->
            if (kind.type == Type.url && !kind.url.isNullOrBlank()) {
                ExploreCategoryTile(
                    kind = kind,
                    source = source,
                    infoMap = infoMap,
                    selected = kind == selectedKind,
                    wide = span >= EXPLORE_DETAIL_MAX_SPAN,
                    onClick = { onSelectKind(kind) },
                    modifier = Modifier.weight(span.toFloat())
                )
            } else {
                ExploreKindItem(
                    kind = kind,
                    source = source,
                    infoMap = infoMap,
                    sourceJsExtensions = sourceJsExtensions,
                    onOpenKind = onSelectKind,
                    onShowError = onShowError,
                    displayLabelTransform = { label ->
                        sanitizeExploreDetailLabel(label).ifBlank { fallbackLabel }
                    },
                    modifier = Modifier.weight(span.toFloat())
                )
            }
        }
        if (usedSpan < EXPLORE_DETAIL_MAX_SPAN) {
            Spacer(Modifier.weight((EXPLORE_DETAIL_MAX_SPAN - usedSpan).toFloat()))
        }
    }
}

@Composable
private fun ExploreCategoryTile(
    kind: ExploreKind,
    source: BookSource?,
    infoMap: InfoMap,
    selected: Boolean,
    wide: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sourceLabel by rememberExploreKindLabel(kind, source, infoMap)
    val fallbackLabel = stringResource(R.string.explore_category_default)
    val label = remember(sourceLabel, fallbackLabel) {
        sanitizeExploreDetailLabel(sourceLabel).ifBlank { fallbackLabel }
    }
    val compactLabel = remember(label) {
        if (label.length > 4) "${label.take(4)}…" else label
    }
    val primary = Color(NgTheme.colors.primary)
    val tileColorInt = remember(source?.bookSourceUrl, kind.title) {
        sourceTileColor("${source?.bookSourceUrl}#${kind.title}")
    }
    val tileColor = Color(tileColorInt)
    val tileContentColor = remember(tileColorInt) { sourceTileContentColor(tileColorInt) }
    if (wide) {
        Row(
            modifier = modifier
                .height(ExploreCategoryTileHeight)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExploreCategoryGlyph(label, tileColor, tileContentColor, 36.dp)
            Text(
                text = label,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                color = if (selected) primary else colorResource(R.color.primaryText),
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        Column(
            modifier = modifier
                .height(ExploreCategoryTileHeight)
                .clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ExploreCategoryGlyph(label, tileColor, tileContentColor, 36.dp)
            Spacer(Modifier.height(2.dp))
            Text(
                text = compactLabel,
                color = if (selected) primary else colorResource(R.color.primaryText),
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp
            )
        }
    }
}

@Composable
private fun ExploreCategoryGlyph(
    label: String,
    background: Color,
    foreground: Color,
    size: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label.firstOrNull()?.toString()?.uppercase() ?: "源",
            color = foreground,
            fontSize = when {
                size >= 48.dp -> 23.sp
                size >= 40.dp -> 20.sp
                else -> 18.sp
            },
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun ExploreContentToolbar(
    selectedKindLabel: String,
    page: Int,
    layoutMode: ExploreShowLayoutMode,
    onSelectPage: () -> Unit,
    onLayoutModeChange: (ExploreShowLayoutMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(colorResource(R.color.ng_surface))
            .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = selectedKindLabel.ifBlank {
                stringResource(R.string.explore_category_empty)
            },
            modifier = Modifier.weight(1f),
            color = colorResource(R.color.primaryText),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(R.string.menu_page, page),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onSelectPage)
                .padding(horizontal = 7.dp, vertical = 4.dp),
            color = colorResource(R.color.secondaryText),
            fontSize = 12.sp
        )
        ExploreLayoutButton(
            selected = layoutMode == ExploreShowLayoutMode.LIST,
            iconRes = R.drawable.ic_chapter_list,
            description = stringResource(R.string.replace_view_list),
            onClick = { onLayoutModeChange(ExploreShowLayoutMode.LIST) }
        )
        Spacer(Modifier.width(2.dp))
        ExploreLayoutButton(
            selected = layoutMode == ExploreShowLayoutMode.GRID,
            iconRes = R.drawable.ic_view_quilt,
            description = stringResource(R.string.explore_view_grid),
            onClick = { onLayoutModeChange(ExploreShowLayoutMode.GRID) }
        )
    }
}

@Composable
private fun ExploreLayoutButton(
    selected: Boolean,
    iconRes: Int,
    description: String,
    onClick: () -> Unit
) {
    val primary = Color(NgTheme.colors.primary)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (selected) primary.copy(alpha = 0.13f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = description,
                modifier = Modifier.size(16.dp),
                tint = if (selected) primary else colorResource(R.color.secondaryText)
            )
        }
    }
}

@Composable
private fun ExplorePreviousPageRow(isLoading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colorResource(R.color.ng_surface_card).copy(alpha = 0.74f))
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(
                text = stringResource(R.string.prev_page),
                color = colorResource(R.color.primaryText),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun ExploreBookGridCard(
    book: SearchBook,
    inBookshelf: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colorResource(R.color.ng_surface_card))
            .clickable(onClick = onClick)
            .padding(7.dp)
    ) {
        Box {
            SearchBookCover(
                book = book,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.74f),
                coverWidth = 108.dp,
                coverHeight = 146.dp
            )
            if (inBookshelf) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(9.dp)
                        .background(colorResource(R.color.md_green_600), CircleShape)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = book.name,
            color = colorResource(R.color.primaryText),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp
        )
        Text(
            text = book.author,
            color = colorResource(R.color.secondaryText),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ExploreStatusRow(text: String, loading: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(text = text, color = colorResource(R.color.secondaryText), fontSize = 14.sp)
    }
}

@Composable
private fun ExploreErrorRow(error: String, onShowError: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colorResource(R.color.ng_surface_card).copy(alpha = 0.76f))
            .clickable { onShowError(error) }
            .padding(14.dp)
    ) {
        Text(
            text = stringResource(R.string.load_error_retry),
            color = colorResource(R.color.primaryText),
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ExploreContentErrorRow(
    error: String,
    onRetry: () -> Unit,
    onShowError: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colorResource(R.color.ng_surface_card).copy(alpha = 0.76f))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.load_error_retry),
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onRetry)
                .padding(10.dp),
            color = colorResource(R.color.primaryText),
            fontSize = 14.sp
        )
        Text(
            text = stringResource(R.string.explore_error_details),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onShowError(error) }
                .padding(horizontal = 10.dp, vertical = 9.dp),
            color = Color(NgTheme.colors.primary),
            fontSize = 13.sp
        )
    }
}
