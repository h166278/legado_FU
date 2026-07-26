package io.legado.app.ui.book.character

import com.google.gson.annotations.SerializedName

data class ChapterStoryboard(
    @SerializedName("chapterTitle")
    val chapterTitle: String,
    @SerializedName("scenes")
    val scenes: List<StoryboardScene>,
    @SerializedName("identityLinks")
    val identityLinks: List<StoryboardIdentityLink> = emptyList(),
    @SerializedName("sourceCacheKey")
    val sourceCacheKey: String = "",
    @SerializedName("sourceCacheRevision")
    val sourceCacheRevision: Long = 0L,
    @SerializedName("sourceChapterIndex")
    val sourceChapterIndex: Int = -1
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
    val contextText: String = "",
    @SerializedName("voice_assignments")
    val voiceAssignments: List<StoryboardSceneVoiceAssignment> = emptyList()
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
    @SerializedName("castRoleId")
    val castRoleId: Long? = null,
    @SerializedName("speakerGender")
    val speakerGender: String = SpeakerGender.UNKNOWN,
    @SerializedName("identityType")
    val identityType: String = IdentityType.NONE,
    @SerializedName("nameType")
    val nameType: String = NameType.UNKNOWN,
    @SerializedName("identityEvidence")
    val identityEvidence: String = Evidence.UNKNOWN,
    @SerializedName("genderEvidence")
    val genderEvidence: String = Evidence.UNKNOWN,
    @SerializedName("mergeCastRoleIds")
    val mergeCastRoleIds: List<Long> = emptyList(),
    @SerializedName("start")
    val start: Int = 0,
    @SerializedName("end")
    val end: Int = start + text.length,
    @SerializedName("performance_context")
    val performanceContext: List<String> = emptyList(),
    @SerializedName("performance_instruction")
    val performanceInstruction: String = "",
    @SerializedName("style_concepts")
    val styleConcepts: List<String> = emptyList(),
    @SerializedName("emotion")
    val emotion: String? = null,
    @SerializedName("emotion_intensity")
    val emotionIntensity: Float? = null,
    @SerializedName("expressive_confidence")
    val expressiveConfidence: Float? = null
) {
    object SpeakerGender {
        const val MALE = "male"
        const val FEMALE = "female"
        const val UNKNOWN = "unknown"
    }

    object IdentityType {
        const val NONE = "none"
        const val FORMAL_CHARACTER = "formal_character"
        const val CAST_ROLE = "cast_role"
        const val STABLE_CANDIDATE = "stable_candidate"
        const val PENDING = "pending"
        const val GUEST = "guest"
    }

    object NameType {
        const val PROPER_NAME = "proper_name"
        const val ALIAS = "alias"
        const val UNIQUE_TITLE = "unique_title"
        const val GENERIC_LABEL = "generic_label"
        const val UNKNOWN = "unknown"
    }

    object Evidence {
        const val EXPLICIT = "explicit"
        const val CONTEXTUAL = "contextual"
        const val INFERRED = "inferred"
        const val UNKNOWN = "unknown"
    }
}

data class StoryboardSceneVoiceAssignment(
    @SerializedName("engineId")
    val engineId: String,
    @SerializedName("catalogSignature")
    val catalogSignature: String = "",
    @SerializedName("targetType")
    val targetType: String,
    @SerializedName("targetId")
    val targetId: Long,
    @SerializedName("voiceId")
    val voiceId: String? = null,
    @SerializedName("decision")
    val decision: String,
    @SerializedName("confidence")
    val confidence: Float = 0f,
    @SerializedName("reason")
    val reason: String? = null
)

data class StoryboardIdentityLink(
    @SerializedName("aliasName")
    val aliasName: String,
    @SerializedName("characterId")
    val characterId: Long? = null,
    @SerializedName("castRoleId")
    val castRoleId: Long? = null,
    @SerializedName("evidence")
    val evidence: String = ""
)

enum class StoryboardSegmentType {
    @SerializedName("NARRATION")
    NARRATION,
    @SerializedName("DIALOGUE")
    DIALOGUE,
    @SerializedName("THOUGHT")
    THOUGHT
}
