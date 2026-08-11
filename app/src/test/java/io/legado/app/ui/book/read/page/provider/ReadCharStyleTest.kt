package io.legado.app.ui.book.read.page.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadCharStyleTest {

    @Test
    fun `resolves colors for the current reading theme`() {
        val style = ReadCharStyle(
            textColor = 0x11,
            textColorNight = 0x22,
            bgColor = 0x33,
            bgColorNight = 0x44,
            underlineColor = 0x55,
            underlineColorNight = 0x66,
        )

        assertEquals(0x11, style.resolveTextColor(isNight = false))
        assertEquals(0x22, style.resolveTextColor(isNight = true))
        assertEquals(0x33, style.resolveBackgroundColor(isNight = false))
        assertEquals(0x44, style.resolveBackgroundColor(isNight = true))
        assertEquals(0x55, style.resolveUnderlineColor(isNight = false))
        assertEquals(0x66, style.resolveUnderlineColor(isNight = true))
    }

    @Test
    fun `night colors inherit day colors when no override exists`() {
        val style = ReadCharStyle(
            textColor = 0x11,
            bgColor = 0x33,
            underlineColor = 0x55,
        )

        assertEquals(0x11, style.resolveTextColor(isNight = true))
        assertEquals(0x33, style.resolveBackgroundColor(isNight = true))
        assertEquals(0x55, style.resolveUnderlineColor(isNight = true))
    }
}
