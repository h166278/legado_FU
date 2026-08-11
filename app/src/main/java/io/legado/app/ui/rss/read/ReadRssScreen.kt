package io.legado.app.ui.rss.read

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.rss.RssPageScaffold
import io.legado.app.ui.rss.RssToolbarAction

@Composable
internal fun ReadRssScreen(
    title: String,
    webView: WebView,
    progress: Int,
    starred: Boolean,
    ttsPlaying: Boolean,
    loginVisible: Boolean,
    customView: View?,
    onBack: () -> Unit,
    onAction: (Int) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        if (customView == null) {
            RssPageScaffold(
                title = title,
                onBack = onBack,
                actions = buildList {
                    add(
                        RssToolbarAction(
                            R.id.menu_rss_refresh,
                            R.string.refresh,
                            R.drawable.ic_refresh_black_24dp
                        )
                    )
                    add(
                        RssToolbarAction(
                            R.id.menu_rss_star,
                            if (starred) R.string.in_favorites else R.string.out_favorites,
                            if (starred) R.drawable.ic_star else R.drawable.ic_star_border
                        )
                    )
                    add(
                        RssToolbarAction(
                            R.id.menu_share_it,
                            R.string.share,
                            R.drawable.ic_share
                        )
                    )
                    add(
                        RssToolbarAction(
                            R.id.menu_aloud,
                            if (ttsPlaying) R.string.aloud_stop else R.string.read_aloud,
                            if (ttsPlaying) R.drawable.ic_stop_black_24dp else R.drawable.ic_volume_up
                        )
                    )
                    if (loginVisible) {
                        add(
                            RssToolbarAction(
                                R.id.menu_login,
                                R.string.login,
                                R.drawable.ic_lock_outline
                            )
                        )
                    }
                    add(
                        RssToolbarAction(
                            R.id.menu_browser_open,
                            R.string.open_in_browser,
                            R.drawable.ic_web_outline
                        )
                    )
                    add(
                        RssToolbarAction(
                            R.id.menu_read_record,
                            R.string.read_record,
                            R.drawable.ic_history
                        )
                    )
                    add(
                        RssToolbarAction(
                            R.id.menu_edit_source,
                            R.string.edit_source,
                            R.drawable.ic_edit
                        )
                    )
                    add(RssToolbarAction(R.id.menu_log, R.string.log, R.drawable.ic_code))
                    add(
                        RssToolbarAction(
                            R.id.menu_network_log,
                            R.string.network_request_log,
                            R.drawable.ic_web_outline
                        )
                    )
                },
                onAction = onAction
            ) {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = {
                            (webView.parent as? ViewGroup)?.removeView(webView)
                            webView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (progress in 0..99) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter),
                            color = Color(NgTheme.colors.primary),
                            trackColor = Color.Transparent
                        )
                    }
                }
            }
        } else {
            AndroidView(
                factory = {
                    (customView.parent as? ViewGroup)?.removeView(customView)
                    customView
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
