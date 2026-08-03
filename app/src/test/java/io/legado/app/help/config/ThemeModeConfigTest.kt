package io.legado.app.help.config

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun resolvesEffectiveNightModeBeforeChoosingRecreatePath() {
        assertTrue(isEffectiveNightMode(AppCompatDelegate.MODE_NIGHT_YES, false))
        assertFalse(isEffectiveNightMode(AppCompatDelegate.MODE_NIGHT_NO, true))
        assertTrue(isEffectiveNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, true))
        assertFalse(isEffectiveNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, false))
    }

    @Test
    fun resolvesThemeModeAgainstDeliveredSystemConfiguration() {
        assertTrue(resolveThemeNightMode("0", true))
        assertFalse(resolveThemeNightMode("0", false))
        assertFalse(resolveThemeNightMode("1", true))
        assertTrue(resolveThemeNightMode("2", false))
        assertFalse(resolveThemeNightMode("3", true))
    }

    @Test
    fun followModeUsesEveryDeliveredConfigurationInsteadOfCachedGlobalState() {
        val day = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_NO
        val night = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES

        assertFalse(resolveThemeNightModeFromUiMode("0", day))
        assertTrue(resolveThemeNightModeFromUiMode("0", night))
        assertFalse(resolveThemeNightModeFromUiMode("0", day))
    }
}
