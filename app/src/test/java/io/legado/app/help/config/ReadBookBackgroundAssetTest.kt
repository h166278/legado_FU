package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadBookBackgroundAssetTest {

    @Test
    fun `legacy bundled png names resolve to webp assets`() {
        assertEquals("暖色渐变.webp", resolveBundledReadBackgroundName("暖色渐变.png"))
        assertEquals("竹影之韵.webp", resolveBundledReadBackgroundName("竹影之韵.png"))
        assertEquals("灰色雾霭.webp", resolveBundledReadBackgroundName("灰色雾霭.png"))
        assertEquals(
            "秋山书意-日间.webp",
            resolveBundledReadBackgroundName("秋山书意-日间.png"),
        )
        assertEquals(
            "秋山书意-夜间.webp",
            resolveBundledReadBackgroundName("秋山书意-夜间.png"),
        )
        assertEquals("起点读书.jpg", resolveBundledReadBackgroundName("起点读书.jpg"))
    }
}
