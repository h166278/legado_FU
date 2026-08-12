package io.legado.app.help.config

import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ReadBookDefaultFloatingAppearanceTest {

    @Test
    fun `built in presets keep the approved daytime floating appearance`() {
        val asset = sequenceOf(
            File("src/main/assets/defaultData/readConfig.json"),
            File("app/src/main/assets/defaultData/readConfig.json"),
        ).first(File::isFile)
        val configs = GSON.fromJsonArray<ReadBookConfig.Config>(asset.readText()).getOrThrow()
        val actual = configs.associateBy(ReadBookConfig.Config::name)
        val expected = listOf(
            Expected("秋山书意", 0xFFF8E4D2.toInt(), ReadFloatingColorStyle.FRUIT_SALAD),
            Expected("微信读书", 0xFFC0EDC6.toInt(), ReadFloatingColorStyle.VIBRANT),
            Expected("起点读书", 0xFFEBC08C.toInt(), ReadFloatingColorStyle.VIBRANT),
            Expected("番茄小说", 0xFFE8E3CE.toInt(), ReadFloatingColorStyle.VIBRANT),
            Expected("经典纯白", 0, ReadFloatingColorStyle.VIBRANT),
            Expected("暖纸书香", 0xFFDDC090.toInt(), ReadFloatingColorStyle.FRUIT_SALAD),
        )

        assertEquals(expected.map(Expected::name), configs.map(ReadBookConfig.Config::name))
        expected.forEach { preset ->
            val config = requireNotNull(actual[preset.name])
            assertEquals(preset.seed, config.readFloatingSeed)
            assertEquals(0, config.readFloatingSeedNight)
            assertEquals(0, config.curReadFloatingTransparency())
            assertEquals(100, config.curReadFloatingPrimaryStrength())
            assertEquals(preset.style, config.curReadFloatingColorStyle())
        }
    }

    private data class Expected(
        val name: String,
        val seed: Int,
        val style: ReadFloatingColorStyle,
    )
}
