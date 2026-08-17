package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NgDrawerAppearanceConfigTest {

    @Test
    fun `percent values stay inside valid range`() {
        assertEquals(0, NgDrawerAppearanceConfig.normalizePercent(-1))
        assertEquals(42, NgDrawerAppearanceConfig.normalizePercent(42))
        assertEquals(100, NgDrawerAppearanceConfig.normalizePercent(101))
    }

    @Test
    fun `surface alpha keeps opaque baseline and transparent endpoints`() {
        assertEquals(1f, NgDrawerAppearanceConfig.surfaceAlpha(0, 0.8f), 0.0001f)
        assertEquals(0.8f, NgDrawerAppearanceConfig.surfaceAlpha(20, 0.8f), 0.0001f)
        assertEquals(0f, NgDrawerAppearanceConfig.surfaceAlpha(100, 0.8f), 0.0001f)
        assertTrue(NgDrawerAppearanceConfig.surfaceAlpha(10, 0.8f) > 0.8f)
    }

    @Test
    fun `horizontal margin snaps to two dp steps`() {
        assertEquals(0, NgDrawerAppearanceConfig.normalizeHorizontalMarginDp(-1))
        assertEquals(4, NgDrawerAppearanceConfig.normalizeHorizontalMarginDp(3))
        assertEquals(32, NgDrawerAppearanceConfig.normalizeHorizontalMarginDp(40))
    }

    @Test
    fun `corner radius supports zero and snaps to two dp steps`() {
        assertEquals(0, NgDrawerAppearanceConfig.normalizeCornerRadiusDp(0))
        assertEquals(18, NgDrawerAppearanceConfig.normalizeCornerRadiusDp(17))
        assertEquals(40, NgDrawerAppearanceConfig.normalizeCornerRadiusDp(48))
    }
}
