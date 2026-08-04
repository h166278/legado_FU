package io.legado.app.ui.main.bookshelf

import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.config.BookshelfFloatingDockConfig
import io.legado.app.help.glide.ImageLoader
import io.legado.app.ui.design.theme.NgTheme

internal data class BookshelfDockGroup(
    val groupId: Long,
    val name: String,
    val cover: String?
)

@Composable
internal fun BookshelfFloatingDock(
    groups: List<BookshelfDockGroup>,
    selectedIndex: Int,
    onSearchClick: () -> Unit,
    onGroupClick: (Int) -> Unit,
    onGroupLongClick: (Int) -> Unit,
    topDistancePx: Int,
    contentTopInsetPx: Int,
    transparencyPercent: Int,
    modifier: Modifier = Modifier
) {
    val snapshot = NgTheme.snapshot
    val shape = RoundedCornerShape(12.dp)
    val surfaceColor = colorResource(R.color.ng_floating_dock_surface).copy(
        alpha = BookshelfFloatingDockConfig.surfaceAlpha(transparencyPercent)
    )
    val dockTopSpacerHeight = with(LocalDensity.current) {
        (topDistancePx - contentTopInsetPx).coerceAtLeast(0).toDp()
    }
    val dockBorderColor = when {
        snapshot.isEInk -> Color.Black
        snapshot.isDark -> Color.White.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.68f)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dockTopSpacerHeight)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(shape)
                    .background(surfaceColor)
                    .border(0.6.dp, dockBorderColor, shape),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockAction(
                    iconRes = R.drawable.ic_bookshelf_dock_search,
                    label = stringResource(R.string.search),
                    onClick = onSearchClick
                )
                GroupTrack(
                    groups = groups,
                    selectedIndex = selectedIndex,
                    onGroupClick = onGroupClick,
                    onGroupLongClick = onGroupLongClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DockAction(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit
) {
    val contentColor = floatingDockInactiveContentColor()
    DockItemContent(
        label = label,
        contentColor = contentColor,
        modifier = Modifier
            .width(48.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
            }
    ) {
        DockVectorIcon(iconRes = iconRes, tint = contentColor)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupTrack(
    groups: List<BookshelfDockGroup>,
    selectedIndex: Int,
    onGroupClick: (Int) -> Unit,
    onGroupLongClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
        if (groups.isEmpty()) return@BoxWithConstraints
        val visibleItemCount = (maxWidth.value / MIN_GROUP_ITEM_WIDTH.value)
            .toInt()
            .coerceAtLeast(1)
        val scrollable = groups.size > visibleItemCount
        val itemWidth = if (scrollable) {
            maxWidth / visibleItemCount.toFloat()
        } else {
            minOf(MIN_GROUP_ITEM_WIDTH, maxWidth)
        }
        val listState = rememberLazyListState()
        val snapFlingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Start)
        LaunchedEffect(selectedIndex, groups.size, itemWidth, maxWidth) {
            if (scrollable && selectedIndex in groups.indices) {
                listState.animateScrollToItem(selectedIndex)
            }
        }
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.Start,
                flingBehavior = snapFlingBehavior,
                userScrollEnabled = scrollable,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = groups,
                    key = { _, item -> item.groupId }
                ) { index, group ->
                    GroupItem(
                        group = group,
                        selected = index == selectedIndex,
                        width = itemWidth,
                        onClick = { onGroupClick(index) },
                        onLongClick = { onGroupLongClick(index) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupItem(
    group: BookshelfDockGroup,
    selected: Boolean,
    width: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val contentColor = if (selected) {
        floatingDockActiveContentColor()
    } else {
        floatingDockInactiveContentColor()
    }
    DockItemContent(
        label = group.name,
        contentColor = contentColor,
        labelWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics(mergeDescendants = true) {
                this.selected = selected
                role = Role.Tab
                contentDescription = group.name
            }
    ) {
        GroupIcon(group = group, selected = selected)
    }
}

@Composable
private fun DockItemContent(
    label: String,
    contentColor: Color,
    modifier: Modifier = Modifier,
    labelWeight: FontWeight = FontWeight.Normal,
    iconContent: @Composable () -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            iconContent()
        }
        Text(
            text = label,
            color = contentColor,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = labelWeight,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
        )
    }
}

@Composable
private fun DockVectorIcon(@DrawableRes iconRes: Int, tint: Color) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
private fun GroupIcon(group: BookshelfDockGroup, selected: Boolean) {
    val iconTint = if (selected) {
        floatingDockActiveContentColor()
    } else {
        floatingDockInactiveContentColor()
    }
    val builtInIconRes = group.builtInIconRes()
    when {
        builtInIconRes != null -> DockVectorIcon(
            iconRes = builtInIconRes,
            tint = iconTint
        )

        !group.cover.isNullOrBlank() -> GroupCoverIcon(
            path = group.cover.orEmpty(),
            selected = selected
        )

        else -> Text(
            text = group.name.firstOrNull()?.toString().orEmpty(),
            color = iconTint,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.offset(y = (-1).dp)
        )
    }
}

@Composable
private fun GroupCoverIcon(path: String, selected: Boolean) {
    val borderColor = Color(NgTheme.colors.primary)
    val shape = RoundedCornerShape(7.dp)
    AndroidView(
        factory = { context ->
            AppCompatImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
        },
        update = { imageView ->
            if (imageView.tag != path) {
                imageView.tag = path
                ImageLoader.load(imageView.context, path)
                    .centerCrop()
                    .into(imageView)
            }
        },
        modifier = Modifier
            .size(22.dp)
            .clip(shape)
            .then(
                if (selected) Modifier.border(1.dp, borderColor, shape) else Modifier
            )
    )
}

@Composable
private fun floatingDockActiveContentColor(): Color {
    return Color(NgTheme.colors.primary)
}

@Composable
private fun floatingDockInactiveContentColor(): Color {
    val snapshot = NgTheme.snapshot
    return if (snapshot.isDark) {
        Color(snapshot.colors.onSurface)
    } else {
        Color(snapshot.colors.onSurfaceVariant).copy(alpha = 0.66f)
    }
}

@DrawableRes
private fun BookshelfDockGroup.builtInIconRes(): Int? {
    return when (groupId) {
        BookGroup.IdAll -> R.drawable.ic_bookshelf_dock_all
        BookGroup.IdLocal -> R.drawable.ic_bookshelf_dock_local
        BookGroup.IdAudio -> R.drawable.ic_bookshelf_dock_audio
        BookGroup.IdVideo -> R.drawable.ic_bookshelf_dock_video
        else -> null
    }
}

private val MIN_GROUP_ITEM_WIDTH = 64.dp
