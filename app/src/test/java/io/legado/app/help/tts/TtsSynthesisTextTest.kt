package io.legado.app.help.tts

import io.legado.app.ui.book.character.StoryboardSegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsSynthesisTextTest {

    @Test
    fun `dialogue removes one balanced outer quote pair`() {
        assertEquals(
            "是我，陈升。",
            normalizeStoryboardSynthesisText("“是我，陈升。”", StoryboardSegmentType.DIALOGUE)
        )
        assertEquals(
            "别急。",
            normalizeStoryboardSynthesisText("「别急。」", StoryboardSegmentType.DIALOGUE)
        )
    }

    @Test
    fun `narration and unmatched quotes stay unchanged`() {
        assertEquals(
            "“是我，陈升。”",
            normalizeStoryboardSynthesisText("“是我，陈升。”", StoryboardSegmentType.NARRATION)
        )
        assertEquals(
            "“是我，陈升。",
            normalizeStoryboardSynthesisText("“是我，陈升。", StoryboardSegmentType.DIALOGUE)
        )
    }

    @Test
    fun `punctuation only storyboard text is silent`() {
        assertTrue(isReadAloudSynthesisTextSilent("　　…………………………"))
        assertTrue(isReadAloudSynthesisTextSilent("“……”"))
        assertTrue(isReadAloudSynthesisTextSilent("？！——"))
        assertFalse(isReadAloudSynthesisTextSilent("被举报了，提交了复审。"))
        assertFalse(isReadAloudSynthesisTextSilent("3000"))
    }
}
