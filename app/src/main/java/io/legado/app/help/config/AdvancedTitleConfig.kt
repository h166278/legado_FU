package io.legado.app.help.config

import io.legado.app.R
import com.airbnb.lottie.LottieCompositionFactory
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.Book
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import org.json.JSONArray
import org.json.JSONObject
import splitties.init.appCtx
import java.io.File

object AdvancedTitleConfig {

    const val TITLE_MODE_ADVANCED = 3
    const val SPLIT_DELIMITER = 0
    const val SPLIT_REGEX = 1
    const val LOTTIE_BLOCK_ROLE = "advanced_title_lottie"
    const val DEFAULT_HEIGHT_FACTOR = 55

    // Rimchars built-in advanced title geometry and typography.
    const val TEMPLATE_WIDTH = 720f
    const val TEMPLATE_HEIGHT = 210f
    const val CHAPTER_NUMBER_X = 360f
    const val CHAPTER_NUMBER_Y = 58f
    const val CHAPTER_NUMBER_SIZE = 28
    const val CHAPTER_NUMBER_TRACKING = 90
    const val TITLE_X = 360f
    const val TITLE_SIZE = 48f
    const val TITLE_Y = 124f
    const val TITLE_LETTER_SPACING = 0.01f
    const val TITLE_FONT_ASCENT_RATIO = 0.75f
    const val ORNAMENT_X = 360f
    const val ORNAMENT_Y = 180f

    private const val BOOK_RULE_KEY = "advancedTitleRule"

    data class SplitRule(
        val mode: Int = SPLIT_DELIMITER,
        val delimiter: String = " ",
        val regex: String = DEFAULT_REGEX
    )

    data class Parts(
        val title: String,
        val s1: String,
        val s2: String
    )

    var globalRule: SplitRule
        get() = appCtx.getPrefString(PreferKey.advancedTitleConfig)
            ?.let { GSON.fromJsonObject<SplitRule>(it).getOrNull() }
            ?: SplitRule()
        set(value) {
            appCtx.putPrefString(PreferKey.advancedTitleConfig, GSON.toJson(value))
        }

    var lottieJson: String?
        get() = appCtx.getPrefString(PreferKey.advancedTitleLottieJson)
        set(value) {
            appCtx.putPrefString(PreferKey.advancedTitleLottieJson, value?.takeIf { it.isNotBlank() })
        }

    var lottiePath: String?
        get() = appCtx.getPrefString(PreferKey.advancedTitleLottiePath)
        set(value) {
            appCtx.putPrefString(PreferKey.advancedTitleLottiePath, value?.takeIf { it.isNotBlank() })
        }

    var heightFactor: Int
        get() = appCtx.getPrefInt(PreferKey.advancedTitleHeightFactor, DEFAULT_HEIGHT_FACTOR)
            .coerceIn(30, 120)
        set(value) {
            appCtx.putPrefInt(PreferKey.advancedTitleHeightFactor, value.coerceIn(30, 120))
        }

    var fontWeight: Int
        get() = appCtx.getPrefInt(PreferKey.advancedTitleFontWeight, 400)
            .coerceIn(100, 900)
        set(value) {
            appCtx.putPrefInt(PreferKey.advancedTitleFontWeight, value.coerceIn(100, 900))
        }

    /** 高级标题文本字号缩放（百分比，50-200，100 表示模板原字号） */
    var fontSizeScale: Int
        get() = appCtx.getPrefInt(PreferKey.advancedTitleFontSize, 100)
            .coerceIn(50, 200)
        set(value) {
            appCtx.putPrefInt(PreferKey.advancedTitleFontSize, value.coerceIn(50, 200))
        }

    var textColor: Int?
        get() = appCtx.getPrefInt(PreferKey.advancedTitleTextColor, Int.MIN_VALUE)
            .takeUnless { it == Int.MIN_VALUE }
        set(value) {
            appCtx.putPrefInt(PreferKey.advancedTitleTextColor, value ?: Int.MIN_VALUE)
        }

    fun effectiveFontWeight(): Int = AdvancedTitlePackageManager.activeConfig()
        ?.normalizedFontWeightOrNull() ?: fontWeight

    fun effectiveFontSizeScale(): Int = AdvancedTitlePackageManager.activeConfig()
        ?.normalizedFontSizeScaleOrNull() ?: fontSizeScale

    fun effectiveTextColor(): Int? = AdvancedTitlePackageManager.activeConfig()
        ?.normalizedTextColorOrNull() ?: textColor

    fun effectiveTitleTopSpacing(fallback: Int): Int = AdvancedTitlePackageManager.activeConfig()
        ?.normalizedTitleTopSpacingOrNull() ?: fallback

    fun effectiveTitleBottomSpacing(fallback: Int): Int = AdvancedTitlePackageManager.activeConfig()
        ?.normalizedTitleBottomSpacingOrNull() ?: fallback

    fun bookRule(book: Book?): SplitRule? {
        val value = book?.getVariable(BOOK_RULE_KEY)?.takeIf { it.isNotBlank() } ?: return null
        return GSON.fromJsonObject<SplitRule>(value).getOrNull()
    }

    fun setBookRule(book: Book, rule: SplitRule?) {
        book.putVariable(BOOK_RULE_KEY, rule?.let { GSON.toJson(it) })
    }

    fun effectiveRule(book: Book?): SplitRule = bookRule(book) ?: globalRule

    fun split(title: String, book: Book? = null): Parts {
        val cleanTitle = title.trim()
        val rule = effectiveRule(book)
        return split(cleanTitle, rule)
    }

    fun split(title: String, rule: SplitRule): Parts {
        val cleanTitle = title.trim()
        return when (rule.mode) {
            SPLIT_REGEX -> splitByRegex(cleanTitle, rule.regex)
            else -> splitByDelimiter(cleanTitle, rule.delimiter)
        }
    }

    fun renderLottieJson(book: Book, title: String): String? = runCatching {
        val raw = AdvancedTitlePackageManager.currentTemplate()
            ?: lottieJson?.takeIf { it.isNotBlank() }
            ?: lottiePath?.takeIf { it.isNotBlank() }?.let { path ->
                runCatching { File(path).takeIf { it.isFile }?.readText() }.getOrNull()
            }
        raw?.let {
            val configuredTextColor = effectiveTextColor()
            val renderTextColor = configuredTextColor
                ?: ReadBookConfig.textColor
            hideLottieChapterTitle(
                replaceVariables(it, book, title)
            )
        }
    }.getOrNull()

    fun renderValidLottieJson(book: Book, title: String): String? {
        val json = renderLottieJson(book, title)?.takeIf { it.isNotBlank() } ?: return null
        return json.takeIf { hasRenderableLayers(it) }
    }

    fun isValidLottieJson(json: String): Boolean {
        return runCatching {
            val obj = JSONObject(json)
            obj.has("layers") &&
                obj.optJSONArray("layers") != null &&
                LottieCompositionFactory.fromJsonStringSync(
                    json,
                    null
                ).value != null
        }.getOrDefault(false)
    }

    fun hasRenderableLayers(json: String): Boolean {
        return runCatching {
            val obj = JSONObject(json)
            obj.optJSONArray("layers")?.length()?.let { it > 0 } == true
        }.getOrDefault(false)
    }

    fun preview(title: String, book: Book? = null): String {
        val parts = split(title, book)
        return appCtx.getString(
            R.string.advanced_title_preview_template,
            parts.s1.ifBlank { appCtx.getString(R.string.empty) },
            parts.s2.ifBlank { appCtx.getString(R.string.empty) }
        )
    }

    private fun splitByDelimiter(title: String, delimiter: String): Parts {
        val mark = delimiter.ifEmpty { " " }
        val index = if (mark.isBlank()) {
            title.indexOfFirst { it.isWhitespace() || it == '　' }
        } else {
            title.indexOf(mark)
        }
        if (index < 0) return splitByRegex(title, DEFAULT_REGEX)
        val end = if (mark.isBlank()) {
            var next = index
            while (next < title.length && (title[next].isWhitespace() || title[next] == '　')) next++
            next
        } else {
            index + mark.length
        }
        val s1 = title.substring(0, index).trim()
        val s2 = title.substring(end.coerceAtMost(title.length)).trim()
        return if (s1.isBlank() || s2.isBlank()) {
            Parts(title, "", title)
        } else {
            Parts(title, s1, s2)
        }
    }

    private fun splitByRegex(title: String, regex: String): Parts {
        val pattern = regex.ifBlank { DEFAULT_REGEX }
        val match = runCatching { Regex(pattern).find(title) }.getOrNull()
        if (match != null) {
            val groups = match.groups
            val namedGroups = groups as? MatchNamedGroupCollection
            val namedS1 = runCatching { namedGroups?.get("s1")?.value }.getOrNull()
            val namedS2 = runCatching { namedGroups?.get("s2")?.value }.getOrNull()
            val s1 = (namedS1 ?: groups.getOrNull(1)?.value).orEmpty().trim()
            val s2 = (namedS2 ?: groups.getOrNull(2)?.value).orEmpty().trim()
            if (s1.isNotBlank() && s2.isNotBlank()) return Parts(title, s1, s2)
        }
        return Parts(title, "", title)
    }

    private fun MatchGroupCollection.getOrNull(index: Int): MatchGroup? {
        return if (index in 0 until size) get(index) else null
    }

    private fun replaceVariables(
        source: String,
        book: Book,
        title: String
    ): String {
        val parts = split(title, book)
        return replaceTemplateVariables(source, variables(book, parts))
    }

    internal fun replaceTemplateVariables(
        source: String,
        variables: Map<String, String>
    ): String {
        return variables.entries.fold(source) { value, entry ->
            val replacement = GSON.toJson(entry.value).let { encoded ->
                if (encoded.length >= 2 && encoded.first() == '"' && encoded.last() == '"') {
                    encoded.substring(1, encoded.lastIndex)
                } else {
                    entry.value
                }
            }
            value
                .replace("\${${entry.key}}", replacement)
                .replace("{{${entry.key}}}", replacement)
        }
    }

    private fun hideLottieChapterTitle(source: String): String = runCatching {
        val root = JSONObject(source).apply {
            put("w", TEMPLATE_WIDTH)
            put("h", TEMPLATE_HEIGHT)
        }
        root.optJSONArray("layers")?.let { layers ->
            for (index in 0 until layers.length()) {
                val layer = layers.optJSONObject(index) ?: continue
                when (layer.optString("nm")) {
                    "chapter_number" -> {
                        layer.setLottiePosition(CHAPTER_NUMBER_X, CHAPTER_NUMBER_Y)
                        layer.optJSONObject("t")?.optJSONObject("d")
                            ?.optJSONArray("k")?.let { keyframes ->
                                for (frameIndex in 0 until keyframes.length()) {
                                    keyframes.optJSONObject(frameIndex)
                                        ?.optJSONObject("s")?.apply {
                                            put("s", CHAPTER_NUMBER_SIZE)
                                            put("tr", CHAPTER_NUMBER_TRACKING)
                                            put("fc", ReadDrawerStyle.indicatorColor(appCtx).toLottieColor())
                                        }
                                }
                            }
                    }
                    "chapter_title" -> {
                        layer.setLottiePosition(TITLE_X, TITLE_Y)
                        layer.optJSONObject("ks")?.put("o", JSONObject().apply {
                            put("a", 0)
                            put("k", 0)
                        })
                    }
                    "ornament", "star_ornament", "page_ornament" ->
                        layer.setLottiePosition(ORNAMENT_X, ORNAMENT_Y)
                }
            }
        }
        root.toString()
    }.getOrDefault(source)

    private fun JSONObject.setLottiePosition(x: Float, y: Float) {
        optJSONObject("ks")?.put("p", JSONObject().apply {
            put("a", 0)
            put("k", JSONArray().apply {
                put(x)
                put(y)
                put(0)
            })
        })
    }

    internal fun applyCompatibleTextStyle(
        source: String,
        color: Int?,
        fontWeight: Int,
        fontSizeScale: Int = 100,
        fontPath: String = "",
    ): String {
        if (color == null && fontWeight == 400 && fontSizeScale == 100 && fontPath.isBlank()) return source
        return runCatching {
            val root = JSONObject(source)
            val layers = root.optJSONArray("layers") ?: return source
            val fontIdentity = fontIdentity(fontPath)
            val weightedFont = weightedFontFamily(fontIdentity, fontWeight)
            var hasCompatibleLayer = false
            for (index in 0 until layers.length()) {
                val layer = layers.optJSONObject(index) ?: continue
                if (layer.optString("nm") !in COMPATIBLE_TEXT_LAYERS) continue
                hasCompatibleLayer = true
                val keyframes = layer.optJSONObject("t")
                    ?.optJSONObject("d")
                    ?.optJSONArray("k")
                    ?: continue
                for (frameIndex in 0 until keyframes.length()) {
                    keyframes.optJSONObject(frameIndex)?.optJSONObject("s")?.apply {
                        // 字号：按缩放百分比改写模板字号，随 JSON 变化触发动画重载
                        if (fontSizeScale != 100) {
                            val size = optDouble("s", 0.0)
                            if (size > 0) put("s", (size * fontSizeScale / 100.0).toInt())
                        }
                        // 字重：把权重数值编码进字体名，保证 JSON 随字重变化触发动画重载，
                        // 避免 Lottie FontAssetManager 缓存旧 Typeface 导致调整不生效
                        weightedFont?.let { put("f", it) }
                        color?.let { put("fc", it.toLottieColor()) }
                    }
                }
            }
            if (hasCompatibleLayer && weightedFont != null) {
                val fonts = root.optJSONObject("fonts") ?: JSONObject().also { root.put("fonts", it) }
                val list = fonts.optJSONArray("list") ?: JSONArray().also { fonts.put("list", it) }
                list.put(JSONObject().apply {
                    put("fName", weightedFont)
                    put("fFamily", weightedFont)
                    put("fStyle", "Regular")
                    put("ascent", 75)
                })
            }
            root.toString()
        }.getOrDefault(source)
    }

    /** 把字重数值编码进字体名，使 JSON 内容随字重变化（用于触发动画重载与字体缓存刷新） */
    fun weightedFontFamily(weight: Int): String = weightedFontFamily("default", weight)

    fun weightedFontFamily(fontIdentity: String, weight: Int): String =
        "${WEIGHTED_FONT_FAMILY}_${fontIdentity}_$weight"

    private fun fontIdentity(fontPath: String): String {
        val value = fontPath.ifBlank { "default" }
        return value.hashCode().toUInt().toString(16)
    }

    private fun Int.toLottieColor() = JSONArray().apply {
        put(((this@toLottieColor ushr 16) and 0xff) / 255.0)
        put(((this@toLottieColor ushr 8) and 0xff) / 255.0)
        put((this@toLottieColor and 0xff) / 255.0)
    }

    private fun variables(book: Book, parts: Parts): Map<String, String> {
        return mapOf(
            "title" to parts.title,
            "s1" to parts.s1,
            "s2" to parts.s2,
            "bookName" to book.name,
            "author" to book.author
        )
    }

    const val DEFAULT_REGEX = "^\\s*(第\\S+[章节回卷部篇集])\\s+(.+?)\\s*$"
    private val COMPATIBLE_TEXT_LAYERS = setOf("chapter_number", "chapter_title")
    const val WEIGHTED_FONT_FAMILY = "legado_advanced_title_weighted"

}
