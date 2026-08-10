package io.legado.app.ui.rss.favorites

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.RssStar
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.rss.RssEmptyState
import io.legado.app.ui.rss.RssPageScaffold
import io.legado.app.ui.rss.RssRemoteImage
import io.legado.app.ui.rss.RssToolbarAction

private sealed interface FavoriteDeleteTarget {
    data class Item(val star: RssStar) : FavoriteDeleteTarget
    data class Group(val name: String) : FavoriteDeleteTarget
    data object All : FavoriteDeleteTarget
}

@Composable
internal fun RssFavoritesScreen(
    groups: List<String>,
    selectedGroup: String?,
    stars: List<RssStar>,
    onBack: () -> Unit,
    onGroupSelected: (String) -> Unit,
    onOpen: (RssStar) -> Unit,
    onDeleteItem: (RssStar) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onDeleteAll: () -> Unit
) {
    var deleteTarget by remember { mutableStateOf<FavoriteDeleteTarget?>(null) }
    RssPageScaffold(
        title = stringResource(R.string.favorite),
        onBack = onBack,
        actions = listOf(
            RssToolbarAction(
                R.id.menu_del_group,
                R.string.delete_select_group,
                R.drawable.ic_outline_delete,
                visible = selectedGroup != null
            ),
            RssToolbarAction(
                R.id.menu_del_all,
                R.string.delete_all,
                R.drawable.ic_outline_delete,
                visible = groups.isNotEmpty(),
                dividerBefore = true
            )
        ),
        onAction = {
            when (it) {
                R.id.menu_del_group -> selectedGroup?.let { group ->
                    deleteTarget = FavoriteDeleteTarget.Group(group)
                }
                R.id.menu_del_all -> deleteTarget = FavoriteDeleteTarget.All
            }
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            if (groups.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(groups, key = { it }) { group ->
                        Surface(
                            onClick = { onGroupSelected(group) },
                            shape = RoundedCornerShape(16.dp),
                            color = Color(
                                if (group == selectedGroup) {
                                    NgTheme.colors.selectedContainer
                                } else {
                                    NgTheme.colors.surfaceContainerLow
                                }
                            )
                        ) {
                            Text(
                                text = group,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                color = Color(NgTheme.colors.onSurface),
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            if (stars.isEmpty()) {
                RssEmptyState(stringResource(R.string.empty), Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(stars, key = { it.origin + '\u0000' + it.link }) { star ->
                        RssFavoriteRow(
                            star = star,
                            onClick = { onOpen(star) },
                            onLongClick = { deleteTarget = FavoriteDeleteTarget.Item(star) }
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        val label = when (target) {
            is FavoriteDeleteTarget.Item -> target.star.title
            is FavoriteDeleteTarget.Group -> target.name
            FavoriteDeleteTarget.All -> stringResource(R.string.all)
        }
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.draw)) },
            text = { Text("${stringResource(R.string.sure_del)}\n<$label>") },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (target) {
                            is FavoriteDeleteTarget.Item -> onDeleteItem(target.star)
                            is FavoriteDeleteTarget.Group -> onDeleteGroup(target.name)
                            FavoriteDeleteTarget.All -> onDeleteAll()
                        }
                        deleteTarget = null
                    }
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RssFavoriteRow(
    star: RssStar,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        color = Color(NgTheme.colors.surface).copy(alpha = 0.82f)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!star.image.isNullOrBlank()) {
                RssRemoteImage(
                    imageUrl = star.image,
                    sourceOrigin = star.origin,
                    placeholder = R.drawable.image_rss_article,
                    contentDescription = star.title,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = star.title,
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (!star.pubDate.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = star.pubDate.orEmpty(),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
