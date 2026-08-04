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

internal data class ReadAloudPreparedPlaybackTarget(
    val itemIndex: Int,
    val itemOffset: Int
)

internal fun canReusePreparedReadAloudPlaylist(
    forceRebuild: Boolean,
    playlistChapterIndex: Int,
    currentChapterIndex: Int,
    hasSpeakItems: Boolean
): Boolean = !forceRebuild &&
        playlistChapterIndex == currentChapterIndex &&
        hasSpeakItems

internal fun preparedReadAloudChapterPosition(
    paragraphStarts: List<Int>,
    paragraphIndex: Int,
    preparedEnd: Int
): Int? = paragraphStarts.getOrNull(paragraphIndex)
    ?.plus(preparedEnd.coerceAtLeast(0))

internal fun preparedReadAloudPlaybackTarget(
    ranges: List<ReadAloudPreparedItemRange>,
    targetParagraphIndex: Int,
    targetParagraphOffset: Int,
    mediaItemCount: Int
): ReadAloudPreparedPlaybackTarget? {
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
    if (index !in 0 until mediaItemCount) return null
    val range = ranges[index]
    val itemOffset = if (range.paragraphIndex == targetParagraphIndex) {
        (targetParagraphOffset - range.start).coerceIn(0, range.end - range.start)
    } else {
        0
    }
    return ReadAloudPreparedPlaybackTarget(index, itemOffset)
}

internal fun readAloudSeekPositionMs(
    durationMs: Long,
    itemLength: Int,
    itemOffset: Int
): Long {
    if (durationMs <= 0L || itemLength <= 0) return 0L
    val safeOffset = itemOffset.coerceIn(0, itemLength)
    return (durationMs.toDouble() * safeOffset / itemLength)
        .toLong()
        .coerceIn(0L, durationMs)
}
