package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import com.github.liuyueyi.quick.transfer.constants.TransType
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadHighlightRule
import io.legado.app.lib.dialogs.alert
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.font.FontSelectDialog
import io.legado.app.utils.ChineseUtils
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.createFileReplace
import io.legado.app.utils.createFolderReplace
import io.legado.app.utils.externalCache
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.hexString
import io.legado.app.utils.inputStream
import io.legado.app.utils.longToast
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.openInputStream
import io.legado.app.utils.outputStream
import io.legado.app.utils.postEvent
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.readBytes
import io.legado.app.utils.readUri
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

private val EDITOR_FALLBACK_BG_COLOR = 0xFFF7F3EA.toInt()

class ReadStyleDialog : BaseDialogFragment(R.layout.dialog_read_book_style),
    FontSelectDialog.CallBack {

    private val callBack get() = activity as? ReadBookActivity
    private lateinit var composeView: ComposeView
    private var page by mutableStateOf(ReadStylePage.PRESET)
    private var screenState by mutableStateOf<ReadStyleUiState?>(null)
    private var editorBackgroundCache: List<ReadStyleBackgroundUi>? = null
    private var editingHighlightIndex: Int? = null
    private var highlightDraft: ReadHighlightRule? = null
    private val configFileName = "readConfig.zip"
    private val selectExportDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(::exportConfig) }
    private val selectImportDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(::importConfig) }
    private val selectBackgroundImage = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(::setBackgroundFromUri) }
    private val selectHighlightBackground = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { installHighlightResource(it, "background") } }
    private val selectHighlightFont = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { installHighlightResource(it, "font") } }

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
            attributes = attributes.apply {
                dimAmount = 0.0f
                gravity = Gravity.BOTTOM
            }
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        (activity as ReadBookActivity).bottomDialog++
        composeView = view as ComposeView
        composeView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        refreshUi()
        composeView.apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme(
                    updateSystemBars = false,
                    darkModeOverride = ReadBookConfig.isNightTheme,
                ) {
                    screenState?.let { state ->
                        ReadStyleScreen(
                            page = page,
                            state = state,
                            contentColor = Color(ReadDrawerStyle.contentColor(requireContext())),
                            accentColor = Color(ReadDrawerStyle.accentColor(requireContext())),
                            actions = createActions(),
                        )
                    }
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        ReadBookConfig.save()
        (activity as ReadBookActivity).bottomDialog--
    }

    private fun createActions() = ReadStyleActions(
        onPageSelected = ::navigateTo,
        onCreatePreset = {
            ReadBookConfig.configList.add(ReadBookConfig.Config())
            openEditor(ReadBookConfig.configList.lastIndex)
        },
        onSelectPreset = ::changeBgTextConfig,
        onImportPreset = {
            selectImportDocument.launch(
                arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
            )
        },
        onEditPreset = { openEditor(ReadBookConfig.styleSelect) },
        onExportPreset = {
            selectExportDocument.launch(currentExportFileName())
        },
        onDeletePreset = ::deleteCurrentStyle,
        onShareLayoutChanged = { checked ->
            ReadBookConfig.shareLayout = checked
            refreshUi()
            postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
        },
        onOpenHighlights = {
            page = ReadStylePage.HIGHLIGHT
            refreshUi()
        },
        onBack = ::navigateBack,
        onPresetNameChanged = { value ->
            ReadBookConfig.durConfig.name = value
            updateEditorState { copy(selectedPresetName = value) }
        },
        onTextColorChanged = ::applyEditorTextColor,
        onBackgroundColorChanged = ::applyEditorBackgroundColor,
        onTextAccentColorChanged = ::applyEditorAccentColor,
        onResetEditorColor = ::resetEditorColor,
        onBackgroundAlphaChanged = { alpha ->
            val safeAlpha = alpha.coerceIn(0, 100)
            ReadBookConfig.bgAlpha = safeAlpha
            updateEditorState { copy(editorBackgroundAlpha = safeAlpha) }
            postEvent(EventBus.UP_CONFIG, arrayListOf(3))
        },
        onSelectBackgroundImage = {
            selectBackgroundImage.launch(arrayOf("image/*"))
        },
        onSelectBackground = ::selectBackground,
        onFullLineUnderlineEnabledChanged = { enabled ->
            ReadBookConfig.fullLineUnderlineEnabled = enabled
            refreshFullLineUnderlineState()
        },
        onFullLineUnderlineDashedChanged = { dashed ->
            ReadBookConfig.dottedLine = dashed
            refreshFullLineUnderlineState()
        },
        onFullLineUnderlineColorChanged = { color ->
            ReadBookConfig.config.setCurUnderlineColor(color)
            refreshFullLineUnderlineState()
        },
        onFullLineUnderlineWidthChanged = { width ->
            ReadBookConfig.underlineHeight = width.coerceIn(1, 20)
            refreshFullLineUnderlineState()
        },
        onFullLineUnderlineOffsetChanged = { offset ->
            ReadBookConfig.underlinePadding = offset.coerceIn(0, 20)
            refreshFullLineUnderlineState()
        },
        onFullLineUnderlineExtendChanged = { extend ->
            ReadBookConfig.underlineExtend = extend
            refreshFullLineUnderlineState()
        },
        onFullLineUnderlineDashLengthChanged = { length ->
            ReadBookConfig.dottedBase = length.coerceIn(1f, 20f)
            refreshFullLineUnderlineState()
        },
        onFullLineUnderlineGapLengthChanged = { length ->
            ReadBookConfig.dottedRatio = length.coerceIn(1f, 20f)
            refreshFullLineUnderlineState()
        },
        onFontWeight = ::showFontWeightSetting,
        onFont = { showDialogFragment<FontSelectDialog>() },
        onIndent = ::showParagraphIndentSetting,
        onChineseConverter = ::showChineseConverterSetting,
        onPadding = {
            PaddingConfigDialog().show(childFragmentManager, "paddingConfigDialog")
        },
        onTip = {
            TipConfigDialog().show(childFragmentManager, "tipConfigDialog")
        },
        onTextSizeChanged = { value ->
            ReadBookConfig.textSize = value
            updateAdjustState { copy(textSize = value) }
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        },
        onLetterSpacingChanged = { value ->
            val rounded = (value * 100).roundToInt() / 100f
            ReadBookConfig.letterSpacing = rounded
            updateAdjustState { copy(letterSpacing = rounded) }
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        },
        onLineSpacingChanged = { value ->
            ReadBookConfig.lineSpacingExtra = value
            updateAdjustState { copy(lineSpacingExtra = value) }
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        },
        onParagraphSpacingChanged = { value ->
            ReadBookConfig.paragraphSpacing = value
            updateAdjustState { copy(paragraphSpacing = value) }
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        },
        onPageAnimChanged = { pageAnim ->
            ReadBook.book?.setPageAnim(-1)
            ReadBookConfig.pageAnim = pageAnim
            updateAdjustState { copy(pageAnim = pageAnim) }
            callBack?.upPageAnim()
            ReadBook.loadContent(false)
        },
        onCreateHighlight = { openHighlightEditor() },
        onEditHighlight = { openHighlightEditor(it) },
        onHighlightDraftChanged = { draft ->
            highlightDraft = draft
            updateEditorState { copy(highlightDraft = draft) }
        },
        onSelectHighlightBackground = {
            selectHighlightBackground.launch(arrayOf("image/*"))
        },
        onClearHighlightBackground = {
            updateHighlightDraft { copy(bgImage = null) }
        },
        onSelectHighlightFont = {
            selectHighlightFont.launch(
                arrayOf(
                    "font/ttf",
                    "font/otf",
                    "application/x-font-ttf",
                    "application/x-font-opentype",
                    "application/octet-stream",
                )
            )
        },
        onClearHighlightFont = {
            updateHighlightDraft { copy(fontPath = null) }
        },
        onSaveHighlight = ::saveHighlightDraft,
        onDeleteHighlight = ::deleteHighlightDraft,
        onHighlightEnabledChanged = { index, enabled ->
            val rules = currentRules().toMutableList()
            rules.getOrNull(index)?.let { rule ->
                rules[index] = rule.copy(enabled = enabled)
                applyHighlightRules(rules)
            }
        },
        onMoveHighlight = { from, to ->
            val rules = currentRules().toMutableList()
            if (from in rules.indices && to in rules.indices && from != to) {
                val moved = rules.removeAt(from)
                rules.add(to, moved)
                applyHighlightRules(rules)
            }
        },
    )

    private fun updateAdjustState(transform: ReadStyleUiState.() -> ReadStyleUiState) {
        screenState = screenState?.transform()
    }

    private fun updateEditorState(transform: ReadStyleUiState.() -> ReadStyleUiState) {
        screenState = screenState?.transform()
    }

    private fun refreshUi() {
        val config = ReadBookConfig.durConfig
        val name = config.name.ifBlank { getString(R.string.text) }
        val mode = if (ReadBookConfig.isNightTheme) 1 else 0
        val modeLabel = getString(
            if (mode == 1) R.string.read_style_mode_night else R.string.read_style_mode_day
        )
        val backgroundType = config.curBgType()
        val backgroundName = config.curBgStr()
        val backgroundColor = if (backgroundType == 0) {
            runCatching { backgroundName.toColorInt() }.getOrDefault(EDITOR_FALLBACK_BG_COLOR)
        } else {
            EDITOR_FALLBACK_BG_COLOR
        }
        val previewBackground = if (backgroundType == 0) {
            null
        } else {
            runCatching {
                config.curBgDrawable(352, 176).toBitmap(352, 176).asImageBitmap()
            }.getOrNull()
        }
        val rules = currentRules()
        screenState = ReadStyleUiState(
            presets = ReadBookConfig.configList.mapIndexed { index, item ->
                ReadStylePresetUi(
                    index = index,
                    name = item.name.ifBlank { getString(R.string.text) },
                    textColor = item.curTextColor(),
                    background = runCatching {
                        item.curBgDrawable(176, 128).toBitmap(176, 128).asImageBitmap()
                    }.getOrNull(),
                )
            },
            selectedPresetIndex = ReadBookConfig.styleSelect,
            selectedPresetName = name,
            highlightSummary = getString(
                R.string.read_highlight_summary,
                rules.count(ReadHighlightRule::enabled),
                rules.size,
            ),
            shareLayout = ReadBookConfig.shareLayout,
            textSize = ReadBookConfig.textSize,
            letterSpacing = ReadBookConfig.letterSpacing,
            lineSpacingExtra = ReadBookConfig.lineSpacingExtra,
            paragraphSpacing = ReadBookConfig.paragraphSpacing,
            pageAnim = ReadBook.pageAnim().coerceIn(0, 4),
            highlightRules = rules,
            editorMode = mode,
            editorModeLabel = modeLabel,
            editorPreviewBackground = previewBackground,
            editorBackgrounds = if (page.isEditorPage()) loadEditorBackgrounds() else emptyList(),
            editorBackgroundType = backgroundType,
            editorBackgroundName = backgroundName,
            editorTextColor = config.curTextColor(),
            editorBackgroundColor = backgroundColor,
            editorTextAccentColor = config.curTextAccentColor(),
            editorBackgroundAlpha = ReadBookConfig.bgAlpha.coerceIn(0, 100),
            fullLineUnderline = currentFullLineUnderlineState(),
            highlightDraft = highlightDraft,
            editingHighlightIndex = editingHighlightIndex,
            editorInitialColor = null,
            editorInitialColorWasUnset = false,
            editorInitialBackgroundType = null,
            editorInitialBackgroundName = null,
            editorInitialBackground = null,
        )
    }

    private fun currentRules(): List<ReadHighlightRule> =
        ReadBookConfig.config.highlightRules.sortedBy(ReadHighlightRule::position)

    private fun changeBgTextConfig(index: Int) {
        val oldIndex = ReadBookConfig.styleSelect
        if (index !in ReadBookConfig.configList.indices || index == oldIndex) return
        ReadBookConfig.styleSelect = index
        refreshUi()
        postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
        if (AppConfig.readBarStyleFollowPage) {
            postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
        }
    }

    private fun openEditor(index: Int) {
        changeBgTextConfig(index)
        page = ReadStylePage.EDIT
        refreshUi()
    }

    private fun navigateTo(target: ReadStylePage) {
        if (target.isAnyColorEditorPage()) {
            val state = screenState ?: return
            val initialNullableColor = when (target) {
                ReadStylePage.EDIT_TEXT_COLOR -> state.editorTextColor
                ReadStylePage.EDIT_BACKGROUND_COLOR -> state.editorBackgroundColor
                ReadStylePage.EDIT_ACCENT_COLOR -> state.editorTextAccentColor
                ReadStylePage.EDIT_UNDERLINE_COLOR -> state.fullLineUnderline.color
                ReadStylePage.HIGHLIGHT_TEXT_COLOR -> state.highlightDraft?.textColor
                ReadStylePage.HIGHLIGHT_BACKGROUND_COLOR -> state.highlightDraft?.bgColor
                ReadStylePage.HIGHLIGHT_UNDERLINE_COLOR -> state.highlightDraft?.underlineColor
                else -> null
            }
            val fallbackColor = when (target) {
                ReadStylePage.HIGHLIGHT_BACKGROUND_COLOR ->
                    (state.editorTextAccentColor and 0x00FFFFFF) or 0x33000000
                else -> state.editorTextAccentColor
            }
            screenState = state.copy(
                editorInitialColor = initialNullableColor ?: fallbackColor,
                editorInitialColorWasUnset = initialNullableColor == null,
                editorInitialBackgroundType = if (target == ReadStylePage.EDIT_BACKGROUND_COLOR) {
                    state.editorBackgroundType
                } else {
                    null
                },
                editorInitialBackgroundName = if (target == ReadStylePage.EDIT_BACKGROUND_COLOR) {
                    state.editorBackgroundName
                } else {
                    null
                },
                editorInitialBackground = if (target == ReadStylePage.EDIT_BACKGROUND_COLOR) {
                    state.editorPreviewBackground
                } else {
                    null
                },
            )
            page = target
            return
        }
        page = target
        refreshUi()
    }

    private fun navigateBack() {
        when {
            page.isPresetColorEditorPage() -> {
                page = ReadStylePage.EDIT
                clearEditorColorInitialState()
            }

            page == ReadStylePage.EDIT_UNDERLINE_COLOR -> {
                page = ReadStylePage.EDIT_UNDERLINE
                clearEditorColorInitialState()
            }

            page.isHighlightColorEditorPage() -> {
                page = ReadStylePage.HIGHLIGHT_EDIT
                clearEditorColorInitialState()
            }

            page == ReadStylePage.EDIT_UNDERLINE -> page = ReadStylePage.EDIT

            page == ReadStylePage.HIGHLIGHT_EDIT -> {
                clearHighlightDraft()
                page = ReadStylePage.HIGHLIGHT
                refreshUi()
            }

            page == ReadStylePage.EDIT || page == ReadStylePage.HIGHLIGHT -> {
                page = ReadStylePage.PRESET
                refreshUi()
            }
        }
    }

    private fun applyEditorTextColor(color: Int) {
        ReadBookConfig.durConfig.setCurTextColor(color)
        updateEditorState { copy(editorTextColor = color) }
        postEditorTextColorChanged()
    }

    private fun applyEditorAccentColor(color: Int) {
        ReadBookConfig.durConfig.setCurTextAccentColor(color)
        updateEditorState { copy(editorTextAccentColor = color) }
        postEditorTextColorChanged()
    }

    private fun applyEditorBackgroundColor(color: Int) {
        ReadBookConfig.durConfig.setCurBg(0, "#${color.hexString}")
        updateEditorState {
            copy(
                editorPreviewBackground = null,
                editorBackgroundType = 0,
                editorBackgroundName = "#${color.hexString}",
                editorBackgroundColor = color,
            )
        }
        postEditorBackgroundChanged()
    }

    private fun resetEditorColor() {
        val state = screenState ?: return
        val initialColor = state.editorInitialColor ?: return
        when (page) {
            ReadStylePage.EDIT_TEXT_COLOR -> applyEditorTextColor(initialColor)
            ReadStylePage.EDIT_BACKGROUND_COLOR -> {
                val initialType = state.editorInitialBackgroundType ?: 0
                val initialName = state.editorInitialBackgroundName.orEmpty()
                if (initialType == 0) {
                    applyEditorBackgroundColor(initialColor)
                } else {
                    ReadBookConfig.durConfig.setCurBg(initialType, initialName)
                    updateEditorState {
                        copy(
                            editorPreviewBackground = state.editorInitialBackground,
                            editorBackgroundType = initialType,
                            editorBackgroundName = initialName,
                        )
                    }
                    postEditorBackgroundChanged()
                }
            }
            ReadStylePage.EDIT_ACCENT_COLOR -> applyEditorAccentColor(initialColor)
            ReadStylePage.EDIT_UNDERLINE_COLOR -> {
                ReadBookConfig.config.setCurUnderlineColor(initialColor)
                refreshFullLineUnderlineState()
            }
            ReadStylePage.HIGHLIGHT_TEXT_COLOR -> updateHighlightDraft {
                copy(textColor = if (state.editorInitialColorWasUnset) null else initialColor)
            }
            ReadStylePage.HIGHLIGHT_BACKGROUND_COLOR -> updateHighlightDraft {
                copy(bgColor = if (state.editorInitialColorWasUnset) null else initialColor)
            }
            ReadStylePage.HIGHLIGHT_UNDERLINE_COLOR -> updateHighlightDraft {
                copy(underlineColor = if (state.editorInitialColorWasUnset) null else initialColor)
            }
            else -> return
        }
        page = when (page) {
            ReadStylePage.EDIT_UNDERLINE_COLOR -> ReadStylePage.EDIT_UNDERLINE
            ReadStylePage.HIGHLIGHT_TEXT_COLOR,
            ReadStylePage.HIGHLIGHT_BACKGROUND_COLOR,
            ReadStylePage.HIGHLIGHT_UNDERLINE_COLOR -> ReadStylePage.HIGHLIGHT_EDIT
            else -> ReadStylePage.EDIT
        }
        clearEditorColorInitialState()
    }

    private fun clearEditorColorInitialState() {
        updateEditorState {
            copy(
                editorInitialColor = null,
                editorInitialColorWasUnset = false,
                editorInitialBackgroundType = null,
                editorInitialBackgroundName = null,
                editorInitialBackground = null,
            )
        }
    }

    private fun postEditorTextColorChanged() {
        postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6, 9, 11))
        if (AppConfig.readBarStyleFollowPage) {
            postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
        }
    }

    private fun postEditorBackgroundChanged() {
        postEvent(EventBus.UP_CONFIG, arrayListOf(1))
        if (AppConfig.readBarStyleFollowPage) {
            postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
        }
    }

    private fun currentFullLineUnderlineState() = FullLineUnderlineUiState(
        enabled = ReadBookConfig.fullLineUnderlineEnabled,
        dashed = ReadBookConfig.dottedLine,
        color = ReadBookConfig.resolvedUnderlineColor,
        width = ReadBookConfig.underlineHeight.coerceIn(1, 20),
        offset = ReadBookConfig.underlinePadding.coerceIn(0, 20),
        extend = ReadBookConfig.underlineExtend,
        dashLength = ReadBookConfig.dottedBase.coerceIn(1f, 20f),
        gapLength = ReadBookConfig.dottedRatio.coerceIn(1f, 20f),
    )

    private fun refreshFullLineUnderlineState() {
        updateEditorState { copy(fullLineUnderline = currentFullLineUnderlineState()) }
        postEvent(EventBus.UP_CONFIG, arrayListOf(6, 9, 11))
    }

    private fun selectBackground(type: Int, name: String) {
        val selected = loadEditorBackgrounds().firstOrNull {
            it.type == type && it.name == name
        } ?: return
        ReadBookConfig.durConfig.setCurBg(type, name)
        updateEditorState {
            copy(
                editorPreviewBackground = selected.background,
                editorBackgroundType = type,
                editorBackgroundName = name,
            )
        }
        postEditorBackgroundChanged()
    }

    private fun loadEditorBackgrounds(): List<ReadStyleBackgroundUi> {
        editorBackgroundCache?.let { return it }
        val customBackgrounds = linkedMapOf<String, String>()
        fun addCustomBackground(reference: String) {
            val path = resolveCustomBackgroundPath(reference)
            val file = File(path)
            if (!file.isFile) return
            val key = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
            customBackgrounds.putIfAbsent(key, reference)
        }
        ReadBookConfig.durConfig.takeIf { it.curBgType() == 2 }
            ?.curBgStr()
            ?.let(::addCustomBackground)
        ReadBookConfig.getAllPicBgStr().forEach(::addCustomBackground)

        val customItems = customBackgrounds.values.mapNotNull { reference ->
            runCatching {
                val path = resolveCustomBackgroundPath(reference)
                BitmapUtils.decodeBitmap(path, 128, 112)
                    ?.asImageBitmap()
                    ?.let { bitmap ->
                        ReadStyleBackgroundUi(
                            type = 2,
                            name = reference,
                            label = FileUtils.getNameExcludeExtension(File(path).name),
                            background = bitmap,
                        )
                    }
            }.getOrNull()
        }
        val names = requireContext().assets.list("bg")?.toList().orEmpty()
        val preferred = listOf("午后沙滩.jpg", "宁静夜色.jpg", "山水墨影.jpg", "山水画.jpg")
        val ordered = preferred.filter(names::contains) + names.filterNot(preferred::contains)
        val builtInItems = ordered.mapNotNull { name ->
            runCatching {
                BitmapUtils.decodeAssetsBitmap(requireContext(), "bg/$name", 128, 112)
                    ?.asImageBitmap()
                    ?.let { bitmap ->
                        ReadStyleBackgroundUi(
                            type = 1,
                            name = name,
                            label = FileUtils.getNameExcludeExtension(name),
                            background = bitmap,
                        )
                    }
            }.getOrNull()
        }
        return (customItems + builtInItems).also { editorBackgroundCache = it }
    }

    private fun resolveCustomBackgroundPath(reference: String): String =
        if (reference.contains(File.separator)) {
            reference
        } else {
            FileUtils.getPath(requireContext().externalFiles, "bg", reference)
        }

    private fun setBackgroundFromUri(uri: Uri) {
        readUri(uri) { fileDoc, inputStream ->
            runCatching {
                val suffix = if (fileDoc.name.contains(".9.png", true)) {
                    ".9.png"
                } else {
                    ".${fileDoc.name.substringAfterLast('.', "jpg")}"
                }
                val fileName = uri.inputStream(requireContext()).getOrThrow().use {
                    MD5Utils.md5Encode(it) + suffix
                }
                val file = FileUtils.createFileIfNotExist(
                    requireContext().externalFiles,
                    "bg",
                    fileName,
                )
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                ReadBookConfig.durConfig.setCurBg(2, fileName)
            }.onSuccess {
                editorBackgroundCache = null
                postEditorBackgroundChanged()
                composeView.post { refreshUi() }
            }.onFailure {
                toastOnUi(it.localizedMessage.orEmpty())
            }
        }
    }

    private fun showFontWeightSetting() {
        val weights = (100..900 step 100).toList()
        val descriptions = resources.getStringArray(R.array.text_font_weight_levels).toList()
        ReadTypographySettingDialog.showDiscrete(
            context = requireContext(),
            avoidView = composeView,
            title = getString(R.string.text_font_weight_converter),
            stepLabels = weights.map(Int::toString),
            currentValues = descriptions,
            selectedIndex = weights.indexOf(resolveFontWeight(ReadBookConfig.textBold))
                .coerceAtLeast(0),
            applyCurrentValueStyle = { view, index ->
                val weight = weights[index]
                view.typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Typeface.create(Typeface.DEFAULT, weight, false)
                } else {
                    Typeface.create(
                        Typeface.DEFAULT,
                        if (weight >= 600) Typeface.BOLD else Typeface.NORMAL,
                    )
                }
            },
        ) { index ->
            ReadBookConfig.textBold = weights[index]
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 9, 6))
        }
    }

    private fun showParagraphIndentSetting() {
        ReadTypographySettingDialog.showDiscrete(
            context = requireContext(),
            avoidView = composeView,
            title = getString(R.string.paragraph_first_line_indent),
            stepLabels = resources.getStringArray(R.array.indent_short).toList(),
            currentValues = resources.getStringArray(R.array.indent).toList(),
            selectedIndex = paragraphIndentLevel(),
            currentValueTextSizeSp = 24f,
        ) { index ->
            ReadBookConfig.paragraphIndent = "　".repeat(index)
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
    }

    private fun showChineseConverterSetting() {
        ReadTypographySettingDialog.showChineseConverter(
            context = requireContext(),
            avoidView = composeView,
            title = getString(R.string.chinese_converter),
            labels = resources.getStringArray(R.array.chinese_mode_short).toList(),
            selectedIndex = AppConfig.chineseConverterType,
        ) { index ->
            AppConfig.chineseConverterType = index
            ChineseUtils.unLoad(*TransType.entries.toTypedArray())
            postEvent(EventBus.UP_CONFIG, arrayListOf(5))
        }
    }

    private fun resolveFontWeight(type: Int): Int = when (type) {
        0 -> 400
        1 -> 900
        2 -> 300
        else -> ((type.coerceIn(100, 900) + 50) / 100) * 100
    }

    private fun paragraphIndentLevel(): Int {
        val indent = ReadBookConfig.paragraphIndent
        val ideographicSpaces = indent.count { it == '　' }
        return (if (ideographicSpaces > 0) ideographicSpaces else indent.length)
            .coerceIn(0, 4)
    }

    private fun deleteCurrentStyle() {
        val name = ReadBookConfig.durConfig.name.ifBlank { getString(R.string.text) }
        alert(R.string.delete) {
            setMessage(getString(R.string.sure_del_any, name))
            okButton {
                if (ReadBookConfig.deleteDur()) {
                    editorBackgroundCache = null
                    refreshUi()
                    postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
                } else {
                    toastOnUi(R.string.read_style_keep_one_preset)
                }
            }
            cancelButton()
        }
    }

    private fun applyHighlightRules(rules: List<ReadHighlightRule>) {
        ReadBookConfig.config.highlightRules = ArrayList(
            rules.mapIndexed { index, rule -> rule.copy(position = index) }
        )
        ReadBookConfig.save()
        refreshUi()
        postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
    }

    private fun openHighlightEditor(position: Int? = null) {
        val oldRule = position?.let(currentRules()::getOrNull)
        editingHighlightIndex = position
        highlightDraft = oldRule ?: ReadHighlightRule(
            id = UUID.randomUUID().toString(),
            name = getString(R.string.highlight_rule_default_name),
            sampleText = getString(R.string.highlight_rule_default_sample),
            position = currentRules().size,
            textColor = ReadBookConfig.textAccentColor,
        )
        page = ReadStylePage.HIGHLIGHT_EDIT
        refreshUi()
    }

    private fun updateHighlightDraft(transform: ReadHighlightRule.() -> ReadHighlightRule) {
        val updated = highlightDraft?.transform() ?: return
        highlightDraft = updated
        updateEditorState { copy(highlightDraft = updated) }
    }

    private fun clearHighlightDraft() {
        highlightDraft = null
        editingHighlightIndex = null
    }

    private fun saveHighlightDraft() {
        val draft = highlightDraft ?: return
        val pattern = draft.pattern
        val patternValid = pattern.isNotBlank() && runCatching { Regex(pattern) }.isSuccess
        val saved = draft.copy(
            name = draft.name.trim().ifBlank {
                getString(R.string.highlight_rule_default_name)
            },
            enabled = draft.enabled && patternValid,
        ).normalized()
        val updated = currentRules().toMutableList()
        val position = editingHighlightIndex
        if (position == null) {
            updated.add(saved)
        } else if (position in updated.indices) {
            updated[position] = saved
        }
        clearHighlightDraft()
        page = ReadStylePage.HIGHLIGHT
        applyHighlightRules(updated)
        if (!patternValid) toastOnUi(R.string.highlight_rule_invalid_pattern)
    }

    private fun deleteHighlightDraft() {
        val position = editingHighlightIndex ?: return
        val updated = currentRules().toMutableList()
        if (position in updated.indices) updated.removeAt(position)
        clearHighlightDraft()
        page = ReadStylePage.HIGHLIGHT
        applyHighlightRules(updated)
    }

    private fun installHighlightResource(uri: Uri, kind: String) {
        readUri(uri) { fileDoc, inputStream ->
            runCatching {
                val extension = fileDoc.name.substringAfterLast('.', "bin")
                val target = FileUtils.createFileIfNotExist(
                    requireContext().externalFiles,
                    "read_style_editor",
                    "${kind}_${UUID.randomUUID()}.$extension",
                )
                FileOutputStream(target).use { output -> inputStream.copyTo(output) }
                target.absolutePath
            }.onSuccess { path ->
                if (kind == "background") {
                    updateHighlightDraft { copy(bgImage = path) }
                } else {
                    updateHighlightDraft { copy(fontPath = path) }
                }
            }.onFailure {
                toastOnUi(it.localizedMessage.orEmpty())
            }
        }
    }

    private fun importConfig(uri: Uri) {
        execute {
            ReadBookConfig.importWithReport(uri.readBytes(requireContext()))
        }.onSuccess { result ->
            ReadBookConfig.configList.add(result.config)
            ReadBookConfig.styleSelect = ReadBookConfig.configList.lastIndex
            editorBackgroundCache = null
            refreshUi()
            postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
            if (result.warnings.isEmpty()) {
                toastOnUi("导入成功")
            } else {
                longToast("导入成功\n${result.warnings.joinToString("\n")}")
            }
        }.onError {
            it.printOnDebug()
            longToast("导入失败:${it.localizedMessage}")
        }
    }

    private fun exportConfig(uri: Uri) {
        val exportFileName = currentExportFileName()
        execute {
            val exportFiles = arrayListOf<File>()
            val configDir = requireContext().externalCache.getFile("readConfig")
            configDir.createFolderReplace()
            val configFile = configDir.getFile("readConfig.json")
            configFile.createFileReplace()
            val config = ReadBookConfig.getExportConfig()
            val fontPath = ReadBookConfig.textFont
            if (fontPath.isNotEmpty()) {
                val fontDoc = FileDoc.fromFile(fontPath)
                val fontName = fontDoc.name
                fontDoc.openInputStream().getOrNull()?.use { input ->
                    val fontExportFile = FileUtils.createFileIfNotExist(configDir, fontName)
                    fontExportFile.outputStream().use { out -> input.copyTo(out) }
                    config.textFont = fontName
                    exportFiles.add(fontExportFile)
                }
            }
            config.highlightRules = ArrayList(config.highlightRules.mapIndexed { index, rule ->
                rule.copy(
                    bgImage = copyRuleResource(
                        reference = rule.bgImage,
                        prefix = "rule_${index}_background",
                        configDir = configDir,
                        exportFiles = exportFiles,
                    ),
                    fontPath = copyRuleResource(
                        reference = rule.fontPath,
                        prefix = "rule_${index}_font",
                        configDir = configDir,
                        exportFiles = exportFiles,
                    ),
                )
            })
            configFile.writeText(GSON.toJson(config))
            exportFiles.add(configFile)
            repeat(3) {
                val path = ReadBookConfig.durConfig.getBgPath(it) ?: return@repeat
                copyBgImage(path, configDir)?.let(exportFiles::add)
            }
            val configZipPath = FileUtils.getPath(requireContext().externalCache, configFileName)
            if (ZipUtils.zipFiles(exportFiles, File(configZipPath))) {
                uri.outputStream(requireContext()).getOrThrow().use { out ->
                    File(configZipPath).inputStream().use { it.copyTo(out) }
                }
            }
        }.onSuccess {
            toastOnUi("导出成功, 文件名为 $exportFileName")
        }.onError {
            it.printOnDebug()
            AppLog.put("导出失败:${it.localizedMessage}", it)
            longToast("导出失败:${it.localizedMessage}")
        }
    }

    private fun currentExportFileName(): String {
        val presetName = ReadBookConfig.durConfig.name.normalizeFileName()
        return if (presetName.isBlank()) configFileName else "$presetName.zip"
    }

    private fun copyBgImage(path: String, configDir: File): File? {
        val bgFile = File(path)
        if (!bgFile.exists()) return null
        val bgExportFile = File(FileUtils.getPath(configDir, FileUtils.getName(path)))
        if (bgExportFile.exists()) return null
        bgFile.copyTo(bgExportFile)
        return bgExportFile
    }

    private fun copyRuleResource(
        reference: String?,
        prefix: String,
        configDir: File,
        exportFiles: MutableList<File>,
    ): String? {
        val path = reference?.takeIf(String::isNotBlank) ?: return null
        if (path.startsWith("assets://")) return path
        val source = File(path)
        if (!source.isFile) return null
        val suffix = source.extension.takeIf(String::isNotBlank)?.let { ".$it" }.orEmpty()
        val target = File(configDir, "$prefix$suffix")
        source.copyTo(target, overwrite = true)
        exportFiles.add(target)
        return target.name
    }

    override val curFontPath: String
        get() = ReadBookConfig.textFont

    override fun selectFont(path: String) {
        if (path != ReadBookConfig.textFont || path.isEmpty()) {
            ReadBookConfig.textFont = path
            postEvent(EventBus.UP_CONFIG, arrayListOf(2, 5))
        }
    }
}

private fun ReadStylePage.isPresetColorEditorPage(): Boolean = when (this) {
    ReadStylePage.EDIT_TEXT_COLOR,
    ReadStylePage.EDIT_BACKGROUND_COLOR,
    ReadStylePage.EDIT_ACCENT_COLOR -> true

    else -> false
}

private fun ReadStylePage.isHighlightColorEditorPage(): Boolean = when (this) {
    ReadStylePage.HIGHLIGHT_TEXT_COLOR,
    ReadStylePage.HIGHLIGHT_BACKGROUND_COLOR,
    ReadStylePage.HIGHLIGHT_UNDERLINE_COLOR -> true

    else -> false
}

private fun ReadStylePage.isAnyColorEditorPage(): Boolean =
    isPresetColorEditorPage() || isHighlightColorEditorPage() ||
        this == ReadStylePage.EDIT_UNDERLINE_COLOR

private fun ReadStylePage.isEditorPage(): Boolean =
    this == ReadStylePage.EDIT || isPresetColorEditorPage() ||
        this == ReadStylePage.EDIT_UNDERLINE || this == ReadStylePage.EDIT_UNDERLINE_COLOR
