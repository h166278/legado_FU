package io.legado.app.ui.book.character

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookTtsCastRole
import io.legado.app.databinding.ActivityBookStoryboardBinding
import io.legado.app.databinding.ItemBookStoryboardCacheBinding
import io.legado.app.databinding.ItemBookStoryboardGroupBinding
import io.legado.app.databinding.ItemBookStoryboardGroupSegmentBinding
import io.legado.app.help.ai.AiTtsStoryboardHelper
import io.legado.app.help.config.AppConfig
import io.legado.app.help.tts.ReadAloudTtsRouter
import io.legado.app.help.tts.BookTtsCastingCoordinator
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.help.tts.TtsPlayerFactory
import io.legado.app.help.tts.TtsScriptEngineClient
import io.legado.app.help.tts.TtsSpeedPolicy
import io.legado.app.help.tts.normalizeStoryboardSynthesisText
import io.legado.app.help.tts.toTtsSynthesisContext
import io.legado.app.help.tts.forEngineCapabilities
import io.legado.app.help.tts.writeReadAloudAudioWithWavRetry
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.widget.NgActionPopup
import io.legado.app.ui.widget.NgActionPopupItem
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import splitties.init.appCtx

class BookStoryboardActivity : BaseActivity<ActivityBookStoryboardBinding>(),
    ItemTouchCallback.Callback {

    override val binding by viewBinding(ActivityBookStoryboardBinding::inflate)
    private val sceneAdapter by lazy { StoryboardAdapter() }
    private val cacheAdapter by lazy { StoryboardCacheAdapter() }
    private lateinit var workKey: String
    private var bookName: String = ""
    private var bookAuthor: String = ""
    private var previewJob: Job? = null
    private var storyboardJob: Job? = null
    private var renderJob: Job? = null
    private var previewPlayer: ExoPlayer? = null
    private var loadingAnimator: ObjectAnimator? = null
    private var canonicalCharacterIds: Set<Long> = emptySet()
    private var castRoleNames: Set<String> = emptySet()
    private var stableCastRoleIds: Set<Long> = emptySet()
    private var pendingCastRoleIds: Set<Long> = emptySet()
    private var renderedRouter: ReadAloudTtsRouter? = null
    private var renderedBaseEngine: TtsEngineSetting? = null
    private var showingCacheList = true
    private var cachedRowCount = 0
    private var quickDelete = false

    private data class StoryboardCacheRow(
        val chapterIndex: Int,
        val chapterTitle: String,
        val entry: AiTtsStoryboardHelper.CachedStoryboardEntry?,
        val isCurrent: Boolean
    )

    private data class StoryboardRenderState(
        val chapterTitle: String,
        val chapterIndex: Int,
        val scenes: List<StoryboardScene>,
        val visibleDialogueCount: Int,
        val visibleThoughtCount: Int,
        val canonicalCharacterIds: Set<Long>,
        val castRoleNames: Set<String>,
        val stableCastRoleIds: Set<Long>,
        val pendingCastRoleIds: Set<Long>,
        val router: ReadAloudTtsRouter?,
        val baseEngine: TtsEngineSetting?,
        val currentParagraphIndex: Int?
    )

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        bookName = intent.getStringExtra(BookCharacterActivity.EXTRA_BOOK_NAME).orEmpty()
        bookAuthor = intent.getStringExtra(BookCharacterActivity.EXTRA_BOOK_AUTHOR).orEmpty()
        workKey = intent.getStringExtra(BookCharacterActivity.EXTRA_WORK_KEY)
            ?: BookCharacterProfile.workKey(bookName, bookAuthor)
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = cacheAdapter
        ItemTouchHelper(ItemTouchCallback(this).apply {
            isCanSwipe = true
        }).attachToRecyclerView(binding.recyclerView)
        binding.ivStoryboardLoading.imageTintList = ColorStateList.valueOf(accentColor)
        binding.btnClose.setOnClickListener { navigateBack() }
        binding.btnMore.setOnClickListener { showStoryboardMenu() }
        onBackPressedDispatcher.addCallback(this) { navigateBack() }
        loadCacheList()
    }

    private fun navigateBack() {
        if (!showingCacheList) {
            stopPreview()
            loadCacheList()
        } else {
            finish()
        }
    }

    private fun loadCacheList() {
        showingCacheList = true
        stopPreview()
        renderJob?.cancel()
        renderJob = null
        setLoading(true, "正在加载分镜缓存…")
        storyboardJob?.cancel()
        storyboardJob = lifecycleScope.launch {
            val result = withContext(IO) {
                runCatching {
                    val book = ReadBook.book ?: error("当前书籍为空")
                    val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
                    val characters = appDb.bookCharacterDao.getCharacters(workKey)
                    val cached = AiTtsStoryboardHelper.listCachedStoryboards(book, chapters, characters)
                    val currentIndex = ReadBook.durChapterIndex
                    val currentTitle = ReadBook.curTextChapter?.title
                        ?: chapters.firstOrNull { it.index == currentIndex }?.title
                        ?: "当前章节"
                    val rows = cached.map { entry ->
                        StoryboardCacheRow(
                            chapterIndex = entry.chapterIndex,
                            chapterTitle = entry.chapterTitle,
                            entry = entry,
                            isCurrent = entry.chapterIndex == currentIndex
                        )
                    }.toMutableList()
                    if (rows.none { it.isCurrent }) {
                        rows += StoryboardCacheRow(
                            chapterIndex = currentIndex,
                            chapterTitle = currentTitle,
                            entry = null,
                            isCurrent = true
                        )
                    }
                    rows.sortedWith(
                        compareByDescending<StoryboardCacheRow> { it.isCurrent }
                            .thenByDescending { it.chapterIndex }
                    )
                }
            }
            result
                .onSuccess { rows ->
                    setLoading(false)
                    binding.tvChapterTitle.text = getString(R.string.book_storyboard)
                    val cachedCount = rows.count { it.entry != null }
                    cachedRowCount = cachedCount
                    updateCacheListHeader()
                    binding.recyclerView.adapter = cacheAdapter
                    cacheAdapter.submitRows(rows)
                    binding.tvEmpty.isVisible = false
                    binding.recyclerView.isVisible = true
                }
                .onFailure { renderEmpty("分镜缓存加载失败：${it.localizedMessage ?: "未知错误"}") }
            storyboardJob = null
        }
    }

    private fun showStoryboardMenu() {
        val items = if (showingCacheList) {
            listOf(
                NgActionPopupItem(
                    itemId = R.id.menu_quick_delete,
                    titleRes = R.string.character_quick_delete,
                    iconRes = R.drawable.ic_outline_delete,
                    checked = quickDelete
                )
            )
        } else {
            listOf(
                NgActionPopupItem(
                    itemId = R.id.menu_refresh,
                    titleRes = R.string.book_storyboard_regenerate,
                    iconRes = R.drawable.ic_refresh_black_24dp
                )
            )
        }
        NgActionPopup(
            context = this,
            items = items
        ) { item ->
            when (item.itemId) {
                R.id.menu_quick_delete -> {
                    quickDelete = !quickDelete
                    toastOnUi(
                        if (quickDelete) {
                            R.string.character_quick_delete_enabled
                        } else {
                            R.string.character_quick_delete_disabled
                        }
                    )
                }

                R.id.menu_refresh -> confirmRegenerateStoryboard()
            }
        }.show(binding.btnMore)
    }

    private fun confirmRegenerateStoryboard() {
        if (storyboardJob?.isActive == true) return
        alert(titleResource = R.string.book_storyboard_regenerate) {
            setMessage(getString(R.string.book_storyboard_regenerate_message))
            yesButton { loadStoryboard(forceRegenerate = true) }
            noButton()
        }
    }

    private fun loadStoryboard(forceRegenerate: Boolean = false) {
        val chapter = ReadBook.curTextChapter
        val content = chapter?.let { AiTtsStoryboardHelper.readAloudContentFromChapter(it) }.orEmpty()
        if (chapter == null || content.isBlank()) {
            renderEmpty(getString(R.string.book_storyboard_empty))
            return
        }
        showingCacheList = false
        if (forceRegenerate) stopPreview()
        setLoading(true, "正在生成 AI 分镜…")
        storyboardJob?.cancel()
        storyboardJob = lifecycleScope.launch {
            val result = withContext(IO) {
                val characters = appDb.bookCharacterDao.getCharacters(workKey)
                val book = ReadBook.book ?: return@withContext Result.failure<Pair<ChapterStoryboard, List<BookCharacter>>>(
                    IllegalStateException("当前书籍为空")
                )
                runCatching {
                    if (forceRegenerate) {
                        AiTtsStoryboardHelper.regenerate(
                            book = book,
                            chapterIndex = ReadBook.durChapterIndex,
                            chapterTitle = chapter.title,
                            content = content,
                            characters = characters
                        )
                    } else {
                        AiTtsStoryboardHelper.getOrGenerate(
                            book = book,
                            chapterIndex = ReadBook.durChapterIndex,
                            chapterTitle = chapter.title,
                            content = content,
                            characters = characters
                        )
                    } to characters
                }
            }
            result
                .onSuccess {
                    showStoryboard(it.first, ReadBook.durChapterIndex)
                    if (forceRegenerate) {
                        setResult(RESULT_OK)
                        if (BaseReadAloudService.isRun && AppConfig.readAloudMultiRole) {
                            ReadAloud.refreshTtsRoute(this@BookStoryboardActivity)
                        }
                    }
                }
                .onFailure { renderEmpty("AI 分镜生成失败：${it.localizedMessage ?: "未知错误"}") }
            storyboardJob = null
        }
    }

    private fun renderEmpty(message: String) = binding.run {
        setLoading(false)
        tvChapterTitle.text = if (showingCacheList) {
            getString(R.string.book_storyboard)
        } else {
            ReadBook.curTextChapter?.title ?: getString(R.string.book_storyboard)
        }
        tvSummary.text = ""
        if (showingCacheList) cachedRowCount = 0
        tvEmpty.text = message
        tvEmpty.isVisible = true
        recyclerView.isVisible = false
    }

    private fun showStoryboard(storyboard: ChapterStoryboard, chapterIndex: Int) {
        showingCacheList = false
        if (storyboard.scenes.isEmpty()) {
            renderEmpty(getString(R.string.book_storyboard_empty))
            return
        }
        binding.tvChapterTitle.text = storyboard.chapterTitle
        setLoading(true, "正在加载分镜…")
        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            val result = withContext(IO) {
                runCatching { prepareStoryboardRenderState(storyboard, chapterIndex) }
            }
            result
                .onSuccess(::renderStoryboard)
                .onFailure {
                    if (it !is CancellationException) {
                        renderEmpty("分镜加载失败：${it.localizedMessage ?: "未知错误"}")
                    }
                }
            renderJob = null
        }
    }

    private fun prepareStoryboardRenderState(
        storyboard: ChapterStoryboard,
        chapterIndex: Int
    ): StoryboardRenderState {
        val canonicalIds = appDb.bookCharacterDao.getCharacters(workKey).mapTo(mutableSetOf()) { it.id }
        val castRoles = appDb.bookCharacterDao.getTtsCastRoles(workKey)
            .filter { it.linkedCharacterId == null && it.isRoutableRole() }
        val scenes = storyboard.scenes.map { scene ->
            scene.copy(segments = scene.segments.filterNot { it.isChapterTitleSegment(storyboard.chapterTitle) })
        }.filter { it.segments.isNotEmpty() }
        return StoryboardRenderState(
            chapterTitle = storyboard.chapterTitle,
            chapterIndex = chapterIndex,
            scenes = scenes,
            visibleDialogueCount = scenes.sumOf { scene ->
                scene.segments.count { it.type == StoryboardSegmentType.DIALOGUE }
            },
            visibleThoughtCount = scenes.sumOf { scene ->
                scene.segments.count { it.type == StoryboardSegmentType.THOUGHT }
            },
            canonicalCharacterIds = canonicalIds,
            stableCastRoleIds = castRoles.filter { it.isVisibleTemporaryRole() }
                .mapTo(mutableSetOf()) { it.id },
            pendingCastRoleIds = castRoles.filter {
                it.identityState == BookTtsCastRole.IdentityState.PENDING
            }.mapTo(mutableSetOf()) { it.id },
            castRoleNames = castRoles.filter { it.isVisibleTemporaryRole() }
                .flatMap { role ->
                    buildList {
                        add(role.name)
                        GSON.fromJsonObject<List<String>>(role.aliasesJson).getOrNull().orEmpty().forEach(::add)
                    }
                }
                .map(BookTtsCastingCoordinator::normalizeIdentityName)
                .toSet(),
            router = ReadBook.book?.let { ReadAloudTtsRouter.create(it) },
            baseEngine = currentBaseEngine(),
            currentParagraphIndex = currentParagraphIndex(chapterIndex)
        )
    }

    private fun renderStoryboard(state: StoryboardRenderState) = binding.run {
        setLoading(false)
        canonicalCharacterIds = state.canonicalCharacterIds
        stableCastRoleIds = state.stableCastRoleIds
        pendingCastRoleIds = state.pendingCastRoleIds
        castRoleNames = state.castRoleNames
        renderedRouter = state.router
        renderedBaseEngine = state.baseEngine
        tvChapterTitle.text = state.chapterTitle
        tvSummary.text = buildList {
            add("${state.scenes.size} 个场景")
            add("${state.visibleDialogueCount} 段对白")
            if (state.visibleThoughtCount > 0) add("${state.visibleThoughtCount} 段心声")
        }.joinToString(" · ")
        if (state.scenes.isEmpty()) {
            renderEmpty(getString(R.string.book_storyboard_empty))
            return@run
        }
        recyclerView.adapter = sceneAdapter
        sceneAdapter.submitScenes(
            newScenes = state.scenes,
            chapterTitle = state.chapterTitle,
            chapterIndex = state.chapterIndex,
            currentParagraphIndex = state.currentParagraphIndex
        )
        btnMore.isVisible = state.chapterIndex == ReadBook.durChapterIndex
        tvEmpty.isVisible = false
        recyclerView.isVisible = true
    }

    private fun setLoading(loading: Boolean, message: String = "正在生成 AI 分镜…") = binding.run {
        layoutStoryboardLoading.isVisible = loading
        recyclerView.isVisible = !loading
        tvEmpty.isVisible = false
        btnMore.isVisible = !loading && if (showingCacheList) cachedRowCount > 0 else true
        btnMore.isEnabled = !loading
        btnMore.alpha = if (loading) 0.45f else 1f
        if (loading) {
            tvStoryboardLoading.text = message
            startLoadingAnimation()
        } else {
            stopLoadingAnimation()
        }
    }

    private fun startLoadingAnimation() {
        val icon = binding.ivStoryboardLoading
        if (loadingAnimator?.isStarted == true) return
        icon.rotation = 0f
        loadingAnimator = ObjectAnimator.ofFloat(icon, "rotation", 0f, 360f).apply {
            duration = 750L
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            start()
        }
    }

    private fun stopLoadingAnimation() {
        loadingAnimator?.cancel()
        loadingAnimator = null
        binding.ivStoryboardLoading.rotation = 0f
    }

    private inner class StoryboardCacheAdapter : RecyclerView.Adapter<StoryboardCacheHolder>() {

        private val rows = mutableListOf<StoryboardCacheRow>()

        fun submitRows(newRows: List<StoryboardCacheRow>) {
            rows.clear()
            rows.addAll(newRows)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoryboardCacheHolder {
            return StoryboardCacheHolder(
                ItemBookStoryboardCacheBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }

        override fun onBindViewHolder(holder: StoryboardCacheHolder, position: Int) {
            holder.bind(rows[position])
        }

        override fun getItemCount(): Int = rows.size

        fun getRow(position: Int): StoryboardCacheRow? = rows.getOrNull(position)

        fun applyDeletion(row: StoryboardCacheRow) {
            val position = rows.indexOfFirst { it.chapterIndex == row.chapterIndex }
            if (position < 0) return
            if (row.isCurrent) {
                rows[position] = row.copy(entry = null)
                notifyItemChanged(position)
            } else {
                rows.removeAt(position)
                notifyItemRemoved(position)
            }
        }

        fun restoreRow(row: StoryboardCacheRow) {
            val position = rows.indexOfFirst { it.chapterIndex == row.chapterIndex }
            if (position >= 0) notifyItemChanged(position)
        }
    }

    override fun getSwipeFlags(adapterPosition: Int, defaultFlags: Int): Int {
        return if (showingCacheList && cacheAdapter.getRow(adapterPosition)?.entry != null) {
            ItemTouchHelper.RIGHT
        } else {
            0
        }
    }

    override fun onSwiped(adapterPosition: Int, direction: Int) {
        val row = cacheAdapter.getRow(adapterPosition)
        if (direction != ItemTouchHelper.RIGHT || !showingCacheList || row?.entry == null) {
            if (adapterPosition >= 0) cacheAdapter.notifyItemChanged(adapterPosition)
            return
        }
        if (quickDelete) {
            deleteCachedStoryboard(row)
        } else {
            confirmDeleteCachedStoryboard(row, adapterPosition)
        }
    }

    private fun confirmDeleteCachedStoryboard(row: StoryboardCacheRow, adapterPosition: Int) {
        alert(titleResource = R.string.book_storyboard_delete_title) {
            setMessage(getString(R.string.book_storyboard_delete_message, row.chapterTitle))
            yesButton { deleteCachedStoryboard(row) }
            noButton { cacheAdapter.notifyItemChanged(adapterPosition) }
            onCancelled { cacheAdapter.notifyItemChanged(adapterPosition) }
        }
    }

    private fun deleteCachedStoryboard(row: StoryboardCacheRow) {
        val entry = row.entry ?: return
        lifecycleScope.launch(IO) {
            val result = runCatching { AiTtsStoryboardHelper.deleteCachedStoryboard(entry) }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                result
                    .onSuccess {
                        cacheAdapter.applyDeletion(row)
                        cachedRowCount = (cachedRowCount - 1).coerceAtLeast(0)
                        updateCacheListHeader()
                        toastOnUi(R.string.book_storyboard_delete_done)
                    }
                    .onFailure {
                        cacheAdapter.restoreRow(row)
                        toastOnUi(
                            getString(
                                R.string.book_storyboard_delete_failed,
                                it.localizedMessage ?: getString(R.string.unknown_error)
                            )
                        )
                    }
            }
        }
    }

    private fun updateCacheListHeader() = binding.run {
        tvSummary.text = "已缓存 $cachedRowCount 章"
        btnMore.isVisible = showingCacheList && cachedRowCount > 0
    }

    private inner class StoryboardCacheHolder(
        private val itemBinding: ItemBookStoryboardCacheBinding
    ) : RecyclerView.ViewHolder(itemBinding.root) {

        fun bind(row: StoryboardCacheRow) = itemBinding.run {
            tvTitle.text = row.chapterTitle
            tvCurrent.isVisible = row.isCurrent
            tvMeta.text = row.entry?.let { entry ->
                val storyboard = entry.storyboard
                "${storyboard.scenes.size} 个场景 · ${storyboard.segmentCount} 个片段"
            } ?: "尚未生成"
            root.setOnClickListener {
                row.entry?.let { entry ->
                    showStoryboard(entry.storyboard, row.chapterIndex)
                } ?: run {
                    if (row.isCurrent) loadStoryboard()
                }
            }
        }
    }

    private inner class StoryboardAdapter : RecyclerView.Adapter<SceneHolder>() {

        private val scenes = mutableListOf<StoryboardScene>()
        private val expandedSceneIndexes = mutableSetOf<Int>()
        private var chapterTitle: String = ""
        private var chapterIndex: Int? = null

        fun submitScenes(
            newScenes: List<StoryboardScene>,
            chapterTitle: String,
            chapterIndex: Int,
            currentParagraphIndex: Int?
        ) {
            if (this.chapterIndex != chapterIndex) expandedSceneIndexes.clear()
            this.chapterTitle = chapterTitle
            this.chapterIndex = chapterIndex
            scenes.clear()
            scenes.addAll(newScenes)
            expandedSceneIndexes.retainAll(newScenes.map { it.index }.toSet())
            if (expandedSceneIndexes.isEmpty()) {
                val initialScene = currentParagraphIndex?.let { paragraphIndex ->
                    newScenes.firstOrNull { scene ->
                        scene.segments.any { it.paragraphIndex == paragraphIndex }
                    }
                } ?: newScenes.firstOrNull()
                initialScene?.let { expandedSceneIndexes += it.index }
            }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SceneHolder {
            return SceneHolder(ItemBookStoryboardGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: SceneHolder, position: Int) {
            holder.bind(scenes[position])
        }

        override fun getItemCount(): Int = scenes.size

        fun toggle(scene: StoryboardScene) {
            if (!expandedSceneIndexes.add(scene.index)) expandedSceneIndexes.remove(scene.index)
            notifyItemChanged(scenes.indexOfFirst { it.index == scene.index })
        }

        fun isExpanded(scene: StoryboardScene): Boolean = scene.index in expandedSceneIndexes

        fun currentChapterTitle(): String = chapterTitle
    }

    private inner class SceneHolder(
        private val itemBinding: ItemBookStoryboardGroupBinding
    ) : RecyclerView.ViewHolder(itemBinding.root) {
        private var segmentRenderToken: Any? = null

        fun bind(scene: StoryboardScene) = itemBinding.run {
            val renderToken = Any()
            segmentRenderToken = renderToken
            tvTitle.text = scene.displayTitle(sceneAdapter.currentChapterTitle())
            tvMeta.text = buildList {
                scene.characters.joinToString("、").takeIf { it.isNotBlank() }?.let(::add)
                add("${scene.segments.size} 个片段")
            }.joinToString(" · ")
            val expanded = sceneAdapter.isExpanded(scene)
            tvExpand.rotation = if (expanded) 180f else 0f
            layoutSegments.isVisible = expanded
            layoutHeader.setOnClickListener { sceneAdapter.toggle(scene) }
            layoutSegments.removeAllViews()
            if (expanded) {
                renderSegments(scene, renderToken, startIndex = 0)
            }
        }

        private fun renderSegments(scene: StoryboardScene, renderToken: Any, startIndex: Int) {
            if (segmentRenderToken !== renderToken || !sceneAdapter.isExpanded(scene)) return
            val endIndex = (startIndex + SEGMENT_RENDER_BATCH_SIZE).coerceAtMost(scene.segments.size)
            for (index in startIndex until endIndex) {
                val segmentBinding = ItemBookStoryboardGroupSegmentBinding.inflate(
                    LayoutInflater.from(itemBinding.layoutSegments.context),
                    itemBinding.layoutSegments,
                    false
                )
                bindSegment(segmentBinding, scene, scene.segments[index])
                itemBinding.layoutSegments.addView(segmentBinding.root)
            }
            if (endIndex < scene.segments.size) {
                itemBinding.layoutSegments.postOnAnimation {
                    renderSegments(scene, renderToken, endIndex)
                }
            }
        }
    }

    private fun bindSegment(
        itemBinding: ItemBookStoryboardGroupSegmentBinding,
        scene: StoryboardScene,
        segment: StoryboardSegment
    ) = itemBinding.run {
        val identity = when (segment.type) {
            StoryboardSegmentType.NARRATION -> "旁白"
            StoryboardSegmentType.DIALOGUE, StoryboardSegmentType.THOUGHT ->
                segment.speakerName ?: segment.virtualSpeakerName()
        }
        tvType.text = identity
        tvType.setTextColor(accentColor)
        tvSpeaker.text = buildList {
            add(segment.type.displayName())
            add("第 ${segment.paragraphIndex + 1} 段")
            segment.identityStatus().takeIf { it != identity && it != segment.type.displayName() }?.let(::add)
        }.joinToString(" · ")
        tvVoice.text = actualVoiceLabel(scene, segment)
        tvText.text = segment.text
        val details = segment.details(scene)
        tvEvidence.text = details
        tvEvidence.isVisible = false
        root.setOnClickListener {
            if (details.isNotBlank()) tvEvidence.isVisible = !tvEvidence.isVisible
        }
        btnPreview.background = accentCircleBackground()
        btnPreview.imageTintList = ColorStateList.valueOf(accentColor)
        btnPreview.setOnClickListener { previewStoryboardSegment(scene, segment) }
    }

    private fun StoryboardScene.displayTitle(chapterTitle: String): String {
        val number = index.coerceAtLeast(1)
        val semanticTitle = title
            .replace(Regex("""^\s*(?:分镜|场景)\s*\d+\s*[·:：\-]?\s*"""), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        val shortTitle = semanticTitle.ifBlank { summary }
            .take(24)
            .takeIf {
                it.isNotBlank() && it.normalizedStoryboardTitle() != chapterTitle.normalizedStoryboardTitle()
            }
        return buildString {
            append("场景 $number")
            shortTitle?.let { append(" · ").append(it) }
        }
    }

    private fun StoryboardSegmentType.displayName(): String = when (this) {
        StoryboardSegmentType.NARRATION -> "旁白"
        StoryboardSegmentType.DIALOGUE -> "对白"
        StoryboardSegmentType.THOUGHT -> "心声"
    }

    private fun StoryboardSegment.identityStatus(): String = when {
        type == StoryboardSegmentType.NARRATION -> "旁白"
        speakerId != null && speakerId in canonicalCharacterIds -> "角色卡"
        castRoleId != null && castRoleId in stableCastRoleIds -> "临时角色"
        castRoleId != null && castRoleId in pendingCastRoleIds -> "待确认"
        speakerName?.let { BookTtsCastingCoordinator.normalizeIdentityName(it) in castRoleNames } == true -> "临时角色"
        speakerGender == StoryboardSegment.SpeakerGender.MALE -> "男性兜底"
        speakerGender == StoryboardSegment.SpeakerGender.FEMALE -> "女性兜底"
        else -> "待确认"
    }

    private fun actualVoiceLabel(scene: StoryboardScene, segment: StoryboardSegment): String {
        val baseEngine = renderedBaseEngine ?: return "未配置可用声音"
        val route = resolvedRoute(scene, segment)
        val engine = route?.engine ?: baseEngine
        val voiceId = route?.voiceId ?: engine.activeVoiceId
        val voiceName = engine.enabledVoices().firstOrNull { it.id == voiceId }?.name
            ?: voiceId?.takeIf { it.isNotBlank() }
            ?: "默认声音"
        return if (route?.bindingUnavailable == true) {
            "发音人不可用 · 已改用 $voiceName · ${engine.name}"
        } else {
            buildString {
                append(voiceName).append(" · ").append(engine.name)
                if (route?.sceneOverrideUsed == true) append(" · 场景音色")
            }
        }
    }

    private fun currentBaseEngine() =
        (ReadAloud.httpTtsEngineV2 ?: runCatching { TtsEngineStore.activeEngine() }.getOrNull())
            ?.takeIf { it.enabled && it.type == TtsEngineType.SCRIPT }

    private fun resolvedRoute(
        scene: StoryboardScene,
        segment: StoryboardSegment
    ): ReadAloudTtsRouter.Route? {
        val baseEngine = renderedBaseEngine ?: return null
        return renderedRouter?.route(segment, baseEngine, scene) ?: ReadAloudTtsRouter.Route(
            engine = baseEngine,
            voiceId = baseEngine.activeVoiceId,
            styleId = null,
            fallbackUsed = segment.type == StoryboardSegmentType.DIALOGUE ||
                segment.type == StoryboardSegmentType.THOUGHT
        )
    }

    private fun StoryboardSegment.details(scene: StoryboardScene): String {
        return buildList {
            performanceContext.joinToString("；")
                .ifBlank { scene.contextText }
                .compactDetail()
                .takeIf { it.isNotBlank() }
                ?.let { add("场景：$it") }
            performanceInstruction.compactDetail()
                .takeIf { it.isNotBlank() }
                ?.let { add("演播：$it") }
            evidence.compactDetail()
                .takeIf { it.isNotBlank() }
                ?.let { add("依据：$it") }
        }.distinct().joinToString("\n")
    }

    private fun String.compactDetail(): String = replace(Regex("\\s+"), " ").trim().take(240)

    private fun currentParagraphIndex(chapterIndex: Int): Int? {
        if (chapterIndex != ReadBook.durChapterIndex) return null
        val chapter = ReadBook.curTextChapter?.takeIf { it.isCompleted } ?: return null
        val pageSplit = appCtx.getPrefBoolean(PreferKey.readAloudByPage)
        val paragraphs = chapter.getParagraphs(pageSplit).filter { it.text.isNotBlank() }
        val position = ReadBook.durChapterPos
        return paragraphs.indexOfFirst { position in it.chapterIndices }.takeIf { it >= 0 }
    }

    private fun StoryboardSegment.virtualSpeakerName(): String {
        return when (speakerGender) {
            StoryboardSegment.SpeakerGender.MALE -> "对白男"
            StoryboardSegment.SpeakerGender.FEMALE -> "对白女"
            else -> if (type == StoryboardSegmentType.THOUGHT) "心声" else "待确认说话人"
        }
    }

    private fun StoryboardSegment.isChapterTitleSegment(chapterTitle: String): Boolean {
        if (type != StoryboardSegmentType.NARRATION || paragraphIndex != 0) {
            return false
        }
        val normalizedTitle = chapterTitle.normalizedStoryboardTitle()
        return normalizedTitle.isNotBlank() && text.normalizedStoryboardTitle() == normalizedTitle
    }

    private fun String.normalizedStoryboardTitle(): String {
        return filterNot { it.isWhitespace() || it == '\u3000' }
    }

    private fun accentCircleBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ColorUtils.withAlpha(accentColor, 0.12f))
        }
    }

    private fun previewStoryboardSegment(scene: StoryboardScene, segment: StoryboardSegment) {
        stopPreview()
        val text = normalizeStoryboardSynthesisText(segment.text, segment.type)
        if (text.isBlank()) {
            toastOnUi("片段内容为空")
            return
        }
        val baseEngine = (ReadAloud.httpTtsEngineV2 ?: runCatching { TtsEngineStore.activeEngine() }.getOrNull())
            ?.takeIf { it.enabled && it.type == TtsEngineType.SCRIPT }
        if (baseEngine == null) {
            toastOnUi("当前朗读引擎不支持片段试听")
            return
        }
        toastOnUi("正在合成片段试听...")
        previewJob = lifecycleScope.launch {
            val result = runCatching {
                val file = withContext(IO) {
                    val router = ReadBook.book?.let { ReadAloudTtsRouter.create(it) }
                    val route = router?.route(segment, baseEngine, scene)
                    val engine = (route?.engine ?: baseEngine)
                        .takeIf { it.enabled && it.type == TtsEngineType.SCRIPT }
                        ?: error("角色绑定的朗读引擎不可用")
                    val synthesisContext = segment
                        .toTtsSynthesisContext(scene)
                        ?.forEngineCapabilities(engine)
                    val file = File(cacheDir, "storyboard_preview_${System.currentTimeMillis()}.audio")
                    writeReadAloudAudioWithWavRetry(file, text) {
                        TtsScriptEngineClient.getSynthesisStream(
                            engine = engine,
                            text = text,
                            voiceId = route?.voiceId ?: engine.activeVoiceId,
                            styleId = route?.styleId,
                            synthesisContext = synthesisContext
                        )
                    }
                }
                previewPlayer?.release()
                previewPlayer = TtsPlayerFactory.create(this@BookStoryboardActivity).apply {
                    setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                    setPlaybackSpeed(TtsSpeedPolicy.playbackRate(AppConfig.speechRatePlay))
                    prepare()
                    play()
                }
            }
            result.onFailure {
                if (it !is CancellationException) {
                    toastOnUi("片段试听失败：${it.localizedMessage ?: it.javaClass.simpleName}")
                }
            }
        }
    }

    private fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        previewPlayer?.release()
        previewPlayer = null
    }

    override fun onDestroy() {
        storyboardJob?.cancel()
        storyboardJob = null
        renderJob?.cancel()
        renderJob = null
        stopPreview()
        stopLoadingAnimation()
        super.onDestroy()
    }

    private companion object {
        const val SEGMENT_RENDER_BATCH_SIZE = 4
    }

}
