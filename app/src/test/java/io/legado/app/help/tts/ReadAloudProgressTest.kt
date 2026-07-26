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
            ReadAloudPreparedItemRange(paragraphIndex = 2, end = 12),
            ReadAloudPreparedItemRange(paragraphIndex = 2, end = 30),
            ReadAloudPreparedItemRange(paragraphIndex = 3, end = 18)
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
            ReadAloudPreparedItemRange(paragraphIndex = 0, end = 10),
            ReadAloudPreparedItemRange(paragraphIndex = 1, end = 20)
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
}
