package io.legado.app.help.tts

import io.legado.app.ui.book.character.StoryboardScene
import io.legado.app.ui.book.character.StoryboardSegment
import io.legado.app.ui.book.character.StoryboardSegmentType

fun StoryboardSegment.toTtsSynthesisContext(
    scene: StoryboardScene?
): TtsSynthesisContext? {
    if (type == StoryboardSegmentType.NARRATION) return null
    val contextTexts = performanceContext
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(3)
    val instruction = performanceInstruction.trim()
    val expressive = TtsExpressiveContext(
        styleConcepts = styleConcepts.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(4),
        emotion = emotion?.trim()?.takeIf { it.isNotEmpty() },
        intensity = emotionIntensity?.coerceIn(0f, 1f),
        confidence = expressiveConfidence?.coerceIn(0f, 1f)
    ).takeUnless { it.styleConcepts.isEmpty() && it.emotion == null && it.intensity == null }
    if (contextTexts.isEmpty() && instruction.isEmpty() && expressive == null) return null
    return TtsSynthesisContext(
        mode = TtsSynthesisContext.Mode.PERFORMANCE,
        role = TtsRoleContext(
            id = speakerId,
            name = speakerName ?: when (speakerGender) {
                StoryboardSegment.SpeakerGender.MALE -> "对白男"
                StoryboardSegment.SpeakerGender.FEMALE -> "对白女"
                else -> null
            },
            gender = speakerGender
        ),
        scene = contextTexts.takeIf { it.isNotEmpty() }?.let {
            TtsSceneContext(
                title = scene?.title,
                text = it.joinToString("\n"),
                contextTexts = it
            )
        },
        performanceInstruction = instruction,
        expressive = expressive
    )
}

fun TtsSynthesisContext.forEngineCapabilities(engine: TtsEngineSetting?): TtsSynthesisContext? {
    val supportsInstruction = engine?.supportsCapability(
        TtsEngineCapability.PERFORMANCE_INSTRUCTION
    ) == true
    val supportsScene = supportsInstruction || engine?.supportsCapability(
        TtsEngineCapability.SCENE_CONTEXT
    ) == true
    val filteredScene = scene.takeIf { supportsScene }
    val filteredInstruction = performanceInstruction.takeIf { supportsInstruction }.orEmpty()
    val supportsStyle = engine?.supportsCapability(TtsEngineCapability.STYLE_TAGS) == true
    val supportsEmotion = engine?.supportsCapability(TtsEngineCapability.EMOTION) == true
    val supportsIntensity = engine?.supportsCapability(TtsEngineCapability.EMOTION_INTENSITY) == true
    val filteredExpressive = expressive?.copy(
        styleConcepts = expressive.styleConcepts.takeIf { supportsStyle }.orEmpty(),
        emotion = expressive.emotion.takeIf { supportsEmotion },
        intensity = expressive.intensity.takeIf { supportsIntensity }
    )?.takeUnless { it.styleConcepts.isEmpty() && it.emotion == null && it.intensity == null }
    if (filteredScene == null && filteredInstruction.isEmpty() && filteredExpressive == null) return null
    return copy(
        scene = filteredScene,
        performanceInstruction = filteredInstruction,
        expressive = filteredExpressive
    )
}
