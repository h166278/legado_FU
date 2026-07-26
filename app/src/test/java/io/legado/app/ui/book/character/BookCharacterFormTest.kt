package io.legado.app.ui.book.character

import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookTtsCastRole
import org.junit.Assert.assertEquals
import org.junit.Test

class BookCharacterFormTest {

    @Test
    fun promotionDefaultsToLeadRoleByGender() {
        assertEquals(
            BookCharacter.RoleTag.MALE_LEAD,
            defaultPromotedRoleTag(BookCharacter.Gender.MALE)
        )
        assertEquals(
            BookCharacter.RoleTag.FEMALE_LEAD,
            defaultPromotedRoleTag(BookCharacter.Gender.FEMALE)
        )
        assertEquals(
            BookCharacter.RoleTag.UNKNOWN,
            defaultPromotedRoleTag(BookCharacter.Gender.UNKNOWN)
        )
    }

    @Test
    fun promotionAbsorbsTemporaryNameAndAliases() {
        assertEquals(
            listOf("青青子衿", "言卿"),
            mergePromotedAliases(
                canonicalName = "沈言卿",
                formAliases = listOf("青青子衿"),
                castRole = BookTtsCastRole(
                    name = "青青子衿",
                    aliasesJson = "[\"言卿\",\"沈言卿\"]"
                )
            )
        )
    }
}
