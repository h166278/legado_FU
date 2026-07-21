package io.legado.app.help.tts

import io.legado.app.ui.book.character.StoryboardScene
import io.legado.app.ui.book.character.StoryboardSegment
import io.legado.app.ui.book.character.StoryboardSegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoryboardTtsContextTest {

    private val scene = StoryboardScene(
        index = 1,
        title = "宿舍楼下",
        summary = "安秋月丢失生活费",
        characters = listOf("陈升", "安秋月"),
        segments = emptyList(),
        contextText = "安秋月低着头抽泣，陈升走过去询问。"
    )

    @Test
    fun basicModeDoesNotExposeSceneContext() {
        assertNull(dialogue().toTtsSynthesisContext(scene, storyboardMode = 0))
    }

    @Test
    fun narrationDoesNotExposeSceneContext() {
        val narration = dialogue().copy(type = StoryboardSegmentType.NARRATION)

        assertNull(narration.toTtsSynthesisContext(scene, storyboardMode = 1))
    }

    @Test
    fun performanceModeExposesStableRoleAndApprovedContextItems() {
        val context = dialogue().toTtsSynthesisContext(scene, storyboardMode = 1)!!

        assertEquals(TtsSynthesisContext.Mode.PERFORMANCE, context.mode)
        assertEquals(7L, context.role?.id)
        assertEquals("安秋月", context.role?.name)
        assertEquals(StoryboardSegment.SpeakerGender.FEMALE, context.role?.gender)
        assertEquals("宿舍楼下", context.scene?.title)
        assertEquals("安秋月独自在陌生城市丢失生活费。\n陈升追问后，她忍不住哭起来。", context.scene?.text)
        assertEquals(
            listOf("安秋月独自在陌生城市丢失生活费。", "陈升追问后，她忍不住哭起来。"),
            context.scene?.contextTexts
        )
    }

    private fun dialogue() = StoryboardSegment(
        type = StoryboardSegmentType.DIALOGUE,
        paragraphIndex = 3,
        text = "我生活费丢了。",
        speakerName = "安秋月",
        evidence = "后文出现安秋月哭泣",
        speakerId = 7L,
        speakerGender = StoryboardSegment.SpeakerGender.FEMALE,
        performanceContext = listOf(
            "安秋月独自在陌生城市丢失生活费。",
            "陈升追问后，她忍不住哭起来。"
        )
    )
}
