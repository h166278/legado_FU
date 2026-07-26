package io.legado.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import com.script.ScriptException
import io.legado.app.constant.EventBus
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.Status
import io.legado.app.data.appDb
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.ai.AiTtsStoryboardHelper
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.tts.ReadAloudTtsRouter
import io.legado.app.help.tts.ReadAloudAudioTask
import io.legado.app.help.tts.ReadAloudPlaylistProductionState
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsPlayerFactory
import io.legado.app.help.tts.TtsSynthesisContext
import io.legado.app.help.tts.TtsSpeedPolicy
import io.legado.app.help.tts.TtsScriptEngineClient
import io.legado.app.help.tts.isReadAloudSynthesisTextSilent
import io.legado.app.help.tts.prepareReadAloudAudioTasks
import io.legado.app.help.tts.normalizeStoryboardSynthesisText
import io.legado.app.help.tts.toTtsSynthesisContext
import io.legado.app.help.tts.forEngineCapabilities
import io.legado.app.help.tts.writeReadAloudAudioAtomically
import io.legado.app.help.tts.writeReadAloudAudioWithWavRetry
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.model.CacheBook
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.ui.book.character.ChapterStoryboard
import io.legado.app.ui.book.character.StoryboardScene
import io.legado.app.ui.book.character.StoryboardSegment
import io.legado.app.ui.book.character.StoryboardSegmentType
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.postEvent
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Response
import org.mozilla.javascript.WrappedException
import java.io.File
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

private const val SILENT_SOUND_FILE_SIZE = 2160L

/**
 * 在线朗读
 */
@SuppressLint("UnsafeOptInUsageError")
class HttpReadAloudService : BaseReadAloudService(),
    Player.Listener {
    private val exoPlayer: ExoPlayer by lazy {
        TtsPlayerFactory.create(this)
    }
    private val ttsFolderPath: String by lazy {
        cacheDir.absolutePath + File.separator + "httpTTS" + File.separator
    }
    private var legacySpeechRate: Int = AppConfig.speechRatePlay + 5
    private var downloadTask: Coroutine<*>? = null
    private var playIndexJob: Job? = null
    private var backgroundStoryboardPreloadJob: Job? = null
    private val downloadErrorNo = AtomicInteger()
    private var playErrorNo = 0
    private var ttsRouter: ReadAloudTtsRouter? = null
    private var speakItems: List<SpeakItem> = emptyList()
    private var speakItemIndex = 0
    private val downloadTaskActiveLock = Mutex()
    private val playlistProductionState = ReadAloudPlaylistProductionState()

    override fun onCreate() {
        super.onCreate()
        exoPlayer.addListener(this)
        applyPlaybackRate()
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadTask?.cancel()
        backgroundStoryboardPreloadJob?.cancel()
        exoPlayer.release()
        Coroutine.async {
            removeCacheFile()
        }
    }

    override fun play() {
        pageChanged = false
        exoPlayer.stop()
        if (!requestFocus()) return
        if (contentList.isEmpty()) {
            AppLog.putDebug("朗读列表为空")
            ReadBook.readAloud()
        } else {
            while (nowSpeak in contentList.indices && isReadAloudTextSilent()) {
                if (!skipCurrentReadAloudTextIfNeeded()) {
                    return
                }
            }
            updatePreparationStage(
                if (AppConfig.readAloudMultiRole) {
                    BaseReadAloudService.PREPARATION_STORYBOARD
                } else {
                    BaseReadAloudService.PREPARATION_AUDIO
                }
            )
            super.play()
            postEvent(EventBus.ALOUD_STATE, Status.LOADING)
            downloadAndPlayAudios()
        }
    }

    override fun playStop() {
        updatePreparationStage(BaseReadAloudService.PREPARATION_NONE)
        exoPlayer.stop()
        playIndexJob?.cancel()
        speakItems = emptyList()
        speakItemIndex = 0
    }

    private fun updateNextPos() {
        if (speakItems.isNotEmpty()) {
            updateNextPosBySpeakItem()
        } else {
            advanceReadAloudPosition()
        }
    }

    private fun updateNextPosBySpeakItem() {
        val currentItem = speakItems.getOrNull(speakItemIndex)
        if (currentItem == null) {
            advanceReadAloudPosition()
            return
        }
        if (speakItemIndex < speakItems.lastIndex) {
            val nextItem = speakItems[speakItemIndex + 1]
            speakItemIndex++
            if (nextItem.paragraphIndex == currentItem.paragraphIndex) {
                upTtsProgress(currentParagraphBaseNumber() + nextItem.start + 1)
                return
            }
            advanceToParagraph(nextItem.paragraphIndex)
        } else {
            advanceToParagraph(currentItem.paragraphIndex + 1)
            speakItems = emptyList()
            speakItemIndex = 0
        }
    }

    private fun advanceToParagraph(paragraphIndex: Int) {
        while (nowSpeak < paragraphIndex && nowSpeak in contentList.indices) {
            if (!advanceReadAloudPosition()) {
                return
            }
        }
    }

    private fun currentParagraphBaseNumber(): Int {
        return readAloudNumber - paragraphStartPos
    }

    private fun downloadAndPlayAudios() {
        if (!pause) {
            updatePreparationStage(
                if (AppConfig.readAloudMultiRole) {
                    BaseReadAloudService.PREPARATION_STORYBOARD
                } else {
                    BaseReadAloudService.PREPARATION_AUDIO
                }
            )
            postEvent(EventBus.ALOUD_STATE, Status.LOADING)
        }
        exoPlayer.clearMediaItems()
        downloadTask?.cancel()
        val productionToken = playlistProductionState.begin()
        downloadTask = execute {
            downloadTaskActiveLock.withLock {
                ensureActive()
                val httpTts = ReadAloud.httpTTS
                val engineV2 = ReadAloud.httpTtsEngineV2
                if (httpTts == null && engineV2 == null) {
                    throw NoStackTraceException("tts is null")
                }
                val storyboard = try {
                    loadCurrentAiStoryboard()
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    playlistProductionState.cancel(productionToken)
                    AppLog.put(
                        "AI听书分镜生成失败，朗读已暂停\n${error.localizedMessage}",
                        error,
                        true
                    )
                    pauseReadAloud()
                    return@execute
                }
                if (!pause) {
                    updatePreparationStage(BaseReadAloudService.PREPARATION_AUDIO)
                    postEvent(EventBus.ALOUD_STATE, Status.LOADING)
                }
                // 分镜准备可能按本书开关发现临时角色或自动绑定发音人，完成后再创建路由快照。
                ttsRouter = ReadAloudTtsRouter.createForCurrentBook()
                val nextStoryboardTask = startNextStoryboardPreload()
                scheduleAdditionalStoryboardPreloads(nextStoryboardTask)
                speakItems = buildSpeakItems(storyboard)
                speakItemIndex = 0
                if (speakItems.isEmpty()) {
                    playlistProductionState.cancel(productionToken)
                    nextChapter()
                    return@execute
                }
                try {
                    prepareSpeakFilesConcurrently(
                        httpTts = httpTts,
                        engineV2 = engineV2,
                        items = speakItems
                    ) { file ->
                        withContext(Main) {
                            val nextIndex = exoPlayer.mediaItemCount
                            exoPlayer.addMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                            if (playlistProductionState.onItemAppended(productionToken)) {
                                exoPlayer.seekTo(nextIndex, 0L)
                                exoPlayer.prepare()
                            }
                        }
                    }
                    withContext(Main) {
                        if (playlistProductionState.finish(productionToken)) {
                            finishPlaybackBatch()
                        }
                    }
                } catch (e: Throwable) {
                    playlistProductionState.cancel(productionToken)
                    if (e !is CancellationException) {
                        AppLog.put("朗读音频合成失败\n${e.localizedMessage}", e, true)
                        pauseReadAloud()
                    }
                    return@execute
                }
                val nextStoryboard = nextStoryboardTask?.await()
                if (nextStoryboard != null) {
                    preDownloadAudios(httpTts, engineV2, nextStoryboard)
                }
            }
        }.onError {
            playlistProductionState.cancel(productionToken)
            AppLog.put("朗读下载出错\n${it.localizedMessage}", it, true)
        }
    }

    private suspend fun preDownloadAudios(
        httpTts: HttpTTS?,
        engineV2: TtsEngineSetting?,
        storyboard: ChapterStoryboard?
    ) {
        val textChapter = ReadBook.nextTextChapter ?: return
        // 预生成下一章也可能新增角色或绑定，预下载应使用更新后的路由。
        ttsRouter = ReadAloudTtsRouter.createForCurrentBook()
        val contentList = textChapter.getNeedReadAloud(0, readAloudByPage, 0, 1)
            .splitToSequence("\n")
            .filter { it.isNotEmpty() }
            .toList()
        val preDownloadItems = buildSpeakItemsForContent(
            paragraphs = contentList,
            storyboard = storyboard,
            startParagraphIndex = 0,
            maxItems = 10
        )
        prepareSpeakFilesConcurrently(
            httpTts = httpTts,
            engineV2 = engineV2,
            items = preDownloadItems,
            cacheChapter = textChapter
        )
    }

    private suspend fun prepareSpeakFilesConcurrently(
        httpTts: HttpTTS?,
        engineV2: TtsEngineSetting?,
        items: List<SpeakItem>,
        cacheChapter: TextChapter? = null,
        onPrepared: suspend (File) -> Unit = {}
    ) {
        val globalConcurrency = AppConfig.readAloudWorkerCount
        val tasks = items.map { item ->
            val route = routeFor(engineV2, item.segment)
            val routedEngine = route?.engine ?: engineV2
            val synthesisText = item.synthesisText()
            val synthesisContext = item.synthesisContext?.forEngineCapabilities(routedEngine)
            val fileName = if (cacheChapter == null) {
                md5SpeakFileName(synthesisText, route, synthesisContext = synthesisContext)
            } else {
                md5SpeakFileName(synthesisText, route, cacheChapter, synthesisContext)
            }
            ReadAloudAudioTask(
                cacheKey = fileName,
                engineKey = routedEngine?.id ?: "legacy:${httpTts?.getKey().orEmpty()}",
                maxConcurrency = routedEngine?.effectiveMaxConcurrency(globalConcurrency)
                    ?: globalConcurrency,
                prepare = {
                    prepareSpeakFileWithFallback(
                        httpTts = httpTts,
                        engineV2 = engineV2,
                        item = item,
                        primaryRoute = route,
                        cacheChapter = cacheChapter,
                        synthesisText = synthesisText
                    )
                }
            )
        }
        prepareReadAloudAudioTasks(tasks, globalConcurrency, onPrepared)
    }

    private suspend fun prepareSpeakFileWithFallback(
        httpTts: HttpTTS?,
        engineV2: TtsEngineSetting?,
        item: SpeakItem,
        primaryRoute: ReadAloudTtsRouter.Route?,
        cacheChapter: TextChapter?,
        synthesisText: String
    ): File {
        val routes = buildList<ReadAloudTtsRouter.Route?> {
            add(primaryRoute)
            if (engineV2 != null) {
                addAll(ttsRouter?.fallbackRoutes(item.segment, engineV2, primaryRoute).orEmpty())
            }
        }.distinctBy { route ->
            listOf(route?.engine?.id, route?.voiceId, route?.styleId)
        }
        var lastError: Throwable? = null
        routes.forEachIndexed { index, route ->
            val routedEngine = route?.engine ?: engineV2
            val synthesisContext = item.synthesisContext?.forEngineCapabilities(routedEngine)
            val fileName = md5SpeakFileName(
                content = synthesisText,
                route = route,
                textChapter = cacheChapter ?: textChapter,
                synthesisContext = synthesisContext
            )
            try {
                return prepareSpeakFile(
                    httpTts = httpTts,
                    engineV2 = engineV2,
                    item = item,
                    route = route,
                    fileName = fileName,
                    synthesisContext = synthesisContext,
                    synthesisText = synthesisText
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                lastError = error
                val fallback = routes.getOrNull(index + 1)
                if (fallback != null) {
                    AppLog.put(
                        "TTS片段合成失败，改用${fallback.kind.displayName()}继续朗读" +
                            "\n片段：${item.text.take(80)}\n${error.localizedMessage}",
                        error
                    )
                }
            }
        }
        throw lastError ?: NoStackTraceException("TTS片段无可用合成路径")
    }

    private suspend fun prepareSpeakFile(
        httpTts: HttpTTS?,
        engineV2: TtsEngineSetting?,
        item: SpeakItem,
        route: ReadAloudTtsRouter.Route?,
        fileName: String,
        synthesisContext: TtsSynthesisContext?,
        synthesisText: String
    ): File {
        currentCoroutineContext().ensureActive()
        val speakText = synthesisText.replace(AppPattern.notReadAloudRegex, "")
        if (speakText.isEmpty()) {
            AppLog.put("阅读片段内容为空，使用无声音频代替。\n朗读文本：${item.sourceText}")
            if (!hasSpeakFile(fileName)) {
                createSilentSound(fileName)
            }
        } else {
            removeSilentSpeakFile(fileName)
            val target = getSpeakFileAsMd5(fileName)
            val routedEngine = route?.engine ?: engineV2
            if (routedEngine != null) {
                writeReadAloudAudioWithWavRetry(
                    target = target,
                    text = speakText,
                    onRejected = { issue, nextAttempt ->
                        AppLog.put(
                            "TTS音频疑似在句中截断，正在第${nextAttempt}次合成" +
                                    "\n实际时长：${issue.durationMillis}ms，文本长度：${issue.speechUnits}"
                        )
                    }
                ) {
                    getSpeakStream(
                        httpTts,
                        engineV2,
                        speakText,
                        route,
                        synthesisContext
                    )
                }
            } else if (!hasSpeakFile(fileName)) {
                createSpeakFile(
                    fileName,
                    getSpeakStream(httpTts, engineV2, speakText, route, synthesisContext)
                )
            }
        }
        return getSpeakFileAsMd5(fileName)
    }

    private fun SpeakItem.synthesisText(): String {
        return normalizeStoryboardSynthesisText(text, segment?.type)
    }

    private suspend fun getSpeakStream(
        httpTts: HttpTTS?,
        engineV2: TtsEngineSetting?,
        speakText: String,
        route: ReadAloudTtsRouter.Route?,
        synthesisContext: TtsSynthesisContext?
    ): InputStream {
        while (true) {
            try {
                val routedEngine = route?.engine ?: engineV2
                if (routedEngine != null) {
                    val stream = TtsScriptEngineClient.getSynthesisStream(
                        engine = routedEngine,
                        text = speakText,
                        voiceId = route?.voiceId ?: routedEngine.activeVoiceId,
                        styleId = route?.styleId,
                        speed = TtsSpeedPolicy.synthesisSpeed(routedEngine),
                        synthesisContext = synthesisContext,
                        coroutineContext = currentCoroutineContext()
                    )
                    currentCoroutineContext().ensureActive()
                    downloadErrorNo.set(0)
                    return stream
                }
                val legacyHttpTts = httpTts ?: throw NoStackTraceException("tts is null")
                val analyzeUrl = AnalyzeUrl(
                    legacyHttpTts.url,
                    speakText = speakText,
                    speakSpeed = legacySpeechRate,
                    source = legacyHttpTts,
                    readTimeout = 300 * 1000L,
                    coroutineContext = currentCoroutineContext()
                )
                val checkJs = legacyHttpTts.loginCheckJs
                val response = kotlin.runCatching {
                    analyzeUrl.getResponseAwait().let {
                        currentCoroutineContext().ensureActive()
                        if (!checkJs.isNullOrBlank()) {
                            analyzeUrl.evalJS(checkJs, it) as Response
                        } else {
                            it
                        }
                    }
                }.getOrElse { throwable ->
                    currentCoroutineContext().ensureActive()
                    if (!checkJs.isNullOrBlank()) {
                        val errResponse = analyzeUrl.getErrResponse(throwable)
                        try {
                            (analyzeUrl.evalJS(checkJs, errResponse) as Response).also {
                                if (it.code == 500) {
                                    throw throwable
                                }
                            }
                        } catch (_: Throwable) {
                            throw throwable
                        }
                    } else {
                        throw throwable
                    }
                }
                response.headers["Content-Type"]?.let { contentType ->
                    val contentType = contentType.substringBefore(";")
                    val ct = legacyHttpTts.contentType
                    if (contentType == "application/json" || contentType.startsWith("text/")) {
                        throw NoStackTraceException(response.body.string())
                    } else if (ct?.isNotBlank() == true) {
                        if (!contentType.matches(ct.toRegex())) {
                            throw NoStackTraceException(
                                "TTS服务器返回错误：" + response.body.string()
                            )
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                response.body.byteStream().let { stream ->
                    downloadErrorNo.set(0)
                    return stream
                }
            } catch (e: Exception) {
                when (e) {
                    is CancellationException -> throw e
                    is ScriptException, is WrappedException -> {
                        AppLog.put("js错误\n${e.localizedMessage}", e, true)
                        e.printOnDebug()
                        throw e
                    }

                    is SocketTimeoutException, is ConnectException -> {
                        if (downloadErrorNo.incrementAndGet() > 5) {
                            val msg = "tts超时或连接错误超过5次\n${e.localizedMessage}"
                            AppLog.put(msg, e, true)
                            throw e
                        }
                    }

                    else -> {
                        val errorCount = downloadErrorNo.incrementAndGet()
                        val msg = "tts下载错误\n${e.localizedMessage}"
                        AppLog.put(msg, e)
                        e.printOnDebug()
                        if (errorCount > 5) {
                            val msg1 = "TTS服务器连续5次错误，已暂停阅读。"
                            AppLog.put(msg1, e, true)
                        }
                        throw e
                    }
                }
            }
        }
    }

    private suspend fun loadCurrentAiStoryboard(): ChapterStoryboard? {
        if (!AppConfig.readAloudMultiRole) {
            return null
        }
        val book = ReadBook.book ?: return null
        val chapter = textChapter ?: return null
        val content = AiTtsStoryboardHelper.readAloudContentFromChapter(chapter, readAloudByPage)
            .takeIf { it.isNotBlank() } ?: return null
        val workKey = BookCharacterProfile.workKey(book.name, book.author)
        val characters = appDb.bookCharacterDao.getCharacters(workKey)
        return AiTtsStoryboardHelper.getOrGenerate(
            book = book,
            chapterIndex = ReadBook.durChapterIndex,
            chapterTitle = chapter.title,
            content = content,
            characters = characters
        )
    }

    private fun startNextStoryboardPreload(): Deferred<ChapterStoryboard?>? {
        if (!AppConfig.readAloudMultiRole) {
            return null
        }
        val preloadCount = AiConfig.readAloudStoryboardPreloadCount
        if (preloadCount <= 0) {
            return null
        }
        val chapterIndex = ReadBook.durChapterIndex + 1
        if (chapterIndex !in 0 until ReadBook.chapterSize) {
            return null
        }
        return lifecycleScope.async(IO) {
            preGenerateAiStoryboard(chapterIndex)
        }
    }

    private fun scheduleAdditionalStoryboardPreloads(
        nextStoryboardTask: Deferred<ChapterStoryboard?>?
    ) {
        if (!AppConfig.readAloudMultiRole || backgroundStoryboardPreloadJob?.isActive == true) {
            return
        }
        val preloadCount = AiConfig.readAloudStoryboardPreloadCount
        if (preloadCount <= 1) {
            return
        }
        val firstChapterIndex = ReadBook.durChapterIndex + 2
        val maxChapterIndex = minOf(
            ReadBook.durChapterIndex + preloadCount,
            ReadBook.chapterSize - 1
        )
        if (firstChapterIndex > maxChapterIndex) {
            return
        }
        backgroundStoryboardPreloadJob = lifecycleScope.launch(IO) {
            if (nextStoryboardTask?.await() == null) return@launch
            for (chapterIndex in firstChapterIndex..maxChapterIndex) {
                currentCoroutineContext().ensureActive()
                preGenerateAiStoryboard(chapterIndex)
            }
        }
    }

    private suspend fun preGenerateAiStoryboard(chapterIndex: Int): ChapterStoryboard? {
        val book = ReadBook.book ?: return null
        val chapter = loadStoryboardTextChapter(chapterIndex) ?: return null
        val content = AiTtsStoryboardHelper.readAloudContentFromChapter(chapter, readAloudByPage)
            .takeIf { it.isNotBlank() } ?: return null
        val workKey = BookCharacterProfile.workKey(book.name, book.author)
        val characters = appDb.bookCharacterDao.getCharacters(workKey)
        return runCatching {
            AiTtsStoryboardHelper.getOrGenerate(
                book = book,
                chapterIndex = chapterIndex,
                chapterTitle = chapter.title,
                content = content,
                characters = characters
            )
        }.onFailure {
            if (it !is CancellationException) {
                AppLog.put("AI听书分镜预处理失败，章节 $chapterIndex\n${it.localizedMessage}", it)
            }
        }.getOrNull()
    }

    private suspend fun loadStoryboardTextChapter(chapterIndex: Int): TextChapter? {
        textChapter?.takeIf { chapterIndex == ReadBook.durChapterIndex }?.let {
            return it
        }
        if (chapterIndex !in 0 until ReadBook.chapterSize) {
            return null
        }
        val book = ReadBook.book ?: return null
        val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex) ?: return null
        val rawContent = BookHelp.getContent(book, chapter) ?: run {
            val bookSource = ReadBook.bookSource ?: return null
            CacheBook.getOrCreate(bookSource, book).downloadAwait(chapter)
        }
        val contentProcessor = ContentProcessor.get(book.name, book.origin)
        val displayTitle = chapter.getDisplayTitle(
            contentProcessor.getTitleReplaceRules(),
            book.getUseReplaceRule(),
            replaceBook = book.toReplaceBook()
        )
        val contents = contentProcessor.getContent(
            book,
            chapter,
            rawContent,
            includeTitle = false
        )
        return ChapterProvider.getTextChapterAsync(
            lifecycleScope,
            book,
            chapter,
            displayTitle,
            contents,
            ReadBook.simulatedChapterSize
        ).also { generated ->
            generated.layoutChannel.receiveAsFlow().collect()
        }
    }

    private fun buildSpeakItems(storyboard: ChapterStoryboard?): List<SpeakItem> {
        return buildSpeakItemsForContent(
            paragraphs = contentList,
            storyboard = storyboard,
            startParagraphIndex = nowSpeak,
            maxItems = Int.MAX_VALUE
        )
    }

    private fun buildSpeakItemsForContent(
        paragraphs: List<String>,
        storyboard: ChapterStoryboard?,
        startParagraphIndex: Int,
        maxItems: Int
    ): List<SpeakItem> {
        val items = arrayListOf<SpeakItem>()
        val sceneByParagraph = storyboard?.scenes
            .orEmpty()
            .flatMap { scene ->
                scene.segments.map { segment -> segment.paragraphIndex to scene }
            }
            .toMap()
        paragraphs.forEachIndexed { index, originalText ->
            if (index < startParagraphIndex || items.size >= maxItems) return@forEachIndexed
            val readableStart = if (paragraphs === contentList) {
                readableStartOffset(index, originalText)
            } else {
                0
            }
            if (readableStart >= originalText.length) return@forEachIndexed
            val paragraphSegments = AiTtsStoryboardHelper.segmentsForParagraph(
                storyboard = storyboard,
                paragraphIndex = index,
                fallbackText = originalText
            )
            val paragraphItems = paragraphSegments.mapNotNull { segment ->
                segment.toSpeakItem(
                    index,
                    originalText,
                    readableStart,
                    sceneByParagraph[index]
                )
            }
            if (paragraphItems.isNotEmpty()) {
                items += paragraphItems.take(maxItems - items.size)
            } else {
                val readable = if (paragraphs === contentList) {
                    getReadAloudText(index)
                } else {
                    originalText
                }
                if (!isReadAloudSynthesisTextSilent(readable)) {
                    items += SpeakItem(
                        paragraphIndex = index,
                        text = readable,
                        start = readableStart,
                        end = originalText.length,
                        sourceText = originalText,
                        synthesisContext = null,
                        segment = StoryboardSegment(
                            type = StoryboardSegmentType.NARRATION,
                            paragraphIndex = index,
                            text = readable,
                            speakerName = null,
                            evidence = "旁白",
                            start = readableStart,
                            end = originalText.length
                        )
                    )
                }
            }
        }
        return items
    }

    private fun StoryboardSegment.toSpeakItem(
        paragraphIndex: Int,
        originalText: String,
        readableStart: Int,
        scene: StoryboardScene?
    ): SpeakItem? {
        val safeStart = maxOf(start, readableStart).coerceIn(0, originalText.length)
        val safeEnd = end.coerceIn(0, originalText.length)
        if (safeEnd <= safeStart) return null
        val speakText = originalText.substring(safeStart, safeEnd)
        // 只过滤音频项，正文段落与 paragraphIndex 保持不变，由播放推进逻辑跨过该段。
        if (isReadAloudSynthesisTextSilent(speakText)) return null
        return SpeakItem(
            paragraphIndex = paragraphIndex,
            text = speakText,
            start = safeStart,
            end = safeEnd,
            sourceText = originalText,
            synthesisContext = toTtsSynthesisContext(scene),
            segment = copy(
                paragraphIndex = paragraphIndex,
                text = speakText,
                start = safeStart,
                end = safeEnd
            )
        )
    }

    private fun readableStartOffset(index: Int, originalText: String): Int {
        val readableText = getReadAloudText(index)
        if (readableText.isBlank()) {
            return originalText.length
        }
        if (readableText == originalText) {
            return 0
        }
        if (originalText.endsWith(readableText)) {
            return originalText.length - readableText.length
        }
        return originalText.indexOf(readableText).takeIf { it >= 0 } ?: 0
    }

    private fun routeFor(
        engineV2: TtsEngineSetting?,
        segment: StoryboardSegment?
    ): ReadAloudTtsRouter.Route? {
        val baseEngine = engineV2 ?: return null
        return ttsRouter?.route(segment, baseEngine)
    }

    private fun ReadAloudTtsRouter.RouteKind.displayName(): String = when (this) {
        ReadAloudTtsRouter.RouteKind.DIALOGUE_FALLBACK -> "对白兜底"
        ReadAloudTtsRouter.RouteKind.NARRATOR -> "旁白"
        ReadAloudTtsRouter.RouteKind.ENGINE_DEFAULT -> "默认声音"
        ReadAloudTtsRouter.RouteKind.CHARACTER,
        ReadAloudTtsRouter.RouteKind.CAST_ROLE -> "角色声音"
    }

    private fun md5SpeakFileName(
        content: String,
        route: ReadAloudTtsRouter.Route?,
        textChapter: TextChapter? = this.textChapter,
        synthesisContext: TtsSynthesisContext? = null
    ): String {
        val scenarioMode = if (AppConfig.readAloudMultiRole) "multi" else "single"
        (route?.engine ?: ReadAloud.httpTtsEngineV2)?.let { engine ->
            val effectiveSpeed = TtsSpeedPolicy.synthesisSpeed(engine)
            return MD5Utils.md5Encode16(textChapter?.title ?: "") + "_" +
                    MD5Utils.md5Encode16(
                        listOf(
                            scenarioMode,
                            TtsScriptEngineClient.audioCacheKey(
                                engine = engine,
                                text = content,
                                voiceId = route?.voiceId ?: engine.activeVoiceId,
                                styleId = route?.styleId,
                                speed = effectiveSpeed,
                                synthesisContext = synthesisContext
                            )
                        ).joinToString("-|-")
                    )
        }
        return MD5Utils.md5Encode16(textChapter?.title ?: "") + "_" +
                MD5Utils.md5Encode16("$scenarioMode-|-${ReadAloud.httpTTS?.url}-|-$legacySpeechRate-|-$content")
    }

    private fun applyPlaybackRate() {
        val rate = if (ReadAloud.httpTtsEngineV2 != null) {
            TtsSpeedPolicy.playbackRate(AppConfig.speechRatePlay)
        } else {
            1f
        }
        exoPlayer.setPlaybackSpeed(rate)
    }

    private suspend fun createSilentSound(fileName: String) {
        writeReadAloudAudioAtomically(
            getSpeakFileAsMd5(fileName),
            resources.openRawResource(R.raw.silent_sound)
        )
    }

    private fun hasSpeakFile(name: String): Boolean {
        return FileUtils.exist("${ttsFolderPath}$name.mp3")
    }

    private fun removeSilentSpeakFile(name: String) {
        getSpeakFileAsMd5(name)
            .takeIf { it.isFile && it.length() == SILENT_SOUND_FILE_SIZE }
            ?.delete()
    }

    private fun getSpeakFileAsMd5(name: String): File {
        return File("${ttsFolderPath}$name.mp3")
    }

    private suspend fun createSpeakFile(name: String, inputStream: InputStream) {
        writeReadAloudAudioAtomically(getSpeakFileAsMd5(name), inputStream)
    }

    /**
     * 移除缓存文件
     */
    private fun removeCacheFile() {
        val titleMd5 = MD5Utils.md5Encode16(textChapter?.title ?: "")
        FileUtils.listDirsAndFiles(ttsFolderPath)?.forEach {
            val isSilentSound = it.length() == SILENT_SOUND_FILE_SIZE
            if ((!it.name.startsWith(titleMd5)
                        && System.currentTimeMillis() - it.lastModified() > 600000)
                || isSilentSound
            ) {
                FileUtils.delete(it.absolutePath)
            }
        }
    }


    override fun pauseReadAloud(abandonFocus: Boolean) {
        runOnPlayerThread {
            playIndexJob?.cancel()
            kotlin.runCatching { exoPlayer.pause() }
                .onSuccess { super.pauseReadAloud(abandonFocus) }
                .onFailure { AppLog.put("暂停在线朗读失败", it) }
        }
    }

    override fun resumeReadAloud() {
        runOnPlayerThread(::resumeReadAloudOnPlayerThread)
    }

    private fun resumeReadAloudOnPlayerThread() {
        if (pageChanged || exoPlayer.mediaItemCount == 0 ||
            exoPlayer.playbackState == Player.STATE_IDLE ||
            exoPlayer.playbackState == Player.STATE_ENDED
        ) {
            play()
            return
        }
        kotlin.runCatching { exoPlayer.play() }
            .onSuccess {
                super.resumeReadAloud()
                upPlayPos()
            }
            .onFailure { AppLog.put("继续在线朗读失败", it) }
    }

    private fun runOnPlayerThread(action: () -> Unit) {
        if (Looper.myLooper() == exoPlayer.applicationLooper) {
            action()
        } else {
            Handler(exoPlayer.applicationLooper).post(action)
        }
    }

    private fun upPlayPos() {
        playIndexJob?.cancel()
        val textChapter = textChapter ?: return
        playIndexJob = lifecycleScope.launch {
            val activeItem = speakItems.getOrNull(speakItemIndex)
            val progressBase = activeItem
                ?.takeIf { it.paragraphIndex == nowSpeak }
                ?.let { currentParagraphBaseNumber() + it.start }
                ?: readAloudNumber
            upTtsProgress(progressBase + 1)
            if (exoPlayer.duration <= 0) {
                return@launch
            }
            val speakTextLength = activeItem?.text?.length ?: contentList[nowSpeak].length
            if (speakTextLength <= 0) {
                return@launch
            }
            val playbackRate = exoPlayer.playbackParameters.speed.coerceAtLeast(0.1f)
            val sleep = maxOf(1L, (exoPlayer.duration / speakTextLength / playbackRate).toLong())
            val start = speakTextLength * exoPlayer.currentPosition / exoPlayer.duration
            for (i in start..speakTextLength.toLong()) {
                if (pageIndex + 1 < textChapter.pageSize
                    && progressBase + i > textChapter.getReadLength(pageIndex + 1)
                ) {
                    pageIndex++
                    ReadBook.moveToNextPage()
                    upTtsProgress(progressBase + i.toInt())
                }
                delay(sleep)
            }
        }
    }

    /**
     * 更新朗读速度
     */
    override fun upSpeechRate(reset: Boolean) {
        legacySpeechRate = AppConfig.speechRatePlay + 5
        if (ReadAloud.httpTtsEngineV2 != null) {
            applyPlaybackRate()
            if (!pause) {
                upPlayPos()
            }
        } else {
            refreshTtsRoute()
        }
    }

    override fun refreshTtsRoute() {
        playIndexJob?.cancel()
        downloadTask?.cancel()
        exoPlayer.stop()
        if (!pause) {
            postEvent(EventBus.ALOUD_STATE, Status.LOADING)
        }
        downloadAndPlayAudios()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        if (!ownsPlaybackState()) return
        when (playbackState) {
            Player.STATE_IDLE -> {
                // 空闲
            }

            Player.STATE_BUFFERING -> {
                if (!pause) {
                    postEvent(EventBus.ALOUD_STATE, Status.LOADING)
                }
            }

            Player.STATE_READY -> {
                // 准备好
                updatePreparationStage(BaseReadAloudService.PREPARATION_NONE)
                if (pause) return
                exoPlayer.play()
                upPlayPos()
                postEvent(EventBus.ALOUD_STATE, Status.PLAY)
            }

            Player.STATE_ENDED -> {
                if (playlistProductionState.onPlaybackEnded()) {
                    finishPlaybackBatch()
                }
            }
        }
    }

    private fun finishPlaybackBatch() {
        playErrorNo = 0
        updateNextPos()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)
        syncActualPlaybackState(isPlaying)
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (!ownsPlaybackState()) return
        when (reason) {
            Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED -> {
                if (!timeline.isEmpty && exoPlayer.playbackState == Player.STATE_IDLE) {
                    exoPlayer.prepare()
                }
            }

            else -> {}
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (!ownsPlaybackState()) return
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) return
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            playErrorNo = 0
        }
        updateNextPos()
        upPlayPos()
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        if (!ownsPlaybackState()) return
        AppLog.put("朗读错误\n${contentList[nowSpeak]}", error)
        deleteCurrentSpeakFile()
        playErrorNo++
        if (playErrorNo >= 5) {
            toastOnUi("朗读连续5次错误, 最后一次错误代码(${error.localizedMessage})")
            AppLog.put("朗读连续5次错误, 最后一次错误代码(${error.localizedMessage})", error)
            pauseReadAloud()
        } else {
            if (exoPlayer.hasNextMediaItem()) {
                exoPlayer.seekToNextMediaItem()
                exoPlayer.prepare()
            } else {
                exoPlayer.clearMediaItems()
                updateNextPos()
            }
        }
    }

    private fun deleteCurrentSpeakFile() {
        val mediaItem = exoPlayer.currentMediaItem ?: return
        val filePath = mediaItem.localConfiguration!!.uri.path!!
        File(filePath).delete()
    }

    override fun aloudServicePendingIntent(actionStr: String): PendingIntent? {
        return servicePendingIntent<HttpReadAloudService>(actionStr)
    }

    private data class SpeakItem(
        val paragraphIndex: Int,
        val text: String,
        val start: Int,
        val end: Int,
        val sourceText: String,
        val synthesisContext: TtsSynthesisContext?,
        val segment: StoryboardSegment?
    )

}
