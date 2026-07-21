package io.legado.app.help.tts

import io.legado.app.ui.book.character.StoryboardScene
import io.legado.app.ui.book.character.StoryboardSegment
import io.legado.app.ui.book.character.StoryboardSegmentType

fun StoryboardSegment.toTtsSynthesisContext(
    scene: StoryboardScene?,
    storyboardMode: Int
): TtsSynthesisContext? {
    if (storyboardMode != 1 || type == StoryboardSegmentType.NARRATION) return null
    val contextTexts = performanceContext
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(3)
    if (contextTexts.isEmpty()) return null
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
        scene = TtsSceneContext(
            title = scene?.title,
            text = contextTexts.joinToString("\n"),
            contextTexts = contextTexts
        )
    )
}
