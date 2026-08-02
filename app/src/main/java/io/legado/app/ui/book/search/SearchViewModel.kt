package io.legado.app.ui.book.search

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.config.AppConfig
import io.legado.app.model.webBook.SearchModel
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(application: Application) : BaseViewModel(application) {
    val handler = Handler(Looper.getMainLooper())
    val bookshelf: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val searchScope: SearchScope = SearchScope(AppConfig.searchScope)
    var searchKey: String = ""
    var hasMore = true
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    val history = _query.flatMapLatest { value ->
        value.trim().let { key ->
            if (key.isEmpty()) {
                appDb.searchKeywordDao.flowByTime()
            } else {
                appDb.searchKeywordDao.flowSearch(key)
            }
        }
    }.catch {
        AppLog.put("搜索界面获取搜索历史数据失败\n${it.localizedMessage}", it)
        emit(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val bookshelfMatches = _query.flatMapLatest { value ->
        value.trim().let { key ->
            if (key.isEmpty()) flowOf(emptyList()) else appDb.bookDao.flowSearch(key)
        }
    }.catch {
        AppLog.put("搜索界面获取书架匹配失败\n${it.localizedMessage}", it)
        emit(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val groups = appDb.bookSourceDao.flowEnabledGroups()
        .catch {
            AppLog.put("搜索界面获取书源分组失败\n${it.localizedMessage}", it)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _scopeSourceQuery = MutableStateFlow("")
    val scopeSources = _scopeSourceQuery.flatMapLatest { key ->
        if (key.isBlank()) {
            appDb.bookSourceDao.flowAll()
        } else {
            appDb.bookSourceDao.flowSearch(key.trim())
        }
    }.catch {
        AppLog.put("搜索范围获取书源失败\n${it.localizedMessage}", it)
        emit(emptyList<BookSourcePart>())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _events = Channel<SearchEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var searchID = 0L
    private var allSourcesTarget: Pair<String, String>? = null
    private var pendingBooks: List<SearchBook> = emptyList()
    private var lastBooksPublishTime = 0L
    private val publishBooksRunnable = Runnable { publishPendingBooks() }
    private val searchModel = SearchModel(viewModelScope, object : SearchModel.CallBack {

        override fun getSearchScope(): SearchScope {
            return searchScope
        }

        override fun onSearchStart() {
            _uiState.update { it.copy(isSearching = true) }
        }

        override fun onSearchSuccess(searchBooks: List<SearchBook>) {
            val result = allSourcesTarget?.let {
                getAllSources(it.first, it.second)
            } ?: searchBooks
            publishBooks(result)
        }

        override fun onSearchProgress(
            resultCount: Int,
            progress: Int,
            total: Int,
            sourceName: String
        ) {
            val value = SearchProgress(resultCount, progress, total, sourceName)
            _uiState.update { it.copy(progress = value) }
        }

        override fun onSearchFinish(isEmpty: Boolean, hasMore: Boolean) {
            this@SearchViewModel.hasMore = hasMore
            publishPendingBooks()
            _uiState.update {
                it.copy(isSearching = false, progress = null, hasMore = hasMore)
            }
            _events.trySend(SearchEvent.Finished(isEmpty))
        }

        override fun onSearchCancel(exception: Throwable?) {
            _uiState.update { it.copy(isSearching = false, progress = null) }
            exception?.let {
                context.toastOnUi(it.localizedMessage)
            }
        }

    })

    init {
        execute {
            appDb.bookDao.flowAll().mapLatest { books ->
                val keys = arrayListOf<String>()
                books.filterNot { it.isNotShelf }
                    .forEach {
                        keys.add("${it.name}-${it.author}")
                        keys.add(it.name)
                        keys.add(it.bookUrl)
                    }
                keys
            }.catch {
                AppLog.put("搜索界面获取书籍列表失败\n${it.localizedMessage}", it)
            }.collect {
                bookshelf.clear()
                bookshelf.addAll(it)
                _uiState.update { state ->
                    state.copy(bookshelfRevision = state.bookshelfRevision + 1)
                }
            }
        }.onError {
            AppLog.put("加载书架数据失败", it)
        }
    }

    fun isInBookShelf(book: SearchBook): Boolean {
        val name = book.name
        val author = book.author
        val bookUrl = book.bookUrl
        val key = if (author.isNotBlank()) "$name-$author" else name
        return bookshelf.contains(key) || bookshelf.contains(bookUrl)
    }

    /**
     * 开始搜索
     */
    fun search(key: String) {
        execute {
            if ((searchKey == key) || key.isNotEmpty()) {
                searchModel.cancelSearch()
                searchID = System.currentTimeMillis()
                publishBooks(emptyList(), immediate = true, activeQuery = key)
                searchKey = key
                hasMore = true
                allSourcesTarget = null
            }
            if (searchKey.isEmpty()) {
                return@execute
            }
            _uiState.update {
                it.copy(progress = SearchProgress(0, 0, 0, ""), hasMore = true)
            }
            searchModel.search(searchID, searchKey)
        }
    }

    /**
     * 停止搜索
     */
    fun stop() {
        searchModel.cancelSearch()
    }

    fun updateQuery(value: String) {
        if (_query.value == value) return
        stop()
        _query.value = value
    }

    fun updateScopeSourceQuery(value: String) {
        _scopeSourceQuery.value = value
    }

    fun submitSearch(value: String = _query.value) {
        val key = value.trim()
        if (key.isEmpty()) return
        _query.value = key
        _uiState.update { it.copy(manualStopped = false) }
        saveSearchKey(key)
        searchKey = ""
        search(key)
    }

    fun stopManually() {
        _uiState.update { it.copy(manualStopped = true) }
        stop()
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.isSearching && !state.manualStopped && state.hasMore && searchKey.isNotEmpty()) {
            _uiState.update { it.copy(isSearching = true) }
            search("")
        }
    }

    fun pause() {
        searchModel.pause()
    }

    fun resume() {
        searchModel.resume()
    }

    /**
     * 保存搜索关键字
     */
    fun saveSearchKey(key: String) {
        execute {
            appDb.searchKeywordDao.get(key)?.let {
                it.usage += 1
                it.lastUseTime = System.currentTimeMillis()
                appDb.searchKeywordDao.update(it)
            } ?: appDb.searchKeywordDao.insert(SearchKeyword(key, 1))
        }
    }

    /**
     * 清楚搜索关键字
     */
    fun clearHistory() {
        execute {
            appDb.searchKeywordDao.deleteAll()
        }
    }

    fun deleteHistory(searchKeyword: SearchKeyword) {
        execute {
            appDb.searchKeywordDao.delete(searchKeyword)
        }
    }

    fun showAllSources(book: SearchBook) {
        execute {
            allSourcesTarget = book.name to book.author
            getAllSources(book.name, book.author).let {
                val result = it.ifEmpty { listOf(book) }
                publishBooks(result, immediate = true)
            }
        }
    }

    @Synchronized
    private fun publishBooks(
        books: List<SearchBook>,
        immediate: Boolean = false,
        activeQuery: String? = null
    ) {
        pendingBooks = books
        val delay = lastBooksPublishTime + 1_000L - System.currentTimeMillis()
        handler.removeCallbacks(publishBooksRunnable)
        if (immediate || delay <= 0L) {
            publishPendingBooks(activeQuery)
        } else {
            handler.postDelayed(publishBooksRunnable, delay)
        }
    }

    @Synchronized
    private fun publishPendingBooks(activeQuery: String? = null) {
        handler.removeCallbacks(publishBooksRunnable)
        lastBooksPublishTime = System.currentTimeMillis()
        _uiState.update { state ->
            state.copy(
                books = pendingBooks,
                activeQuery = activeQuery ?: state.activeQuery,
                resultRevision = state.resultRevision + 1
            )
        }
    }

    private fun getAllSources(name: String, author: String): List<SearchBook> {
        return appDb.searchBookDao.getEnabledByNameAuthor(name, author)
    }

    override fun onCleared() {
        super.onCleared()
        handler.removeCallbacks(publishBooksRunnable)
        searchModel.close()
    }

    data class SearchProgress(
        val resultCount: Int,
        val progress: Int,
        val total: Int,
        val sourceName: String
    )

    data class SearchUiState(
        val books: List<SearchBook> = emptyList(),
        val isSearching: Boolean = false,
        val progress: SearchProgress? = null,
        val hasMore: Boolean = true,
        val activeQuery: String = "",
        val manualStopped: Boolean = false,
        val bookshelfRevision: Long = 0,
        val resultRevision: Long = 0
    )

    sealed interface SearchEvent {
        data class Finished(val isEmpty: Boolean) : SearchEvent
    }

}
