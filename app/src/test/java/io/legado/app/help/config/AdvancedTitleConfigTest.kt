package io.legado.app.help.config

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        assertEquals("legado_advanced_title_weighted", textStyle(layers, 0).getString("f"))
        assertEquals("legado_advanced_title_weighted", textStyle(layers, 1).getString("f"))
        assertEquals("original_font", textStyle(layers, 2).getString("f"))
        assertEquals(0.2, textStyle(layers, 0).getJSONArray("fc").getDouble(0), 0.0001)
        assertEquals(0.4, textStyle(layers, 0).getJSONArray("fc").getDouble(1), 0.0001)
        assertEquals(0.6, textStyle(layers, 0).getJSONArray("fc").getDouble(2), 0.0001)
        assertEquals(0.1, textStyle(layers, 2).getJSONArray("fc").getDouble(0), 0.0001)
        assertTrue(rendered.contains("legado_advanced_title_weighted"))
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
            {"nm":"chapter_number","t":{"d":{"k":[{"s":{"f":"original_font","fc":[0.1,0.1,0.1]}}]}}},
            {"nm":"chapter_title","t":{"d":{"k":[{"s":{"f":"original_font","fc":[0.2,0.2,0.2]}}]}}},
            {"nm":"ornament","t":{"d":{"k":[{"s":{"f":"original_font","fc":[0.1,0.2,0.3]}}]}}}
          ]
        }
    """.trimIndent()
}
