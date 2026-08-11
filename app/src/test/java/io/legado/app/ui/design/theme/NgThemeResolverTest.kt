package io.legado.app.ui.design.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NgThemeResolverTest {

    @Test
    fun lightThemeProducesReadableSemanticColors() {
        val snapshot = NgThemeResolver.resolve(lightInput())

        assertFalse(snapshot.isDark)
        assertEquals(0xFFF78E66.toInt(), snapshot.colors.primary)
        assertEquals(0xFF795548.toInt(), snapshot.colors.topBarContainer)
        assertTrue(
            NgColorMath.contrastRatio(
                snapshot.colors.background,
                snapshot.colors.onBackground
            ) >= 4.5
        )
        assertTrue(
            NgColorMath.contrastRatio(
                snapshot.colors.surface,
                snapshot.colors.onSurface
            ) >= 4.5
        )
        assertNotEquals(snapshot.colors.surface, snapshot.colors.surfaceVariant)
        assertEquals(snapshot.colors.primaryContainer, snapshot.colors.selectedContainer)
    }

    @Test
    fun darkThemeUsesLightSystemBarIcons() {
        val snapshot = NgThemeResolver.resolve(
            lightInput().copy(
                backgroundColor = 0xFF202020.toInt(),
                bottomBackground = 0xFF2A2A2A.toInt(),
                isDark = true
            )
        )

        assertTrue(snapshot.isDark)
        assertFalse(snapshot.systemBars.darkStatusBarIcons)
        assertFalse(snapshot.systemBars.darkNavigationBarIcons)
        assertTrue(
            NgColorMath.contrastRatio(
                snapshot.colors.surface,
                snapshot.colors.onSurface
            ) >= 4.5
        )
    }

    @Test
    fun einkDisablesBlurMotionAndTransparency() {
        val snapshot = NgThemeResolver.resolve(lightInput().copy(isEInk = true))

        assertTrue(snapshot.isEInk)
        assertFalse(snapshot.effects.blurEnabled)
        assertFalse(snapshot.motion.enabled)
        assertEquals(1f, snapshot.effects.containerAlpha)
        assertEquals(1f, snapshot.effects.dialogAlpha)
        assertEquals(0, snapshot.effects.blurRadiusDp)
        assertEquals(0, snapshot.motion.mediumDurationMs)
    }

    private fun lightInput() = NgLegacyThemeInput(
        primaryColor = 0xFF795548.toInt(),
        accentColor = 0xFFF78E66.toInt(),
        backgroundColor = 0xFFF5F5F5.toInt(),
        bottomBackground = 0xFFEEEEEE.toInt(),
        errorColor = 0xFFB3261E.toInt(),
        isDark = false,
        isEInk = false
    )
}
