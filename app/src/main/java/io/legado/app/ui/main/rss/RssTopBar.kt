package io.legado.app.ui.main.rss

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.components.compose.NgSearchBarVariant
import io.legado.app.ui.design.theme.NgTheme

private const val RSS_FAVORITE_ITEM_ID = 0x53000001
private const val RSS_GROUP_ITEM_ID = 0x53000002
private const val RSS_ALL_GROUP_ITEM_ID = 0x53000003
private const val RSS_SOURCE_MANAGE_ITEM_ID = 0x53000004
private const val RSS_GROUP_CHILD_ITEM_ID_BASE = 0x53100000

@Composable
internal fun RssTopBar(
    query: String,
    groups: List<String>,
    selectedGroup: String?,
    transparent: Boolean,
    onQueryChange: (String) -> Unit,
    onGroupSelected: (String?) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSourceManage: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val containerColor = Color.White.copy(alpha = if (transparent) 0.8f else 1f)
    val menuItems = remember(groups, selectedGroup) {
        listOf(
            NgExpandableActionMenuItem(
                itemId = RSS_FAVORITE_ITEM_ID,
                titleRes = R.string.favorite,
                iconRes = R.drawable.ic_star
            ),
            NgExpandableActionMenuItem(
                itemId = RSS_GROUP_ITEM_ID,
                titleRes = R.string.group,
                iconRes = R.drawable.ic_groups,
                checked = selectedGroup != null,
                children = buildList {
                    add(
                        NgExpandableActionMenuItem(
                            itemId = RSS_ALL_GROUP_ITEM_ID,
                            titleRes = R.string.all,
                            iconRes = R.drawable.ic_check_source,
                            checked = selectedGroup == null
                        )
                    )
                    groups.forEachIndexed { index, group ->
                        add(
                            NgExpandableActionMenuItem(
                                itemId = RSS_GROUP_CHILD_ITEM_ID_BASE + index,
                                titleRes = 0,
                                iconRes = R.drawable.ic_groups,
                                title = group,
                                checked = group == selectedGroup
                            )
                        )
                    }
                }
            ),
            NgExpandableActionMenuItem(
                itemId = RSS_SOURCE_MANAGE_ITEM_ID,
                titleRes = R.string.rss_source_manage,
                iconRes = R.drawable.ic_settings,
                dividerBefore = true
            )
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NgSearchBar(
            query = query,
            onQueryChange = onQueryChange,
            hint = stringResource(R.string.search_rss_source),
            modifier = Modifier.weight(1f),
            variant = NgSearchBarVariant.TOOLBAR,
            containerColor = containerColor,
            hideHintOnFocus = true
        )
        Spacer(Modifier.width(12.dp))
        Box {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(containerColor)
                    .clickable { menuExpanded = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_grid_menu),
                    contentDescription = stringResource(R.string.group),
                    tint = Color(NgTheme.colors.onTopBar),
                    modifier = Modifier.size(20.dp)
                )
            }
            NgExpandableActionMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                items = menuItems,
                onItemClick = { item ->
                    menuExpanded = false
                    when (item.itemId) {
                        RSS_FAVORITE_ITEM_ID -> onOpenFavorites()
                        RSS_ALL_GROUP_ITEM_ID -> onGroupSelected(null)
                        RSS_SOURCE_MANAGE_ITEM_ID -> onOpenSourceManage()
                        else -> {
                            val index = item.itemId - RSS_GROUP_CHILD_ITEM_ID_BASE
                            onGroupSelected(groups.getOrNull(index))
                        }
                    }
                }
            )
        }
    }
}
