package io.legado.app.help.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsEngineImportResolverTest {

    @Test
    fun askReportsExistingCustomScriptWithoutChangingItsIdentity() {
        val existing = engine(id = "shared", name = "旧引擎", version = "1.0.0")
        val imported = engine(id = "shared", name = "新引擎", version = "1.1.0")

        val error = runCatching {
            TtsEngineImportResolver.resolve(
                imported = listOf(imported),
                existing = listOf(existing),
                defaultIds = emptySet(),
                action = TtsEngineImportConflictAction.ASK
            )
        }.exceptionOrNull()

        assertTrue(error is TtsEngineImportConflictException)
        val conflict = (error as TtsEngineImportConflictException).conflicts.single()
        assertEquals("shared", conflict.id)
        assertEquals("旧引擎", conflict.existingName)
        assertEquals("新引擎", conflict.importedName)
        assertTrue(conflict.canOverwrite)
    }

    @Test
    fun overwriteReplacesDefinitionButPreservesLocalUserState() {
        val existing = engine(id = "shared", name = "旧引擎", version = "1.0.0").copy(
            enabled = false,
            builtIn = true,
            optionValues = mapOf("token" to "local-secret", "sampleRate" to "48000"),
            activeVoiceId = "voice-a",
            disabledVoiceIds = listOf("voice-b")
        )
        val imported = engine(id = "shared", name = "新引擎", version = "1.1.0").copy(
            contentType = "audio/mpeg",
            maxConcurrency = 3,
            defaultSpeed = 60,
            capabilities = setOf(TtsEngineCapability.SCENE_CONTEXT)
        )

        val merged = TtsEngineImportResolver.mergeForOverwrite(existing, imported)

        assertEquals("新引擎", merged.name)
        assertTrue(merged.script.contains("// @version 1.1.0"))
        assertEquals("audio/mpeg", merged.contentType)
        assertEquals(3, merged.maxConcurrency)
        assertEquals(60, merged.defaultSpeed)
        assertEquals(setOf(TtsEngineCapability.SCENE_CONTEXT), merged.capabilities)
        assertFalse(merged.enabled)
        assertTrue(merged.builtIn)
        assertEquals(existing.optionValues, merged.optionValues)
        assertEquals("voice-a", merged.activeVoiceId)
        assertEquals(listOf("voice-b"), merged.disabledVoiceIds)
    }

    @Test
    fun keepBothRewritesUuidAndNameOnlyWhenExplicitlyRequested() {
        val existing = engine(id = "shared", name = "旧引擎", version = "1.0.0")
        val imported = engine(id = "shared", name = "新引擎", version = "1.1.0")

        val copy = TtsEngineImportResolver.resolve(
            imported = listOf(imported),
            existing = listOf(existing),
            defaultIds = emptySet(),
            action = TtsEngineImportConflictAction.KEEP_BOTH,
            copyIdSeed = 42L
        ).single()

        assertEquals("shared_42", copy.id)
        assertEquals("新引擎 副本", copy.name)
        assertTrue(copy.script.contains("// @uuid shared_42"))
        assertTrue(copy.script.contains("// @name 新引擎 副本"))
        assertTrue(copy.script.contains("// @version 1.1.0"))
    }

    @Test
    fun systemEngineCannotBeOverwrittenByImportedScript() {
        val existing = engine(id = "system_engine", name = "系统引擎", version = "1.0.0")
            .copy(type = TtsEngineType.SYSTEM)
        val imported = engine(id = "system_engine", name = "导入脚本", version = "1.1.0")

        val error = runCatching {
            TtsEngineImportResolver.resolve(
                imported = listOf(imported),
                existing = listOf(existing),
                defaultIds = emptySet(),
                action = TtsEngineImportConflictAction.OVERWRITE
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun defaultScriptKeepsExistingSilentUpdateBehavior() {
        val existing = engine(id = "default_script", name = "旧默认脚本", version = "1.0.0")
        val imported = engine(id = "default_script", name = "新默认脚本", version = "1.1.0")

        val resolved = TtsEngineImportResolver.resolve(
            imported = listOf(imported),
            existing = listOf(existing),
            defaultIds = setOf("default_script"),
            action = TtsEngineImportConflictAction.ASK
        )

        assertEquals(listOf(imported), resolved)
    }

    private fun engine(
        id: String,
        name: String,
        version: String
    ): TtsEngineSetting {
        return TtsEngineSetting(
            id = id,
            name = name,
            type = TtsEngineType.SCRIPT,
            script = """
                // @name $name
                // @version $version
                // @uuid $id

                function synthesize(text, voice, params, options, ctx) {
                    return {};
                }
            """.trimIndent()
        )
    }
}
