package io.legado.app.ui.main.rss

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.transparentNavBar
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.rss.article.RssSortActivity
import io.legado.app.ui.rss.favorites.RssFavoritesActivity
import io.legado.app.ui.rss.read.ReadRssActivity
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.rss.source.manage.RssSourceActivity
import io.legado.app.ui.rss.subscription.RuleSubActivity
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.openUrl
import io.legado.app.utils.startActivity
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * 订阅界面
 */
class RssFragment() : Fragment(), MainFragmentInterface {

    constructor(position: Int) : this() {
        arguments = Bundle().apply { putInt("position", position) }
    }

    override val position: Int? get() = arguments?.getInt("position")

    private val viewModel by viewModels<RssViewModel>()
    private lateinit var composeView: ComposeView
    private var searchQuery by mutableStateOf("")
    private var groups by mutableStateOf<List<String>>(emptyList())
    private var selectedGroup by mutableStateOf<String?>(null)
    private var sources by mutableStateOf<List<RssSource>>(
        emptyList(),
        referentialEqualityPolicy()
    )
    private var bottomInsetPx by mutableStateOf(0)
    private var groupsFlowJob: Job? = null
    private var rssFlowJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        composeView = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    RssScreen(
                        sources = sources,
                        query = searchQuery,
                        groups = groups,
                        selectedGroup = selectedGroup,
                        bottomInsetPx = bottomInsetPx,
                        transparentTopBar = requireContext().transparentNavBar ||
                                requireContext().getPrefBoolean(PreferKey.tNavBar, false),
                        onQueryChange = ::updateSearchQuery,
                        onGroupSelected = ::selectGroup,
                        onOpenRuleSubscription = { startActivity<RuleSubActivity>() },
                        onOpenFavorites = { startActivity<RssFavoritesActivity>() },
                        onOpenSourceManage = { startActivity<RssSourceActivity>() },
                        onOpenSource = ::openRss,
                        onSourceAction = ::handleSourceAction
                    )
                }
            }
        }
        return composeView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initGroupData()
        upRssFlowJob()
    }

    private fun initGroupData() {
        groupsFlowJob?.cancel()
        groupsFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.rssSourceDao.flowEnabledGroups().catch {
                AppLog.put("订阅界面获取分组数据失败\n${it.localizedMessage}", it)
            }.flowWithLifecycleAndDatabaseChange(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.RSS_SOURCE_TABLE_NAME
            ).conflate().collect { newGroups ->
                groups = newGroups
                val group = selectedGroup
                if (group != null && group !in newGroups) {
                    selectedGroup = null
                    upRssFlowJob()
                }
            }
        }
    }

    private fun updateSearchQuery(query: String) {
        if (searchQuery == query) return
        searchQuery = query
        if (query.isNotBlank()) selectedGroup = null
        upRssFlowJob()
    }

    private fun selectGroup(group: String?) {
        if (selectedGroup == group && searchQuery.isEmpty()) return
        selectedGroup = group
        searchQuery = ""
        upRssFlowJob()
    }

    private fun upRssFlowJob() {
        rssFlowJob?.cancel()
        val query = searchQuery.trim()
        val group = selectedGroup
        rssFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            when {
                query.isNotEmpty() -> appDb.rssSourceDao.flowEnabled(query)
                group != null -> appDb.rssSourceDao.flowEnabledByGroup(group)
                else -> appDb.rssSourceDao.flowEnabled()
            }.flowWithLifecycleAndDatabaseChange(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.RSS_SOURCE_TABLE_NAME
            ).catch {
                AppLog.put("订阅界面更新数据出错", it)
            }.conflate().flowOn(IO).collect {
                sources = it
            }
        }
    }

    private fun openRss(rssSource: RssSource) {
        if (rssSource.singleUrl) {
            viewModel.getSingleUrl(rssSource) { url ->
                if (url.startsWith("http", true)) {
                    ReadRssActivity.start(
                        requireContext(),
                        true,
                        rssSource.sourceUrl,
                        rssSource.sourceName,
                        url
                    )
                } else {
                    context?.openUrl(url)
                }
            }
        } else {
            viewModel.launchRssWithHtml(rssSource, {
                startActivity<RssSortActivity> {
                    putExtra("sourceUrl", rssSource.sourceUrl)
                }
            }) { html ->
                ReadRssActivity.start(
                    requireContext(),
                    true,
                    rssSource.sourceUrl,
                    rssSource.sourceName,
                    startHtml = html
                )
            }
        }
    }

    private fun handleSourceAction(rssSource: RssSource, actionId: Int) {
        when (actionId) {
            R.id.menu_edit -> startActivity<RssSourceEditActivity> {
                putExtra("sourceUrl", rssSource.sourceUrl)
            }

            R.id.menu_top -> viewModel.topSource(rssSource)
            R.id.menu_login -> startActivity<SourceLoginActivity> {
                putExtra("type", "rssSource")
                putExtra("key", rssSource.sourceUrl)
            }

            R.id.menu_disable -> viewModel.disable(rssSource)
            R.id.menu_del -> deleteSource(rssSource)
        }
    }

    private fun deleteSource(rssSource: RssSource) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + rssSource.sourceName)
            noButton()
            yesButton { viewModel.del(rssSource) }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.resolveFloatingBottomContentInset {
            bottomInsetPx = it
        }
    }

    override fun onPause() {
        if (this::composeView.isInitialized) composeView.clearFocus()
        super.onPause()
    }
}
