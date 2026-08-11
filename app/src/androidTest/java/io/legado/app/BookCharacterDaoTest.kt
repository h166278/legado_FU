package io.legado.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookCharacterTtsBinding
import io.legado.app.data.entities.BookTtsCastRole
import io.legado.app.data.entities.BookTtsCastRoleContribution
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookCharacterDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun promotionMigratesBindingAndDeletesSourceRole() {
        val workKey = "测试书\n作者"
        val dao = db.bookCharacterDao
        dao.insertProfile(BookCharacterProfile(workKey = workKey))
        val characterId = dao.insertCharacter(BookCharacter(workKey = workKey, name = "沈言卿"))
        val roleId = dao.upsertTtsCastRole(BookTtsCastRole(workKey = workKey, name = "青青子衿"))
        val role = dao.getTtsCastRole(roleId)!!
        dao.upsertTtsCastRoleContribution(
            BookTtsCastRoleContribution(
                workKey = workKey,
                chapterIndex = 9,
                roleId = roleId,
                cacheKey = "chapter-9",
                cacheRevision = 1L
            )
        )
        dao.upsertTtsBinding(
            BookCharacterTtsBinding.castRole(workKey, roleId).apply {
                engineId = "engine"
                voiceId = "voice"
            }
        )

        dao.mergeTtsCastRoleIntoCharacter(role, characterId)

        assertNull(dao.getTtsCastRole(roleId))
        assertEquals(emptyList<BookTtsCastRoleContribution>(), dao.getTtsCastRoleContributions(roleId))
        assertEquals(
            listOf("voice"),
            dao.getTtsBindings(workKey)
                .filter { it.targetType == BookCharacterTtsBinding.TargetType.CHARACTER }
                .map { it.voiceId }
        )
        assertEquals(
            0,
            dao.getTtsBindings(workKey).count {
                it.targetType == BookCharacterTtsBinding.TargetType.CAST_ROLE
            }
        )
    }

    @Test
    fun deletingFormalCharacterDoesNotResurrectLegacyLinkedRole() {
        val workKey = "测试书\n作者"
        val dao = db.bookCharacterDao
        dao.insertProfile(BookCharacterProfile(workKey = workKey))
        val characterId = dao.insertCharacter(BookCharacter(workKey = workKey, name = "沈言卿"))
        val character = dao.getCharacter(characterId)!!
        val roleId = dao.upsertTtsCastRole(
            BookTtsCastRole(
                workKey = workKey,
                name = "青青子衿",
                linkedCharacterId = characterId
            )
        )

        dao.deleteCharacterWithTts(character)

        assertNull(dao.getCharacter(characterId))
        assertNull(dao.getTtsCastRole(roleId))
        assertEquals(emptyList<BookCharacterTtsBinding>(), dao.getTtsBindings(workKey))
    }
}
