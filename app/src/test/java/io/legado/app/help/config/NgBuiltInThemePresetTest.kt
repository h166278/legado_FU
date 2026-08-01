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
}
