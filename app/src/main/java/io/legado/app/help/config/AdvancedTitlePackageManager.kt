package io.legado.app.help.config

import androidx.annotation.Keep
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.utils.GSON
import io.legado.app.utils.externalFiles
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getFile
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.IOException
import java.util.UUID

object AdvancedTitlePackageManager {

    const val BUILTIN_ID = "builtin_default"
    const val BUILTIN_STAR_ID = "builtin_star"
    const val BUILTIN_PAGE_ID = "builtin_page"
    const val BUILTIN_MOON_ID = "builtin_moon"
    const val BUILTIN_SWORD_ID = "builtin_sword"
    const val MAX_JSON_BYTES = 2L * 1024L * 1024L
    private const val MAX_PACKAGES = 64
    private const val MANIFEST_FILE = "package.json"
    private const val LOTTIE_FILE = "title.json"

    @Keep
    data class Config(
        val id: String,
        val name: String,
        val updatedAt: Long = System.currentTimeMillis(),
        val splitMode: Int? = null,
        val delimiter: String? = null,
        val regex: String? = null,
        val heightFactor: Int? = null,
        val fontWeight: Int? = null,
        val fontSizeScale: Int? = null,
        val titleTopSpacing: Int? = null,
        val titleBottomSpacing: Int? = null,
        val textColor: Int? = null
    ) {
        fun splitRuleOrNull(): AdvancedTitleConfig.SplitRule? {
            if (splitMode == null && delimiter == null && regex == null) return null
            return AdvancedTitleConfig.SplitRule(
                mode = if (splitMode == AdvancedTitleConfig.SPLIT_REGEX) {
                    AdvancedTitleConfig.SPLIT_REGEX
                } else {
                    AdvancedTitleConfig.SPLIT_DELIMITER
                },
                delimiter = delimiter ?: " ",
                regex = regex ?: AdvancedTitleConfig.DEFAULT_REGEX
            )
        }

        fun normalizedHeightFactorOrNull(): Int? = heightFactor?.coerceIn(30, 120)
        fun normalizedFontWeightOrNull(): Int? = fontWeight?.coerceIn(100, 900)
        fun normalizedFontSizeScaleOrNull(): Int? = fontSizeScale?.coerceIn(50, 200)
        fun normalizedTitleTopSpacingOrNull(): Int? = titleTopSpacing?.coerceIn(0, 200)
        fun normalizedTitleBottomSpacingOrNull(): Int? = titleBottomSpacing?.coerceIn(0, 200)
        fun normalizedTextColorOrNull(): Int? = textColor
    }

    data class Entry(
        val config: Config,
        val directory: File? = null,
        val isBuiltin: Boolean = false
    ) {
        val id: String get() = config.id
        val name: String get() = config.name
        val updatedAt: Long get() = config.updatedAt
    }

    private data class BuiltinStyle(
        val fontWeight: Int? = null,
        val fontSizeScale: Int? = null,
        val titleTopSpacing: Int? = null,
        val titleBottomSpacing: Int? = null,
        val textColor: Int? = null
    )

    val rootDir: File
        get() = appCtx.externalFiles.getFile("advancedTitlePackages")

    private fun builtinStyle(id: String): BuiltinStyle? = appCtx
        .getPrefString(PreferKey.advancedTitleBuiltinStyles)
        ?.let { GSON.fromJsonObject<Map<String, BuiltinStyle>>(it).getOrNull() }
        ?.get(id)

    private fun saveBuiltinStyle(id: String, config: Config) {
        val styles = appCtx.getPrefString(PreferKey.advancedTitleBuiltinStyles)
            ?.let { GSON.fromJsonObject<Map<String, BuiltinStyle>>(it).getOrNull() }
            ?.toMutableMap() ?: mutableMapOf()
        styles[id] = BuiltinStyle(
            fontWeight = config.normalizedFontWeightOrNull(),
            fontSizeScale = config.normalizedFontSizeScaleOrNull(),
            titleTopSpacing = config.normalizedTitleTopSpacingOrNull(),
            titleBottomSpacing = config.normalizedTitleBottomSpacingOrNull(),
            textColor = config.normalizedTextColorOrNull()
        )
        appCtx.putPrefString(PreferKey.advancedTitleBuiltinStyles, GSON.toJson(styles))
    }

    @Volatile
    private var cachedId: String? = null
    @Volatile
    private var cachedStamp: Long = Long.MIN_VALUE
    @Volatile
    private var cachedJson: String? = null
    @Volatile
    private var builtinJsonCache: String? = null
    private val mutationLock = Any()

    fun builtinEntry(): Entry = builtinEntry(
        BUILTIN_ID,
        R.string.advanced_title_builtin
    )

    fun builtinEntries(): List<Entry> = listOf(
        builtinEntry(),
        builtinEntry(BUILTIN_STAR_ID, R.string.advanced_title_builtin_star),
        builtinEntry(BUILTIN_PAGE_ID, R.string.advanced_title_builtin_page),
    )

    private fun builtinEntry(id: String, nameRes: Int): Entry = Entry(
        config = Config(
            id = id,
            name = appCtx.getString(nameRes),
            updatedAt = 0L,
            splitMode = AdvancedTitleConfig.SPLIT_DELIMITER,
            delimiter = " ",
            regex = AdvancedTitleConfig.DEFAULT_REGEX,
            heightFactor = AdvancedTitleConfig.DEFAULT_HEIGHT_FACTOR
        ).let { base -> builtinStyle(id)?.let { style ->
            base.copy(
                fontWeight = style.fontWeight,
                fontSizeScale = style.fontSizeScale,
                titleTopSpacing = style.titleTopSpacing,
                titleBottomSpacing = style.titleBottomSpacing,
                textColor = style.textColor
            )
        } ?: base },
        isBuiltin = true
    )

    private fun isBuiltinId(id: String): Boolean = builtinEntries().any { it.id == id }

    private fun builtinEntry(id: String): Entry = builtinEntries().first { it.id == id }

    fun activeId(): String {
        val id = appCtx.getPrefString(PreferKey.advancedTitlePackage)
            ?.takeIf(::isValidId)
            ?: BUILTIN_ID
        return if (id == BUILTIN_MOON_ID || id == BUILTIN_SWORD_ID) BUILTIN_ID else id
    }

    fun activeConfig(): Config? {
        val id = activeId()
        if (isBuiltinId(id)) return builtinEntry(id).config
        return runCatching {
            verifyInstalledDirectory(localDir(id), expectedId = id)
        }.getOrNull()
    }

    suspend fun loadEntries(): List<Entry> = withContext(IO) {
        synchronized(mutationLock) {
            rootDir.mkdirs()
            AdvancedTitlePackageStorage.cleanupStaleStagingDirectories(rootDir)
            migrateLegacyIfNeeded()
            var local = loadLocalEntries()
            val validIds = local.asSequence().map { it.id }.toSet() + builtinEntries().map { it.id }
            if (activeId() !in validIds) {
                val recovery = legacyTemplate()
                    ?.takeIf { runCatching { validateJson(it) }.isSuccess }
                    ?.let { addOrUpdate(appCtx.getString(R.string.advanced_title_migrated), it) }
                appCtx.putPrefString(
                    PreferKey.advancedTitlePackage,
                    recovery?.id ?: BUILTIN_ID
                )
                if (recovery != null) local = loadLocalEntries()
                invalidate()
            }
            builtinEntries() + local.sortedWith(
                compareByDescending<Entry> { it.updatedAt }.thenBy { it.name }
            )
        }
    }

    fun currentTemplate(): String? {
        val storedId = appCtx.getPrefString(PreferKey.advancedTitlePackage)
            ?.takeIf(::isValidId)
        if (storedId == BUILTIN_MOON_ID || storedId == BUILTIN_SWORD_ID) {
            return builtinJson(BUILTIN_ID)
        }
        val explicitId = storedId
        if (explicitId == null) {
            legacyTemplate()?.takeIf { runCatching { AdvancedTitleConfig.isValidLottieJson(it) }.getOrDefault(false) }
                ?.let { return it }
        }
        val id = explicitId ?: BUILTIN_ID
        return if (isBuiltinId(id)) {
            builtinJson(id)
        } else {
            val file = lottieFile(localDir(id))
            readCached(id, file)
                ?: legacyTemplate()?.takeIf {
                    runCatching { AdvancedTitleConfig.isValidLottieJson(it) }.getOrDefault(false)
                }
                ?: builtinJson(id)
        }
    }

    fun readTemplate(entry: Entry): String {
        return if (entry.isBuiltin) {
            builtinJson(entry.id)
        } else {
            val directory = requireNotNull(entry.directory) { "Missing advanced title directory" }
            readJsonFile(lottieFile(directory))
        }
    }

    fun readTemplate(id: String): String {
        if (isBuiltinId(id)) return builtinJson(id)
        require(isValidId(id)) { "Invalid advanced title id" }
        val parent = rootDir.apply { mkdirs() }.canonicalFile
        val directory = File(parent, id).canonicalFile
        require(directory.parentFile == parent) { "Advanced title directory escaped its root" }
        val config = verifyInstalledDirectory(directory, expectedId = id)
        return readTemplate(Entry(config, directory))
    }

    fun addOrUpdate(
        name: String,
        json: String,
        oldEntry: Entry? = null,
        splitRule: AdvancedTitleConfig.SplitRule? = oldEntry?.config?.splitRuleOrNull()
            ?: AdvancedTitleConfig.globalRule,
        heightFactor: Int? = oldEntry?.config?.normalizedHeightFactorOrNull()
            ?: AdvancedTitleConfig.heightFactor,
        fontWeight: Int? = oldEntry?.config?.normalizedFontWeightOrNull()
            ?: AdvancedTitleConfig.fontWeight,
        fontSizeScale: Int? = oldEntry?.config?.normalizedFontSizeScaleOrNull()
            ?: AdvancedTitleConfig.fontSizeScale,
        titleTopSpacing: Int? = oldEntry?.config?.normalizedTitleTopSpacingOrNull()
            ?: 0,
        titleBottomSpacing: Int? = oldEntry?.config?.normalizedTitleBottomSpacingOrNull()
            ?: 0,
        textColor: Int? = oldEntry?.config?.normalizedTextColorOrNull()
            ?: AdvancedTitleConfig.textColor
    ): Entry =
        synchronized(mutationLock) {
        val normalizedName = normalizeName(name)
        validateJson(json)
        if (oldEntry?.isBuiltin == true) {
            val config = oldEntry.config.copy(
                fontWeight = fontWeight?.coerceIn(100, 900),
                fontSizeScale = fontSizeScale?.coerceIn(50, 200),
                titleTopSpacing = titleTopSpacing?.coerceIn(0, 200),
                titleBottomSpacing = titleBottomSpacing?.coerceIn(0, 200),
                textColor = textColor
            )
            saveBuiltinStyle(oldEntry.id, config)
            return@synchronized Entry(config, isBuiltin = true)
        }
        val editableOld = oldEntry
        if (editableOld == null) {
            val packageCount = rootDir.listFiles().orEmpty().count {
                it.isDirectory && !it.name.startsWith('.')
            }
            require(packageCount < MAX_PACKAGES) {
                appCtx.getString(R.string.advanced_title_package_limit)
            }
        }
        val id = editableOld?.id ?: "title_${UUID.randomUUID().toString().replace("-", "")}".take(38)
        require(isValidId(id)) { "Invalid advanced title id" }
        val parent = rootDir.apply { mkdirs() }.canonicalFile
        val target = File(parent, id).canonicalFile
        require(target.parentFile == parent) { "Advanced title directory escaped its root" }
        val staging = File(parent, ".$id.staging-${UUID.randomUUID()}")
        val backup = File(parent, ".$id.backup-${UUID.randomUUID()}")
        val config = Config(
            id = id,
            name = normalizedName,
            updatedAt = System.currentTimeMillis(),
            splitMode = splitRule?.mode,
            delimiter = splitRule?.delimiter,
            regex = splitRule?.regex,
            heightFactor = heightFactor?.coerceIn(30, 120),
            fontWeight = fontWeight?.coerceIn(100, 900),
            fontSizeScale = fontSizeScale?.coerceIn(50, 200),
            titleTopSpacing = titleTopSpacing?.coerceIn(0, 200),
            titleBottomSpacing = titleBottomSpacing?.coerceIn(0, 200),
            textColor = textColor
        )
        try {
            staging.mkdirs()
            File(staging, MANIFEST_FILE).writeText(GSON.toJson(config))
            lottieFile(staging).writeText(json)
            verifyInstalledDirectory(
                directory = staging,
                expectedId = id,
                requireDirectoryIdMatch = false
            )
            val installed = BubbleDirectoryTransaction().install(
                target,
                staging,
                backup
            ) { installedDir ->
                val verified = verifyInstalledDirectory(installedDir, expectedId = id)
                Entry(verified, installedDir)
            }
            invalidate()
            installed
        } finally {
            AdvancedTitlePackageStorage.deleteStagingDirectory(parent, staging)
        }
    }

    fun createBuiltinCopy(name: String): Entry = addOrUpdate(name, builtinJson())

    fun apply(entry: Entry) = synchronized(mutationLock) {
        val json = readTemplate(entry)
        validateJson(json)
        appCtx.putPrefString(PreferKey.advancedTitlePackage, entry.id)
        // Keep the active JSON in the legacy backup field as a recovery copy. Rendering still
        // uses the bounded file cache above, so chapter changes do not repeatedly parse prefs.
        AdvancedTitleConfig.lottieJson = json
        AdvancedTitleConfig.lottiePath = null
        entry.config.splitRuleOrNull()?.let { AdvancedTitleConfig.globalRule = it }
        entry.config.normalizedHeightFactorOrNull()?.let { AdvancedTitleConfig.heightFactor = it }
        invalidate()
    }

    fun delete(entry: Entry) {
        synchronized(mutationLock) {
            if (entry.isBuiltin || entry.id == BUILTIN_ID) return@synchronized
            val parent = rootDir.canonicalFile
            val target = (entry.directory ?: localDir(entry.id)).canonicalFile
            require(target.parentFile == parent) { "Advanced title directory escaped its root" }
            if (target.exists() && !target.deleteRecursively() && target.exists()) {
                throw IOException("Unable to delete advanced title")
            }
            if (activeId() == entry.id) {
                appCtx.putPrefString(PreferKey.advancedTitlePackage, BUILTIN_ID)
                AdvancedTitleConfig.lottieJson = builtinJson()
                AdvancedTitleConfig.lottiePath = null
                val builtin = builtinEntry().config
                builtin.splitRuleOrNull()?.let { AdvancedTitleConfig.globalRule = it }
                builtin.normalizedHeightFactorOrNull()?.let {
                    AdvancedTitleConfig.heightFactor = it
                }
            }
            invalidate()
        }
    }

    fun validateJson(json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty()) { appCtx.getString(R.string.advanced_title_invalid_json) }
        require(bytes.size <= MAX_JSON_BYTES) { appCtx.getString(R.string.advanced_title_too_large) }
        require(AdvancedTitleConfig.isValidLottieJson(json)) {
            appCtx.getString(R.string.advanced_title_invalid_json)
        }
    }

    fun invalidate() {
        cachedId = null
        cachedStamp = Long.MIN_VALUE
        cachedJson = null
    }

    private fun loadLocalEntries(): List<Entry> {
        val parent = rootDir.apply { mkdirs() }.canonicalFile
        return parent.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory && !it.name.startsWith('.') }
            .take(MAX_PACKAGES * 2)
            .mapNotNull { directory ->
                runCatching {
                    val canonical = directory.canonicalFile
                    require(canonical.parentFile == parent)
                    val config = verifyInstalledDirectory(canonical)
                    Entry(config, canonical)
                }.getOrNull()
            }
            .take(MAX_PACKAGES)
            .toList()
    }

    private fun verifyInstalledDirectory(
        directory: File,
        expectedId: String? = null,
        requireDirectoryIdMatch: Boolean = true
    ): Config {
        val manifest = File(directory, MANIFEST_FILE)
        require(manifest.isFile && manifest.length() in 1..64L * 1024L) {
            "Advanced title manifest is invalid"
        }
        val config = GSON.fromJsonObject<Config>(manifest.readText()).getOrThrow()
        require(isValidId(config.id)) { "Advanced title id is invalid" }
        require(expectedId == null || config.id == expectedId) { "Advanced title id changed" }
        AdvancedTitlePackageStorage.requireDirectoryMatchesId(
            directoryName = directory.name,
            configId = config.id,
            requireMatch = requireDirectoryIdMatch
        )
        require(config.name.isNotBlank() && config.name.length <= 100) { "Advanced title name is invalid" }
        val json = readJsonFile(lottieFile(directory))
        require(AdvancedTitleConfig.hasRenderableLayers(json)) {
            appCtx.getString(R.string.advanced_title_invalid_json)
        }
        return config.copy(name = config.name.trim())
    }

    private fun migrateLegacyIfNeeded() {
        if (!appCtx.getPrefString(PreferKey.advancedTitlePackage).isNullOrBlank()) return
        val legacy = legacyTemplate()
            ?.takeIf { runCatching { validateJson(it) }.isSuccess }
        if (legacy == null) {
            appCtx.putPrefString(PreferKey.advancedTitlePackage, BUILTIN_ID)
            return
        }
        val builtin = builtinJson()
        val activeJson: String
        if (legacy == builtin) {
            appCtx.putPrefString(PreferKey.advancedTitlePackage, BUILTIN_ID)
            activeJson = builtin
        } else {
            val migrated = addOrUpdate(appCtx.getString(R.string.advanced_title_migrated), legacy)
            appCtx.putPrefString(PreferKey.advancedTitlePackage, migrated.id)
            activeJson = legacy
        }
        AdvancedTitleConfig.lottieJson = activeJson
        AdvancedTitleConfig.lottiePath = null
        invalidate()
    }

    private fun legacyTemplate(): String? {
        AdvancedTitleConfig.lottieJson?.takeIf { it.isNotBlank() }?.let { return it }
        val path = AdvancedTitleConfig.lottiePath?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { readJsonFile(File(path)) }.getOrNull()
    }

    private fun readCached(id: String, file: File): String? {
        if (!file.isFile) return null
        val stamp = file.lastModified() xor file.length()
        if (cachedId == id && cachedStamp == stamp) return cachedJson
        return runCatching { readJsonFile(file) }.getOrNull()?.also { json ->
            cachedJson = json
            cachedStamp = stamp
            cachedId = id
        }
    }

    private fun builtinJson(id: String = BUILTIN_ID): String {
        if (id == BUILTIN_ID) {
            builtinJsonCache?.let { return it }
        }
        val resource = when (id) {
            BUILTIN_STAR_ID -> R.raw.advanced_title_star
            BUILTIN_PAGE_ID -> R.raw.advanced_title_page
            BUILTIN_MOON_ID -> R.raw.advanced_title_moon
            BUILTIN_SWORD_ID -> R.raw.advanced_title_sword
            else -> R.raw.advanced_title_lottie
        }
        return appCtx.resources.openRawResource(resource)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
            .also { if (id == BUILTIN_ID) builtinJsonCache = it }
    }

    private fun readJsonFile(file: File): String {
        require(file.isFile) { "Advanced title file is missing" }
        require(file.length() in 1..MAX_JSON_BYTES) {
            appCtx.getString(R.string.advanced_title_too_large)
        }
        return file.readText(Charsets.UTF_8)
    }

    private fun localDir(id: String): File = rootDir.getFile(id)

    private fun lottieFile(directory: File): File = directory.getFile(LOTTIE_FILE)

    private fun normalizeName(value: String): String {
        return value.trim().replace(Regex("[\\r\\n\\t]+"), " ")
            .take(100)
            .ifBlank { appCtx.getString(R.string.advanced_title_unnamed) }
    }

    private fun isValidId(value: String): Boolean = value.matches(Regex("^[A-Za-z0-9_-]{1,64}$"))
}
