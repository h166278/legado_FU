package io.legado.app.help.config

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedTitleConfigTest {

    @Test
    fun `keeps template unchanged without style override`() {
        val source = fixtureJson()
        assertEquals(source, AdvancedTitleConfig.applyCompatibleTextStyle(source, null, 400))
    }

    @Test
    fun `color override keeps compatible layer font at default weight`() {
        val rendered = AdvancedTitleConfig.applyCompatibleTextStyle(fixtureJson(), 0xff336699.toInt(), 400)
        val layers = JSONObject(rendered).getJSONArray("layers")

        assertEquals("original_font", textStyle(layers, 0).getString("f"))
        assertEquals("original_font", textStyle(layers, 1).getString("f"))
    }

    @Test
    fun `overrides only compatible text layer colors`() {
        val rendered = AdvancedTitleConfig.applyCompatibleTextStyle(fixtureJson(), 0xff336699.toInt(), 700)
        val layers = JSONObject(rendered).getJSONArray("layers")

        assertEquals("legado_advanced_title_weighted_700", textStyle(layers, 0).getString("f"))
        assertEquals("legado_advanced_title_weighted_700", textStyle(layers, 1).getString("f"))
        assertEquals("original_font", textStyle(layers, 2).getString("f"))
        assertEquals(0.2, textStyle(layers, 0).getJSONArray("fc").getDouble(0), 0.0001)
        assertEquals(0.4, textStyle(layers, 0).getJSONArray("fc").getDouble(1), 0.0001)
        assertEquals(0.6, textStyle(layers, 0).getJSONArray("fc").getDouble(2), 0.0001)
        assertEquals(0.1, textStyle(layers, 2).getJSONArray("fc").getDouble(0), 0.0001)
        assertTrue(rendered.contains("legado_advanced_title_weighted_700"))
    }

    @Test
    fun `weight value is encoded into font name so json changes between non default weights`() {
        val w500 = AdvancedTitleConfig.applyCompatibleTextStyle(fixtureJson(), null, 500)
        val w700 = AdvancedTitleConfig.applyCompatibleTextStyle(fixtureJson(), null, 700)

        // 字重数值必须进入 JSON，否则 PageView 不会重载动画、Lottie 缓存旧 Typeface 导致调整不生效
        assertNotEquals(w500, w700)
        assertTrue(w500.contains("legado_advanced_title_weighted_500"))
        assertTrue(w700.contains("legado_advanced_title_weighted_700"))
    }

    @Test
    fun `font size scale rewrites size field so json changes`() {
        val source = fixtureJson()
        val base = AdvancedTitleConfig.applyCompatibleTextStyle(source, null, 400, 100)
        val scaled = AdvancedTitleConfig.applyCompatibleTextStyle(source, null, 400, 150)

        assertNotEquals(base, scaled)
        val layers = JSONObject(scaled).getJSONArray("layers")
        // fixture 文本层原字号 30 → 150% → 45
        assertEquals(45, textStyle(layers, 0).getInt("s"))
        assertEquals(45, textStyle(layers, 1).getInt("s"))
        // 装饰层不动
        assertFalse(JSONObject(scaled).getJSONArray("layers").getJSONObject(2).toString().contains("\"s\":"))
    }

    @Test
    fun `no override leaves template untouched`() {
        val source = fixtureJson()
        assertEquals(source, AdvancedTitleConfig.applyCompatibleTextStyle(source, null, 400, 100))
    }

    @Test
    fun `leaves non compatible template properties unchanged`() {
        val source = fixtureJson()
        val rendered = AdvancedTitleConfig.applyCompatibleTextStyle(source, null, 700)
        val ornament = JSONObject(rendered).getJSONArray("layers").getJSONObject(2)

        assertEquals("ornament", ornament.getString("nm"))
        assertFalse(ornament.toString().contains("legado_advanced_title_weighted"))
    }

    private fun textStyle(layers: org.json.JSONArray, index: Int): JSONObject =
        layers.getJSONObject(index).getJSONObject("t").getJSONObject("d")
            .getJSONArray("k").getJSONObject(0).getJSONObject("s")

    private fun fixtureJson() = """
        {
          "v":"5.9.6",
          "layers":[
            {"nm":"chapter_number","t":{"d":{"k":[{"s":{"s":30,"f":"original_font","fc":[0.1,0.1,0.1]}}]}}},
            {"nm":"chapter_title","t":{"d":{"k":[{"s":{"s":30,"f":"original_font","fc":[0.2,0.2,0.2]}}]}}},
            {"nm":"ornament","t":{"d":{"k":[{"s":{"f":"original_font","fc":[0.1,0.2,0.3]}}]}}}
          ]
        }
    """.trimIndent()
}
