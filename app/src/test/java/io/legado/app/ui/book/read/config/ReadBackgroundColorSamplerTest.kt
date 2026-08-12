package io.legado.app.ui.book.read.config

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadBackgroundColorSamplerTest {

    @Test
    fun patchAverageSmoothsAHighContrastCenterPixel() {
        val pixels = IntArray(9) { 0xFF000000.toInt() }.apply {
            this[4] = 0xFFFFFFFF.toInt()
        }

        val result = ReadBackgroundColorSampler.samplePatch(
            width = 3,
            height = 3,
            centerX = 1,
            centerY = 1,
            radius = 1,
        ) { x, y -> pixels[y * 3 + x] }

        assertEquals(rgb(28, 28, 28), result)
    }

    @Test
    fun patchSamplingClampsAtBitmapEdges() {
        val pixels = intArrayOf(
            rgb(255, 0, 0), rgb(0, 255, 0),
            rgb(0, 0, 255), rgb(255, 255, 255),
        )

        val result = ReadBackgroundColorSampler.samplePatch(
            width = 2,
            height = 2,
            centerX = 0,
            centerY = 0,
            radius = 1,
        ) { x, y -> pixels[y * 2 + x] }

        assertEquals(rgb(127, 127, 127), result)
    }

    private fun rgb(red: Int, green: Int, blue: Int): Int =
        0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue
}
