package io.legado.app.ui.design.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NgAppThemeModeTest {

    @Test
    fun explicitReaderModeOverridesAppAndSystemMode() {
        assertFalse(resolveNgThemeNightMode("2", true, false))
        assertTrue(resolveNgThemeNightMode("1", false, true))
    }

    @Test
    fun absentOverrideKeepsAppThemeBehavior() {
        assertTrue(resolveNgThemeNightMode("0", true, null))
        assertFalse(resolveNgThemeNightMode("0", false, null))
        assertFalse(resolveNgThemeNightMode("1", true, null))
        assertTrue(resolveNgThemeNightMode("2", false, null))
    }
}
