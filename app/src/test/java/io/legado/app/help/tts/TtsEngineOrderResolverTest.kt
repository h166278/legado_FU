package io.legado.app.help.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsEngineOrderResolverTest {

    @Test
    fun `reorder preserves latest fields and items outside visible order`() {
        val hidden = engine("hidden", enabled = false)
        val latest = listOf(
            engine("a", name = "A latest", optionValue = "saved"),
            hidden,
            engine("b", name = "B latest"),
            engine("new", name = "New engine")
        )

        val merged = TtsEngineOrderResolver.mergeLatest(
            latest = latest,
            orderedIds = listOf("b", "a")
        )!!

        assertEquals(listOf("b", "hidden", "a", "new"), merged.map { it.id })
        assertEquals("saved", merged.first { it.id == "a" }.optionValues["token"])
        assertEquals(hidden, merged.first { it.id == "hidden" })
    }

    @Test
    fun `consecutive reorder intents compose on latest stored order`() {
        val initial = listOf(engine("a"), engine("b"), engine("c"))
        val first = TtsEngineOrderResolver.mergeLatest(initial, listOf("b", "a", "c"))!!
        val second = TtsEngineOrderResolver.mergeLatest(first, listOf("c", "b", "a"))!!

        assertEquals(listOf("c", "b", "a"), second.map { it.id })
    }

    @Test
    fun `stale order cannot resurrect a deleted engine`() {
        val latestAfterDelete = listOf(engine("a"), engine("c"))

        assertNull(
            TtsEngineOrderResolver.mergeLatest(
                latest = latestAfterDelete,
                orderedIds = listOf("c", "b", "a")
            )
        )
    }

    @Test
    fun `duplicate order ids are rejected`() {
        assertNull(
            TtsEngineOrderResolver.mergeLatest(
                latest = listOf(engine("a"), engine("b")),
                orderedIds = listOf("a", "a")
            )
        )
    }

    private fun engine(
        id: String,
        name: String = id,
        enabled: Boolean = true,
        optionValue: String? = null
    ) = TtsEngineSetting(
        id = id,
        name = name,
        type = TtsEngineType.SCRIPT,
        enabled = enabled,
        optionValues = optionValue?.let { mapOf("token" to it) }.orEmpty()
    )
}
