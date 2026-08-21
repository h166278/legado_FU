package io.legado.app.help.config

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI

internal object AdvancedTitleNetworkImportPolicy {

    fun requireHttps(url: String): String {
        val normalized = url.trim()
        val uri = runCatching { URI(normalized) }.getOrNull()
        require(uri?.scheme.equals("https", ignoreCase = true) && !uri?.host.isNullOrBlank()) {
            "Advanced title URL must use HTTPS"
        }
        return normalized
    }

    fun readUtf8Bounded(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size().toLong() + count <= AdvancedTitlePackageManager.MAX_JSON_BYTES) {
                "Advanced title JSON exceeds size limit"
            }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }
}
