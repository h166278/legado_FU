package io.legado.app.ui.book.character

import com.google.gson.annotations.SerializedName

data class ChapterStoryboard(
    @SerializedName("chapterTitle")
    val chapterTitle: String,
    @SerializedName("scenes")
    val scenes: List<StoryboardScene>
) {
    val segmentCount: Int get() = scenes.sumOf { it.segments.size }
    val dialogueCount: Int get() = scenes.sumOf { scene ->
        scene.segments.count { it.type == StoryboardSegmentType.DIALOGUE }
    }
    val thoughtCount: Int get() = scenes.sumOf { scene ->
        scene.segments.count { it.type == StoryboardSegmentType.THOUGHT }
    }
}

data class StoryboardScene(
    @SerializedName("index")
    val index: Int,
    @SerializedName("title")
    val title: String,
    @SerializedName("summary")
    val summary: String,
    @SerializedName("characters")
    val characters: List<String>,
    @SerializedName("segments")
    val segments: List<StoryboardSegment>,
    @SerializedName("context_text")
    val contextText: String = ""
) {
    val narrationCount: Int get() = segments.count { it.type == StoryboardSegmentType.NARRATION }
    val dialogueCount: Int get() = segments.count { it.type == StoryboardSegmentType.DIALOGUE }
    val thoughtCount: Int get() = segments.count { it.type == StoryboardSegmentType.THOUGHT }
}

data class StoryboardSegment(
    @SerializedName("type")
    val type: StoryboardSegmentType,
    @SerializedName("paragraphIndex")
    val paragraphIndex: Int,
    @SerializedName("text")
    val text: String,
    @SerializedName("speakerName")
    val speakerName: String?,
    @SerializedName("evidence")
    val evidence: String,
    @SerializedName("speakerId")
    val speakerId: Long? = null,
    @SerializedName("speakerGender")
    val speakerGender: String = SpeakerGender.UNKNOWN,
    @SerializedName("start")
    val start: Int = 0,
    @SerializedName("end")
    val end: Int = start + text.length,
    @SerializedName("performance_context")
    val performanceContext: List<String> = emptyList(),
    @SerializedName("performance_instruction")
    val performanceInstruction: String = ""
) {
    object SpeakerGender {
        const val MALE = "male"
        const val FEMALE = "female"
        const val UNKNOWN = "unknown"
    }
}

enum class StoryboardSegmentType {
    @SerializedName("NARRATION")
    NARRATION,
    @SerializedName("DIALOGUE")
    DIALOGUE,
    @SerializedName("THOUGHT")
    THOUGHT
}
