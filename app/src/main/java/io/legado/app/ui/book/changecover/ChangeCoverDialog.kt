package io.legado.app.ui.book.changecover

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.ui.widget.dialog.ngDialogMaxHeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/** 换封面。业务状态保持不变，可见内容由 Compose 原样迁移。 */
class ChangeCoverDialog() : BaseComposeDialogFragment() {

    constructor(name: String, author: String) : this() {
        arguments = Bundle().apply {
            putString("name", name)
            putString("author", author)
        }
    }

    private val callBack: CallBack? get() = activity as? CallBack
    private val viewModel: ChangeCoverViewModel by viewModels()
    private var covers by mutableStateOf(emptyList<SearchBook>())
    private var searchState by mutableIntStateOf(0)

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(height = ngDialogMaxHeight(0.92f))
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.initData(arguments)
        (view as ComposeView).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    ChangeCoverDialogContent(
                        covers = covers,
                        searchState = searchState,
                        onRefreshToggle = viewModel::startOrStopSearch,
                        onCoverClick = ::changeTo,
                    )
                }
            }
        }
        viewModel.searchStateData.observe(viewLifecycleOwner) {
            searchState = it
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(STARTED) {
                viewModel.dataFlow.conflate().collect {
                    covers = it.toList()
                    delay(1000)
                }
            }
        }
    }

    private fun changeTo(coverUrl: String) {
        callBack?.coverChangeTo(coverUrl)
        dismissAllowingStateLoss()
    }

    interface CallBack {
        fun coverChangeTo(coverUrl: String)
    }
}
