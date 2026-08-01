package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeConfigTest {

    @Test
    fun keepsSupportedAppearanceModes() {
        listOf("0", "1", "2", "3").forEach { mode ->
            assertEquals(mode, normalizeThemeMode(mode))
        }
    }

    @Test
    fun migratesLegacyBuiltInThemeModesToDayMode() {
        listOf("4", "5", "6").forEach { mode ->
            assertEquals("1", normalizeThemeMode(mode))
        }
    }

    @Test
    fun defaultsMissingOrInvalidModeToFollowSystem() {
        assertEquals("0", normalizeThemeMode(null))
        assertEquals("0", normalizeThemeMode(""))
        assertEquals("0", normalizeThemeMode("unexpected"))
    }
}
