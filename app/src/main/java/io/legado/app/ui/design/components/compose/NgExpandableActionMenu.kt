package io.legado.app.ui.design.components.compose

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme

@Immutable
data class NgExpandableActionMenuItem(
    @IdRes val itemId: Int,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    val dividerBefore: Boolean = false,
    val children: List<NgExpandableActionMenuItem> = emptyList(),
    val title: String? = null,
    val checked: Boolean = false
)

/**
 * Reading NG 可原位展开的轻量操作菜单。
 *
 * 子项与一级项共用同一水平栅格，展开只改变内容高度，不创建二级窗口。
 */
@Composable
fun NgExpandableActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<NgExpandableActionMenuItem>,
    onItemClick: (NgExpandableActionMenuItem) -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    width: Dp = 152.dp
) {
    var expandedItemIds by remember(items) { mutableStateOf(emptySet<Int>()) }
    LaunchedEffect(expanded) {
        if (!expanded) expandedItemIds = emptySet()
    }

    val shape = RoundedCornerShape(NgTheme.shapes.largeDp.dp)
    val containerColor = colorResource(R.color.ng_surface_soft)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
        modifier = modifier.width(width),
        shape = shape,
        containerColor = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = NgTheme.effects.overlayElevationDp.dp
    ) {
        NgExpandableActionMenuRows(
            items = items,
            expandedItemIds = expandedItemIds,
            onToggle = { itemId ->
                expandedItemIds = if (itemId in expandedItemIds) {
                    expandedItemIds - itemId
                } else {
                    expandedItemIds + itemId
                }
            },
            onItemClick = onItemClick
        )
    }
}

@Composable
private fun NgExpandableActionMenuRows(
    items: List<NgExpandableActionMenuItem>,
    expandedItemIds: Set<Int>,
    onToggle: (Int) -> Unit,
    onItemClick: (NgExpandableActionMenuItem) -> Unit
) {
    items.forEach { item ->
        key(item.itemId) {
            if (item.dividerBefore) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                    color = Color(NgTheme.colors.outlineVariant).copy(
                        alpha = if (NgTheme.snapshot.isEInk) 1f else 0.35f
                    )
                )
            }
            val isExpanded = item.itemId in expandedItemIds
            NgExpandableActionMenuRow(
                item = item,
                isExpanded = isExpanded,
                onClick = {
                    if (item.children.isEmpty()) {
                        onItemClick(item)
                    } else {
                        onToggle(item.itemId)
                    }
                }
            )
            if (isExpanded) {
                NgExpandableActionMenuRows(
                    items = item.children,
                    expandedItemIds = expandedItemIds,
                    onToggle = onToggle,
                    onItemClick = onItemClick
                )
            }
        }
    }
}

@Composable
private fun NgExpandableActionMenuRow(
    item: NgExpandableActionMenuItem,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val contentColor = Color(NgTheme.colors.onSurface)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(item.iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = item.title ?: stringResource(item.titleRes),
            color = contentColor,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (item.checked) {
            Spacer(Modifier.width(10.dp))
            Icon(
                painter = painterResource(R.drawable.ng_ic_popup_selected),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
        if (item.children.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        if (isExpanded) R.drawable.ic_arrow_down else R.drawable.ic_arrow_right
                    ),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
