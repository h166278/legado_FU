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
    if (contextTexts.isEmpty() && instruction.isEmpty()) return null
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
        performanceInstruction = instruction
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
    if (filteredScene == null && filteredInstruction.isEmpty()) return null
    return copy(
        scene = filteredScene,
        performanceInstruction = filteredInstruction
    )
}
