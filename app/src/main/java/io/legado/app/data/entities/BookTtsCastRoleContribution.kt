package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "bookTtsCastRoleContributions",
    primaryKeys = ["workKey", "chapterIndex", "roleId"],
    indices = [
        Index(value = ["workKey", "chapterIndex"]),
        Index(value = ["roleId"]),
        Index(value = ["cacheKey"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = BookCharacterProfile::class,
            parentColumns = ["workKey"],
            childColumns = ["workKey"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = BookTtsCastRole::class,
            parentColumns = ["id"],
            childColumns = ["roleId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BookTtsCastRoleContribution(
    @ColumnInfo(defaultValue = "")
    @SerializedName("workKey")
    var workKey: String = "",
    @ColumnInfo(defaultValue = "0")
    @SerializedName("chapterIndex")
    var chapterIndex: Int = 0,
    @ColumnInfo(defaultValue = "0")
    @SerializedName("roleId")
    var roleId: Long = 0L,
    @ColumnInfo(defaultValue = "")
    @SerializedName("cacheKey")
    var cacheKey: String = "",
    @ColumnInfo(defaultValue = "0")
    @SerializedName("cacheRevision")
    var cacheRevision: Long = 0L,
    @ColumnInfo(defaultValue = "[]")
    @SerializedName("namesJson")
    var namesJson: String = "[]",
    @ColumnInfo(defaultValue = "unknown")
    @SerializedName("gender")
    var gender: String = BookCharacter.Gender.UNKNOWN,
    @ColumnInfo(defaultValue = "pending")
    @SerializedName("identityState")
    var identityState: String = BookTtsCastRole.IdentityState.PENDING,
    @ColumnInfo(defaultValue = "unknown")
    @SerializedName("nameType")
    var nameType: String = BookTtsCastRole.NameType.UNKNOWN,
    @ColumnInfo(defaultValue = "unknown")
    @SerializedName("identityEvidence")
    var identityEvidence: String = BookTtsCastRole.Evidence.UNKNOWN,
    @ColumnInfo(defaultValue = "unknown")
    @SerializedName("genderEvidence")
    var genderEvidence: String = BookTtsCastRole.Evidence.UNKNOWN,
    @ColumnInfo(defaultValue = "0")
    @SerializedName("occurrenceCount")
    var occurrenceCount: Int = 0,
    @ColumnInfo(defaultValue = "[]")
    @SerializedName("representativeTextsJson")
    var representativeTextsJson: String = "[]",
    @ColumnInfo(defaultValue = "[]")
    @SerializedName("identityEvidenceJson")
    var identityEvidenceJson: String = "[]",
    @ColumnInfo(defaultValue = "0")
    @SerializedName("createdAt")
    var createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    @SerializedName("updatedAt")
    var updatedAt: Long = System.currentTimeMillis()
)
