package io.legado.app.ui.rss.source.debug

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.help.source.sortUrls
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.rss.RssComposeBinding
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch

class RssSourceDebugActivity :
    VMBaseActivity<RssComposeBinding, RssSourceDebugModel>() {

    override val binding by viewBinding(RssComposeBinding::inflate)
    override val viewModel by viewModels<RssSourceDebugModel>()

    private var query by mutableStateOf("")
    private var logs by mutableStateOf<List<String>>(emptyList())
    private var sortKinds by mutableStateOf<List<Pair<String, String>>>(emptyList())
    private var loading by mutableStateOf(false)
    private var sourceDialog by mutableStateOf<Pair<String, String>?>(null)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.root.setContent {
            NgAppTheme {
                RssSourceDebugScreen(
                    query = query,
                    logs = logs,
                    sortKinds = sortKinds,
                    loading = loading,
                    onBack = ::finish,
                    onQueryChange = { query = it },
                    onSearch = ::startSearch,
                    onShowListSource = {
                        sourceDialog = getString(R.string.list_src) to viewModel.listSrc.orEmpty()
                    },
                    onShowContentSource = {
                        sourceDialog = getString(R.string.content_src) to
                                viewModel.contentSrc.orEmpty()
                    }
                )
                sourceDialog?.let { (title, content) ->
                    RssDebugSourceDialog(title, content) { sourceDialog = null }
                }
            }
        }
        viewModel.initData(intent.getStringExtra("key")) {
            lifecycleScope.launch {
                sortKinds = viewModel.rssSource?.sortUrls()
                    ?.filter { it.second.isNotBlank() }
                    .orEmpty()
                sortKinds.firstOrNull { it.first.startsWith("ERROR:") }?.let {
                    logs = listOf("获取发现出错\n${it.second}")
                }
            }
        }
        viewModel.observe { state, message ->
            lifecycleScope.launch {
                logs = logs + message
                if (state == -1 || state == 1000) loading = false
            }
        }
    }

    private fun startSearch(value: String) {
        val normalized = value.ifBlank { "我的" }
        query = normalized
        logs = emptyList()
        viewModel.startDebug(
            normalized,
            start = { loading = true },
            error = {
                loading = false
                toastOnUi("未获取到书源")
            }
        )
    }
}
