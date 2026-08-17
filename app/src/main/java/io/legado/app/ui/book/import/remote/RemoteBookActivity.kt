package io.legado.app.ui.book.import.remote

import android.net.Uri
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.remote.RemoteBook
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.about.NetworkLogDialog
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.ArchiveUtils
import io.legado.app.utils.FileDoc
import io.legado.app.utils.SelectDirectoryContract
import io.legado.app.utils.find
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 以 Compose 呈现远程书籍目录，沿用既有 WebDAV 与导入逻辑。 */
class RemoteBookActivity :
    VMBaseActivity<ComposeActivityBinding, RemoteBookViewModel>(),
    ServersDialog.Callback {

    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val viewModel by viewModels<RemoteBookViewModel>()
    override val bindNgToolbarMenu: Boolean = false

    private var items by mutableStateOf<List<RemoteBook>>(
        emptyList(),
        referentialEqualityPolicy(),
    )
    private var selectedItems by mutableStateOf<Set<RemoteBook>>(emptySet())
    private var query by mutableStateOf("")
    private var searchExpanded by mutableStateOf(false)
    private var pathText by mutableStateOf("/")
    private var isAtRoot by mutableStateOf(true)
    private var isLoading by mutableStateOf(false)
    private var currentSortKey by mutableStateOf(RemoteBookSort.Default)
    private var localBookTreeSelectListener: ((Boolean) -> Unit)? = null

    private val localBookTreeSelect = registerForActivityResult(SelectDirectoryContract()) {
        it.uri?.let { treeUri ->
            AppConfig.defaultBookTreeUri = treeUri.toString()
            localBookTreeSelectListener?.invoke(true)
        } ?: localBookTreeSelectListener?.invoke(false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        currentSortKey = viewModel.sortKey
        initContent()
        onBackPressedDispatcher.addCallback(this) {
            if (!goBackDir()) finish()
        }
        lifecycleScope.launch {
            if (!setBookStorage()) {
                finish()
                return@launch
            }
            if (!LocalConfig.webDavBookHelpVersionIsLast) {
                showHelp("webDavBookHelp")
            }
            launch {
                viewModel.dataFlow.conflate().collect { remoteBooks ->
                    items = remoteBooks.toList()
                    delay(500)
                }
            }
            viewModel.initData(::upPath)
        }
    }

    override fun observeLiveBus() {
        viewModel.permissionDenialLiveData.observe(this) {
            localBookTreeSelect.launch(null)
        }
    }

    private fun initContent() {
        binding.composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        binding.composeView.setContent {
            NgAppTheme {
                RemoteBookScreen(
                    items = items,
                    selectedItems = selectedItems,
                    query = query,
                    searchExpanded = searchExpanded,
                    pathText = pathText,
                    isAtRoot = isAtRoot,
                    isLoading = isLoading,
                    sortKey = currentSortKey,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onSearchExpandedChange = { expanded ->
                        if (expanded) searchExpanded = true else closeSearch()
                    },
                    onQueryChange = ::updateQuery,
                    onRefresh = ::upPath,
                    onGoUp = { goBackDir() },
                    onSortChange = ::changeSort,
                    onMenuAction = ::onMenuAction,
                    onItemClick = ::onItemClick,
                    onItemLongClick = ::onItemLongClick,
                    onToggleItem = ::toggleItem,
                    onSelectAll = ::selectAllVisible,
                    onInvertSelection = ::invertVisibleSelection,
                    onAddSelected = ::addSelected,
                )
            }
        }
    }

    private suspend fun setBookStorage() = suspendCancellableCoroutine { continuation ->
        localBookTreeSelectListener = { selected ->
            localBookTreeSelectListener = null
            if (continuation.isActive) continuation.resume(selected)
        }
        if (!AppConfig.defaultBookTreeUri.isNullOrBlank()) {
            localBookTreeSelectListener = null
            continuation.resume(true)
            return@suspendCancellableCoroutine
        }
        val storageHelp = String(assets.open("storageHelp.md").readBytes())
        alert(getString(R.string.select_book_folder), storageHelp) {
            okButton { localBookTreeSelect.launch(null) }
            cancelButton {
                localBookTreeSelectListener = null
                if (continuation.isActive) continuation.resume(false)
            }
            onCancelled {
                localBookTreeSelectListener = null
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    private fun updateQuery(value: String) {
        query = value
        viewModel.updateCallBackFlow(value)
    }

    private fun closeSearch() {
        searchExpanded = false
        updateQuery("")
    }

    private fun onMenuAction(itemId: Int) {
        when (itemId) {
            R.id.menu_server_config -> showDialogFragment<ServersDialog>()
            R.id.menu_help -> showHelp("webDavBookHelp")
            R.id.menu_log -> showDialogFragment<AppLogDialog>()
            R.id.menu_network_log -> showDialogFragment<NetworkLogDialog>()
        }
    }

    private fun changeSort(sortKey: RemoteBookSort) {
        if (viewModel.sortKey == sortKey) {
            viewModel.sortAscending = !viewModel.sortAscending
        } else {
            viewModel.sortAscending = true
            viewModel.sortKey = sortKey
        }
        currentSortKey = viewModel.sortKey
        upPath()
    }

    private fun goBackDir(): Boolean {
        if (viewModel.dirList.isEmpty()) return false
        viewModel.dirList.removeLastOrNull()
        upPath()
        return true
    }

    private fun upPath() {
        isAtRoot = viewModel.dirList.isEmpty()
        pathText = buildList {
            add(if (viewModel.isDefaultWebdav) "books" else "/")
            addAll(viewModel.dirList.map(RemoteBook::filename))
        }.joinToString("  ›  ")
        selectedItems = emptySet()
        viewModel.dataCallback?.clear()
        viewModel.loadRemoteBookList(viewModel.dirList.lastOrNull()?.path) {
            isLoading = it
        }
    }

    private fun onItemClick(remoteBook: RemoteBook) {
        when {
            remoteBook.isDir -> {
                viewModel.dirList.add(remoteBook)
                upPath()
            }

            !remoteBook.isOnBookShelf -> toggleItem(remoteBook)
            else -> startRead(remoteBook)
        }
    }

    private fun onItemLongClick(remoteBook: RemoteBook) {
        if (remoteBook.isOnBookShelf) addToBookShelfAgain(remoteBook)
    }

    private fun toggleItem(remoteBook: RemoteBook) {
        if (remoteBook.isDir || remoteBook.isOnBookShelf) return
        selectedItems = if (remoteBook in selectedItems) {
            selectedItems - remoteBook
        } else {
            selectedItems + remoteBook
        }
    }

    private fun selectAllVisible() {
        val selectable = items.filter { !it.isDir && !it.isOnBookShelf }
        selectedItems = if (selectable.isNotEmpty() && selectable.all(selectedItems::contains)) {
            selectedItems - selectable.toSet()
        } else {
            selectedItems + selectable
        }
    }

    private fun invertVisibleSelection() {
        var updated = selectedItems
        items.filter { !it.isDir && !it.isOnBookShelf }.forEach { item ->
            updated = if (item in updated) updated - item else updated + item
        }
        selectedItems = updated
    }

    private fun addSelected() {
        val selected = HashSet(selectedItems)
        if (selected.isEmpty()) return
        isLoading = true
        viewModel.addToBookshelf(selected) {
            selectedItems = emptySet()
            items = items.toList()
            isLoading = false
        }
    }

    override fun onDialogDismiss(tag: String) {
        viewModel.initData(::upPath)
    }

    private fun showRemoteBookDownloadAlert(
        remoteBook: RemoteBook,
        onDownloadFinish: (() -> Unit)? = null,
    ) {
        alert(R.string.draw, R.string.archive_not_found) {
            okButton {
                viewModel.addToBookshelf(hashSetOf(remoteBook)) {
                    onDownloadFinish?.invoke()
                }
            }
            noButton()
        }
    }

    private fun startRead(remoteBook: RemoteBook) {
        val downloadFileName = remoteBook.filename
        if (!ArchiveUtils.isArchive(downloadFileName)) {
            appDb.bookDao.getBookByFileName(downloadFileName)?.let(::startActivityForBook)
        } else {
            AppConfig.defaultBookTreeUri ?: return
            val archive = FileDoc.fromUri(Uri.parse(AppConfig.defaultBookTreeUri), true)
                .find(downloadFileName)
            if (archive == null) {
                showRemoteBookDownloadAlert(remoteBook) { startRead(remoteBook) }
            } else {
                onArchiveFileClick(archive)
            }
        }
    }

    private fun addToBookShelfAgain(remoteBook: RemoteBook) {
        alert(getString(R.string.sure), "是否重新加入书架？") {
            yesButton {
                isLoading = true
                viewModel.addToBookshelf(hashSetOf(remoteBook)) {
                    items = items.toList()
                    isLoading = false
                }
            }
            noButton()
        }
    }

    private fun onArchiveFileClick(fileDoc: FileDoc) {
        val fileNames = ArchiveUtils.getArchiveFilesName(fileDoc) {
            it.matches(AppPattern.bookFileRegex)
        }
        if (fileNames.size == 1) {
            val name = fileNames[0]
            appDb.bookDao.getBookByFileName(name)?.let(::startActivityForBook)
                ?: showImportAlert(fileDoc, name)
        } else {
            showSelectBookReadAlert(fileDoc, fileNames)
        }
    }

    private fun showSelectBookReadAlert(fileDoc: FileDoc, fileNames: List<String>) {
        if (fileNames.isEmpty()) {
            toastOnUi(R.string.unsupport_archivefile_entry)
            return
        }
        selector(R.string.start_read, fileNames) { _, name, _ ->
            appDb.bookDao.getBookByFileName(name)?.let(::startActivityForBook)
                ?: showImportAlert(fileDoc, name)
        }
    }

    private inline fun addArchiveToBookShelf(
        fileDoc: FileDoc,
        fileName: String,
        onSuccess: (Book) -> Unit,
    ) {
        LocalBook.importArchiveFile(fileDoc.uri, fileName) {
            it.contains(fileName)
        }.firstOrNull()?.run {
            onSuccess(this)
        }
    }

    private fun showImportAlert(fileDoc: FileDoc, fileName: String) {
        alert(R.string.draw, R.string.no_book_found_bookshelf) {
            okButton {
                addArchiveToBookShelf(fileDoc, fileName, ::startActivityForBook)
            }
            noButton()
        }
    }
}
