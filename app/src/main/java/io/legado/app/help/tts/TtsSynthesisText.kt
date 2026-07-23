package io.legado.app.help.tts

import io.legado.app.constant.AppPattern
import io.legado.app.ui.book.character.StoryboardSegmentType

internal fun isReadAloudSynthesisTextSilent(text: String): Boolean {
    return text.isBlank() || text.matches(AppPattern.notReadAloudRegex)
}

/**
 * 生成语音时移除对白最外层的排版引号，正文显示仍保留原样。
 */
fun normalizeStoryboardSynthesisText(
    text: String,
    type: StoryboardSegmentType?
): String {
    val value = text.trim()
    if (type == null || type == StoryboardSegmentType.NARRATION || value.length < 2) {
        return value
    }
    val matchingEnd = when (value.first()) {
        '“' -> '”'
        '‘' -> '’'
        '「' -> '」'
        '『' -> '』'
        '"' -> '"'
        else -> null
    }
    return if (matchingEnd != null && value.last() == matchingEnd) {
        value.substring(1, value.lastIndex).trim()
    } else {
        value
    }
}
