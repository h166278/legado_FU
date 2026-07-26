package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "bookCharacterTtsBindings",
    primaryKeys = ["workKey", "targetType", "targetId", "engineId"],
    indices = [
        Index(value = ["workKey"]),
        Index(value = ["workKey", "targetType", "targetId", "engineId"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = BookCharacterProfile::class,
            parentColumns = ["workKey"],
            childColumns = ["workKey"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BookCharacterTtsBinding(
    @ColumnInfo(defaultValue = "")
    @SerializedName("workKey")
    var workKey: String = "",
    @ColumnInfo(defaultValue = "character")
    @SerializedName("targetType")
    var targetType: String = TargetType.CHARACTER,
    @ColumnInfo(defaultValue = "0")
    @SerializedName("targetId")
    var targetId: Long = 0L,
    @ColumnInfo(defaultValue = "")
    @SerializedName("engineId")
    var engineId: String = "",
    @SerializedName("voiceId")
    var voiceId: String? = null,
    @ColumnInfo(defaultValue = "manual")
    @SerializedName("bindingMode")
    var bindingMode: String = BindingMode.MANUAL,
    @ColumnInfo(defaultValue = "{}")
    @SerializedName("emotionStyleMapJson")
    var emotionStyleMapJson: String = "{}",
    @ColumnInfo(defaultValue = "1.0")
    @SerializedName("autoConfidence")
    var autoConfidence: Float = 1f,
    @ColumnInfo(defaultValue = "")
    @SerializedName("autoEvidenceSignature")
    var autoEvidenceSignature: String = "",
    @ColumnInfo(defaultValue = "0")
    @SerializedName("createdAt")
    var createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    @SerializedName("updatedAt")
    var updatedAt: Long = System.currentTimeMillis()
) {
    object TargetType {
        const val NARRATOR = "narrator"
        const val CHARACTER = "character"
        const val CAST_ROLE = "cast_role"
        const val DIALOGUE_MALE = "dialogue_male"
        const val DIALOGUE_FEMALE = "dialogue_female"
    }

    object BindingMode {
        const val AUTO = "auto"
        const val MANUAL = "manual"
        const val INHERIT = "inherit"
    }

    companion object {
        fun narrator(workKey: String): BookCharacterTtsBinding {
            return BookCharacterTtsBinding(
                workKey = workKey,
                targetType = TargetType.NARRATOR,
                targetId = 0L
            )
        }

        fun character(workKey: String, characterId: Long): BookCharacterTtsBinding {
            return BookCharacterTtsBinding(
                workKey = workKey,
                targetType = TargetType.CHARACTER,
                targetId = characterId
            )
        }

        fun castRole(workKey: String, castRoleId: Long): BookCharacterTtsBinding {
            return BookCharacterTtsBinding(
                workKey = workKey,
                targetType = TargetType.CAST_ROLE,
                targetId = castRoleId
            )
        }

        fun dialogueMale(workKey: String): BookCharacterTtsBinding {
            return BookCharacterTtsBinding(
                workKey = workKey,
                targetType = TargetType.DIALOGUE_MALE,
                targetId = 0L
            )
        }

        fun dialogueFemale(workKey: String): BookCharacterTtsBinding {
            return BookCharacterTtsBinding(
                workKey = workKey,
                targetType = TargetType.DIALOGUE_FEMALE,
                targetId = 0L
            )
        }
    }
}
