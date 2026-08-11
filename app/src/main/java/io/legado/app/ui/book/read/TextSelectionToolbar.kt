@file:OptIn(ExperimentalFoundationApi::class)

package io.legado.app.ui.book.read

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.theme.NgTheme

internal const val TEXT_SELECTION_TOOLBAR_HEIGHT_DP = 52
internal const val TEXT_SELECTION_FIRST_PAGE_ACTION_COUNT = 4
private const val TEXT_SELECTION_TOOLBAR_BASE_WIDTH_DP = 280
private const val TEXT_SELECTION_PAGE_BUTTON_WIDTH_DP = 36
private const val SECONDARY_PAGE_ACTION_COUNT = 3
internal const val TEXT_SELECTION_MORE_ACTION_HEIGHT_DP = 44
internal const val TEXT_SELECTION_MORE_PANEL_WIDTH_DP = 156
private const val MORE_PANEL_VERTICAL_PADDING_DP = 4
private const val MORE_PANEL_MAX_VISIBLE_ROWS = 10
internal const val TEXT_SELECTION_MORE_PANEL_GAP_DP = 4
internal const val TEXT_SELECTION_MORE_PANEL_SCREEN_MARGIN_DP = 8

internal data class TextSelectionAction(
    val title: String,
    @param:DrawableRes val iconRes: Int,
    val iconBitmap: ImageBitmap? = null,
    val onClick: () -> Unit,
)

internal fun textSelectionToolbarWidthDp(primaryActionCount: Int): Int {
    return TEXT_SELECTION_TOOLBAR_BASE_WIDTH_DP +
        if (primaryActionCount > TEXT_SELECTION_FIRST_PAGE_ACTION_COUNT) {
            TEXT_SELECTION_PAGE_BUTTON_WIDTH_DP
        } else {
            0
        }
}

internal fun textSelectionMoreMenuHeightDp(actionCount: Int): Int {
    if (actionCount <= 0) return 0
    return actionCount.coerceAtMost(MORE_PANEL_MAX_VISIBLE_ROWS) *
        TEXT_SELECTION_MORE_ACTION_HEIGHT_DP + MORE_PANEL_VERTICAL_PADDING_DP * 2
}

@Composable
internal fun TextSelectionToolbar(
    primaryActions: List<TextSelectionAction>,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    moreMenuVisible: Boolean,
    onMoreMenuVisibleChange: (Boolean) -> Unit,
    onLongClick: () -> Unit,
) {
    val pages = buildList {
        add(primaryActions.take(TEXT_SELECTION_FIRST_PAGE_ACTION_COUNT))
        primaryActions
            .drop(TEXT_SELECTION_FIRST_PAGE_ACTION_COUNT)
            .chunked(SECONDARY_PAGE_ACTION_COUNT)
            .forEach(::add)
    }
    val page = currentPage.coerceIn(0, pages.lastIndex)

    NgGlassSurface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(12.dp),
        style = NgGlassDefaults.floatingStyle(),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (page == 0) {
                pages[page].forEach { action ->
                    TextSelectionActionItem(
                        action = action,
                        onLongClick = onLongClick,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(TEXT_SELECTION_FIRST_PAGE_ACTION_COUNT - pages[page].size) {
                    Spacer(Modifier.weight(1f))
                }
                MoreButton(
                    expanded = moreMenuVisible,
                        onExpandedChange = onMoreMenuVisibleChange,
                    )
                if (pages.size > 1) {
                    PageButton(
                        iconRes = R.drawable.ic_chevron_right_20,
                        contentDescription = stringResource(R.string.more_menu),
                        onClick = { onPageChange(1) },
                    )
                }
            } else {
                PageButton(
                    iconRes = R.drawable.ic_chevron_left_20,
                    contentDescription = stringResource(R.string.back),
                    onClick = { onPageChange(page - 1) },
                )
                pages[page].forEach { action ->
                    TextSelectionActionItem(
                        action = action,
                        onLongClick = onLongClick,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(SECONDARY_PAGE_ACTION_COUNT - pages[page].size) {
                    Spacer(Modifier.weight(1f))
                }
                if (page == pages.lastIndex) {
                    Spacer(Modifier.width(TEXT_SELECTION_PAGE_BUTTON_WIDTH_DP.dp))
                }
                MoreButton(
                    expanded = moreMenuVisible,
                    onExpandedChange = onMoreMenuVisibleChange,
                )
                if (page < pages.lastIndex) {
                    PageButton(
                        iconRes = R.drawable.ic_chevron_right_20,
                        contentDescription = stringResource(R.string.more_menu),
                        onClick = { onPageChange(page + 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TextSelectionActionItem(
    action: TextSelectionAction,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(9.dp))
            .combinedClickable(
                role = Role.Button,
                onClick = action.onClick,
                onLongClickLabel = stringResource(R.string.switch_selection_read_mode),
                onLongClick = onLongClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ActionIcon(
            action = action,
            sizeDp = 20,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = action.title,
            color = Color(NgTheme.colors.onSurface),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MoreButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .width(TEXT_SELECTION_PAGE_BUTTON_WIDTH_DP.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(9.dp))
            .combinedClickable(
                role = Role.Button,
                onClick = { onExpandedChange(!expanded) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_more_vert),
            contentDescription = stringResource(R.string.more_menu),
            modifier = Modifier.size(20.dp),
            tint = Color(NgTheme.colors.onSurface),
        )
    }
}

@Composable
private fun PageButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(TEXT_SELECTION_PAGE_BUTTON_WIDTH_DP.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(9.dp))
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = Color(NgTheme.colors.onSurface),
        )
    }
}

@Composable
internal fun TextSelectionMoreMenu(
    actions: List<TextSelectionAction>,
    onLongClick: () -> Unit,
) {
    NgGlassSurface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(12.dp),
        style = NgGlassDefaults.floatingStyle(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(MORE_PANEL_VERTICAL_PADDING_DP.dp))
            actions.forEach { action ->
                MoreActionItem(
                    action = action,
                    onLongClick = onLongClick,
                )
            }
            Spacer(Modifier.height(MORE_PANEL_VERTICAL_PADDING_DP.dp))
        }
    }
}

@Composable
private fun MoreActionItem(
    action: TextSelectionAction,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TEXT_SELECTION_MORE_ACTION_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                role = Role.Button,
                onClick = action.onClick,
                onLongClickLabel = stringResource(R.string.switch_selection_read_mode),
                onLongClick = onLongClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(10.dp))
        ActionIcon(
            action = action,
            sizeDp = 20,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = action.title,
            modifier = Modifier.weight(1f),
            color = Color(NgTheme.colors.onSurface),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 18.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(10.dp))
    }
}

@Composable
private fun ActionIcon(
    action: TextSelectionAction,
    sizeDp: Int,
    contentDescription: String? = null,
) {
    val iconBitmap = action.iconBitmap
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
            contentDescription = contentDescription,
            modifier = Modifier.size(sizeDp.dp),
        )
    } else {
        Icon(
            painter = painterResource(action.iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(sizeDp.dp),
            tint = Color(NgTheme.colors.onSurface),
        )
    }
}
