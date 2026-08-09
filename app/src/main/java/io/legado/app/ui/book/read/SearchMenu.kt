package io.legado.app.ui.book.read

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.ui.book.searchContent.SearchResult
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.NgThemeSnapshot
import io.legado.app.utils.activity
import io.legado.app.utils.invisible
import io.legado.app.utils.visible

/** 阅读页内的全文搜索结果控制层。 */
class SearchMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val callBack: CallBack get() = activity as CallBack
    private var searchResults by mutableStateOf<List<SearchResult>>(emptyList())
    private var currentSearchResultIndex by mutableStateOf(-1)
    private var controlsVisible by mutableStateOf(false)
    private var themeSnapshot by mutableStateOf(ReadDrawerStyle.themeSnapshot(context))

    val selectedSearchResult: SearchResult?
        get() = searchResults.getOrNull(currentSearchResultIndex)
    val bottomMenuVisible: Boolean
        get() = isVisible && controlsVisible

    init {
        addView(
            ComposeView(context).apply {
                layoutParams = LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                )
                setContent {
                    NgAppTheme(snapshot = themeSnapshot, updateSystemBars = false) {
                        if (controlsVisible) {
                            SearchResultControls(
                                searchResults = searchResults,
                                currentIndex = currentSearchResultIndex,
                                onDismissControls = { runMenuOut() },
                                onAllResults = {
                                    runMenuOut {
                                        callBack.openSearchDrawer(selectedSearchResult?.query)
                                    }
                                },
                                onRestoreOrigin = {
                                    runMenuOut { callBack.restoreSearchOrigin() }
                                },
                                onPrevious = { navigateTo(currentSearchResultIndex - 1) },
                                onNext = { navigateTo(currentSearchResultIndex + 1) },
                                onExit = { callBack.exitSearchMenu() },
                            )
                        }
                    }
                }
            },
        )
    }

    fun upSearchResultList(resultList: List<SearchResult>) {
        searchResults = resultList.toList()
        if (searchResults.isEmpty()) {
            currentSearchResultIndex = -1
        } else if (currentSearchResultIndex !in searchResults.indices) {
            currentSearchResultIndex = 0
        }
    }

    fun runMenuIn() {
        themeSnapshot = ReadDrawerStyle.themeSnapshot(context)
        controlsVisible = true
        visible()
        callBack.upSystemUiVisibility()
    }

    fun runMenuOut(onMenuOutEnd: (() -> Unit)? = null) {
        controlsVisible = false
        invisible()
        onMenuOutEnd?.invoke()
        callBack.upSystemUiVisibility()
    }

    fun updateSearchInfo() {
        themeSnapshot = ReadDrawerStyle.themeSnapshot(context)
    }

    fun updateSearchResultIndex(updateIndex: Int) {
        currentSearchResultIndex = if (searchResults.isEmpty()) {
            -1
        } else {
            updateIndex.coerceIn(searchResults.indices)
        }
    }

    private fun navigateTo(index: Int) {
        if (index !in searchResults.indices) return
        updateSearchResultIndex(index)
        callBack.navigateToSearch(searchResults[index], index)
    }

    interface CallBack {
        fun openSearchDrawer(searchWord: String?)
        fun restoreSearchOrigin()
        fun upSystemUiVisibility()
        fun exitSearchMenu()
        fun navigateToSearch(searchResult: SearchResult, index: Int)
    }
}

@Composable
private fun SearchResultControls(
    searchResults: List<SearchResult>,
    currentIndex: Int,
    onDismissControls: () -> Unit,
    onAllResults: () -> Unit,
    onRestoreOrigin: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
) {
    val selected = searchResults.getOrNull(currentIndex)
    val contentColor = Color(NgTheme.colors.onSurface)
    val mutedColor = Color(NgTheme.colors.onSurfaceVariant)
    val backgroundInteraction = remember { MutableInteractionSource() }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = backgroundInteraction,
                    indication = null,
                    onClick = onDismissControls,
                ),
        )

        NgGlassSurface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(start = 36.dp, top = 8.dp, end = 36.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            style = NgGlassDefaults.floatingStyle(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(onClick = onRestoreOrigin),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_restore),
                        contentDescription = stringResource(R.string.search_restore_origin),
                        modifier = Modifier.size(19.dp),
                        tint = mutedColor.copy(alpha = 0.86f),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = selected?.query?.let {
                            stringResource(R.string.search_result_title, it)
                        } ?: stringResource(R.string.search_content),
                        color = contentColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = selected?.let {
                            stringResource(
                                R.string.search_result_position,
                                currentIndex + 1,
                                searchResults.size,
                                it.chapterTitle,
                            )
                        }.orEmpty(),
                        color = mutedColor.copy(alpha = 0.82f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(onClick = onExit),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.close),
                        modifier = Modifier.size(18.dp),
                        tint = mutedColor.copy(alpha = 0.82f),
                    )
                }
            }
        }

        NgGlassSurface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 8.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            style = NgGlassDefaults.floatingStyle(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchDockAction(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.search_all_results),
                    enabled = searchResults.isNotEmpty(),
                    contentColor = contentColor,
                    mutedColor = mutedColor,
                    onClick = onAllResults,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
                SearchDockDivider(mutedColor)
                SearchDockAction(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.search_previous_result),
                    enabled = currentIndex > 0,
                    contentColor = contentColor,
                    mutedColor = mutedColor,
                    onClick = onPrevious,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                    )
                }
                SearchDockDivider(mutedColor)
                SearchDockAction(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.search_next_result),
                    enabled = currentIndex in 0 until searchResults.lastIndex,
                    contentColor = contentColor,
                    mutedColor = mutedColor,
                    onClick = onNext,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchDockAction(
    modifier: Modifier,
    label: String,
    enabled: Boolean,
    contentColor: Color,
    mutedColor: Color,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val color = if (enabled) contentColor else mutedColor.copy(alpha = 0.42f)
    Row(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .height(40.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides color,
            ) {
                icon()
            }
        }
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchDockDivider(color: Color) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(20.dp)
            .background(color.copy(alpha = 0.14f)),
    )
}
