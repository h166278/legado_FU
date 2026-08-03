package io.legado.app.help.config

import io.legado.app.ui.design.theme.NgTopBarTextMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NgBuiltInThemePresetTest {

    @Test
    fun `classic dark preset matches legacy native night palette`() {
        val colors = NgBuiltInThemes.classic.colors.manualDark

        assertEquals(0xFFD84315.toInt(), colors.primary)
        assertEquals(0xFF546E7A.toInt(), colors.secondary)
        assertEquals(0xFFFFFFFF.toInt(), colors.primaryText)
        assertEquals(0xB3FFFFFF.toInt(), colors.secondaryText)
        assertEquals(0xFF212121.toInt(), colors.background)
        assertEquals(0xFF303030.toInt(), colors.labelContainer)
    }

    @Test
    fun `warm and bamboo presets only provide light backgrounds`() {
        assertEquals(
            "asset://defaultData/theme/reading_ng_warm.png",
            NgBuiltInThemes.warm.lightBackground.path,
        )
        assertNull(NgBuiltInThemes.warm.darkBackground.path)
        assertEquals(
            "asset://defaultData/theme/reading_ng_bamboo.png",
            NgBuiltInThemes.bamboo.lightBackground.path,
        )
        assertNull(NgBuiltInThemes.bamboo.darkBackground.path)
    }

    @Test
    fun `mist preset provides both backgrounds and light top bar text`() {
        val expectedBackground = "asset://defaultData/theme/reading_ng_mist.png"

        assertEquals(expectedBackground, NgBuiltInThemes.mist.lightBackground.path)
        assertEquals(expectedBackground, NgBuiltInThemes.mist.darkBackground.path)
        assertEquals(
            NgTopBarTextMode.LIGHT,
            NgBuiltInThemes.mist.colors.lightTopBarTextMode,
        )
        assertEquals(
            NgTopBarTextMode.LIGHT,
            NgBuiltInThemes.mist.colors.darkTopBarTextMode,
        )
        assertEquals(
            NgBuiltInThemes.mist.colors.manualLight,
            NgBuiltInThemes.mist.colors.manualDark,
        )
        assertEquals(
            NgBuiltInThemes.mist.colors.lightSeed,
            NgBuiltInThemes.mist.colors.darkSeed,
        )
    }

    @Test
    fun `built in themes except autumn use traditional bars`() {
        val expected = NgThemeBarProfile(
            useFloatingBottomBar = false,
            bookshelfTopBarStyle = BookshelfTopBarStyle.TRADITIONAL.value,
        )

        assertEquals(
            listOf(expected, expected, expected, expected),
            NgBuiltInThemes.all.dropLast(1).map { it.barProfile },
        )
    }

    @Test
    fun `autumn preset reuses warm palette and configures both floating docks`() {
        val autumn = NgBuiltInThemes.autumn

        assertEquals("秋山书意", autumn.name)
        assertEquals(NgBuiltInThemes.warm.colors, autumn.colors)
        assertEquals(
            "asset://defaultData/theme/reading_ng_autumn_mountains.png",
            autumn.lightBackground.path,
        )
        assertNull(autumn.darkBackground.path)
        assertEquals(
            NgThemeBarProfile(
                useFloatingBottomBar = true,
                floatingBottomBarBottomDistancePx = 40,
                floatingBottomBarTransparency = 40,
                bookshelfTopBarStyle = BookshelfTopBarStyle.FLOATING_DOCK.value,
                bookshelfFloatingDockTopDistancePx = 360,
                bookshelfFloatingDockTransparency = 40,
            ),
            autumn.barProfile,
        )
        assertEquals(autumn, NgBuiltInThemes.all.last())
    }

    @Test
    fun `legacy bar profile uses current settings as editor fallback`() {
        val current = NgThemeBarProfile(
            useFloatingBottomBar = true,
            floatingBottomBarBottomDistancePx = 40,
            floatingBottomBarTransparency = 40,
            bookshelfTopBarStyle = BookshelfTopBarStyle.FLOATING_DOCK.value,
            bookshelfFloatingDockTopDistancePx = 360,
            bookshelfFloatingDockTransparency = 40,
        )
        val legacy: NgThemeBarProfile? = null

        assertEquals(current, legacy.withFallback(current))
        assertEquals(
            current.copy(
                useFloatingBottomBar = false,
                bookshelfFloatingDockTransparency = 75,
            ),
            NgThemeBarProfile(
                useFloatingBottomBar = false,
                bookshelfFloatingDockTransparency = 75,
            ).withFallback(current),
        )
    }
}
