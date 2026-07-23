package io.legado.app.ui.book.read.aloud

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookChapter
import io.legado.app.databinding.DialogReadAloudModeSheetBinding
import io.legado.app.databinding.DialogReadAloudMoreSheetBinding
import io.legado.app.databinding.DialogReadAloudSpeedSheetBinding
import io.legado.app.databinding.DialogReadAloudTimerSheetBinding
import io.legado.app.help.IntentHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.tts.TtsEngineCapability
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsSpeedPolicy
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.view.ThemeSwitch
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.config.TtsVoiceOption
import io.legado.app.ui.config.TtsVoiceSelectionSheet
import io.legado.app.ui.widget.dialog.NgLongListBottomSheet
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.ui.widget.seekbar.SeekBarChangeListener
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class ReadAloudBottomSheet(layoutId: Int) : BaseDialogFragment(layoutId) {
    override fun onStart() {
        super.onStart()
        view?.setBackgroundResource(R.drawable.ng_bg_read_aloud_sheet)
        dialog?.window?.run {
            setBackgroundDrawableResource(R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
            val attr = attributes
            attr.dimAmount = 0.18f
            attr.gravity = Gravity.BOTTOM
            attributes = attr
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    protected fun applyThemeSheetBackground() {
        val background = runCatching {
            ThemeConfig.getBgImage(requireContext(), resources.displayMetrics)
        }.getOrNull() ?: return
        view?.background = ReadDrawerStyle.wrapTopRounded(background)
    }
}

abstract class ReadAloudSliderDialog(@LayoutRes layoutId: Int) : BaseDialogFragment(layoutId) {
    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(marginDp = 10)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundResource(R.drawable.ng_bg_read_aloud_slider_dialog)
    }
}

class ReadAloudTimerDialog : ReadAloudSliderDialog(R.layout.dialog_read_aloud_timer_sheet) {
    private val binding by viewBinding(DialogReadAloudTimerSheetBinding::bind)

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        seekTimer.applyReadAloudSliderStyle()
        val activeMinute = BaseReadAloudService.timeMinute
        val initialMinute = if (activeMinute > 0) {
            activeMinute
        } else {
            AppConfig.ttsTimer
        }.coerceIn(0, seekTimer.max)
        seekTimer.progress = initialMinute
        upTitle(initialMinute)
        seekTimer.setOnSeekBarChangeListener(object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                upTitle(progress)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val safeContext = this@ReadAloudTimerDialog.context ?: return
                val minute = seekBar.progress.coerceIn(0, seekBar.max)
                AppConfig.ttsTimer = minute
                ReadAloud.setTimer(safeContext, minute)
            }
        })
    }

    private fun upTitle(minute: Int) {
        binding.tvTitle.text = if (minute <= 0) {
            "定时关闭"
        } else {
            "定时关闭 ${minute}分钟"
        }
    }
}

class ReadAloudSpeedDialog : ReadAloudSliderDialog(R.layout.dialog_read_aloud_speed_sheet) {
    private val binding by viewBinding(DialogReadAloudSpeedSheetBinding::bind)

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        seekSpeed.applyReadAloudSliderStyle()
        seekSpeed.progress = AppConfig.ttsSpeechRate.coerceIn(0, seekSpeed.max)
        upTitle(seekSpeed.progress)
        seekSpeed.setOnSeekBarChangeListener(object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                upTitle(progress)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val safeContext = this@ReadAloudSpeedDialog.context ?: return
                AppConfig.ttsFlowSys = false
                AppConfig.ttsSpeechRate = seekBar.progress
                (activity as? ReadAloudPlayerActivity)?.refreshPlaybackSpeedLabel()
                ReadAloud.upTtsSpeechRate(safeContext)
                if (BaseReadAloudService.isPlay() && ReadAloud.httpTtsEngineV2 == null) {
                    ReadAloud.pause(safeContext)
                    ReadAloud.resume(safeContext)
                }
            }
        })
    }

    private fun upTitle(progress: Int) {
        binding.tvTitle.text = "播放速度 ${TtsSpeedPolicy.playbackLabel(progress)}"
    }
}

class ReadAloudModeSheet(
    private val activity: ReadAloudPlayerActivity
) : ReadAloudBottomSheet(R.layout.dialog_read_aloud_mode_sheet) {

    private val binding by viewBinding(DialogReadAloudModeSheetBinding::bind)

    override fun onStart() {
        super.onStart()
        applyThemeSheetBackground()
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        renderState()
        cardSingleRole.setOnClickListener {
            selectMultiRole(false)
        }
        cardMultiRole.setOnClickListener {
            selectMultiRole(true)
        }
        itemStoryboardResult.setOnClickListener {
            activity.openStoryboardResult()
            dismissAllowingStateLoss()
        }
    }

    private fun renderState() = binding.run {
        val multiRole = AppConfig.readAloudMultiRole
        cardSingleRole.isSelected = !multiRole
        cardMultiRole.isSelected = multiRole
        applyReadAloudModeCardStyles()
        layoutMultiRoleDetails.isVisible = multiRole
        if (multiRole) {
            val engine = TtsEngineStore.engine(AppConfig.multiRoleTtsEngineId)
            textMultiRoleEngine.text = engine?.name ?: "未选择多人 TTS 引擎"
            bindStoryboardCapabilities(engine)
        }
        val storyboardAlpha = if (multiRole) 1f else 0.42f
        itemStoryboardResult.isEnabled = multiRole
        itemStoryboardResult.alpha = storyboardAlpha
    }

    private fun applyReadAloudModeCardStyles() = binding.run {
        val safeContext = root.context
        val activeColor = safeContext.accentColor
        val innerSurfaceColor = ContextCompat.getColor(safeContext, R.color.ng_surface)
        val textColor = ContextCompat.getColor(safeContext, R.color.ng_on_surface)
        val inactiveIconColor = ContextCompat.getColor(safeContext, R.color.ng_on_surface_variant)
        layoutReadAloudMode.setBackgroundResource(R.drawable.ng_bg_settings_item)
        layoutReadAloudMode.elevation = 0f
        layoutMultiRoleDetails.background = GradientDrawable().apply {
            cornerRadius = 14.dpToPx().toFloat()
            setColor(innerSurfaceColor)
        }
        itemStoryboardResult.background = GradientDrawable().apply {
            cornerRadius = 18.dpToPx().toFloat()
            setColor(innerSurfaceColor)
        }
        itemStoryboardResult.elevation = 0f
        val cards = listOf(
            Triple(cardSingleRole, iconSingleRole, titleSingleRole),
            Triple(cardMultiRole, iconMultiRole, titleMultiRole)
        )
        cards.forEach { (card, icon, title) ->
            val selected = card.isSelected
            card.background = GradientDrawable().apply {
                cornerRadius = 14.dpToPx().toFloat()
                setColor(innerSurfaceColor)
            }
            icon.imageTintList = ColorStateList.valueOf(if (selected) activeColor else inactiveIconColor)
            title.setTextColor(if (selected) activeColor else textColor)
            title.typeface = Typeface.defaultFromStyle(
                if (selected) Typeface.BOLD else Typeface.NORMAL
            )
        }
    }

    private fun selectMultiRole(enabled: Boolean) {
        activity.setMultiRoleEnabled(enabled)
        renderState()
    }

    private fun bindStoryboardCapabilities(engine: TtsEngineSetting?) = binding.run {
        layoutMultiRoleCapabilities.removeAllViews()
        storyboardCapabilityTags(engine).forEach { tag ->
            layoutMultiRoleCapabilities.addView(
                TextView(layoutMultiRoleCapabilities.context).apply {
                    text = tag.text
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    maxLines = 1
                    setTextColor(ContextCompat.getColor(context, tag.colorRes))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setBackgroundResource(tag.backgroundRes)
                    setPadding(8.dpToPx(), 0, 8.dpToPx(), 0)
                    layoutParams = FlexboxLayout.LayoutParams(
                        FlexboxLayout.LayoutParams.WRAP_CONTENT,
                        24.dpToPx()
                    ).apply {
                        marginEnd = 6.dpToPx()
                        bottomMargin = 4.dpToPx()
                    }
                }
            )
        }
    }

    private fun storyboardCapabilityTags(engine: TtsEngineSetting?): List<StoryboardCapabilityTag> {
        val activeEngine = engine
            ?.takeIf { it.enabled && it.isScriptEngine }
            ?: return listOf(
                StoryboardCapabilityTag(
                    "需先选择引擎",
                    R.drawable.ng_bg_tag_warning,
                    R.color.ng_warning
                )
            )
        return buildList {
            add(
                StoryboardCapabilityTag(
                    "角色识别",
                    R.drawable.ng_bg_tts_voice_tag_blue,
                    R.color.ng_tts_tag_blue
                )
            )
            add(
                StoryboardCapabilityTag(
                    "片段拆分",
                    R.drawable.ng_bg_tts_voice_tag_purple,
                    R.color.ng_tts_tag_purple
                )
            )
            if (activeEngine.supportsCapability(TtsEngineCapability.SCENE_CONTEXT) ||
                activeEngine.supportsCapability(TtsEngineCapability.PERFORMANCE_INSTRUCTION)
            ) {
                add(
                    StoryboardCapabilityTag(
                        "场景理解",
                        R.drawable.ng_bg_tts_voice_tag_orange,
                        R.color.ng_tts_tag_orange
                    )
                )
            }
            if (activeEngine.supportsCapability(TtsEngineCapability.PERFORMANCE_INSTRUCTION)) {
                add(
                    StoryboardCapabilityTag(
                        "演员指导",
                        R.drawable.ng_bg_tts_voice_tag_green,
                        R.color.ng_tts_tag_green
                    )
                )
            }
        }
    }

    private data class StoryboardCapabilityTag(
        val text: String,
        val backgroundRes: Int,
        val colorRes: Int
    )
}

private fun SeekBar.applyReadAloudSliderStyle() {
    val accent = context.accentColor
    val trackBackgroundTint = ColorStateList.valueOf(ColorUtils.adjustAlpha(accent, 0.18f))
    progressDrawable = ContextCompat.getDrawable(context, R.drawable.ng_read_aloud_progress)?.mutate()
    progressTintList = ColorStateList.valueOf(accent)
    progressBackgroundTintList = trackBackgroundTint
    secondaryProgressTintList = trackBackgroundTint
    thumb = readAloudSheetSeekThumb(context, accent)
    thumbTintList = null
    thumbOffset = 11.dpToPx()
    splitTrack = false
}

private fun readAloudSheetSeekThumb(context: Context, accent: Int): LayerDrawable {
    val outerSize = 22.dpToPx()
    val innerInset = 4.dpToPx()
    val outer = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.WHITE)
        setStroke(1.dpToPx(), ColorUtils.withAlpha(accent, 0.18f))
        setSize(outerSize, outerSize)
    }
    val inner = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(accent)
        setSize(14.dpToPx(), 14.dpToPx())
    }
    return LayerDrawable(arrayOf(outer, inner)).apply {
        setLayerInset(1, innerInset, innerInset, innerInset, innerInset)
    }
}

class ReadAloudMoreSheet : ReadAloudBottomSheet(R.layout.dialog_read_aloud_more_sheet) {
    private val binding by viewBinding(DialogReadAloudMoreSheetBinding::bind)

    override fun onStart() {
        super.onStart()
        applyThemeSheetBackground()
        val height = (resources.displayMetrics.heightPixels * 0.86f).toInt()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        applyReadAloudMoreCardStyles()
        tvEngineSummary.text = TtsEngineStore.activeEngine().name
        bindSwitch(itemIgnoreAudioFocus, switchIgnoreAudioFocus, PreferKey.ignoreAudioFocus) {
            syncPauseOnCallState()
        }
        bindSwitch(itemPauseOnCall, switchPauseOnCall, PreferKey.pauseReadAloudWhilePhoneCalls)
        bindSwitch(itemWakeLock, switchWakeLock, PreferKey.readAloudWakeLock)
        bindSwitch(itemMediaButtonPerNext, switchMediaButtonPerNext, "mediaButtonPerNext")
        bindSwitch(itemReadByPage, switchReadByPage, PreferKey.readAloudByPage) {
            notifyReadAloudRuntimeChanged()
        }
        bindSwitch(itemSkipChapterTitle, switchSkipChapterTitle, PreferKey.skipReadAloudChapterTitle) {
            notifyReadAloudRuntimeChanged()
        }
        seekWorkerCount.applyReadAloudSliderStyle()
        seekWorkerCount.tickMarkTintList = ColorStateList.valueOf(view.context.accentColor)
        seekWorkerCount.progress = AppConfig.readAloudWorkerCount - 1
        syncWorkerCount(seekWorkerCount.progress + 1)
        seekWorkerCount.setOnSeekBarChangeListener(object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                syncWorkerCount(progress + 1)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val safeContext = context ?: return
                val count = seekBar.progress + 1
                safeContext.putPrefString(PreferKey.readAloudWorkerCount, count.toString())
                notifyReadAloudRuntimeChanged()
            }
        })
        syncPauseOnCallState()
        itemEngine.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            safeContext.startActivity<ConfigActivity> {
                ReadAloudLauncher.markPlayerDerived(this)
                putExtra("configTag", ConfigTag.TTS_ENGINE_CONFIG)
            }
            dismissAllowingStateLoss()
        }
        itemSystemTts.setOnClickListener { IntentHelp.openTTSSetting() }
        itemStop.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            ReadAloud.stop(safeContext)
            dismissAllowingStateLoss()
            activity?.finish()
        }
    }

    private fun applyReadAloudMoreCardStyles() = binding.run {
        val surfaceColor = ContextCompat.getColor(root.context, R.color.ng_surface)
        listOf(layoutPlaybackSettings, layoutEngineSettings).forEach { group ->
            group.background = null
            group.elevation = 0f
        }
        listOf(
            itemIgnoreAudioFocus,
            itemPauseOnCall,
            itemWakeLock,
            itemMediaButtonPerNext,
            itemReadByPage,
            itemSkipChapterTitle,
            itemWorkerCount,
            itemStop,
            itemEngine,
            itemSystemTts
        ).forEach { item ->
            item.background = GradientDrawable().apply {
                cornerRadius = 14.dpToPx().toFloat()
                setColor(surfaceColor)
            }
            item.elevation = 0f
        }
    }

    private fun bindSwitch(
        row: View,
        switch: ThemeSwitch,
        key: String,
        defaultValue: Boolean = false,
        afterChanged: () -> Unit = {}
    ) {
        val safeContext = row.context
        switch.isChecked = safeContext.getPrefBoolean(key, defaultValue)
        switch.setOnCheckedChangeListener { _, isChecked ->
            safeContext.putPrefBoolean(key, isChecked)
            afterChanged()
        }
        row.setOnClickListener {
            if (row.isEnabled && switch.isEnabled) {
                switch.isChecked = !switch.isChecked
            }
        }
    }

    private fun syncWorkerCount(count: Int) = binding.run {
        val activeColor = seekWorkerCount.context.accentColor
        val inactiveColor = ContextCompat.getColor(
            seekWorkerCount.context,
            R.color.ng_on_surface_variant
        )
        listOf(
            tvWorkerCount1,
            tvWorkerCount2,
            tvWorkerCount3,
            tvWorkerCount4,
            tvWorkerCount5
        ).forEachIndexed { index, label ->
            label.setTextColor(if (index + 1 == count) activeColor else inactiveColor)
        }
    }

    private fun syncPauseOnCallState() = binding.run {
        val enabled = itemPauseOnCall.context.getPrefBoolean(PreferKey.ignoreAudioFocus, false)
        itemPauseOnCall.isEnabled = enabled
        switchPauseOnCall.isEnabled = enabled
        itemPauseOnCall.alpha = if (enabled) 1f else 0.42f
    }

    private fun notifyReadAloudRuntimeChanged() {
        if (BaseReadAloudService.isRun) {
            postEvent(EventBus.MEDIA_BUTTON, false)
        }
    }
}

class ReadAloudCatalogSheet(
    private val activity: ReadAloudPlayerActivity
) {
    private lateinit var sheet: NgLongListBottomSheet
    private lateinit var recyclerChapters: RecyclerView
    private lateinit var textSummary: TextView
    private lateinit var textEmpty: TextView
    private val adapter by lazy {
        ReadAloudCatalogAdapter(
            context = activity,
            currentIndex = { ReadBook.durChapterIndex },
            onSelect = ::selectChapter
        )
    }

    fun show() {
        sheet = NgLongListBottomSheet(
            context = activity,
            searchHint = "搜索章节",
            title = "目录",
            showSearch = false
        )
        textSummary = TextView(activity).apply {
            setTextColor(ContextCompat.getColor(activity, R.color.ng_on_surface_variant))
            textSize = 15f
            includeFontPadding = false
            setPadding(0, 2.dpToPx(), 0, 12.dpToPx())
        }
        recyclerChapters = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = this@ReadAloudCatalogSheet.adapter
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, 0, 0, 8.dpToPx())
        }
        textEmpty = TextView(activity).apply {
            text = activity.getString(R.string.chapter_list_empty)
            setTextColor(ContextCompat.getColor(activity, R.color.ng_on_surface_variant))
            textSize = 15f
            gravity = Gravity.CENTER
            isVisible = false
        }
        val content = FrameLayout(activity).apply {
            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        textSummary,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                    addView(
                        recyclerChapters,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            0,
                            1f
                        )
                    )
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                textEmpty,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        sheet.setContent(content) {}
        sheet.show()
        loadChapters()
    }

    private fun loadChapters() {
        val book = ReadBook.book ?: run {
            showChapters(emptyList())
            return
        }
        activity.lifecycleScope.launch {
            val chapters = withContext(Dispatchers.IO) {
                appDb.bookChapterDao.getChapterList(book.bookUrl)
            }
            showChapters(chapters)
        }
    }

    private fun showChapters(chapters: List<BookChapter>) {
        adapter.submitItems(chapters)
        textSummary.text = "共 ${chapters.size} 章"
        val empty = chapters.isEmpty()
        recyclerChapters.isVisible = !empty
        textSummary.isVisible = !empty
        textEmpty.isVisible = empty
        scrollToCurrent()
    }

    private fun scrollToCurrent() {
        val position = adapter.positionOf(ReadBook.durChapterIndex)
        if (position >= 0) {
            recyclerChapters.post {
                (recyclerChapters.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset((position - 2).coerceAtLeast(0), 0)
            }
        }
    }

    private fun selectChapter(chapter: BookChapter) {
        sheet.dismiss()
        activity.openChapterFromCatalog(chapter.index)
    }
}

class ReadAloudVoiceSheet(
    private val activity: ReadAloudPlayerActivity
) {
    private lateinit var voiceSheet: TtsVoiceSelectionSheet

    fun show() {
        val activeEngineId = runCatching { TtsEngineStore.activeEngineId() }.getOrDefault("")
        val activeEngine = runCatching { TtsEngineStore.engine(activeEngineId) }.getOrNull()
        val activeVoiceId = activeEngine?.activeVoiceId
        voiceSheet = TtsVoiceSelectionSheet(
            context = activity,
            lifecycleScope = activity.lifecycleScope,
            searchHint = "搜索引擎或发音人",
            emptyText = "没有可选发音人",
            engines = { TtsEngineStore.engines().filter { it.enabled } },
            isSelected = { option ->
                activeEngineId == option.engine.id && if (option.systemDefault) {
                    activeVoiceId.isNullOrBlank()
                } else {
                    activeVoiceId == option.voice.id
                }
            },
            onSelect = ::selectVoice,
            beforePreview = {
                activity.stopStoryboardPreview()
                if (BaseReadAloudService.isPlay()) ReadAloud.pause(activity)
            },
            dismissOnSelect = false
        )
        voiceSheet.show()
    }

    private fun selectVoice(option: TtsVoiceOption) {
        val wasRun = BaseReadAloudService.isRun
        val oldEngineType = runCatching { TtsEngineStore.activeEngine().type }.getOrNull()
        val pageIndex = ReadBook.durPageIndex
        val startPos = activity.currentPageStartPos()
        activity.runVoiceSwitch {
            if (wasRun && oldEngineType != null && oldEngineType != option.engine.type) {
                ReadAloud.stop(activity)
            }
            val selected = TtsEngineStore.selectVoice(
                engineId = option.engine.id,
                voiceId = option.voice.id.takeUnless { option.systemDefault }
            )
            if (selected != null) {
                if (wasRun) {
                    ReadAloud.play(activity, play = true, pageIndex = pageIndex, startPos = startPos)
                }
                voiceSheet.dismiss()
            }
        }
    }
}

private class ReadAloudCatalogAdapter(
    private val context: Context,
    private val currentIndex: () -> Int,
    private val onSelect: (BookChapter) -> Unit
) : RecyclerView.Adapter<ReadAloudCatalogAdapter.ChapterHolder>() {

    private val items = mutableListOf<BookChapter>()

    fun submitItems(newItems: List<BookChapter>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun positionOf(chapterIndex: Int): Int {
        return items.indexOfFirst { it.index == chapterIndex }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterHolder {
        val itemView = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 13.dpToPx(), 0, 13.dpToPx())
        }
        return ChapterHolder(itemView)
    }

    override fun onBindViewHolder(holder: ChapterHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ChapterHolder(
        private val container: LinearLayout
    ) : RecyclerView.ViewHolder(container) {

        private val titleView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            includeFontPadding = false
        }
        private val metaView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            includeFontPadding = false
            setPadding(0, 8.dpToPx(), 0, 0)
        }
        private val divider = View(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.bg_divider_line))
        }

        init {
            container.addView(
                titleView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            container.addView(
                metaView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            container.addView(
                divider,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1
                ).apply {
                    topMargin = 14.dpToPx()
                }
            )
        }

        fun bind(chapter: BookChapter) {
            val isCurrent = chapter.index == currentIndex()
            titleView.text = chapter.title
            titleView.typeface = if (isCurrent) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            titleView.setTextColor(
                if (isCurrent) context.accentColor
                else ContextCompat.getColor(context, R.color.ng_on_surface)
            )
            val meta = chapter.tag?.takeIf { it.isNotBlank() }
                ?: "第 ${chapter.index + 1} 章"
            metaView.text = if (isCurrent) "$meta · 当前播放" else meta
            metaView.setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
            container.setOnClickListener { onSelect(chapter) }
            container.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }
}
