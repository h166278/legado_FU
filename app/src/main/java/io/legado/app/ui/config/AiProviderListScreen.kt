package io.legado.app.ui.config

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
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
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.components.compose.NgSwipeToDelete
import io.legado.app.ui.design.components.compose.ngDraggedItem
import io.legado.app.ui.design.components.compose.ngReorderHandle
import io.legado.app.ui.design.components.compose.rememberNgLazyReorderState
import io.legado.app.ui.design.theme.NgTheme

/**
 * AI Provider 管理页的纯 UI 状态。
 *
 * [listState] 中的内容应由宿主先完成“是否显示已禁用”与查询过滤；Screen 不读取
 * Provider Store，也不负责把过滤后的排序写回完整列表。
 */
@Immutable
internal data class AiProviderListScreenState(
    val query: String = "",
    val listState: NgListState<AiProviderListItemUiModel> = NgListState.Loading,
    val isRefreshing: Boolean = false,
    val searchEnabled: Boolean = true
)

@Immutable
internal data class AiProviderListItemUiModel(
    val id: String,
    val name: String,
    @param:DrawableRes val iconRes: Int,
    val enabled: Boolean,
    val modelCountText: String,
    val reorderable: Boolean = true,
    val deletable: Boolean = false
)

internal fun matchesAiProviderName(name: String, query: String): Boolean {
    return query.isBlank() || name.contains(query, ignoreCase = true)
}

internal sealed interface AiProviderListScreenAction {
    data class QueryChanged(val query: String) : AiProviderListScreenAction
    data class SearchSubmitted(val query: String) : AiProviderListScreenAction
    data class ProviderClicked(val providerId: String) : AiProviderListScreenAction

    /** 标题栏更多按钮由宿主转发此事件，Screen 本身不持有 Toolbar。 */
    data object MoreMenuRequested : AiProviderListScreenAction

    data object RetryRequested : AiProviderListScreenAction
    data object RefreshRequested : AiProviderListScreenAction

    data class ReorderCommitted(val orderedProviderIds: List<String>) :
        AiProviderListScreenAction

    data class DeleteRequested(val providerId: String) : AiProviderListScreenAction
}

@Composable
internal fun AiProviderListScreen(
    state: AiProviderListScreenState,
    onAction: (AiProviderListScreenAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 16.dp)
    ) {
        NgSearchBar(
            query = state.query,
            onQueryChange = {
                onAction(AiProviderListScreenAction.QueryChanged(it))
            },
            hint = stringResource(R.string.ai_search_provider),
            modifier = Modifier.padding(horizontal = 16.dp),
            enabled = state.searchEnabled,
            onSearch = {
                onAction(AiProviderListScreenAction.SearchSubmitted(it))
            }
        )
        if (state.isRefreshing) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = Color(NgTheme.colors.primary),
                trackColor = Color(NgTheme.colors.surfaceVariant)
            )
        }
        NgListStateContent(
            state = state.listState,
            modifier = Modifier.weight(1f),
            onRetry = {
                onAction(AiProviderListScreenAction.RetryRequested)
            }
        ) { providers ->
            var orderedProviders by remember(providers) { mutableStateOf(providers) }
            val reorderState = rememberNgLazyReorderState(
                onMove = { fromIndex, toIndex ->
                    if (fromIndex in orderedProviders.indices &&
                        toIndex in orderedProviders.indices &&
                        fromIndex != toIndex
                    ) {
                        orderedProviders = orderedProviders.toMutableList().apply {
                            add(toIndex, removeAt(fromIndex))
                        }
                    }
                },
                onFinished = {
                    onAction(
                        AiProviderListScreenAction.ReorderCommitted(
                            orderedProviders.map(AiProviderListItemUiModel::id)
                        )
                    )
                }
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = reorderState.listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 12.dp,
                    end = 16.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = orderedProviders,
                    key = AiProviderListItemUiModel::id,
                    contentType = { "ai_provider" }
                ) { provider ->
                    val canReorder = state.canRequestReorder(provider)
                    val dragDescription = stringResource(R.string.ai_provider_drag_sort)
                    NgSwipeToDelete(
                        deletable = provider.deletable,
                        reordering = reorderState.isDragging,
                        onDeleteRequested = {
                            onAction(AiProviderListScreenAction.DeleteRequested(provider.id))
                        },
                        modifier = Modifier
                            .ngDraggedItem(reorderState, provider.id)
                            .testTag("management_item_${provider.id}")
                    ) {
                        NgManagementListCard(
                            title = provider.name,
                            detailTags = listOf(
                                NgStatusTagSpec(
                                    text = stringResource(
                                        if (provider.enabled) {
                                            R.string.enabled
                                        } else {
                                            R.string.disabled
                                        }
                                    ),
                                    variant = if (provider.enabled) {
                                        NgStatusTagVariant.SUCCESS
                                    } else {
                                        NgStatusTagVariant.WARNING
                                    }
                                ),
                                NgStatusTagSpec(
                                    text = provider.modelCountText,
                                    variant = NgStatusTagVariant.INFO
                                )
                            ),
                            trailingContent = if (canReorder) {
                                {
                                    NgManagementTrailingIcon(
                                        trailing = NgManagementTrailing.DRAG,
                                        contentDescription = dragDescription,
                                        modifier = Modifier.ngReorderHandle(
                                            state = reorderState,
                                            key = provider.id,
                                            enabled = true,
                                            contentDescription = dragDescription
                                        )
                                    )
                                }
                            } else {
                                null
                            },
                            onClick = {
                                onAction(
                                    AiProviderListScreenAction.ProviderClicked(provider.id)
                                )
                            },
                            leading = {
                                NgManagementLeadingIcon(
                                    iconRes = provider.iconRes,
                                    contentDescription = null,
                                    tint = Color.Unspecified
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

internal fun AiProviderListScreenState.canRequestReorder(
    item: AiProviderListItemUiModel
): Boolean = query.isBlank() && item.reorderable
