package io.legado.app.help.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadAloudProgressTest {

    @Test
    fun preparedPosition_usesParagraphStartAndSegmentEnd() {
        assertEquals(
            145,
            preparedReadAloudChapterPosition(
                paragraphStarts = listOf(0, 100, 220),
                paragraphIndex = 1,
                preparedEnd = 45
            )
        )
    }

    @Test
    fun preparedPosition_ignoresUnknownParagraph() {
        assertNull(
            preparedReadAloudChapterPosition(
                paragraphStarts = listOf(0, 100),
                paragraphIndex = 3,
                preparedEnd = 20
            )
        )
    }

    @Test
    fun preparedItemIndex_selectsBufferedItemWithoutRebuildingPlaylist() {
        val ranges = listOf(
            ReadAloudPreparedItemRange(paragraphIndex = 2, start = 0, end = 12),
            ReadAloudPreparedItemRange(paragraphIndex = 2, start = 12, end = 30),
            ReadAloudPreparedItemRange(paragraphIndex = 3, start = 0, end = 18)
        )

        assertEquals(
            1,
            preparedReadAloudItemIndex(
                ranges = ranges,
                targetParagraphIndex = 2,
                targetParagraphOffset = 12,
                mediaItemCount = 3
            )
        )
    }

    @Test
    fun preparedItemIndex_rejectsItemNotYetInPlayerQueue() {
        val ranges = listOf(
            ReadAloudPreparedItemRange(paragraphIndex = 0, start = 0, end = 10),
            ReadAloudPreparedItemRange(paragraphIndex = 1, start = 0, end = 20)
        )

        assertNull(
            preparedReadAloudItemIndex(
                ranges = ranges,
                targetParagraphIndex = 1,
                targetParagraphOffset = 0,
                mediaItemCount = 1
            )
        )
    }

    @Test
    fun preparedItemIndex_rejectsTargetBeforePreparedQueue() {
        val ranges = listOf(
            ReadAloudPreparedItemRange(paragraphIndex = 55, start = 0, end = 30),
            ReadAloudPreparedItemRange(paragraphIndex = 56, start = 0, end = 22)
        )

        assertNull(
            preparedReadAloudItemIndex(
                ranges = ranges,
                targetParagraphIndex = 21,
                targetParagraphOffset = 0,
                mediaItemCount = 2
            )
        )
    }

    @Test
    fun preparedItemIndex_rejectsTargetBeforeFirstSegmentInSameParagraph() {
        val ranges = listOf(
            ReadAloudPreparedItemRange(paragraphIndex = 8, start = 15, end = 30),
            ReadAloudPreparedItemRange(paragraphIndex = 8, start = 30, end = 45)
        )

        assertNull(
            preparedReadAloudItemIndex(
                ranges = ranges,
                targetParagraphIndex = 8,
                targetParagraphOffset = 5,
                mediaItemCount = 2
            )
        )
    }
}
