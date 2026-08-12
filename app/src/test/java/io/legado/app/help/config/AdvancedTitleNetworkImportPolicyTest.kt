package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class AdvancedTitleNetworkImportPolicyTest {

    @Test
    fun `accepts HTTPS url with host`() {
        assertEquals(
            "https://example.com/title.json",
            AdvancedTitleNetworkImportPolicy.requireHttps(" https://example.com/title.json "),
        )
    }

    @Test
    fun `rejects non HTTPS urls`() {
        assertThrows(IllegalArgumentException::class.java) {
            AdvancedTitleNetworkImportPolicy.requireHttps("http://example.com/title.json")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AdvancedTitleNetworkImportPolicy.requireHttps("https:///title.json")
        }
    }

    @Test
    fun `rejects stream exceeding package limit`() {
        val oversized = ByteArray((AdvancedTitlePackageManager.MAX_JSON_BYTES + 1).toInt())
        assertThrows(IllegalArgumentException::class.java) {
            AdvancedTitleNetworkImportPolicy.readUtf8Bounded(ByteArrayInputStream(oversized))
        }
    }
}
