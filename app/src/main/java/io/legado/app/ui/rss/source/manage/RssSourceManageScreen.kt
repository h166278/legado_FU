package io.legado.app.ui.rss.source.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.RssSource
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.components.compose.ngDraggedItem
import io.legado.app.ui.design.components.compose.ngReorderHandle
import io.legado.app.ui.design.components.compose.rememberNgLazyReorderState
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.rss.RssEmptyState
import io.legado.app.ui.rss.RssPageScaffold
import io.legado.app.ui.rss.RssToolbarAction

internal sealed interface RssSourceManageAction {
    data object Back : RssSourceManageAction
    data object Add : RssSourceManageAction
    data object ImportLocal : RssSourceManageAction
    data object ImportOnline : RssSourceManageAction
    data object ImportQr : RssSourceManageAction
    data object ImportDefault : RssSourceManageAction
    data object ManageGroups : RssSourceManageAction
    data object Help : RssSourceManageAction
    data object DeleteSelection : RssSourceManageAction
    data object EnableSelection : RssSourceManageAction
    data object DisableSelection : RssSourceManageAction
    data object AddSelectionToGroup : RssSourceManageAction
    data object RemoveSelectionFromGroup : RssSourceManageAction
    data object TopSelection : RssSourceManageAction
    data object BottomSelection : RssSourceManageAction
    data object ExportSelection : RssSourceManageAction
    data object ShareSelection : RssSourceManageAction
    data object CompleteSelectionInterval : RssSourceManageAction
    data object SelectAll : RssSourceManageAction
    data object InvertSelection : RssSourceManageAction
    data class QueryChanged(val query: String) : RssSourceManageAction
    data class ToggleSelected(val source: RssSource) : RssSourceManageAction
    data class ToggleEnabled(val source: RssSource, val enabled: Boolean) : RssSourceManageAction
    data class Edit(val source: RssSource) : RssSourceManageAction
    data class Delete(val source: RssSource) : RssSourceManageAction
    data class Top(val source: RssSource) : RssSourceManageAction
    data class Bottom(val source: RssSource) : RssSourceManageAction
    data class Reorder(val sources: List<RssSource>) : RssSourceManageAction
}

@Composable
internal fun RssSourceManageScreen(
    sources: List<RssSource>,
    groups: List<String>,
    query: String,
    selectedUrls: Set<String>,
    onAction: (RssSourceManageAction) -> Unit
) {
    val topActions = listOf(
        RssToolbarAction(R.id.menu_add, R.string.add_rss_source, R.drawable.ic_add),
        RssToolbarAction(
            R.id.menu_import_local,
            R.string.import_local,
            R.drawable.ic_import
        ),
        RssToolbarAction(
            R.id.menu_import_onLine,
            R.string.import_on_line,
            R.drawable.ic_import
        ),
        RssToolbarAction(
            R.id.menu_import_qr,
            R.string.import_by_qr_code,
            R.drawable.ic_import
        ),
        RssToolbarAction(
            R.id.menu_group_manage,
            R.string.group_manage,
            R.drawable.ic_groups
        ),
        RssToolbarAction(
            R.id.menu_import_default,
            R.string.import_default_rule,
            R.drawable.ic_refresh_black_24dp
        ),
        RssToolbarAction(R.id.menu_help, R.string.help, R.drawable.ic_help)
    )
    RssPageScaffold(
        title = stringResource(R.string.rss_source_manage),
        onBack = { onAction(RssSourceManageAction.Back) },
        actions = topActions,
        onAction = { id ->
            onAction(
                when (id) {
                    R.id.menu_add -> RssSourceManageAction.Add
                    R.id.menu_import_local -> RssSourceManageAction.ImportLocal
                    R.id.menu_import_onLine -> RssSourceManageAction.ImportOnline
                    R.id.menu_import_qr -> RssSourceManageAction.ImportQr
                    R.id.menu_group_manage -> RssSourceManageAction.ManageGroups
                    R.id.menu_import_default -> RssSourceManageAction.ImportDefault
                    else -> RssSourceManageAction.Help
                }
            )
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            NgSearchBar(
                query = query,
                onQueryChange = { onAction(RssSourceManageAction.QueryChanged(it)) },
                hint = stringResource(R.string.search_rss_source),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SourceFilterChip(stringResource(R.string.enabled)) {
                    onAction(RssSourceManageAction.QueryChanged(it))
                }
                SourceFilterChip(stringResource(R.string.disabled)) {
                    onAction(RssSourceManageAction.QueryChanged(it))
                }
                SourceFilterChip(stringResource(R.string.need_login)) {
                    onAction(RssSourceManageAction.QueryChanged(it))
                }
                SourceFilterChip(stringResource(R.string.no_group)) {
                    onAction(RssSourceManageAction.QueryChanged(it))
                }
                groups.forEach { group ->
                    SourceFilterChip(group) {
                        onAction(RssSourceManageAction.QueryChanged("group:$it"))
                    }
                }
            }
            if (sources.isEmpty()) {
                RssEmptyState(stringResource(R.string.empty), Modifier.weight(1f))
            } else {
                val sourceVersions = sources.map { System.identityHashCode(it) }
                var orderedSources by remember(sourceVersions) { mutableStateOf(sources) }
                val reorderState = rememberNgLazyReorderState(
                    onMove = { from, to ->
                        if (query.isBlank() && from in orderedSources.indices &&
                            to in orderedSources.indices
                        ) {
                            orderedSources = orderedSources.toMutableList().apply {
                                add(to, removeAt(from))
                            }
                        }
                    },
                    onFinished = {
                        if (query.isBlank()) {
                            onAction(RssSourceManageAction.Reorder(orderedSources))
                        }
                    }
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = reorderState.listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(orderedSources, key = RssSource::sourceUrl) { source ->
                        RssSourceManageRow(
                            source = source,
                            selected = source.sourceUrl in selectedUrls,
                            onAction = onAction,
                            dragModifier = Modifier
                                .ngDraggedItem(reorderState, source.sourceUrl)
                                .ngReorderHandle(
                                    state = reorderState,
                                    key = source.sourceUrl,
                                    enabled = query.isBlank(),
                                    contentDescription = stringResource(R.string.sort)
                                )
                        )
                    }
                }
            }
            if (selectedUrls.isNotEmpty()) {
                SelectionActionBar(
                    count = selectedUrls.size,
                    total = sources.size,
                    onAction = onAction
                )
            }
        }
    }
}

@Composable
private fun SourceFilterChip(text: String, onClick: (String) -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick(text) },
        shape = RoundedCornerShape(14.dp),
        color = Color(NgTheme.colors.surfaceContainerLow)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun RssSourceManageRow(
    source: RssSource,
    selected: Boolean,
    onAction: (RssSourceManageAction) -> Unit,
    dragModifier: Modifier
) {
    var menuExpanded by remember(source.sourceUrl) { mutableStateOf(false) }
    Surface(
        modifier = dragModifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(NgTheme.colors.surface).copy(alpha = 0.84f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onAction(RssSourceManageAction.ToggleSelected(source)) }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.sourceName,
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onAction(RssSourceManageAction.Edit(source)) }
                )
                if (!source.sourceGroup.isNullOrBlank()) {
                    Text(
                        text = source.sourceGroup.orEmpty(),
                        modifier = Modifier.padding(top = 4.dp),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Switch(
                checked = source.enabled,
                onCheckedChange = {
                    onAction(RssSourceManageAction.ToggleEnabled(source, it))
                },
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Box {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { menuExpanded = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.menu),
                        tint = Color(NgTheme.colors.onSurface),
                        modifier = Modifier.size(20.dp)
                    )
                }
                NgExpandableActionMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    items = listOf(
                        NgExpandableActionMenuItem(
                            R.id.menu_edit,
                            R.string.edit,
                            R.drawable.ic_edit
                        ),
                        NgExpandableActionMenuItem(
                            R.id.menu_top,
                            R.string.to_top,
                            R.drawable.ic_arrow_drop_up
                        ),
                        NgExpandableActionMenuItem(
                            R.id.menu_bottom,
                            R.string.to_bottom,
                            R.drawable.ic_arrow_down
                        ),
                        NgExpandableActionMenuItem(
                            R.id.menu_del,
                            R.string.delete,
                            R.drawable.ic_outline_delete,
                            dividerBefore = true
                        )
                    ),
                    onItemClick = {
                        menuExpanded = false
                        onAction(
                            when (it.itemId) {
                                R.id.menu_edit -> RssSourceManageAction.Edit(source)
                                R.id.menu_top -> RssSourceManageAction.Top(source)
                                R.id.menu_bottom -> RssSourceManageAction.Bottom(source)
                                else -> RssSourceManageAction.Delete(source)
                            }
                        )
                    },
                    width = 152.dp
                )
            }
            Spacer(Modifier.width(6.dp))
        }
    }
}

@Composable
private fun SelectionActionBar(
    count: Int,
    total: Int,
    onAction: (RssSourceManageAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = "$count / $total",
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SelectionButton(R.string.select_all) { onAction(RssSourceManageAction.SelectAll) }
            SelectionButton(R.string.revert_selection) {
                onAction(RssSourceManageAction.InvertSelection)
            }
            SelectionButton(R.string.enable_selection) {
                onAction(RssSourceManageAction.EnableSelection)
            }
            SelectionButton(R.string.disable_selection) {
                onAction(RssSourceManageAction.DisableSelection)
            }
            SelectionButton(R.string.add_group) {
                onAction(RssSourceManageAction.AddSelectionToGroup)
            }
            SelectionButton(R.string.remove_group) {
                onAction(RssSourceManageAction.RemoveSelectionFromGroup)
            }
            SelectionButton(R.string.selection_to_top) {
                onAction(RssSourceManageAction.TopSelection)
            }
            SelectionButton(R.string.selection_to_bottom) {
                onAction(RssSourceManageAction.BottomSelection)
            }
            SelectionButton(R.string.export_selection) {
                onAction(RssSourceManageAction.ExportSelection)
            }
            SelectionButton(R.string.share_selected_source) {
                onAction(RssSourceManageAction.ShareSelection)
            }
            SelectionButton(R.string.check_selected_interval) {
                onAction(RssSourceManageAction.CompleteSelectionInterval)
            }
            SelectionButton(R.string.delete) {
                onAction(RssSourceManageAction.DeleteSelection)
            }
        }
    }
}

@Composable
private fun SelectionButton(titleRes: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(stringResource(titleRes), maxLines = 1)
    }
}

@Composable
internal fun RssSourceTextDialog(
    title: String,
    initialValue: String = "",
    suggestions: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NgFormField(
                    label = title,
                    value = value,
                    onValueChange = { value = it }
                )
                if (suggestions.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        suggestions.forEach { suggestion ->
                            SourceFilterChip(suggestion) { value = it }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
internal fun RssSourceConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
