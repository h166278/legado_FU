package io.legado.app.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActivityResultContractsTest {

    @Test
    fun anyMimeTypeDoesNotFilterBuiltInPicker() {
        assertNull(extensionsOfMimeTypes(arrayOf("*/*")))
    }

    @Test
    fun commonMimeTypesMapToBuiltInPickerExtensions() {
        assertArrayEquals(
            arrayOf("txt", "xml", "json", "md", "csv"),
            extensionsOfMimeTypes(arrayOf("text/*")),
        )
        assertArrayEquals(
            arrayOf("jpg", "jpeg", "png", "gif", "bmp", "webp"),
            extensionsOfMimeTypes(arrayOf("image/*")),
        )
        assertArrayEquals(
            arrayOf("ttf", "otf", "woff", "woff2"),
            extensionsOfMimeTypes(arrayOf("font/*")),
        )
    }

    @Test
    fun duplicateMimeTypesKeepStableDistinctExtensions() {
        assertArrayEquals(
            arrayOf("zip"),
            extensionsOfMimeTypes(
                arrayOf("application/zip", "application/x-zip-compressed"),
            ),
        )
    }
}
