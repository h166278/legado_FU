package io.legado.app.help.config

import android.content.Context
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legado.app.constant.PreferKey
import io.legado.app.ui.design.theme.NgColorGenerationMode
import io.legado.app.ui.design.theme.NgColorMath
import io.legado.app.ui.design.theme.NgColorSpec
import io.legado.app.ui.design.theme.NgColorSystem
import io.legado.app.ui.design.theme.NgContrastLevel
import io.legado.app.ui.design.theme.NgManualColorSet
import io.legado.app.ui.design.theme.NgPaletteStyle
import io.legado.app.ui.design.theme.NgTopBarTextMode
import io.legado.app.utils.GSON
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.statusBarHeight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

internal const val NG_MANAGED_THEME_SCHEMA_VERSION = 1

@Keep
internal data class NgThemeBackground(
    @SerializedName("path") val path: String? = null,
    @SerializedName("blur") val blur: Int = 0
)

@Keep
internal data class NgThemeBarProfile(
    @SerializedName("useFloatingBottomBar")
    val useFloatingBottomBar: Boolean? = null,
    @SerializedName("floatingBottomBarBottomDistancePx")
    val floatingBottomBarBottomDistancePx: Int? = null,
    @SerializedName("floatingBottomBarTransparency")
    val floatingBottomBarTransparency: Int? = null,
    @SerializedName("bookshelfTopBarStyle")
    val bookshelfTopBarStyle: Int? = null,
    @SerializedName("bookshelfFloatingDockTopDistancePx")
    val bookshelfFloatingDockTopDistancePx: Int? = null,
    @SerializedName("bookshelfFloatingDockTransparency")
    val bookshelfFloatingDockTransparency: Int? = null,
) {
    fun normalized(): NgThemeBarProfile = copy(
        floatingBottomBarBottomDistancePx = floatingBottomBarBottomDistancePx?.let {
            FloatingBottomBarConfig.normalizeBottomDistancePx(it)
        },
        floatingBottomBarTransparency = floatingBottomBarTransparency?.let {
            FloatingBottomBarConfig.normalizeTransparencyPercent(it)
        },
        bookshelfTopBarStyle = bookshelfTopBarStyle?.let {
            BookshelfTopBarStyle.fromValue(it).value
        },
        bookshelfFloatingDockTopDistancePx = bookshelfFloatingDockTopDistancePx?.let {
            BookshelfFloatingDockConfig.normalizeTopDistancePx(it)
        },
        bookshelfFloatingDockTransparency = bookshelfFloatingDockTransparency?.let {
            BookshelfFloatingDockConfig.normalizeTransparencyPercent(it)
        },
    )

    companion object {
        const val EDITOR_DEFAULT_BOTTOM_DISTANCE_PX = 40
        const val EDITOR_DEFAULT_TOP_DISTANCE_PX = 360
    }
}

internal fun NgThemeBarProfile?.withFallback(
    fallback: NgThemeBarProfile
): NgThemeBarProfile {
    val profile = this
    return NgThemeBarProfile(
        useFloatingBottomBar = profile?.useFloatingBottomBar
            ?: fallback.useFloatingBottomBar,
        floatingBottomBarBottomDistancePx = profile?.floatingBottomBarBottomDistancePx
            ?: fallback.floatingBottomBarBottomDistancePx,
        floatingBottomBarTransparency = profile?.floatingBottomBarTransparency
            ?: fallback.floatingBottomBarTransparency,
        bookshelfTopBarStyle = profile?.bookshelfTopBarStyle
            ?: fallback.bookshelfTopBarStyle,
        bookshelfFloatingDockTopDistancePx = profile?.bookshelfFloatingDockTopDistancePx
            ?: fallback.bookshelfFloatingDockTopDistancePx,
        bookshelfFloatingDockTransparency = profile?.bookshelfFloatingDockTransparency
            ?: fallback.bookshelfFloatingDockTransparency,
    ).normalized()
}

@Keep
internal data class NgThemeNavigationAssets(
    @SerializedName("home") val home: String? = null,
    @SerializedName("bookshelf") val bookshelf: String? = null,
    @SerializedName("explore") val explore: String? = null,
    @SerializedName("rss") val rss: String? = null,
    @SerializedName("my") val my: String? = null,
) {
    fun normalized(): NgThemeNavigationAssets = copy(
        home = home.normalizedPackageRelativePath(),
        bookshelf = bookshelf.normalizedPackageRelativePath(),
        explore = explore.normalizedPackageRelativePath(),
        rss = rss.normalizedPackageRelativePath(),
        my = my.normalizedPackageRelativePath(),
    )
}

@Keep
internal data class NgThemeResourceProfile(
    @SerializedName("navigation")
    val navigation: NgThemeNavigationAssets = NgThemeNavigationAssets(),
    @SerializedName("appFont") val appFont: String? = null,
) {
    fun normalized(): NgThemeResourceProfile = copy(
        navigation = navigation.normalized(),
        appFont = appFont.normalizedPackageRelativePath(),
    )
}

@Keep
internal data class NgThemeCoverProfile(
    @SerializedName("applyAlbumSelection")
    val applyAlbumSelection: Boolean = false,
    @SerializedName("albumId") val albumId: String? = null,
    @SerializedName("loadOnlyWifi") val loadOnlyWifi: Boolean? = null,
    @SerializedName("useDefault") val useDefault: Boolean? = null,
    @SerializedName("showName") val showName: Boolean? = null,
    @SerializedName("showAuthor") val showAuthor: Boolean? = null,
    @SerializedName("showNameDark") val showNameDark: Boolean? = null,
    @SerializedName("showAuthorDark") val showAuthorDark: Boolean? = null,
) {
    fun normalized(): NgThemeCoverProfile = copy(
        albumId = albumId?.trim()?.takeIf(String::isNotEmpty),
    )
}

@Keep
internal data class NgManagedTheme(
    @SerializedName("schemaVersion")
    val schemaVersion: Int = NG_MANAGED_THEME_SCHEMA_VERSION,
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("colors") val colors: NgColorSystem,
    @SerializedName("lightBackground")
    val lightBackground: NgThemeBackground = NgThemeBackground(),
    @SerializedName("darkBackground")
    val darkBackground: NgThemeBackground = NgThemeBackground(),
    @SerializedName("transparentAppBars")
    val transparentAppBars: Boolean = false,
    @SerializedName("barProfile")
    val barProfile: NgThemeBarProfile? = null,
    @SerializedName("packageRootPath") val packageRootPath: String? = null,
    @SerializedName("resourceProfile")
    val resourceProfile: NgThemeResourceProfile? = null,
    @SerializedName("coverProfile")
    val coverProfile: NgThemeCoverProfile? = null,
) {
    fun normalized(): NgManagedTheme = copy(
        schemaVersion = NG_MANAGED_THEME_SCHEMA_VERSION,
        id = id.trim(),
        name = name.trim(),
        colors = colors.normalized(),
        lightBackground = lightBackground.copy(blur = lightBackground.blur.coerceIn(0, 25)),
        darkBackground = darkBackground.copy(blur = darkBackground.blur.coerceIn(0, 25)),
        barProfile = barProfile?.normalized(),
        resourceProfile = resourceProfile?.normalized() ?: NgThemeResourceProfile(),
        coverProfile = coverProfile?.normalized(),
    )

    fun resolvePackageAsset(relativePath: String?): File? {
        val normalizedPath = relativePath.normalizedPackageRelativePath() ?: return null
        val root = packageRootPath?.let(::File)?.canonicalFile ?: return null
        val target = File(root, normalizedPath).canonicalFile
        return target.takeIf {
            it.toPath().startsWith(root.toPath()) && it.isFile
        }
    }
}

private fun String?.normalizedPackageRelativePath(): String? {
    val normalized = this?.trim()?.replace('\\', '/')
        ?.takeIf { it.isNotEmpty() && !it.startsWith('/') }
        ?: return null
    val segments = normalized.split('/')
    return normalized.takeIf {
        ':' !in it && segments.none { segment ->
            segment.isEmpty() || segment == "." || segment == ".."
        }
    }
}

internal data class NgThemeLibraryState(
    val savedThemes: List<NgManagedTheme> = emptyList(),
    val activeThemeId: String? = null
)

/** 新主题管理只使用 NG v1 记录，不读取或迁移旧 themeConfig.json。 */
internal object NgThemeLibraryStore {

    private const val THEMES_KEY = "ngManagedThemes.v1"
    private const val ACTIVE_THEME_KEY = "ngActiveManagedThemeId.v1"
    private val lock = Any()
    private var initialized = false
    private val mutableState = MutableStateFlow(NgThemeLibraryState())

    fun observe(context: Context): StateFlow<NgThemeLibraryState> {
        ensureInitialized(context)
        return mutableState.asStateFlow()
    }

    fun current(context: Context): NgThemeLibraryState {
        ensureInitialized(context)
        return mutableState.value
    }

    fun allThemes(context: Context): List<NgManagedTheme> =
        NgBuiltInThemes.all + current(context).savedThemes

    fun activeTheme(context: Context): NgManagedTheme? {
        val state = current(context)
        return allThemes(context).firstOrNull { it.id == state.activeThemeId }
    }

    fun snapshotCurrent(context: Context, name: String): NgManagedTheme {
        val state = current(context)
        val active = allThemes(context).firstOrNull { it.id == state.activeThemeId }
        val existing = state.savedThemes.firstOrNull { it.name.equals(name.trim(), true) }
        return NgManagedTheme(
            id = existing?.id ?: "local.${UUID.randomUUID()}",
            name = name.trim(),
            colors = NgColorConfigStore.current(context),
            lightBackground = NgThemeBackground(
                path = context.getPrefString(PreferKey.bgImage),
                blur = context.getPrefInt(PreferKey.bgImageBlurring, 0)
            ),
            darkBackground = NgThemeBackground(
                path = context.getPrefString(PreferKey.bgImageN),
                blur = context.getPrefInt(PreferKey.bgImageNBlurring, 0)
            ),
            transparentAppBars = context.getPrefBoolean(PreferKey.tNavBar, false),
            barProfile = currentBarProfile(context),
            packageRootPath = active?.packageRootPath,
            resourceProfile = active?.resourceProfile ?: NgThemeResourceProfile(),
            coverProfile = NgThemeCoverProfile(
                applyAlbumSelection = true,
                albumId = NgCoverAlbumStore.current(context).selectedAlbumId,
                loadOnlyWifi = context.getPrefBoolean(PreferKey.loadCoverOnlyWifi, false),
                useDefault = context.getPrefBoolean(PreferKey.useDefaultCover, false),
                showName = context.getPrefBoolean(PreferKey.coverShowName, true),
                showAuthor = context.getPrefBoolean(PreferKey.coverShowAuthor, true),
                showNameDark = context.getPrefBoolean(PreferKey.coverShowNameN, true),
                showAuthorDark = context.getPrefBoolean(PreferKey.coverShowAuthorN, true),
            ),
        ).normalized()
    }

    fun editableBarProfile(
        context: Context,
        profile: NgThemeBarProfile?
    ): NgThemeBarProfile = profile.withFallback(currentBarProfile(context))

    fun currentThemeName(context: Context): String {
        val state = current(context)
        allThemes(context).firstOrNull { it.id == state.activeThemeId }?.let { return it.name }
        val dayName = context.getPrefString(PreferKey.dThemeName)
        return when (dayName) {
            null, "", "默认" -> "经典主题"
            else -> dayName
        }
    }

    fun saveCurrent(context: Context, name: String): NgManagedTheme {
        require(name.isNotBlank()) { "主题名称不能为空" }
        val saved = addOrReplace(context, snapshotCurrent(context, name))
        synchronized(lock) {
            persistActive(context, saved.id)
            mutableState.value = mutableState.value.copy(activeThemeId = saved.id)
        }
        return saved
    }

    fun addOrReplace(context: Context, theme: NgManagedTheme): NgManagedTheme = synchronized(lock) {
        ensureInitialized(context)
        val normalized = theme.normalized()
        require(normalized.id.isNotEmpty() && normalized.name.isNotEmpty()) { "主题数据不完整" }
        val current = mutableState.value
        val replacedIds = current.savedThemes
            .filter { it.id == normalized.id || it.name.equals(normalized.name, true) }
            .mapTo(hashSetOf()) { it.id }
        val updated = buildList {
            addAll(current.savedThemes.filterNot { it.id in replacedIds })
            add(normalized)
        }.sortedBy { it.name.lowercase() }
        persistThemes(context, updated)
        mutableState.value = current.copy(savedThemes = updated)
        normalized
    }

    fun rename(context: Context, themeId: String, newName: String): Boolean = synchronized(lock) {
        ensureInitialized(context)
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return@synchronized false
        val current = mutableState.value
        if (current.savedThemes.any { it.id != themeId && it.name.equals(trimmed, true) }) {
            return@synchronized false
        }
        val updated = current.savedThemes.map {
            if (it.id == themeId) it.copy(name = trimmed) else it
        }
        if (updated == current.savedThemes) return@synchronized false
        persistThemes(context, updated)
        mutableState.value = current.copy(savedThemes = updated)
        true
    }

    fun remove(context: Context, themeId: String): NgManagedTheme? = synchronized(lock) {
        ensureInitialized(context)
        val current = mutableState.value
        val removed = current.savedThemes.firstOrNull { it.id == themeId }
            ?: return@synchronized null
        val updated = current.savedThemes.filterNot { it.id == themeId }
        val nextActive = current.activeThemeId.takeUnless { it == themeId }
        persistThemes(context, updated)
        persistActive(context, nextActive)
        mutableState.value = NgThemeLibraryState(updated, nextActive)
        removed.packageRootPath
            ?.takeIf { root -> updated.none { it.packageRootPath == root } }
            ?.let { deleteOwnedPackageRoot(context, it) }
        removed
    }

    fun apply(context: Context, theme: NgManagedTheme) {
        val previousActiveId = synchronized(lock) {
            ensureInitialized(context)
            val previous = mutableState.value.activeThemeId
            persistActive(context, theme.id)
            mutableState.value = mutableState.value.copy(activeThemeId = theme.id)
            previous
        }
        if (ThemeConfig.applyManagedTheme(context, theme)) return
        synchronized(lock) {
            persistActive(context, previousActiveId)
            mutableState.value = mutableState.value.copy(activeThemeId = previousActiveId)
        }
    }

    private fun currentBarProfile(context: Context): NgThemeBarProfile = NgThemeBarProfile(
        useFloatingBottomBar = AppConfig.useFloatingBottomBar,
        floatingBottomBarBottomDistancePx = FloatingBottomBarConfig.resolveBottomDistancePx(
            storedDistancePx = AppConfig.floatingBottomBarBottomDistancePx,
            density = context.resources.displayMetrics.density,
        ),
        floatingBottomBarTransparency = AppConfig.floatingBottomBarTransparency,
        bookshelfTopBarStyle = AppConfig.bookshelfTopBarStyle.value,
        bookshelfFloatingDockTopDistancePx = BookshelfFloatingDockConfig.resolveTopDistancePx(
            storedDistancePx = AppConfig.bookshelfFloatingDockTopDistancePx,
            screenWidthPx = context.resources.displayMetrics.widthPixels,
            density = context.resources.displayMetrics.density,
            statusBarHeightPx = context.statusBarHeight,
        ),
        bookshelfFloatingDockTransparency = AppConfig.bookshelfFloatingDockTransparency,
    )

    fun uniqueName(context: Context, requestedName: String): String {
        val base = requestedName.trim().ifEmpty { "导入主题" }
        val names = allThemes(context).mapTo(hashSetOf()) { it.name.lowercase() }
        if (base.lowercase() !in names) return base
        var index = 2
        while ("$base $index".lowercase() in names) index++
        return "$base $index"
    }

    private fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            val prefs = context.defaultSharedPreferences
            val saved = prefs.getString(THEMES_KEY, null)?.let { raw ->
                runCatching {
                    GSON.fromJson(raw, Array<NgManagedTheme>::class.java)
                        .orEmpty()
                        .filter { it.schemaVersion == NG_MANAGED_THEME_SCHEMA_VERSION }
                        .map(NgManagedTheme::normalized)
                        .filter { it.id.isNotEmpty() && it.name.isNotEmpty() }
                }.getOrDefault(emptyList())
            }.orEmpty()
            mutableState.value = NgThemeLibraryState(
                savedThemes = saved,
                activeThemeId = prefs.getString(ACTIVE_THEME_KEY, null)
            )
            initialized = true
        }
    }

    private fun persistThemes(context: Context, themes: List<NgManagedTheme>) {
        check(
            context.defaultSharedPreferences.edit()
                .putString(THEMES_KEY, GSON.toJson(themes))
                .commit()
        ) { "无法保存主题列表" }
    }

    private fun persistActive(context: Context, themeId: String?) {
        context.defaultSharedPreferences.edit()
            .putString(ACTIVE_THEME_KEY, themeId)
            .apply()
    }

    private fun deleteOwnedPackageRoot(context: Context, path: String) {
        runCatching {
            val root = File(path).canonicalFile
            val owned = File(context.filesDir, NgThemePackageManager.PACKAGE_DIR).canonicalFile
            if (root.parentFile == owned && root.isDirectory) root.deleteRecursively()
        }
    }
}

internal object NgBuiltInThemes {
    private const val BACKGROUND_PREFIX = "asset://defaultData/theme/"

    val classic = theme(
        id = "builtin.ng.classic",
        name = "经典主题",
        lightPrimary = 0xFFE53935.toInt(),
        lightSecondary = 0xFF795548.toInt(),
        darkPrimary = 0xFFD84315.toInt(),
        darkSecondary = 0xFF546E7A.toInt(),
        darkPrimaryText = 0xFFFFFFFF.toInt(),
        darkSecondaryText = 0xB3FFFFFF.toInt(),
        darkBackgroundColor = 0xFF212121.toInt(),
        darkLabelContainer = 0xFF303030.toInt(),
    )
    val warm = theme(
        id = "builtin.ng.warm",
        name = "暖色渐变",
        lightPrimary = 0xFFF78E66.toInt(),
        lightSecondary = 0xFFFFFFFF.toInt(),
        darkPrimary = 0xFFF78E66.toInt(),
        darkSecondary = 0xFF303030.toInt(),
        lightBackgroundPath = "${BACKGROUND_PREFIX}reading_ng_warm.png",
        transparentAppBars = true
    )
    val bamboo = theme(
        id = "builtin.ng.bamboo",
        name = "竹影之韵",
        lightPrimary = 0xFF7F9554.toInt(),
        lightSecondary = 0xFFFFFFFF.toInt(),
        darkPrimary = 0xFFA8C477.toInt(),
        darkSecondary = 0xFF303030.toInt(),
        lightBackgroundPath = "${BACKGROUND_PREFIX}reading_ng_bamboo.png",
        transparentAppBars = true
    )
    val mist = theme(
        id = "builtin.ng.mist",
        name = "灰色雾霭",
        lightPrimary = 0xFF758DB4.toInt(),
        lightSecondary = 0xFFFFFFFF.toInt(),
        darkPrimary = 0xFF9DB6DE.toInt(),
        darkSecondary = 0xFF303030.toInt(),
        lightBackgroundPath = "${BACKGROUND_PREFIX}reading_ng_mist.png",
        darkBackgroundPath = "${BACKGROUND_PREFIX}reading_ng_mist.png",
        lightTopBarTextMode = NgTopBarTextMode.LIGHT,
        darkTopBarTextMode = NgTopBarTextMode.LIGHT,
        reuseLightColorsAtNight = true,
        transparentAppBars = true
    )

    val autumn = warm.copy(
        id = "builtin.ng.autumn_mountains",
        name = "秋山书意",
        lightBackground = NgThemeBackground(
            path = "${BACKGROUND_PREFIX}reading_ng_autumn_mountains.png"
        ),
        barProfile = NgThemeBarProfile(
            useFloatingBottomBar = true,
            floatingBottomBarBottomDistancePx = 40,
            floatingBottomBarTransparency = 40,
            bookshelfTopBarStyle = BookshelfTopBarStyle.FLOATING_DOCK.value,
            bookshelfFloatingDockTopDistancePx = 360,
            bookshelfFloatingDockTransparency = 40,
        ),
    )

    val all = listOf(classic, warm, bamboo, mist, autumn)

    private fun theme(
        id: String,
        name: String,
        lightPrimary: Int,
        lightSecondary: Int,
        darkPrimary: Int,
        darkSecondary: Int,
        darkPrimaryText: Int? = null,
        darkSecondaryText: Int? = null,
        darkBackgroundColor: Int = 0xFF202124.toInt(),
        darkLabelContainer: Int = 0xFF2A2B2F.toInt(),
        lightBackgroundPath: String? = null,
        darkBackgroundPath: String? = null,
        lightTopBarTextMode: NgTopBarTextMode = NgTopBarTextMode.AUTO,
        darkTopBarTextMode: NgTopBarTextMode = NgTopBarTextMode.AUTO,
        reuseLightColorsAtNight: Boolean = false,
        transparentAppBars: Boolean = false
    ): NgManagedTheme {
        val light = manualColors(
            primary = lightPrimary,
            secondary = lightSecondary,
            background = 0xFFF5F5F5.toInt(),
            label = 0xFFEEEEEE.toInt()
        )
        val dark = if (reuseLightColorsAtNight) {
            light
        } else {
            manualColors(
                primary = darkPrimary,
                secondary = darkSecondary,
                background = darkBackgroundColor,
                label = darkLabelContainer,
                primaryText = darkPrimaryText,
                secondaryText = darkSecondaryText,
            )
        }
        return NgManagedTheme(
            id = id,
            name = name,
            colors = NgColorSystem(
                mode = NgColorGenerationMode.MANUAL,
                lightSeed = lightPrimary,
                darkSeed = if (reuseLightColorsAtNight) lightPrimary else darkPrimary,
                paletteStyle = NgPaletteStyle.TONAL_SPOT,
                contrast = NgContrastLevel.DEFAULT,
                colorSpec = NgColorSpec.MATERIAL_3_2021,
                manualLight = light,
                manualDark = dark,
                lightTopBarTextMode = lightTopBarTextMode,
                darkTopBarTextMode = if (reuseLightColorsAtNight) {
                    lightTopBarTextMode
                } else {
                    darkTopBarTextMode
                }
            ),
            lightBackground = NgThemeBackground(lightBackgroundPath),
            darkBackground = NgThemeBackground(darkBackgroundPath),
            transparentAppBars = transparentAppBars,
            barProfile = NgThemeBarProfile(
                useFloatingBottomBar = false,
                bookshelfTopBarStyle = BookshelfTopBarStyle.TRADITIONAL.value,
            ),
        )
    }

    private fun manualColors(
        primary: Int,
        secondary: Int,
        background: Int,
        label: Int,
        primaryText: Int? = null,
        secondaryText: Int? = null,
    ) = NgManualColorSet(
        primary = primary,
        secondary = secondary,
        primaryText = primaryText ?: NgColorMath.contentColorFor(background),
        secondaryText = secondaryText ?: NgColorMath.contentColorFor(label),
        background = background,
        labelContainer = label
    )
}
