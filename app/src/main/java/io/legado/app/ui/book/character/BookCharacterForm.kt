package io.legado.app.ui.book.character

import android.content.Context
import android.widget.ArrayAdapter
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookTtsCastRole
import io.legado.app.databinding.ViewBookCharacterFormBinding
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject

data class BookCharacterFormValue(
    val name: String,
    val aliases: List<String>,
    val gender: String,
    val roleTag: String,
    val intro: String?
)

fun ViewBookCharacterFormBinding.initCharacterForm(context: Context) {
    spinnerGender.adapter = ArrayAdapter(
        context,
        R.layout.item_ng_spinner_text,
        BookCharacterLabels.genderValues.map { BookCharacterLabels.genderLabel(context, it) }
    ).apply {
        setDropDownViewResource(R.layout.item_tts_spinner_dropdown)
    }
    spinnerRole.adapter = ArrayAdapter(
        context,
        R.layout.item_ng_spinner_text,
        BookCharacterLabels.roleValues.map { BookCharacterLabels.roleLabel(context, it) }
    ).apply {
        setDropDownViewResource(R.layout.item_tts_spinner_dropdown)
    }
}

fun ViewBookCharacterFormBinding.bindCharacterForm(
    character: BookCharacter?,
    castRole: BookTtsCastRole?
) {
    if (character != null) {
        editName.setText(character.name)
        editAliases.setText(character.aliases().joinToString(", "))
        spinnerGender.setSelection(
            BookCharacterLabels.genderValues.indexOf(character.gender).coerceAtLeast(0)
        )
        spinnerRole.setSelection(
            BookCharacterLabels.roleValues.indexOf(character.roleTag).coerceAtLeast(0)
        )
        editIntro.setText(character.displayIntro().orEmpty())
        return
    }
    if (castRole != null) {
        editName.setText(castRole.name)
        editAliases.setText(
            GSON.fromJsonObject<List<String>>(castRole.aliasesJson)
                .getOrNull()
                .orEmpty()
                .joinToString(", ")
        )
        spinnerGender.setSelection(
            BookCharacterLabels.genderValues.indexOf(castRole.gender).coerceAtLeast(0)
        )
        spinnerRole.setSelection(
            BookCharacterLabels.roleValues.indexOf(defaultPromotedRoleTag(castRole.gender)).coerceAtLeast(0)
        )
    }
}

fun ViewBookCharacterFormBinding.readCharacterForm(): BookCharacterFormValue {
    return BookCharacterFormValue(
        name = editName.text?.toString()?.trim().orEmpty(),
        aliases = editAliases.text?.toString()
            ?.split(",", "，", "/", "、")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            .orEmpty(),
        gender = BookCharacterLabels.genderValues[spinnerGender.selectedItemPosition],
        roleTag = BookCharacterLabels.roleValues[spinnerRole.selectedItemPosition],
        intro = editIntro.text?.toString()?.trim()?.ifBlank { null }
    )
}

object BookCharacterEditor {
    fun save(
        workKey: String,
        current: BookCharacter?,
        castRole: BookTtsCastRole?,
        value: BookCharacterFormValue
    ): Long {
        val now = System.currentTimeMillis()
        val mergedAliases = mergePromotedAliases(
            canonicalName = value.name,
            formAliases = value.aliases,
            castRole = castRole
        )
        val item = (current ?: BookCharacter(workKey = workKey, createdAt = now)).apply {
            name = value.name
            gender = value.gender
            roleTag = value.roleTag
            identity = null
            aliasesJson = mergedAliases.takeIf { it.isNotEmpty() }?.let(GSON::toJson)
            intro = value.intro
            shortIntro = null
            updatedAt = now
        }
        return appDb.runInTransaction<Long> {
            val savedId = if (item.id == 0L) {
                appDb.bookCharacterDao.insertCharacter(item)
            } else {
                appDb.bookCharacterDao.updateCharacter(item)
                item.id
            }
            castRole?.let { appDb.bookCharacterDao.mergeTtsCastRoleIntoCharacter(it, savedId) }
            appDb.bookCharacterDao.updateCharacterCount(workKey, now)
            savedId
        }
    }
}

internal fun mergePromotedAliases(
    canonicalName: String,
    formAliases: List<String>,
    castRole: BookTtsCastRole?
): List<String> = buildList {
    addAll(formAliases)
    castRole?.let { role ->
        add(role.name)
        addAll(
            GSON.fromJsonObject<List<String>>(role.aliasesJson)
                .getOrNull()
                .orEmpty()
        )
    }
}.map(String::trim)
    .filter { it.isNotBlank() && !it.equals(canonicalName.trim(), ignoreCase = true) }
    .distinctBy { it.lowercase() }

private fun BookCharacter.aliases(): List<String> {
    return aliasesJson?.let { GSON.fromJsonObject<List<String>>(it).getOrNull() }.orEmpty()
}

internal fun defaultPromotedRoleTag(gender: String): String = when (gender) {
    BookCharacter.Gender.MALE -> BookCharacter.RoleTag.MALE_LEAD
    BookCharacter.Gender.FEMALE -> BookCharacter.RoleTag.FEMALE_LEAD
    else -> BookCharacter.RoleTag.UNKNOWN
}
