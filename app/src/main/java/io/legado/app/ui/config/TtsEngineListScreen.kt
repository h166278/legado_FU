package io.legado.app.ui.config

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.design.components.NgManagementTrailing
import io.legado.app.ui.design.components.NgStatusTagSpec
import io.legado.app.ui.design.components.NgStatusTagVariant
import io.legado.app.ui.design.components.compose.NgListState
import io.legado.app.ui.design.components.compose.NgListStateContent
import io.legado.app.ui.design.components.compose.NgManagementLeadingIcon
import io.legado.app.ui.design.components.compose.NgManagementListCard
import io.legado.app.ui.design.components.compose.NgManagementTrailingIcon
import io.legado.app.ui.design.components.compose.NgPullRefreshBox
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.components.compose.NgSwipeToDelete
import io.legado.app.ui.design.components.compose.NgLazyReorderState
import io.legado.app.ui.design.components.compose.ngDraggedItem
import io.legado.app.ui.design.components.compose.ngReorderHandle
import io.legado.app.ui.design.components.compose.rememberNgLazyReorderState
import io.legado.app.ui.design.theme.NgTheme

/** 朗读引擎管理页只用于渲染的数据，不持有 TTS Store 实体。 */
@Immutable
data class TtsEngineListItemUiModel(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val engineTypeText: String,
    val voiceCountText: String,
    val reorderable: Boolean,
    val deletable: Boolean,
    @param:DrawableRes val iconRes: Int = R.drawable.ic_ai_capability_tts,
    val actionContentDescription: String? = null
)

@Immutable
data class TtsEngineListScreenState(
    val query: String = "",
    val listState: NgListState<TtsEngineListItemUiModel> = NgListState.Loading,
    val isRefreshing: Boolean = false,
    val showDisabled: Boolean = false,
    val showSearch: Boolean = false
)

internal class TtsEngineSnapshotGate {
    private var revision = 0L

    fun begin(): Long = ++revision

    fun invalidate() {
        revision++
    }

    fun isCurrent(token: Long): Boolean = token == revision
}

/**
 * 页面事件全部回传宿主。创建、导入、菜单、存储和排序合并均不在 Composable 内执行。
 *
 * 拖动过程只修改 Screen 内的可见顺序，松手后通过 [ReorderCommitted] 一次提交。
 */
sealed interface TtsEngineListAction {
    data class QueryChanged(val query: String) : TtsEngineListAction
    data class SearchSubmitted(val query: String) : TtsEngineListAction
    data object Refresh : TtsEngineListAction
    data object Retry : TtsEngineListAction
    data class OpenEngine(val engineId: String) : TtsEngineListAction
    data class ReorderCommitted(val orderedEngineIds: List<String>) : TtsEngineListAction
    data class DeleteRequested(val engineId: String) : TtsEngineListAction
    data object OpenListMenu : TtsEngineListAction
    data object CreateEngine : TtsEngineListAction
    data object ImportLocal : TtsEngineListAction
    data object ImportOnline : TtsEngineListAction
    data object ToggleShowDisabled : TtsEngineListAction
}

@Composable
fun TtsEngineListScreen(
    state: TtsEngineListScreenState,
    onAction: (TtsEngineListAction) -> Unit,
    modifier: Modifier = Modifier,
    searchHint: String = stringResource(R.string.multi_role_tts_engine_search)
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        if (state.showSearch) {
            NgSearchBar(
                query = state.query,
                onQueryChange = { onAction(TtsEngineListAction.QueryChanged(it)) },
                hint = searchHint,
                onSearch = { onAction(TtsEngineListAction.SearchSubmitted(it)) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        NgListStateContent(
            state = state.listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onRetry = { onAction(TtsEngineListAction.Retry) }
        ) { engines ->
            var orderedEngines by remember(engines) { mutableStateOf(engines) }
            val reorderState = rememberNgLazyReorderState(
                onMove = { fromIndex, toIndex ->
                    if (fromIndex in orderedEngines.indices &&
                        toIndex in orderedEngines.indices &&
                        fromIndex != toIndex
                    ) {
                        orderedEngines = orderedEngines.toMutableList().apply {
                            add(toIndex, removeAt(fromIndex))
                        }
                    }
                },
                onFinished = {
                    onAction(
                        TtsEngineListAction.ReorderCommitted(
                            orderedEngines.map(TtsEngineListItemUiModel::id)
                        )
                    )
                }
            )
            NgPullRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { onAction(TtsEngineListAction.Refresh) },
                enabled = !reorderState.isDragging,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = reorderState.listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 8.dp,
                        end = 16.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = orderedEngines,
                        key = TtsEngineListItemUiModel::id,
                        contentType = { "tts_engine" }
                    ) { engine ->
                        TtsEngineListCard(
                            item = engine,
                            canReorder = state.canRequestReorder(engine),
                            reorderState = reorderState,
                            onAction = onAction,
                            modifier = Modifier
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TtsEngineListCard(
    item: TtsEngineListItemUiModel,
    canReorder: Boolean,
    reorderState: NgLazyReorderState,
    onAction: (TtsEngineListAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val dragDescription = item.actionContentDescription ?: stringResource(R.string.menu)
    NgSwipeToDelete(
        deletable = item.deletable,
        reordering = reorderState.isDragging,
        onDeleteRequested = { onAction(TtsEngineListAction.DeleteRequested(item.id)) },
        modifier = modifier
            .ngDraggedItem(reorderState, item.id)
            .testTag("management_item_${item.id}")
    ) {
        NgManagementListCard(
            title = item.name,
            detailTags = item.statusTags(
                enabledText = stringResource(R.string.enabled),
                disabledText = stringResource(R.string.disabled)
            ),
            trailingContent = if (canReorder) {
                {
                    NgManagementTrailingIcon(
                        trailing = NgManagementTrailing.DRAG,
                        contentDescription = dragDescription,
                        modifier = Modifier.ngReorderHandle(
                            state = reorderState,
                            key = item.id,
                            enabled = true,
                            contentDescription = dragDescription
                        )
                    )
                }
            } else {
                null
            },
            onClick = { onAction(TtsEngineListAction.OpenEngine(item.id)) },
            leading = {
                NgManagementLeadingIcon(
                    iconRes = item.iconRes,
                    contentDescription = null,
                    tint = Color(NgTheme.colors.primary)
                )
            }
        )
    }
}

internal fun TtsEngineListItemUiModel.statusTags(
    enabledText: String,
    disabledText: String
): List<NgStatusTagSpec> {
    return listOf(
        NgStatusTagSpec(
            text = if (enabled) enabledText else disabledText,
            variant = if (enabled) {
                NgStatusTagVariant.SUCCESS
            } else {
                NgStatusTagVariant.WARNING
            }
        ),
        NgStatusTagSpec(
            text = engineTypeText,
            variant = NgStatusTagVariant.INFO
        ),
        NgStatusTagSpec(
            text = voiceCountText,
            variant = NgStatusTagVariant.INFO
        )
    )
}

internal fun TtsEngineListItemUiModel.trailing(): NgManagementTrailing {
    return if (reorderable) {
        NgManagementTrailing.DRAG
    } else {
        NgManagementTrailing.NONE
    }
}

internal fun TtsEngineListScreenState.canRequestReorder(
    item: TtsEngineListItemUiModel
): Boolean = query.isBlank() && item.reorderable
