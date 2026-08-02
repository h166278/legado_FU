package io.legado.app.help.config

import io.legado.app.ui.design.theme.NgTopBarTextMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NgBuiltInThemePresetTest {

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
}
