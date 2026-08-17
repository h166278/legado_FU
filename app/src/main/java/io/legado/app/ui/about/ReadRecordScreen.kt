package io.legado.app.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.ReadRecordShow
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgBookCover
import io.legado.app.ui.design.components.compose.NgButton
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.components.compose.NgSearchBarVariant
import io.legado.app.ui.design.theme.NgTheme

internal data class ReadRecordUiItem(
    val record: ReadRecordShow,
    val book: Book?,
    val durationText: String,
    val lastReadText: String,
)

@Composable
internal fun ReadRecordScreen(
    items: List<ReadRecordUiItem>,
    totalReadTime: String,
    query: String,
    searchExpanded: Boolean,
    sortMode: Int,
    recordEnabled: Boolean,
    deleteTarget: ReadRecordUiItem?,
    clearAllDialogVisible: Boolean,
    onBack: () -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (Int) -> Unit,
    onRecordEnabledChange: (Boolean) -> Unit,
    onClearAllRequest: () -> Unit,
    onClearAllDismiss: () -> Unit,
    onClearAllConfirm: () -> Unit,
    onItemClick: (ReadRecordUiItem) -> Unit,
    onDeleteRequest: (ReadRecordUiItem) -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: (ReadRecordUiItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ReadRecordTopBar(
            query = query,
            searchExpanded = searchExpanded,
            sortMode = sortMode,
            recordEnabled = recordEnabled,
            onBack = onBack,
            onSearchExpandedChange = onSearchExpandedChange,
            onQueryChange = onQueryChange,
            onSortChange = onSortChange,
            onRecordEnabledChange = onRecordEnabledChange,
            onClearAllRequest = onClearAllRequest,
        )
        ReadRecordPanel(
            items = items,
            totalReadTime = totalReadTime,
            recordEnabled = recordEnabled,
            onItemClick = onItemClick,
            onDeleteRequest = onDeleteRequest,
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding()
                .padding(start = 10.dp, top = 4.dp, end = 10.dp, bottom = 10.dp),
        )
    }

    if (clearAllDialogVisible) {
        ReadRecordConfirmationDialog(
            message = stringResource(R.string.sure_del),
            onDismiss = onClearAllDismiss,
            onConfirm = onClearAllConfirm,
        )
    }
    deleteTarget?.let { item ->
        ReadRecordConfirmationDialog(
            message = stringResource(R.string.sure_del_any, item.record.bookName),
            onDismiss = onDeleteDismiss,
            onConfirm = { onDeleteConfirm(item) },
        )
    }
}

@Composable
private fun ReadRecordTopBar(
    query: String,
    searchExpanded: Boolean,
    sortMode: Int,
    recordEnabled: Boolean,
    onBack: () -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (Int) -> Unit,
    onRecordEnabledChange: (Boolean) -> Unit,
    onClearAllRequest: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .statusBarsPadding()
            .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 4.dp)
            .fillMaxWidth()
            .height(56.dp),
        color = colorResource(R.color.ng_surface_card),
        shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
        shadowElevation = NgTheme.effects.cardElevationDp.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReadRecordToolbarIcon(
                iconRes = R.drawable.ic_arrow_back,
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
            )
            if (searchExpanded) {
                NgSearchBar(
                    query = query,
                    onQueryChange = onQueryChange,
                    hint = stringResource(R.string.search),
                    variant = NgSearchBarVariant.TOOLBAR,
                    onSearch = {},
                    modifier = Modifier.weight(1f),
                )
                ReadRecordToolbarIcon(
                    iconRes = R.drawable.ic_baseline_close,
                    contentDescription = stringResource(R.string.close),
                    onClick = { onSearchExpandedChange(false) },
                )
            } else {
                Text(
                    text = stringResource(R.string.read_record),
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ReadRecordToolbarIcon(
                    iconRes = R.drawable.ic_search,
                    contentDescription = stringResource(R.string.search),
                    onClick = { onSearchExpandedChange(true) },
                )
                ReadRecordSortMenu(sortMode = sortMode, onSortChange = onSortChange)
                ReadRecordMoreMenu(
                    recordEnabled = recordEnabled,
                    onRecordEnabledChange = onRecordEnabledChange,
                    onClearAllRequest = onClearAllRequest,
                )
            }
        }
    }
}

@Composable
private fun ReadRecordSortMenu(
    sortMode: Int,
    onSortChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ReadRecordToolbarIcon(
            iconRes = R.drawable.ic_baseline_sort_24,
            contentDescription = stringResource(R.string.sort),
            onClick = { expanded = true },
        )
        NgExpandableActionMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            items = listOf(
                NgExpandableActionMenuItem(
                    itemId = MENU_SORT_NAME,
                    titleRes = R.string.sort_by_name,
                    iconRes = R.drawable.ic_sort,
                    checked = sortMode == SORT_NAME,
                ),
                NgExpandableActionMenuItem(
                    itemId = MENU_SORT_DURATION,
                    titleRes = R.string.reading_time_sort,
                    iconRes = R.drawable.ic_mingcute_time_line,
                    checked = sortMode == SORT_READING_DURATION,
                ),
                NgExpandableActionMenuItem(
                    itemId = MENU_SORT_LAST_READ,
                    titleRes = R.string.last_read_time_sort,
                    iconRes = R.drawable.ic_history,
                    checked = sortMode == SORT_LAST_READ,
                ),
            ),
            width = 160.dp,
            offset = DpOffset(0.dp, 4.dp),
            onItemClick = { item ->
                expanded = false
                onSortChange(
                    when (item.itemId) {
                        MENU_SORT_DURATION -> SORT_READING_DURATION
                        MENU_SORT_LAST_READ -> SORT_LAST_READ
                        else -> SORT_NAME
                    },
                )
            },
        )
    }
}

@Composable
private fun ReadRecordMoreMenu(
    recordEnabled: Boolean,
    onRecordEnabledChange: (Boolean) -> Unit,
    onClearAllRequest: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ReadRecordToolbarIcon(
            iconRes = R.drawable.ic_more_vert,
            contentDescription = stringResource(R.string.menu),
            onClick = { expanded = true },
        )
        NgExpandableActionMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            items = listOf(
                NgExpandableActionMenuItem(
                    itemId = MENU_ENABLE_RECORD,
                    titleRes = R.string.enable_record,
                    iconRes = R.drawable.ic_check_circle_outline,
                    checked = recordEnabled,
                ),
                NgExpandableActionMenuItem(
                    itemId = MENU_CLEAR_RECORDS,
                    titleRes = R.string.clear_records,
                    iconRes = R.drawable.ic_book_info_delete,
                    dividerBefore = true,
                    danger = true,
                ),
            ),
            width = 148.dp,
            offset = DpOffset(0.dp, 4.dp),
            onItemClick = { item ->
                expanded = false
                when (item.itemId) {
                    MENU_ENABLE_RECORD -> onRecordEnabledChange(!recordEnabled)
                    MENU_CLEAR_RECORDS -> onClearAllRequest()
                }
            },
        )
    }
}

@Composable
private fun ReadRecordToolbarIcon(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = Color(NgTheme.colors.onSurface),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ReadRecordPanel(
    items: List<ReadRecordUiItem>,
    totalReadTime: String,
    recordEnabled: Boolean,
    onItemClick: (ReadRecordUiItem) -> Unit,
    onDeleteRequest: (ReadRecordUiItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colorResource(R.color.ng_surface_card),
        shape = RoundedCornerShape(NgTheme.shapes.largeDp.dp),
        shadowElevation = NgTheme.effects.cardElevationDp.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ReadRecordSummary(
                totalReadTime = totalReadTime,
                recordCount = items.size,
                recordEnabled = recordEnabled,
            )
            HorizontalDivider(
                color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.28f),
                thickness = 0.6.dp,
            )
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.read_record_empty),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.record.bookName }) { item ->
                        ReadRecordRow(
                            item = item,
                            onClick = { onItemClick(item) },
                            onDelete = { onDeleteRequest(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadRecordSummary(
    totalReadTime: String,
    recordCount: Int,
    recordEnabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(54.dp),
            color = Color(NgTheme.colors.selectedContainer),
            shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_history),
                    contentDescription = null,
                    tint = Color(NgTheme.colors.primary),
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.all_read_time),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 13.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = totalReadTime,
                color = Color(NgTheme.colors.onSurface),
                fontSize = 22.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReadRecordSolidTag(
                    text = stringResource(R.string.read_record_count, recordCount),
                    containerColor = colorResource(R.color.ng_info),
                )
                ReadRecordSolidTag(
                    text = stringResource(
                        if (recordEnabled) R.string.read_record_active
                        else R.string.read_record_paused,
                    ),
                    containerColor = if (recordEnabled) {
                        colorResource(R.color.ng_success)
                    } else {
                        Color(NgTheme.colors.onSurfaceVariant)
                    },
                )
            }
        }
    }
}

@Composable
private fun ReadRecordSolidTag(
    text: String,
    containerColor: Color,
) {
    Box(
        modifier = Modifier
            .height(20.dp)
            .defaultMinSize(minWidth = 42.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(containerColor)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReadRecordRow(
    item: ReadRecordUiItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.book != null) {
            NgBookCover(
                book = item.book,
                coverRadius = 6,
                contentDescription = item.record.bookName,
                modifier = Modifier.size(width = 44.dp, height = 60.dp),
            )
        } else {
            ReadRecordMissingCover(item.record.bookName)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.record.bookName,
                color = Color(NgTheme.colors.onSurface),
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_mingcute_time_line),
                    contentDescription = null,
                    tint = Color(NgTheme.colors.onSurfaceVariant),
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = item.durationText,
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                )
                if (item.lastReadText.isNotEmpty()) {
                    Text(
                        text = "  ·  ${item.lastReadText}",
                        color = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.82f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_book_info_delete),
                contentDescription = stringResource(R.string.delete),
                tint = Color(NgTheme.colors.onSurfaceVariant),
                modifier = Modifier.size(21.dp),
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 70.dp, end = 12.dp),
        color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.22f),
        thickness = 0.6.dp,
    )
}

@Composable
private fun ReadRecordMissingCover(bookName: String) {
    Surface(
        modifier = Modifier.size(width = 44.dp, height = 60.dp),
        color = Color(NgTheme.colors.surfaceContainerLow),
        shape = RoundedCornerShape(6.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_book_info_read),
                contentDescription = bookName,
                tint = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.72f),
                modifier = Modifier.size(23.dp),
            )
        }
    }
}

@Composable
private fun ReadRecordConfirmationDialog(
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.delete),
            modifier = Modifier.padding(horizontal = 24.dp),
            variant = NgDialogVariant.COMPACT_CONFIRMATION,
            actions = {
                NgButton(
                    onClick = onDismiss,
                    modifier = Modifier.width(92.dp).height(42.dp),
                    variant = NgButtonVariant.OUTLINE,
                ) {
                    Text(stringResource(R.string.cancel), fontSize = 14.sp)
                }
                NgButton(
                    onClick = onConfirm,
                    modifier = Modifier.width(92.dp).height(42.dp),
                    variant = NgButtonVariant.DANGER,
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                }
            },
        ) {
            Text(
                text = message,
                modifier = Modifier.fillMaxWidth(),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
        }
    }
}

private const val SORT_NAME = 0
private const val SORT_READING_DURATION = 1
private const val SORT_LAST_READ = 2
private const val MENU_SORT_NAME = 0x7201
private const val MENU_SORT_DURATION = 0x7202
private const val MENU_SORT_LAST_READ = 0x7203
private const val MENU_ENABLE_RECORD = 0x7204
private const val MENU_CLEAR_RECORDS = 0x7205
