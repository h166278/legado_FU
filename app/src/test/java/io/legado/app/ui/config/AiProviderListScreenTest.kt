package io.legado.app.ui.config

import io.legado.app.ui.design.components.compose.NgListState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderListScreenTest {

    private val provider = AiProviderListItemUiModel(
        id = "provider-id",
        name = "Provider",
        iconRes = 1,
        enabled = true,
        modelCountText = "2 models",
        reorderable = true,
        deletable = true
    )

    @Test
    fun `reorder seam is available only outside search`() {
        val contentState = AiProviderListScreenState(
            listState = NgListState.Content(listOf(provider))
        )

        assertTrue(contentState.canRequestReorder(provider))
        assertFalse(contentState.copy(query = "pro").canRequestReorder(provider))
        assertFalse(contentState.canRequestReorder(provider.copy(reorderable = false)))
    }

    @Test
    fun `actions retain stable provider identity`() {
        assertEquals(
            "provider-id",
            AiProviderListScreenAction.ProviderClicked("provider-id").providerId
        )
        assertEquals(
            listOf("provider-id", "second-id"),
            AiProviderListScreenAction.ReorderCommitted(
                listOf("provider-id", "second-id")
            ).orderedProviderIds
        )
        assertEquals(
            "provider-id",
            AiProviderListScreenAction.DeleteRequested("provider-id").providerId
        )
    }

    @Test
    fun `provider search matches display name only`() {
        assertTrue(matchesAiProviderName(name = "OpenAI", query = "open"))
        assertTrue(matchesAiProviderName(name = "DeepSeek", query = "SEEK"))
        assertFalse(matchesAiProviderName(name = "DeepSeek", query = "openai"))
    }
}
