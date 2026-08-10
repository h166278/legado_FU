package io.legado.app.ui.rss.subscription

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.RuleSub
import io.legado.app.ui.design.components.NgManagementTrailing
import io.legado.app.ui.design.components.NgStatusTagSpec
import io.legado.app.ui.design.components.NgStatusTagVariant
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormSelectField
import io.legado.app.ui.design.components.compose.NgFormSelectOption
import io.legado.app.ui.design.components.compose.NgFormSwitchRow
import io.legado.app.ui.design.components.compose.NgManagementLeadingIcon
import io.legado.app.ui.design.components.compose.NgManagementListCard
import io.legado.app.ui.design.components.compose.NgManagementTrailingIcon
import io.legado.app.ui.design.components.compose.ngDraggedItem
import io.legado.app.ui.design.components.compose.ngReorderHandle
import io.legado.app.ui.design.components.compose.rememberNgLazyReorderState
import io.legado.app.ui.rss.RssEmptyState
import io.legado.app.ui.rss.RssPageScaffold
import io.legado.app.ui.rss.RssToolbarAction

internal sealed interface RuleSubAction {
    data class Open(val item: RuleSub) : RuleSubAction
    data class Edit(val item: RuleSub) : RuleSubAction
    data class Delete(val item: RuleSub) : RuleSubAction
    data class Reorder(val items: List<RuleSub>) : RuleSubAction
    data object Add : RuleSubAction
    data object Back : RuleSubAction
}

private data class RuleSubVersion(
    val id: Long,
    val name: String,
    val url: String,
    val type: Int,
    val customOrder: Int,
    val autoUpdate: Boolean,
    val update: Long,
    val updateInterval: Int,
    val silentUpdate: Boolean,
    val js: String?,
    val showRule: String?,
    val sourceUrl: String?
)

@Composable
internal fun RuleSubScreen(
    items: List<RuleSub>,
    onAction: (RuleSubAction) -> Unit
) {
    RssPageScaffold(
        title = stringResource(R.string.rule_subscription),
        onBack = { onAction(RuleSubAction.Back) },
        actions = listOf(
            RssToolbarAction(R.id.menu_add, R.string.add, R.drawable.ic_add)
        ),
        onAction = { onAction(RuleSubAction.Add) }
    ) {
        if (items.isEmpty()) {
            RssEmptyState(stringResource(R.string.empty))
            return@RssPageScaffold
        }
        val itemVersions = items.map {
            RuleSubVersion(
                id = it.id,
                name = it.name,
                url = it.url,
                type = it.type,
                customOrder = it.customOrder,
                autoUpdate = it.autoUpdate,
                update = it.update,
                updateInterval = it.updateInterval,
                silentUpdate = it.silentUpdate,
                js = it.js,
                showRule = it.showRule,
                sourceUrl = it.sourceUrl
            )
        }
        var orderedItems by remember(itemVersions) { mutableStateOf(items) }
        val reorderState = rememberNgLazyReorderState(
            onMove = { from, to ->
                if (from in orderedItems.indices && to in orderedItems.indices) {
                    orderedItems = orderedItems.toMutableList().apply {
                        add(to, removeAt(from))
                    }
                }
            },
            onFinished = { onAction(RuleSubAction.Reorder(orderedItems)) }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = reorderState.listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(orderedItems, key = RuleSub::id) { item ->
                var menuExpanded by remember(item.id) { mutableStateOf(false) }
                val typeNames = stringArrayResource(R.array.rule_type)
                NgManagementListCard(
                    title = item.name.ifBlank { item.url },
                    summary = item.url,
                    detailTags = listOf(
                        NgStatusTagSpec(
                            text = typeNames.getOrElse(item.type) { typeNames.first() },
                            variant = NgStatusTagVariant.INFO
                        )
                    ),
                    onClick = { onAction(RuleSubAction.Open(item)) },
                    leading = {
                        NgManagementLeadingIcon(
                            iconRes = R.drawable.ic_bottom_rss_feed,
                            contentDescription = null,
                            tint = Color.Unspecified
                        )
                    },
                    trailingContent = {
                        NgManagementTrailingIcon(
                            trailing = NgManagementTrailing.MORE,
                            contentDescription = stringResource(R.string.menu),
                            modifier = Modifier.ngReorderHandle(
                                state = reorderState,
                                key = item.id,
                                enabled = true,
                                contentDescription = stringResource(R.string.sort)
                            ),
                            onClick = { menuExpanded = true }
                        )
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
                                    R.id.menu_del,
                                    R.string.delete,
                                    R.drawable.ic_outline_delete,
                                    dividerBefore = true
                                )
                            ),
                            onItemClick = { menuItem ->
                                menuExpanded = false
                                when (menuItem.itemId) {
                                    R.id.menu_edit -> onAction(RuleSubAction.Edit(item))
                                    R.id.menu_del -> onAction(RuleSubAction.Delete(item))
                                }
                            }
                        )
                    },
                    modifier = Modifier.ngDraggedItem(reorderState, item.id)
                )
            }
        }
    }
}

@Composable
internal fun RuleSubEditorDialog(
    initial: RuleSub,
    onDismiss: () -> Unit,
    onConfirm: (RuleSub) -> Unit
) {
    val typeNames = stringArrayResource(R.array.rule_type)
    var type by remember(initial.id) {
        mutableStateOf(initial.type.takeIf { it in typeNames.indices } ?: 0)
    }
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var url by remember(initial.id) { mutableStateOf(initial.url) }
    var autoUpdate by remember(initial.id) { mutableStateOf(initial.autoUpdate) }
    var silentUpdate by remember(initial.id) { mutableStateOf(initial.silentUpdate) }
    var interval by remember(initial.id) { mutableStateOf(initial.updateInterval.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rule_subscription)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NgFormSelectField(
                    label = stringResource(R.string.book_type),
                    selectedValue = type.toString(),
                    options = typeNames.mapIndexed { index, label ->
                        NgFormSelectOption(label, index.toString())
                    },
                    onValueChange = { type = it.toIntOrNull() ?: 0 },
                    arrowIcon = painterResource(R.drawable.ic_arrow_drop_down)
                )
                NgFormField(
                    label = stringResource(R.string.name),
                    value = name,
                    onValueChange = { name = it }
                )
                NgFormField(
                    label = "Url",
                    value = url,
                    onValueChange = { url = it }
                )
                NgFormSwitchRow(
                    title = stringResource(R.string.auto_update),
                    checked = autoUpdate,
                    onCheckedChange = { checked ->
                        autoUpdate = checked
                        if (checked && interval.toIntOrNull() == 0) interval = "24"
                        if (!checked) {
                            interval = "0"
                            silentUpdate = false
                        }
                    }
                )
                NgFormSwitchRow(
                    title = stringResource(R.string.silent_update),
                    checked = silentUpdate,
                    enabled = autoUpdate && (interval.toIntOrNull() ?: 0) > 0,
                    onCheckedChange = { silentUpdate = it }
                )
                NgFormField(
                    label = stringResource(R.string.update_interval),
                    value = interval,
                    enabled = autoUpdate,
                    onValueChange = {
                        interval = it.filter(Char::isDigit)
                        if (interval.toIntOrNull() == 0) {
                            autoUpdate = false
                            silentUpdate = false
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        initial.copy(
                            type = type,
                            name = name,
                            url = url,
                            autoUpdate = autoUpdate,
                            silentUpdate = silentUpdate,
                            updateInterval = interval.toIntOrNull() ?: 0
                        )
                    )
                }
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
