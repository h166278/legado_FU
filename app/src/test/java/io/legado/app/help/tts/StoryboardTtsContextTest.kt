package io.legado.app.help.tts

import io.legado.app.ui.book.character.StoryboardScene
import io.legado.app.ui.book.character.StoryboardSegment
import io.legado.app.ui.book.character.StoryboardSegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoryboardTtsContextTest {

    @Test
    fun expressiveContext_isFilteredByEngineCapabilities() {
        val context = TtsSynthesisContext(
            expressive = TtsExpressiveContext(
                styleConcepts = listOf("温柔", "低沉"),
                emotion = "sad",
                intensity = 0.7f,
                confidence = 0.9f
            )
        )

        val styleOnly = context.forEngineCapabilities(
            engine(setOf(TtsEngineCapability.STYLE_TAGS))
        )
        assertEquals(listOf("温柔", "低沉"), styleOnly?.expressive?.styleConcepts)
        assertEquals(null, styleOnly?.expressive?.emotion)
        assertEquals(null, styleOnly?.expressive?.intensity)

        val emotion = context.forEngineCapabilities(
            engine(setOf(TtsEngineCapability.EMOTION, TtsEngineCapability.EMOTION_INTENSITY))
        )
        assertEquals("sad", emotion?.expressive?.emotion)
        assertEquals(0.7f, emotion?.expressive?.intensity)
    }

    private val scene = StoryboardScene(
        index = 1,
        title = "宿舍楼下",
        summary = "安秋月丢失生活费",
        characters = listOf("陈升", "安秋月"),
        segments = emptyList(),
        contextText = "安秋月低着头抽泣，陈升走过去询问。"
    )

    @Test
    fun segmentWithoutEnhancementDoesNotExposeSynthesisContext() {
        assertNull(
            dialogue().copy(
                performanceContext = emptyList(),
                performanceInstruction = ""
            ).toTtsSynthesisContext(scene)
        )
    }

    @Test
    fun narrationDoesNotExposeSceneContext() {
        val narration = dialogue().copy(type = StoryboardSegmentType.NARRATION)

        assertNull(narration.toTtsSynthesisContext(scene))
    }

    @Test
    fun performanceModeExposesStableRoleAndApprovedContextItems() {
        val context = dialogue().toTtsSynthesisContext(scene)!!

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
        assertEquals("刚哭过，开口迟疑，后半句逐渐变轻", context.performanceInstruction)
    }

    @Test
    fun engineCapabilitiesFilterSceneAndActorLayers() {
        val context = dialogue().toTtsSynthesisContext(scene)!!
        val sceneOnly = engine(setOf(TtsEngineCapability.SCENE_CONTEXT))
        val actor = engine(setOf(TtsEngineCapability.PERFORMANCE_INSTRUCTION))
        val unsupported = engine(emptySet())

        assertEquals(context.scene, context.forEngineCapabilities(sceneOnly)?.scene)
        assertEquals("", context.forEngineCapabilities(sceneOnly)?.performanceInstruction)
        assertEquals(context.scene, context.forEngineCapabilities(actor)?.scene)
        assertEquals(
            "刚哭过，开口迟疑，后半句逐渐变轻",
            context.forEngineCapabilities(actor)?.performanceInstruction
        )
        assertNull(context.forEngineCapabilities(unsupported))
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
        ),
        performanceInstruction = "刚哭过，开口迟疑，后半句逐渐变轻"
    )

    private fun engine(capabilities: Set<String>) = TtsEngineSetting(
        id = "test",
        name = "测试",
        type = TtsEngineType.SCRIPT,
        capabilities = capabilities
    )
}
