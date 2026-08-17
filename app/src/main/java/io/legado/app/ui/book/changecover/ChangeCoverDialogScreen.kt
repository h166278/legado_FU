package io.legado.app.ui.book.changecover

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.widget.anima.RefreshProgressBar
import io.legado.app.ui.widget.image.CoverImageView

@Composable
internal fun ChangeCoverDialogContent(
    covers: List<SearchBook>,
    searchState: Int,
    onRefreshToggle: () -> Unit,
    onCoverClick: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colorResource(R.color.ng_surface),
        shape = RoundedCornerShape(dimensionResource(R.dimen.ng_dialog_radius)),
    ) {
        Column(Modifier.fillMaxSize()) {
            ChangeCoverHeader(
                searchState = searchState,
                onRefreshToggle = onRefreshToggle,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .clip(RoundedCornerShape(dimensionResource(R.dimen.ng_radius_l)))
                    .background(colorResource(R.color.ng_surface_panel)),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = 12.dp,
                        bottom = 12.dp,
                    ),
                ) {
                    items(covers) { cover ->
                        ChangeCoverItem(
                            cover = cover,
                            onClick = { onCoverClick(cover.coverUrl.orEmpty()) },
                        )
                    }
                }
                ChangeCoverProgress(
                    searching = searchState == 1,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(2.dp),
                )
            }
        }
    }
}

@Composable
private fun ChangeCoverHeader(
    searchState: Int,
    onRefreshToggle: () -> Unit,
) {
    val iconRes = when (searchState) {
        1 -> R.drawable.ic_stop_black_24dp
        2 -> R.drawable.ic_play_outline_24dp
        else -> R.drawable.ic_refresh_black_24dp
    }
    val descriptionRes = when (searchState) {
        1 -> R.string.stop
        2 -> R.string.resume
        else -> R.string.refresh
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.change_cover_source),
            color = colorResource(R.color.ng_on_surface),
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onRefreshToggle,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = stringResource(descriptionRes),
                modifier = Modifier.size(24.dp),
                tint = colorResource(R.color.ng_on_surface),
            )
        }
    }
}

@Composable
private fun ChangeCoverItem(
    cover: SearchBook,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        val loadKey = ChangeCoverLoadKey(
            coverUrl = cover.coverUrl,
            name = cover.name,
            author = cover.author,
            origin = cover.origin,
        )
        AndroidView(
            factory = { context ->
                CoverImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
            },
            update = { imageView ->
                if (imageView.getTag(R.id.change_cover_load_key) != loadKey) {
                    imageView.setTag(R.id.change_cover_load_key, loadKey)
                    imageView.load(cover, false)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f),
        )
        Text(
            text = cover.originName,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            color = colorResource(R.color.primaryText),
            fontSize = 16.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChangeCoverProgress(
    searching: Boolean,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context -> RefreshProgressBar(context) },
        update = { it.isAutoLoading = searching },
        modifier = modifier,
    )
}

private data class ChangeCoverLoadKey(
    val coverUrl: String?,
    val name: String,
    val author: String,
    val origin: String,
)
