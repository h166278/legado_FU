package io.legado.app.ui.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsEngineFormScreenTest {

    @Test
    fun withFieldValue_updatesOnlyTargetField() {
        val state = TtsEngineFormScreenState(
            fields = listOf(
                field("name", "old"),
                field("option:token", "secret")
            )
        )

        val updated = state.withFieldValue("name", "new")

        assertEquals("new", updated.fields[0].value)
        assertEquals("secret", updated.fields[1].value)
    }

    @Test
    fun booleanOption_acceptsLegacyTrueAliases() {
        listOf("true", "1", "yes", "Y", "on", "enabled", "启用", "是").forEach {
            assertTrue(it.toTtsBooleanOption())
        }
        assertFalse("false".toTtsBooleanOption())
        assertFalse(null.toTtsBooleanOption())
    }

    @Test
    fun normalizedType_supportsRandomNumberAndUnknownFallback() {
        assertEquals(
            TtsEngineFormFieldType.RANDOM_NUMBER,
            "random_number".toTtsEngineFormFieldType()
        )
        assertEquals(TtsEngineFormFieldType.SELECT, "select".toTtsEngineFormFieldType())
        assertEquals(TtsEngineFormFieldType.TEXT, "future_type".toTtsEngineFormFieldType())
    }

    @Test
    fun invalidRandomNumber_isGeneratedWithoutChangingValidValue() {
        assertEquals(
            "1234567890123",
            normalizeTtsEngineFormFieldValue(
                type = "random_number",
                value = "",
                digits = 13,
                allowLeadingZero = false
            ) { _, _ -> "1234567890123" }
        )
        assertEquals(
            "9876543210123",
            normalizeTtsEngineFormFieldValue(
                type = "random_number",
                value = "9876543210123",
                digits = 13,
                allowLeadingZero = false
            ) { _, _ ->
                error("valid value must not be regenerated")
            }
        )
    }

    @Test
    fun selectOptions_keepUnknownCurrentValueAndDeduplicateValues() {
        val options = buildTtsEngineFormOptions(
            currentValue = "legacy",
            options = listOf(
                TtsEngineFormOption("First", "one"),
                TtsEngineFormOption("Duplicate", "one")
            )
        )

        assertEquals(listOf("legacy", "one"), options.map { it.value })
        assertEquals("legacy", options.first().label)
    }

    @Test
    fun optionValues_preserveSecretsUntilSchemaSuccessfullyMatches() {
        val source = mapOf("token" to "secret", "removed" to "old")
        val displayed = mapOf("name" to "ignored", "token" to "edited")

        assertEquals(
            mapOf("token" to "edited", "removed" to "old", "name" to "ignored"),
            mergeTtsEngineOptionValues(source, displayed, schemaMatchesCurrentScript = false)
        )
        assertEquals(
            displayed,
            mergeTtsEngineOptionValues(source, displayed, schemaMatchesCurrentScript = true)
        )
    }

    private fun field(key: String, value: String): TtsEngineFormFieldState {
        return TtsEngineFormFieldState(
            key = key,
            label = key,
            value = value,
            type = TtsEngineFormFieldType.TEXT
        )
    }
}
