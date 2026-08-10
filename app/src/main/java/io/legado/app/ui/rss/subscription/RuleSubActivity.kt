package io.legado.app.ui.rss.subscription

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.RuleSub
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.association.ImportReplaceRuleDialog
import io.legado.app.ui.association.ImportRssSourceDialog
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.rss.RssComposeBinding
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 规则订阅管理。业务与导入对话框保持原实现，可见列表和编辑器使用 Compose。 */
class RuleSubActivity : BaseActivity<RssComposeBinding>() {

    override val binding by viewBinding(RssComposeBinding::inflate)
    private var items by mutableStateOf<List<RuleSub>>(
        emptyList(),
        referentialEqualityPolicy()
    )
    private var editingItem by mutableStateOf<RuleSub?>(
        null,
        referentialEqualityPolicy()
    )

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.root.setContent {
            NgAppTheme {
                RuleSubScreen(items = items, onAction = ::handleAction)
                editingItem?.let { item ->
                    RuleSubEditorDialog(
                        initial = item,
                        onDismiss = { editingItem = null },
                        onConfirm = ::saveSubscription
                    )
                }
            }
        }
        lifecycleScope.launch {
            appDb.ruleSubDao.flowAll().catch {
                AppLog.put("规则订阅界面获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect { items = it }
        }
    }

    private fun handleAction(action: RuleSubAction) {
        when (action) {
            RuleSubAction.Back -> finish()
            RuleSubAction.Add -> {
                editingItem = RuleSub(customOrder = appDb.ruleSubDao.maxOrder + 1)
            }
            is RuleSubAction.Open -> openSubscription(action.item)
            is RuleSubAction.Edit -> editingItem = action.item.copy()
            is RuleSubAction.Delete -> lifecycleScope.launch(IO) {
                appDb.ruleSubDao.delete(action.item)
            }
            is RuleSubAction.Reorder -> lifecycleScope.launch(IO) {
                val reordered = action.items.mapIndexed { index, item ->
                    item.copy(customOrder = index + 1)
                }
                appDb.ruleSubDao.update(*reordered.toTypedArray())
            }
        }
    }

    private fun openSubscription(ruleSub: RuleSub) {
        when (ruleSub.type) {
            0 -> showDialogFragment(ImportBookSourceDialog(ruleSub.url))
            1 -> showDialogFragment(ImportRssSourceDialog(ruleSub.url))
            2 -> showDialogFragment(ImportReplaceRuleDialog(ruleSub.url))
        }
    }

    private fun saveSubscription(ruleSub: RuleSub) {
        lifecycleScope.launch {
            if (ruleSub.url.isBlank()) {
                toastOnUi(getString(R.string.null_url))
                return@launch
            }
            val existing = withContext(IO) { appDb.ruleSubDao.findByUrl(ruleSub.url) }
            if (existing != null && existing.id != ruleSub.id) {
                toastOnUi("${getString(R.string.url_already)}(${existing.name})")
                return@launch
            }
            withContext(IO) { appDb.ruleSubDao.insert(ruleSub) }
            editingItem = null
        }
    }
}
