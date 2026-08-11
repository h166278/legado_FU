package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "bookTtsCastRoles",
    indices = [
        Index(value = ["workKey"]),
        Index(value = ["workKey", "name"], unique = true),
        Index(value = ["linkedCharacterId"])
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
data class BookTtsCastRole(
    @PrimaryKey(autoGenerate = true)
    @SerializedName("id")
    val id: Long = 0L,
    @ColumnInfo(defaultValue = "")
    @SerializedName("workKey")
    var workKey: String = "",
    @ColumnInfo(defaultValue = "")
    @SerializedName("name")
    var name: String = "",
    @ColumnInfo(defaultValue = "unknown")
    @SerializedName("gender")
    var gender: String = BookCharacter.Gender.UNKNOWN,
    @ColumnInfo(defaultValue = "[]")
    @SerializedName("aliasesJson")
    var aliasesJson: String = "[]",
    @ColumnInfo(defaultValue = "0")
    @SerializedName("firstChapterIndex")
    var firstChapterIndex: Int = 0,
    @ColumnInfo(defaultValue = "0")
    @SerializedName("lastChapterIndex")
    var lastChapterIndex: Int = 0,
    @ColumnInfo(defaultValue = "0")
    @SerializedName("occurrenceCount")
    var occurrenceCount: Int = 0,
    @ColumnInfo(defaultValue = "[]")
    @SerializedName("representativeTextsJson")
    var representativeTextsJson: String = "[]",
    @SerializedName("linkedCharacterId")
    var linkedCharacterId: Long? = null,
    @ColumnInfo(defaultValue = "ai_storyboard")
    @SerializedName("source")
    var source: String = Source.AI_STORYBOARD,
    @ColumnInfo(defaultValue = "stable")
    @SerializedName("identityState")
    var identityState: String = IdentityState.STABLE,
    @ColumnInfo(defaultValue = "unknown")
    @SerializedName("nameType")
    var nameType: String = NameType.UNKNOWN,
    @ColumnInfo(defaultValue = "unknown")
    @SerializedName("identityEvidence")
    var identityEvidence: String = Evidence.UNKNOWN,
    @ColumnInfo(defaultValue = "unknown")
    @SerializedName("genderEvidence")
    var genderEvidence: String = Evidence.UNKNOWN,
    @ColumnInfo(defaultValue = "{}")
    @SerializedName("chapterOccurrencesJson")
    var chapterOccurrencesJson: String = "{}",
    @ColumnInfo(defaultValue = "[]")
    @SerializedName("identityEvidenceJson")
    var identityEvidenceJson: String = "[]",
    @ColumnInfo(defaultValue = "0")
    @SerializedName("ignored")
    var ignored: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    @SerializedName("createdAt")
    var createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    @SerializedName("updatedAt")
    var updatedAt: Long = System.currentTimeMillis()
) {
    object Source {
        const val AI_STORYBOARD = "ai_storyboard"
    }

    object IdentityState {
        const val STABLE = "stable"
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

    fun isVisibleTemporaryRole(): Boolean = identityState == IdentityState.STABLE

    fun isRoutableRole(): Boolean = !ignored && identityState != IdentityState.GUEST
}
