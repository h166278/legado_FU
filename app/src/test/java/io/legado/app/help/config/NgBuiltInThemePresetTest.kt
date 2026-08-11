package io.legado.app.help.config

import io.legado.app.ui.design.theme.NgColorMath
import io.legado.app.ui.design.theme.NgTopBarTextMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
            "asset://bg/暖色渐变.webp",
            NgBuiltInThemes.warm.lightBackground.path,
        )
        assertNull(NgBuiltInThemes.warm.darkBackground.path)
        assertEquals(
            "asset://bg/竹影之韵.webp",
            NgBuiltInThemes.bamboo.lightBackground.path,
        )
        assertNull(NgBuiltInThemes.bamboo.darkBackground.path)
    }

    @Test
    fun `mist preset provides both backgrounds and light top bar text`() {
        val expectedBackground = "asset://bg/灰色雾霭.webp"

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
    fun `legacy bundled background paths resolve to reading background assets`() {
        assertEquals(
            "bg/暖色渐变.webp",
            resolveBundledBackgroundAssetPath("defaultData/theme/reading_ng_warm.png"),
        )
        assertEquals(
            "bg/暖色渐变.webp",
            resolveBundledBackgroundAssetPath("bg/暖色渐变.png"),
        )
        assertEquals(
            "bg/竹影之韵.webp",
            resolveBundledBackgroundAssetPath("defaultData/theme/reading_ng_bamboo.png"),
        )
        assertEquals(
            "bg/灰色雾霭.webp",
            resolveBundledBackgroundAssetPath("defaultData/theme/reading_ng_mist.png"),
        )
        assertEquals(
            "defaultData/theme/reading_ng_autumn_mountains.webp",
            resolveBundledBackgroundAssetPath(
                "defaultData/theme/reading_ng_autumn_mountains.png"
            ),
        )
        assertEquals(
            "defaultData/theme/reading_ng_autumn_mountains_dark.webp",
            resolveBundledBackgroundAssetPath(
                "defaultData/theme/reading_ng_autumn_mountains_dark.png"
            ),
        )
    }

    @Test
    fun `missing installed background is repaired after built in package update`() {
        val packageRoot = File("build/theme-package").absoluteFile
        val oldPath = File(packageRoot, "assets/background-light.png").path
        val installedPath = File(packageRoot, "assets/background-light.webp").path

        assertEquals(
            installedPath,
            resolveReinstalledThemeBackgroundPath(
                currentPath = oldPath,
                installedPath = installedPath,
                packageRootPath = packageRoot.path,
                isFile = { it == installedPath },
            ),
        )
    }

    @Test
    fun `existing and user managed background paths are not repaired`() {
        val packageRoot = File("build/theme-package").absoluteFile
        val existingPath = File(packageRoot, "assets/background-light.png").path
        val installedPath = File(packageRoot, "assets/background-light.webp").path
        val userPath = File(packageRoot.parentFile, "user/background.png").path

        assertEquals(
            existingPath,
            resolveReinstalledThemeBackgroundPath(
                currentPath = existingPath,
                installedPath = installedPath,
                packageRootPath = packageRoot.path,
                isFile = { it == existingPath || it == installedPath },
            ),
        )
        assertEquals(
            userPath,
            resolveReinstalledThemeBackgroundPath(
                currentPath = userPath,
                installedPath = installedPath,
                packageRootPath = packageRoot.path,
                isFile = { it == installedPath },
            ),
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
    fun `autumn preset provides a paired night theme and configures both floating docks`() {
        val autumn = NgBuiltInThemes.autumn
        val dark = autumn.colors.manualDark

        assertEquals("秋山书意", autumn.name)
        assertTrue(autumn.isBuiltIn)
        assertEquals(NgBuiltInThemes.warm.colors.manualLight, autumn.colors.manualLight)
        assertEquals(NgBuiltInThemes.mist.colors.darkSeed, autumn.colors.darkSeed)
        assertEquals(0xFF758DB4.toInt(), dark.primary)
        assertEquals(0xFF2F3B4B.toInt(), dark.secondary)
        assertEquals(0xFFF2F5F8.toInt(), dark.primaryText)
        assertEquals(0xFFB8C2CC.toInt(), dark.secondaryText)
        assertEquals(0xFF192633.toInt(), dark.background)
        assertEquals(0xFF263440.toInt(), dark.labelContainer)
        val settingsIconContainer = NgColorMath.blend(dark.background, dark.primary, 0.34f)
        assertTrue(
            NgColorMath.contrastRatio(settingsIconContainer, dark.primaryText) >= 4.5
        )
        assertEquals(NgTopBarTextMode.LIGHT, autumn.colors.darkTopBarTextMode)
        assertEquals(
            "asset://defaultData/theme/reading_ng_autumn_mountains.webp",
            autumn.lightBackground.path,
        )
        assertEquals(
            "asset://defaultData/theme/reading_ng_autumn_mountains_dark.webp",
            autumn.darkBackground.path,
        )
        assertEquals(
            NgThemeBarProfile(
                useFloatingBottomBar = true,
                floatingBottomBarBottomDistancePx = 40,
                floatingBottomBarTransparency = 40,
                bookshelfTopBarStyle = BookshelfTopBarStyle.FLOATING_DOCK.value,
                bookshelfFloatingDockTopDistancePx = 360,
                bookshelfFloatingDockTransparency = 40,
                bookshelfFloatingDockSearchPosition =
                    BookshelfFloatingDockSearchPosition.LEFT.value,
            ),
            autumn.barProfile,
        )
        assertEquals(autumn, NgBuiltInThemes.all.last())
        assertEquals(autumn, NgBuiltInThemes.defaultTheme)
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
            bookshelfFloatingDockSearchPosition =
                BookshelfFloatingDockSearchPosition.RIGHT.value,
        )
        val legacy: NgThemeBarProfile? = null

        assertEquals(current, legacy.withFallback(current))
        assertEquals(
            current.copy(
                useFloatingBottomBar = false,
                bookshelfFloatingDockTransparency = 75,
                bookshelfFloatingDockSearchPosition =
                    BookshelfFloatingDockSearchPosition.LEFT.value,
            ),
            NgThemeBarProfile(
                useFloatingBottomBar = false,
                bookshelfFloatingDockTransparency = 75,
                bookshelfFloatingDockSearchPosition =
                    BookshelfFloatingDockSearchPosition.LEFT.value,
            ).withFallback(current),
        )
        assertEquals(
            BookshelfFloatingDockSearchPosition.LEFT.value,
            NgThemeBarProfile(
                bookshelfFloatingDockSearchPosition = Int.MAX_VALUE
            ).normalized().bookshelfFloatingDockSearchPosition,
        )
    }
}
