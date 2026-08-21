package io.legado.app.help.book

import io.legado.app.constant.AppPattern
import io.legado.app.model.analyzeRule.AnalyzeUrl.Companion.paramPattern
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import java.util.regex.Matcher
import java.util.regex.Pattern

/** TXT／EPUB 导出时对正文图片做语义明确的过滤。 */
object ExportContentImageProcessor {

    private val anyImageTag = Regex(
        pattern = """<img\b(?:[^>\"']|\"[^\"]*\"|'[^']*')*>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val formattedImagePattern = Pattern.compile(
        AppPattern.imgPattern.pattern(),
        Pattern.CASE_INSENSITIVE,
    )

    fun process(
        content: String,
        plainText: Boolean,
        filterInteractiveImages: Boolean,
    ): String {
        if (plainText) {
            return anyImageTag.replace(content, "")
        }
        if (!filterInteractiveImages) {
            return content
        }
        return removeInteractiveImages(content)
    }

    private fun removeInteractiveImages(content: String): String {
        val matcher = formattedImagePattern.matcher(content)
        val result = StringBuffer(content.length)
        while (matcher.find()) {
            val src = matcher.group(1)
            val replacement = if (hasClickInteraction(src)) "" else matcher.group()
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement))
        }
        matcher.appendTail(result)
        return result.toString()
    }

    private fun hasClickInteraction(src: String?): Boolean {
        if (src.isNullOrBlank()) return false
        val optionMatcher = paramPattern.matcher(src)
        if (!optionMatcher.find()) return false
        val optionJson = src.substring(optionMatcher.end())
        return GSON.fromJsonObject<Map<String, String>>(optionJson)
            .getOrNull()
            ?.get("click")
            ?.isNotBlank() == true
    }
}
