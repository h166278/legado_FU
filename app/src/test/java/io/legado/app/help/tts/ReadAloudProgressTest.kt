package io.legado.app.help.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadAloudProgressTest {

    @Test
    fun preparedPlaylist_reusesMatchingChapterForNormalPlayback() {
        assertEquals(
            true,
            canReusePreparedReadAloudPlaylist(
                forceRebuild = false,
                playlistChapterIndex = 3,
                currentChapterIndex = 3,
                hasSpeakItems = true
            )
        )
    }

    @Test
    fun preparedPlaylist_rejectsReuseWhenVoiceSwitchForcesRebuild() {
        assertEquals(
            false,
            canReusePreparedReadAloudPlaylist(
                forceRebuild = true,
                playlistChapterIndex = 3,
                currentChapterIndex = 3,
                hasSpeakItems = true
            )
        )
    }

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
    fun preparedTarget_selectsBufferedItemWithoutRebuildingPlaylist() {
        val ranges = listOf(
            ReadAloudPreparedItemRange(paragraphIndex = 2, start = 0, end = 12),
            ReadAloudPreparedItemRange(paragraphIndex = 2, start = 12, end = 30),
            ReadAloudPreparedItemRange(paragraphIndex = 3, start = 0, end = 18)
        )

        assertEquals(
            ReadAloudPreparedPlaybackTarget(itemIndex = 1, itemOffset = 0),
            preparedReadAloudPlaybackTarget(
                ranges = ranges,
                targetParagraphIndex = 2,
                targetParagraphOffset = 12,
                mediaItemCount = 3
            )
        )
    }

    @Test
    fun preparedTarget_rejectsItemNotYetInPlayerQueue() {
        val ranges = listOf(
            ReadAloudPreparedItemRange(paragraphIndex = 0, start = 0, end = 10),
            ReadAloudPreparedItemRange(paragraphIndex = 1, start = 0, end = 20)
        )

        assertNull(
            preparedReadAloudPlaybackTarget(
                ranges = ranges,
                targetParagraphIndex = 1,
                targetParagraphOffset = 0,
                mediaItemCount = 1
            )
        )
    }

    @Test
    fun preparedTarget_keepsOffsetInsidePreparedItem() {
        val ranges = listOf(
            ReadAloudPreparedItemRange(paragraphIndex = 2, start = 0, end = 30),
            ReadAloudPreparedItemRange(paragraphIndex = 3, start = 0, end = 18)
        )

        assertEquals(
            ReadAloudPreparedPlaybackTarget(itemIndex = 0, itemOffset = 12),
            preparedReadAloudPlaybackTarget(
                ranges = ranges,
                targetParagraphIndex = 2,
                targetParagraphOffset = 12,
                mediaItemCount = 2
            )
        )
    }

    @Test
    fun preparedTarget_rejectsTargetBeforePreparedQueue() {
        val ranges = listOf(
            ReadAloudPreparedItemRange(paragraphIndex = 55, start = 0, end = 30),
            ReadAloudPreparedItemRange(paragraphIndex = 56, start = 0, end = 22)
        )

        assertNull(
            preparedReadAloudPlaybackTarget(
                ranges = ranges,
                targetParagraphIndex = 21,
                targetParagraphOffset = 0,
                mediaItemCount = 2
            )
        )
    }

    @Test
    fun preparedTarget_rejectsTargetBeforeFirstSegmentInSameParagraph() {
        val ranges = listOf(
            ReadAloudPreparedItemRange(paragraphIndex = 8, start = 15, end = 30),
            ReadAloudPreparedItemRange(paragraphIndex = 8, start = 30, end = 45)
        )

        assertNull(
            preparedReadAloudPlaybackTarget(
                ranges = ranges,
                targetParagraphIndex = 8,
                targetParagraphOffset = 5,
                mediaItemCount = 2
            )
        )
    }

    @Test
    fun preparedTarget_skipsGapToNextPreparedItem() {
        val ranges = listOf(
            ReadAloudPreparedItemRange(paragraphIndex = 2, start = 0, end = 10),
            ReadAloudPreparedItemRange(paragraphIndex = 2, start = 15, end = 30)
        )

        assertEquals(
            ReadAloudPreparedPlaybackTarget(itemIndex = 1, itemOffset = 0),
            preparedReadAloudPlaybackTarget(
                ranges = ranges,
                targetParagraphIndex = 2,
                targetParagraphOffset = 12,
                mediaItemCount = 2
            )
        )
    }

    @Test
    fun seekPosition_usesSameLinearCharacterRatioAsPlaybackProgress() {
        assertEquals(
            4_000L,
            readAloudSeekPositionMs(
                durationMs = 10_000L,
                itemLength = 30,
                itemOffset = 12
            )
        )
    }
}
