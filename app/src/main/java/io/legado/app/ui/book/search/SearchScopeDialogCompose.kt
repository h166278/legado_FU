package io.legado.app.ui.book.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgSurfaceVariant
import io.legado.app.ui.design.components.compose.NgButton
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.components.compose.NgSurface
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun SearchScopeDialog(
    groups: List<String>,
    sources: List<BookSourcePart>,
    onSourceQueryChange: (String) -> Unit,
    onApply: (SearchScope) -> Unit,
    onDismiss: () -> Unit
) {
    var sourceMode by remember { mutableStateOf(false) }
    var selectedGroups by remember { mutableStateOf(emptySet<String>()) }
    var selectedSource by remember { mutableStateOf<BookSourcePart?>(null) }
    var sourceQuery by remember { mutableStateOf("") }
    var showSourceFilter by remember { mutableStateOf(false) }

    fun close() {
        onSourceQueryChange("")
        onDismiss()
    }

    Dialog(
        onDismissRequest = ::close,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        NgSurface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f),
            variant = NgSurfaceVariant.OVERLAY
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.search_scope),
                    modifier = Modifier.weight(1f),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                if (sourceMode) {
                    IconButton(
                        onClick = {
                            showSourceFilter = !showSourceFilter
                            if (!showSourceFilter) {
                                sourceQuery = ""
                                onSourceQueryChange("")
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_screen),
                            contentDescription = stringResource(R.string.screen),
                            tint = Color(NgTheme.colors.onSurfaceVariant),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScopeModeTab(
                    text = stringResource(R.string.group),
                    selected = !sourceMode,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        sourceMode = false
                        showSourceFilter = false
                        sourceQuery = ""
                        onSourceQueryChange("")
                    }
                )
                ScopeModeTab(
                    text = stringResource(R.string.book_source),
                    selected = sourceMode,
                    modifier = Modifier.weight(1f),
                    onClick = { sourceMode = true }
                )
            }

            if (sourceMode && showSourceFilter) {
                NgSearchBar(
                    query = sourceQuery,
                    onQueryChange = { value ->
                        sourceQuery = value
                        onSourceQueryChange(value)
                    },
                    hint = stringResource(R.string.screen),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }

            HorizontalDivider(color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.5f))

            LazyColumn(modifier = Modifier.weight(1f)) {
                if (sourceMode) {
                    items(sources, key = { it.bookSourceUrl }) { source ->
                        SearchScopeSourceRow(
                            source = source,
                            selected = selectedSource == source,
                            onClick = { selectedSource = source }
                        )
                    }
                } else {
                    items(groups, key = { it }) { group ->
                        SearchScopeGroupRow(
                            group = group,
                            selected = group in selectedGroups,
                            onClick = {
                                selectedGroups = if (group in selectedGroups) {
                                    selectedGroups - group
                                } else {
                                    selectedGroups + group
                                }
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(NgTheme.colors.surfaceContainerHigh))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        onApply(SearchScope(""))
                        close()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.all_source),
                        color = Color(NgTheme.colors.primary),
                        fontSize = 15.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                NgButton(
                    onClick = ::close,
                    modifier = Modifier
                        .width(96.dp)
                        .height(42.dp),
                    variant = NgButtonVariant.TONAL
                ) {
                    Text(stringResource(R.string.cancel), fontSize = 15.sp)
                }
                NgButton(
                    onClick = {
                        val scope = if (sourceMode) {
                            selectedSource?.let(::SearchScope) ?: SearchScope("")
                        } else {
                            SearchScope(groups.filter { it in selectedGroups })
                        }
                        onApply(scope)
                        close()
                    },
                    modifier = Modifier
                        .width(96.dp)
                        .height(42.dp)
                ) {
                    Text(stringResource(R.string.ok), fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun ScopeModeTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(
                if (selected) Color(NgTheme.colors.selectedContainer)
                else Color.Transparent
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color(NgTheme.colors.primary)
            else Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SearchScopeGroupRow(group: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = selected, onCheckedChange = { onClick() })
        Spacer(Modifier.width(8.dp))
        Text(
            text = group,
            color = Color(NgTheme.colors.onSurface),
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchScopeSourceRow(
    source: BookSourcePart,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(
            text = source.bookSourceName,
            color = Color(NgTheme.colors.onSurface),
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
