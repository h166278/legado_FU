package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * NG 管理列表共用的长按拖拽状态。
 *
 * 拖动过程中只调用 [onMove] 修改 Compose 本地顺序；松手时才调用 [onFinished]，
 * 由页面一次性把可见 ID 顺序提交给宿主，避免每跨过一项都写 Store。
 */
@Stable
class NgLazyReorderState internal constructor(
    val listState: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    private val onFinished: () -> Unit
) {
    var draggedKey: Any? by mutableStateOf(null)
        private set

    var draggedOffset by mutableFloatStateOf(0f)
        private set

    val isDragging: Boolean
        get() = draggedKey != null

    private var lastTargetIndex = -1
    private var scrollJob: Job? = null

    internal fun start(key: Any) {
        if (listState.layoutInfo.visibleItemsInfo.any { it.key == key }) {
            draggedKey = key
            draggedOffset = 0f
            lastTargetIndex = -1
        }
    }

    internal fun dragBy(deltaY: Float) {
        val key = draggedKey ?: return
        draggedOffset += deltaY

        val layoutInfo = listState.layoutInfo
        val dragged = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        val draggedCenter = dragged.offset + draggedOffset + dragged.size / 2f
        val target = layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.key != key &&
                draggedCenter >= item.offset &&
                draggedCenter <= item.offset + item.size
        }

        if (target != null && target.index != lastTargetIndex) {
            draggedOffset += dragged.offset - target.offset
            lastTargetIndex = target.index
            onMove(dragged.index, target.index)
        }

        val top = dragged.offset + draggedOffset
        val bottom = top + dragged.size
        val overscroll = when {
            top < layoutInfo.viewportStartOffset -> top - layoutInfo.viewportStartOffset
            bottom > layoutInfo.viewportEndOffset -> bottom - layoutInfo.viewportEndOffset
            else -> 0f
        }.coerceIn(-32f, 32f)

        scrollJob?.cancel()
        if (overscroll != 0f) {
            scrollJob = scope.launch {
                val consumed = listState.scrollBy(overscroll)
                draggedOffset += consumed
            }
        }
    }

    internal fun finish() {
        val hadDrag = draggedKey != null
        scrollJob?.cancel()
        scrollJob = null
        draggedKey = null
        draggedOffset = 0f
        lastTargetIndex = -1
        if (hadDrag) {
            onFinished()
        }
    }
}

@Composable
fun rememberNgLazyReorderState(
    listState: LazyListState = rememberLazyListState(),
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onFinished: () -> Unit
): NgLazyReorderState {
    val scope = rememberCoroutineScope()
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnFinished by rememberUpdatedState(onFinished)
    return remember(listState, scope) {
        NgLazyReorderState(
            listState = listState,
            scope = scope,
            onMove = { fromIndex, toIndex -> currentOnMove(fromIndex, toIndex) },
            onFinished = { currentOnFinished() }
        )
    }
}

fun Modifier.ngReorderHandle(
    state: NgLazyReorderState,
    key: Any,
    enabled: Boolean,
    contentDescription: String? = null
): Modifier {
    return this
        .testTag("management_drag_$key")
        .then(
            if (contentDescription != null) {
                Modifier.semantics { this.contentDescription = contentDescription }
            } else {
                Modifier
            }
        )
        .pointerInput(state, key, enabled) {
            if (!enabled) return@pointerInput
            detectDragGestures(
                onDragStart = { state.start(key) },
                onDragEnd = state::finish,
                onDragCancel = state::finish,
                onDrag = { change, amount ->
                    change.consume()
                    state.dragBy(amount.y)
                }
            )
        }
}

@Composable
fun Modifier.ngDraggedItem(
    state: NgLazyReorderState,
    key: Any
): Modifier {
    val dragging = state.draggedKey == key
    return this
        .zIndex(if (dragging) 1f else 0f)
        .graphicsLayer {
            translationY = if (dragging) state.draggedOffset else 0f
        }
}

/**
 * 向右侧滑只发出删除请求并自动回弹；确认弹窗和真正删除仍由宿主持有。
 */
@Composable
fun NgSwipeToDelete(
    deletable: Boolean,
    reordering: Boolean,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val currentDelete by rememberUpdatedState(onDeleteRequested)
    val currentDeletable by rememberUpdatedState(deletable)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            when (target) {
                SwipeToDismissBoxValue.StartToEnd -> currentDeletable
                SwipeToDismissBoxValue.EndToStart -> false
                SwipeToDismissBoxValue.Settled -> true
            }
        },
        positionalThreshold = { distance -> distance * 0.25f }
    )
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd) {
            currentDelete()
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = deletable,
        enableDismissFromEndToStart = false,
        gesturesEnabled = deletable && !reordering,
        backgroundContent = {},
        content = content
    )
}

/** 把 Material 3 实验性的刷新 API 隔离在组件层，业务页面只消费稳定的 NG 接口。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NgPullRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (enabled && !isRefreshing) onRefresh()
        },
        modifier = modifier.testTag("management_refresh"),
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = Color(NgTheme.colors.cardContainer),
                color = Color(NgTheme.colors.primary)
            )
        },
        content = content
    )
}
