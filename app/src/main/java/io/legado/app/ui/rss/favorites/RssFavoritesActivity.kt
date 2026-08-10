package io.legado.app.ui.rss.favorites

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.legado.app.base.BaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssStar
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.rss.RssComposeBinding
import io.legado.app.ui.rss.read.ReadRss
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/** RSS 收藏夹。分组与收藏数据链不变，页面和确认框使用 Compose。 */
class RssFavoritesActivity : BaseActivity<RssComposeBinding>() {

    override val binding by viewBinding(RssComposeBinding::inflate)
    private var groups by mutableStateOf<List<String>>(emptyList())
    private var selectedGroup by mutableStateOf<String?>(null)
    private var stars by mutableStateOf<List<RssStar>>(emptyList())
    private var starsJob: Job? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.root.setContent {
            NgAppTheme {
                RssFavoritesScreen(
                    groups = groups,
                    selectedGroup = selectedGroup,
                    stars = stars,
                    onBack = ::finish,
                    onGroupSelected = ::selectGroup,
                    onOpen = { ReadRss.readRss(this, it.toRssArticle()) },
                    onDeleteItem = { appDb.rssStarDao.delete(it.origin, it.link) },
                    onDeleteGroup = appDb.rssStarDao::deleteByGroup,
                    onDeleteAll = appDb.rssStarDao::deleteAll
                )
            }
        }
        lifecycleScope.launch {
            appDb.rssStarDao.flowGroups().catch {
                AppLog.put("订阅分组数据获取失败\n${it.localizedMessage}", it)
            }.distinctUntilChanged().flowOn(IO).collect { newGroups ->
                groups = newGroups
                val target = selectedGroup?.takeIf(newGroups::contains) ?: newGroups.firstOrNull()
                if (target != selectedGroup) selectGroup(target)
                if (target == null) stars = emptyList()
            }
        }
    }

    private fun selectGroup(group: String?) {
        selectedGroup = group
        starsJob?.cancel()
        if (group == null) {
            stars = emptyList()
            return
        }
        starsJob = lifecycleScope.launch {
            appDb.rssStarDao.flowByGroup(group).catch {
                AppLog.put("订阅收藏数据获取失败\n${it.localizedMessage}", it)
            }.flowOn(IO).collect { stars = it }
        }
    }
}
