package io.legado.app.ui.design.components.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * NG 长列表快速滚动条。尺寸和显隐节奏与旧 FastScrollRecyclerView 保持一致。
 */
@Composable
fun NgLazyListFastScroller(
    state: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    val visibleItemCount by remember {
        derivedStateOf { state.layoutInfo.visibleItemsInfo.size }
    }
    val canScroll = itemCount > visibleItemCount && visibleItemCount > 0
    val scrollFraction by remember(itemCount) {
        derivedStateOf {
            val visibleItems = state.layoutInfo.visibleItemsInfo
            val first = visibleItems.firstOrNull()
            val maxFirstIndex = (itemCount - visibleItems.size).coerceAtLeast(1)
            if (first == null || first.size <= 0) {
                0f
            } else {
                val itemOffset = (-first.offset).toFloat() / first.size
                ((first.index + itemOffset) / maxFirstIndex).coerceIn(0f, 1f)
            }
        }
    }
    var dragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(canScroll, state.isScrollInProgress, dragging) {
        when {
            !canScroll -> visible = false
            state.isScrollInProgress || dragging -> visible = true
            else -> {
                delay(1_000)
                visible = false
            }
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        label = "NgLazyListFastScrollerAlpha",
    )

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    val verticalPadding = 8.dp
    val thumbHeight = 40.dp

    BoxWithConstraints(
        modifier = modifier
            .width(24.dp)
            .fillMaxHeight(),
    ) {
        val heightPx = with(density) { maxHeight.toPx() }
        val paddingPx = with(density) { verticalPadding.toPx() }
        val thumbHeightPx = with(density) { thumbHeight.toPx() }
        val travelPx = (heightPx - paddingPx * 2f - thumbHeightPx).coerceAtLeast(1f)
        val dragModifier = if (visible && canScroll) {
            Modifier.pointerInput(itemCount, heightPx) {
                fun scrollTo(positionY: Float) {
                    val fraction = ((positionY - paddingPx - thumbHeightPx / 2f) / travelPx)
                        .coerceIn(0f, 1f)
                    val index = (fraction * (itemCount - 1)).roundToInt()
                    scrollJob?.cancel()
                    scrollJob = scope.launch { state.scrollToItem(index) }
                }
                detectDragGestures(
                    onDragStart = {
                        dragging = true
                        scrollTo(it.y)
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onDrag = { change, _ ->
                        change.consume()
                        scrollTo(change.position.y)
                    },
                )
            }
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(dragModifier),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(vertical = verticalPadding)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(
                        Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.30f * alpha)
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        IntOffset(
                            x = 0,
                            y = (paddingPx + travelPx * scrollFraction).roundToInt(),
                        )
                    }
                    .width(8.dp)
                    .height(thumbHeight)
                    .background(Color(NgTheme.colors.primary).copy(alpha = alpha)),
            )
        }
    }
}
