package io.legado.app.ui.config

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.AppContextWrapper
import io.legado.app.base.BaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.LauncherIconHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.config.normalizeThemeMode
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.inputStream
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import io.legado.app.utils.readUri
import io.legado.app.utils.removePref
import io.legado.app.utils.startActivity
import io.legado.app.utils.sysConfiguration
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.launch
import splitties.init.appCtx
import java.io.FileOutputStream
import kotlin.math.roundToInt

@Suppress("SameParameterValue")
class ThemeConfigFragment : BaseFragment(R.layout.fragment_theme_config) {

    private val requestCodeBgLight = 121
    private val requestCodeBgDark = 122
    private var screenState by mutableStateOf(ThemeConfigScreenState())
    private var backgroundEditorState by mutableStateOf<ThemeBackgroundEditorState?>(null)
    private var fontScaleEditorState by mutableStateOf<ThemeFontScaleEditorState?>(null)

    private val selectImage = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            when (it.requestCode) {
                requestCodeBgLight -> copyBgFromUri(uri, PreferKey.bgImage) { path ->
                    updateBackgroundDraft(dark = false, path = path)
                }

                requestCodeBgDark -> copyBgFromUri(uri, PreferKey.bgImageN) { path ->
                    updateBackgroundDraft(dark = true, path = path)
                }
            }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        activity?.setTitle(R.string.theme_setting)
        refreshContent()
        (view as ComposeView).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    ThemeConfigScreen(
                        state = screenState,
                        onThemeModeSelected = ::selectThemeMode,
                        onLauncherIconClick = ::showLauncherIconSelection,
                        onFloatingBottomBarChanged = ::setFloatingBottomBar,
                        onTransparentAppBarsChanged = ::setTransparentAppBars,
                        onOpenCustomColors = {
                            (activity as? ConfigActivity)?.openThemeColorConfigPage()
                        },
                        onOpenFontScale = ::openFontScaleEditor,
                        onOpenCoverConfig = {
                            startActivity<ConfigActivity> {
                                putExtra("configTag", ConfigTag.COVER_CONFIG)
                            }
                        },
                        onOpenThemeManager = {
                            (activity as? ConfigActivity)?.openThemeManagerPage()
                        },
                        onOpenDayBackground = { openBackgroundEditor(false) },
                        onOpenNightBackground = { openBackgroundEditor(true) }
                    )
                    backgroundEditorState?.let { editorState ->
                        ThemeBackgroundEditorSheet(
                            state = editorState,
                            onDismissRequest = { backgroundEditorState = null },
                            onSelectImage = { selectBackgroundImage(editorState.dark) },
                            onRemoveImage = {
                                backgroundEditorState = editorState.copy(path = null, blur = 0)
                            },
                            onBlurChanged = { blur ->
                                backgroundEditorState = editorState.copy(blur = blur)
                            },
                            onSave = ::saveBackgroundEditor
                        )
                    }
                    fontScaleEditorState?.let { editorState ->
                        ThemeFontScaleEditorSheet(
                            state = editorState,
                            onDismissRequest = { fontScaleEditorState = null },
                            onScaleChanged = { scale ->
                                fontScaleEditorState = editorState.copy(
                                    scale = scale,
                                    followSystem = false
                                )
                            },
                            onFollowSystem = {
                                fontScaleEditorState = editorState.copy(
                                    scale = systemFontScaleForEditor(),
                                    followSystem = true
                                )
                            },
                            onSave = ::saveFontScaleEditor
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.theme_setting)
        if (view != null) refreshContent()
    }

    private fun refreshContent() {
        val launcherIcon = getPrefString(PreferKey.launcherIcon, DEFAULT_LAUNCHER_ICON)
            ?: DEFAULT_LAUNCHER_ICON
        screenState = ThemeConfigScreenState(
            themeMode = normalizeThemeMode(AppConfig.themeMode),
            showLauncherIcon = Build.VERSION.SDK_INT >= 26,
            launcherIconRes = launcherIconResource(launcherIcon),
            floatingBottomBar = getPrefBoolean(PreferKey.useFloatingBottomBar, false),
            transparentAppBars = getPrefBoolean(PreferKey.tNavBar, false),
            fontScaleSummary = getString(
                R.string.font_scale_summary,
                AppContextWrapper.getFontScale(requireContext())
            ),
            dayBackgroundSummary = backgroundSummary(
                PreferKey.bgImage,
                PreferKey.bgImageBlurring
            ),
            nightBackgroundSummary = backgroundSummary(
                PreferKey.bgImageN,
                PreferKey.bgImageNBlurring
            )
        )
    }

    private fun launcherIconResource(value: String): Int {
        return resources.getIdentifier(value, "mipmap", requireContext().packageName)
            .takeIf { it != 0 }
            ?: R.mipmap.ic_launcher
    }

    private fun backgroundSummary(imageKey: String, blurKey: String): String {
        val path = getPrefString(imageKey).takeUnless { it.isNullOrBlank() }
            ?: return getString(R.string.ng_theme_background_none)
        val name = path.substringAfterLast('/').substringAfterLast('\\')
        return getString(
            R.string.ng_theme_background_summary,
            name,
            getPrefInt(blurKey, 0)
        )
    }

    private fun selectThemeMode(mode: String) {
        val normalized = normalizeThemeMode(mode)
        if (normalized == screenState.themeMode) return
        screenState = screenState.copy(themeMode = normalized)
        ThemeConfig.applyThemeMode(requireContext(), normalized)
    }

    private fun showLauncherIconSelection() {
        LauncherIconSelectionSheet.show(
            context = requireContext(),
            currentValue = getPrefString(PreferKey.launcherIcon, DEFAULT_LAUNCHER_ICON)
                ?: DEFAULT_LAUNCHER_ICON
        ) { value ->
            putPrefString(PreferKey.launcherIcon, value)
            LauncherIconHelp.changeIcon(value)
            screenState = screenState.copy(launcherIconRes = launcherIconResource(value))
        }
    }

    private fun setFloatingBottomBar(enabled: Boolean) {
        if (screenState.floatingBottomBar == enabled) return
        putPrefBoolean(PreferKey.useFloatingBottomBar, enabled)
        screenState = screenState.copy(floatingBottomBar = enabled)
    }

    private fun setTransparentAppBars(enabled: Boolean) {
        if (screenState.transparentAppBars == enabled) return
        putPrefBoolean(PreferKey.tNavBar, enabled)
        screenState = screenState.copy(transparentAppBars = enabled)
        ThemeConfig.applyTheme(requireContext())
        recreateActivities()
    }

    private fun openFontScaleEditor() {
        val storedScale = getPrefInt(PreferKey.fontScale, 0)
        val followSystem = storedScale !in 8..16
        fontScaleEditorState = ThemeFontScaleEditorState(
            scale = if (followSystem) {
                systemFontScaleForEditor()
            } else {
                storedScale / 10f
            },
            followSystem = followSystem
        )
    }

    private fun saveFontScaleEditor() {
        val editorState = fontScaleEditorState ?: return
        putPrefInt(
            PreferKey.fontScale,
            if (editorState.followSystem) {
                0
            } else {
                (editorState.scale * 10f).roundToInt().coerceIn(8, 16)
            }
        )
        fontScaleEditorState = null
        recreateActivities()
    }

    private fun systemFontScaleForEditor(): Float {
        return (sysConfiguration.fontScale * 10f)
            .roundToInt()
            .coerceIn(8, 16) / 10f
    }

    private fun openBackgroundEditor(dark: Boolean) {
        val imageKey = if (dark) PreferKey.bgImageN else PreferKey.bgImage
        val blurKey = if (dark) PreferKey.bgImageNBlurring else PreferKey.bgImageBlurring
        backgroundEditorState = ThemeBackgroundEditorState(
            dark = dark,
            path = getPrefString(imageKey).takeUnless { it.isNullOrBlank() },
            blur = getPrefInt(blurKey, 0).coerceIn(0, 25)
        )
    }

    private fun selectBackgroundImage(dark: Boolean) {
        selectImage.launch {
            requestCode = if (dark) requestCodeBgDark else requestCodeBgLight
            mode = HandleFileContract.IMAGE
        }
    }

    private fun updateBackgroundDraft(dark: Boolean, path: String) {
        lifecycleScope.launch {
            val editorState = backgroundEditorState ?: return@launch
            if (editorState.dark != dark) return@launch
            backgroundEditorState = editorState.copy(
                path = path,
                blur = if (path.endsWith(".9.png", ignoreCase = true)) 0 else editorState.blur
            )
        }
    }

    private fun saveBackgroundEditor() {
        val editorState = backgroundEditorState ?: return
        val imageKey = if (editorState.dark) PreferKey.bgImageN else PreferKey.bgImage
        val blurKey = if (editorState.dark) {
            PreferKey.bgImageNBlurring
        } else {
            PreferKey.bgImageBlurring
        }
        editorState.path.takeUnless { it.isNullOrBlank() }?.let { path ->
            putPrefString(imageKey, path)
        } ?: removePref(imageKey)
        putPrefInt(
            blurKey,
            if (editorState.path?.endsWith(".9.png", ignoreCase = true) == true) {
                0
            } else {
                editorState.blur.coerceIn(0, 25)
            }
        )
        backgroundEditorState = null
        onBackgroundChanged(editorState.dark)
    }

    private fun onBackgroundChanged(isNightTheme: Boolean) {
        view?.post {
            if (!isAdded) return@post
            refreshContent()
            if (AppConfig.isNightTheme == isNightTheme) {
                ThemeConfig.applyTheme(requireContext())
                recreateActivities()
            }
        }
    }

    private fun recreateActivities() {
        postEvent(EventBus.RECREATE, "")
    }

    private fun copyBgFromUri(uri: Uri, storageKey: String, success: (String) -> Unit) {
        if (uri.scheme?.lowercase() in listOf("http", "https")) {
            lifecycleScope.launch {
                kotlin.runCatching {
                    appCtx.toastOnUi("下载背景图片中...")
                    val analyzeUrl = AnalyzeUrl(uri.toString())
                    val url = analyzeUrl.urlNoQuery
                    var file = requireContext().externalFiles
                    val res = okHttpClient.newCallResponse(0) {
                        addHeaders(analyzeUrl.headerMap)
                        url(url)
                    }
                    val contentType = res.header("Content-Type") ?: "image/jpeg"
                    val imageType = when {
                        contentType.contains("png", ignoreCase = true) -> "png"
                        contentType.contains("gif", ignoreCase = true) -> "gif"
                        contentType.contains("webp", ignoreCase = true) -> "webp"
                        else -> "jpg"
                    }
                    val suffix = if (url.contains(".9.png", true)) {
                        ".9.png"
                    } else {
                        ".$imageType"
                    }
                    val fileName = MD5Utils.md5Encode(url) + suffix
                    file = FileUtils.createFileIfNotExist(file, storageKey, fileName)
                    res.body.byteStream().use { inputStream ->
                        FileOutputStream(file).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    if (isAdded && context != null) success(file.absolutePath)
                }.onFailure {
                    appCtx.toastOnUi(it.localizedMessage)
                }
            }
            return
        }
        readUri(uri) { fileDoc, inputStream ->
            kotlin.runCatching {
                var file = requireContext().externalFiles
                val suffix = if (fileDoc.name.contains(".9.png", true)) {
                    ".9.png"
                } else {
                    "." + fileDoc.name.substringAfterLast(".")
                }
                val fileName = uri.inputStream(requireContext()).getOrThrow().use {
                    MD5Utils.md5Encode(it) + suffix
                }
                file = FileUtils.createFileIfNotExist(file, storageKey, fileName)
                FileOutputStream(file).use {
                    inputStream.copyTo(it)
                }
                success(file.absolutePath)
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }

    private companion object {
        const val DEFAULT_LAUNCHER_ICON = "ic_launcher"
    }
}
