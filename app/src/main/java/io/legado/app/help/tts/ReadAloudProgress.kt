package io.legado.app.help.tts

data class ReadAloudBufferProgress(
    val chapterIndex: Int,
    val chapterPosition: Int
)

internal data class ReadAloudPreparedItemRange(
    val paragraphIndex: Int,
    val start: Int,
    val end: Int
)

internal fun preparedReadAloudChapterPosition(
    paragraphStarts: List<Int>,
    paragraphIndex: Int,
    preparedEnd: Int
): Int? = paragraphStarts.getOrNull(paragraphIndex)
    ?.plus(preparedEnd.coerceAtLeast(0))

internal fun preparedReadAloudItemIndex(
    ranges: List<ReadAloudPreparedItemRange>,
    targetParagraphIndex: Int,
    targetParagraphOffset: Int,
    mediaItemCount: Int
): Int? {
    val firstRange = ranges.firstOrNull() ?: return null
    if (targetParagraphIndex < firstRange.paragraphIndex ||
        (targetParagraphIndex == firstRange.paragraphIndex &&
                targetParagraphOffset < firstRange.start)
    ) {
        return null
    }
    val index = ranges.indexOfFirst { range ->
        range.paragraphIndex > targetParagraphIndex ||
                (range.paragraphIndex == targetParagraphIndex &&
                        range.end > targetParagraphOffset)
    }
    return index.takeIf { it in 0 until mediaItemCount }
}
