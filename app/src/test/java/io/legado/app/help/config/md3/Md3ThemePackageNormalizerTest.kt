package io.legado.app.help.config.md3

import io.legado.app.ui.design.theme.NgColorSpec
import io.legado.app.ui.design.theme.NgContrastLevel
import io.legado.app.ui.design.theme.NgPaletteStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Md3ThemePackageNormalizerTest {

    @Test
    fun `portable selected cover album and visible cover settings become theme profile`() {
        val inspection = inspectZip(
            "manifest.json" to """
                {
                  "formatVersion": 1,
                  "name": "远岫",
                  "config": {
                    "coverLoadOnlyWifi": false,
                    "coverUseDefault": true,
                    "coverShowName": false,
                    "coverShowAuthor": true,
                    "coverShowNameN": false,
                    "coverShowAuthorN": true
                  },
                  "coverAlbums": [{
                    "ref": "album_0",
                    "name": "远岫",
                    "lightImages": [{"path": "covers/light.png"}],
                    "darkImages": []
                  }],
                  "coverSelection": {"albumRef": "album_0"}
                }
            """.trimIndent(),
            "covers/light.png" to "cover",
        )

        val spec = Md3ThemePackageNormalizer.normalize(inspection).spec
        val cover = requireNotNull(
            Md3ThemeImportManager.materializeCoverProfile(
                spec,
                mapOf("album_0" to "installed-album"),
            )
        )

        assertTrue(cover.applyAlbumSelection)
        assertEquals("installed-album", cover.albumId)
        assertEquals(false, cover.loadOnlyWifi)
        assertEquals(true, cover.useDefault)
        assertEquals(false, cover.showName)
        assertEquals(true, cover.showAuthor)
        assertEquals(false, cover.showNameDark)
        assertEquals(true, cover.showAuthorDark)
    }

    @Test
    fun `portable keeps independent light manual and dark palette sources`() {
        val inspection = inspectZip(
            "manifest.json" to """
                {
                  "formatVersion": 1,
                  "name": "混合颜色来源",
                  "config": {
                    "appTheme": "12",
                    "themeMode": "2",
                    "composeEngine": "miuix",
                    "useMiuixMonet": true,
                    "enableDeepPersonalization": true,
                    "paletteStyle": "content",
                    "materialVersion": "material3Expressive",
                    "customContrast": "High",
                    "cPrimary": -16711936,
                    "cNPrimary": -16776961,
                    "themeColor": -65536,
                    "secondaryThemeColor": -1,
                    "primaryTextColor": -16777216,
                    "secondaryTextColor": -16777216,
                    "themeBackgroundColor": -1,
                    "labelContainerColor": -657931,
                    "themeColorNight": 0,
                    "secondaryThemeColorNight": 0,
                    "primaryTextColorNight": 0,
                    "secondaryTextColorNight": 0,
                    "themeBackgroundColorNight": 0,
                    "labelContainerColorNight": 0,
                    "bgImageBlurring": 6,
                    "bgImageNBlurring": 9
                  },
                  "assets": {
                    "background.light": "assets/light.png",
                    "background.dark": "assets/dark.png"
                  }
                }
            """.trimIndent(),
            "assets/light.png" to "light",
            "assets/dark.png" to "dark",
        )

        val preview = Md3ThemePackageNormalizer.normalize(inspection)
        val spec = preview.spec

        assertEquals(NgThemePackageSourceRenderer.MIUIX, spec.sourceRenderer)
        assertTrue(spec.rendererConverted)
        assertEquals("2", spec.themeModeHint)
        assertEquals(NgPaletteStyle.CONTENT, spec.colorProfile.paletteStyle)
        assertEquals(NgColorSpec.MATERIAL_3_EXPRESSIVE_2025, spec.colorProfile.colorSpec)
        assertEquals(NgContrastLevel.HIGH, spec.colorProfile.contrast)
        assertEquals(NgThemePackageColorSource.MANUAL, spec.colorProfile.light.source)
        assertEquals(0xFFFF0000.toInt(), spec.colorProfile.light.manual?.primary)
        assertEquals(NgThemePackageColorSource.PALETTE, spec.colorProfile.dark.source)
        assertNull(spec.colorProfile.dark.manual)
        assertEquals(0xFF0000FF.toInt(), spec.colorProfile.dark.seed)
        assertEquals("assets/light.png", spec.backgroundProfile.light.archivePath)
        assertEquals(6, spec.backgroundProfile.light.blur)
        assertEquals(9, spec.backgroundProfile.dark.blur)
        assertTrue(spec.warnings.any { "Miuix" in it })
        assertTrue(spec.warnings.any { "主题模式" in it })
    }

    @Test
    fun `legacy converts colors backgrounds shell resources and covers without applying`() {
        val inspection = inspectZip(
            "application_theme.json" to """
                {
                  "version": 1,
                  "config": {
                    "name": "阿尼亚",
                    "dayTheme": {
                      "accentColor": "#FFFF8800",
                      "primaryColor": "#FFFFFFFF",
                      "backgroundColor": "#FFF7F7F7",
                      "bottomBackground": "#FFEFEFEF",
                      "backgroundImgPath": "themes/day/background.png",
                      "backgroundImgBlur": 5,
                      "transparentNavBar": true
                    },
                    "nightTheme": {
                      "accentColor": "#FFFFBB77",
                      "primaryColor": "#FF202020",
                      "backgroundColor": "#FF101010",
                      "bottomBackground": "#FF181818",
                      "backgroundImgPath": "themes/night/background.png",
                      "backgroundImgBlur": 7,
                      "transparentNavBar": true
                    }
                  },
                  "dayBottomBar": {
                    "layoutMode": "floating",
                    "effectMode": "glass",
                    "icons": {"home": "themes/day/home.png"}
                  },
                  "nightBottomBar": {
                    "icons": {"homeSelected": "themes/night/home-selected.png"}
                  },
                  "dayCover": {
                    "name": "阿尼亚封面",
                    "images": ["themes/day/cover.png"]
                  },
                  "nightCover": {
                    "images": ["themes/night/cover.png"]
                  }
                }
            """.trimIndent(),
            "themes/day/background.png" to "day",
            "themes/night/background.png" to "night",
            "themes/day/home.png" to "home",
            "themes/night/home-selected.png" to "selected",
            "themes/day/cover.png" to "cover-day",
            "themes/night/cover.png" to "cover-night",
        )

        val preview = Md3ThemePackageNormalizer.normalize(inspection)
        val spec = preview.spec

        assertEquals(NgThemePackageColorSource.MANUAL, spec.colorProfile.light.source)
        assertEquals(0xFFFF8800.toInt(), spec.colorProfile.light.manual?.primary)
        assertEquals(0xFFFFBB77.toInt(), spec.colorProfile.dark.manual?.primary)
        assertEquals("themes/day/background.png", spec.backgroundProfile.light.archivePath)
        assertEquals(5, spec.backgroundProfile.light.blur)
        assertEquals(7, spec.backgroundProfile.dark.blur)
        assertEquals("true", spec.normalizedFields["useFloatingBottomBar"])
        assertEquals("true", spec.normalizedFields["useFloatingBottomBarLiquidGlass"])
        assertEquals("themes/day/home.png", spec.resources["legacy.navigation.day.home"])
        assertEquals(1, spec.coverAlbums.single().lightImages.size)
        assertEquals(1, spec.coverAlbums.single().darkImages.size)
        assertTrue(preview.compatibility.presentKnownFieldCount > 0)
        assertEquals(1, preview.compatibility.coverAlbumCount)
        assertEquals(2, preview.compatibility.coverImageCount)
        assertTrue(spec.warnings.any { "不覆盖 NG 界面栏透明设置" in it })
    }

    private fun inspectZip(vararg entries: Pair<String, String>): Md3ThemePackageInspection {
        val bytes = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray())
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
        return Md3ThemePackageInspector.inspect(ByteArrayInputStream(bytes))
    }
}
