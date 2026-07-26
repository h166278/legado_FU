package io.legado.app

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.AppDatabase
import io.legado.app.data.DatabaseMigrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val TEST_DB = "migration-test"

    private val ALL_MIGRATIONS = arrayOf<Migration>(

    )

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrateAll() {
        // Create earliest version of the database.
        helper.createDatabase(TEST_DB, 50).apply {
            close()
        }

        // Open latest version of the database. Room will validate the schema
        // once all migrations execute.
        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB
        ).addMigrations(*ALL_MIGRATIONS)
            .build().apply {
                openHelper.writableDatabase
                close()
            }
    }

    @Test
    @Throws(IOException::class)
    fun migrate107To108_preservesBindingsAndAddsCastRoles() {
        val dbName = "migration-107-108"
        helper.createDatabase(dbName, 107).apply {
            execSQL("INSERT INTO bookCharacterProfiles(workKey, bookName, bookAuthor) VALUES ('work', 'book', 'author')")
            execSQL(
                "INSERT INTO bookCharacterTtsBindings(workKey, targetType, targetId, engineId, voiceId) " +
                    "VALUES ('work', 'character', 7, 'engine-a', 'voice-a')"
            )
            close()
        }

        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            dbName
        ).addMigrations(*DatabaseMigrations.migrations)
            .build().apply {
                openHelper.writableDatabase.query(
                    "SELECT engineId, voiceId, bindingMode FROM bookCharacterTtsBindings " +
                        "WHERE workKey = 'work' AND targetId = 7"
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("engine-a", cursor.getString(0))
                    assertEquals("voice-a", cursor.getString(1))
                    assertEquals("manual", cursor.getString(2))
                }
                openHelper.writableDatabase.query(
                    "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'bookTtsCastRoles'"
                ).use { cursor -> assertTrue(cursor.moveToFirst()) }
                close()
            }
    }

    @Test
    @Throws(IOException::class)
    fun migrate108To109_addsIgnoredCastRoleState() {
        val dbName = "migration-108-109"
        helper.createDatabase(dbName, 108).apply {
            execSQL("INSERT INTO bookCharacterProfiles(workKey, bookName, bookAuthor) VALUES ('work', 'book', 'author')")
            execSQL("INSERT INTO bookTtsCastRoles(workKey, name, gender) VALUES ('work', '赵文博', 'male')")
            close()
        }

        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            dbName
        ).addMigrations(*DatabaseMigrations.migrations)
            .build().apply {
                openHelper.writableDatabase.query(
                    "SELECT ignored FROM bookTtsCastRoles WHERE workKey = 'work'"
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
                close()
            }
    }

    @Test
    @Throws(IOException::class)
    fun migrate109To110_addsCastIdentityLedger() {
        val dbName = "migration-109-110"
        helper.createDatabase(dbName, 109).apply {
            execSQL("INSERT INTO bookCharacterProfiles(workKey, bookName, bookAuthor) VALUES ('work', 'book', 'author')")
            execSQL("INSERT INTO bookTtsCastRoles(workKey, name, gender) VALUES ('work', '小道童', 'male')")
            close()
        }

        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            dbName
        ).addMigrations(*DatabaseMigrations.migrations)
            .build().apply {
                openHelper.writableDatabase.query(
                    "SELECT identityState, nameType, identityEvidence, genderEvidence, " +
                        "chapterOccurrencesJson, identityEvidenceJson " +
                        "FROM bookTtsCastRoles WHERE workKey = 'work'"
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("stable", cursor.getString(0))
                    assertEquals("unknown", cursor.getString(1))
                    assertEquals("unknown", cursor.getString(2))
                    assertEquals("unknown", cursor.getString(3))
                    assertEquals("{}", cursor.getString(4))
                    assertEquals("[]", cursor.getString(5))
                }
                close()
            }
    }

    @Test
    @Throws(IOException::class)
    fun migrate110To111_addsStoryboardCastContributions() {
        val dbName = "migration-110-111"
        helper.createDatabase(dbName, 110).apply {
            execSQL("INSERT INTO bookCharacterProfiles(workKey, bookName, bookAuthor) VALUES ('work', 'book', 'author')")
            execSQL("INSERT INTO bookTtsCastRoles(workKey, name, gender) VALUES ('work', '沈言卿', 'female')")
            close()
        }

        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            dbName
        ).addMigrations(*DatabaseMigrations.migrations)
            .build().apply {
                openHelper.writableDatabase.query(
                    "SELECT name FROM sqlite_master WHERE type = 'table' " +
                        "AND name = 'bookTtsCastRoleContributions'"
                ).use { cursor -> assertTrue(cursor.moveToFirst()) }
                close()
            }
    }
}
