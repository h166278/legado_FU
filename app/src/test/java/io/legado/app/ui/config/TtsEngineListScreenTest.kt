package io.legado.app.ui.config

import io.legado.app.ui.design.components.NgManagementTrailing
import io.legado.app.ui.design.components.NgStatusTagSpec
import io.legado.app.ui.design.components.NgStatusTagVariant
import org.junit.Assert.assertEquals
import org.junit.Test

class TtsEngineListScreenTest {

    @Test
    fun `newer snapshot token invalidates an older refresh result`() {
        val gate = TtsEngineSnapshotGate()
        val firstRefresh = gate.begin()
        val secondRefresh = gate.begin()

        assertEquals(false, gate.isCurrent(firstRefresh))
        assertEquals(true, gate.isCurrent(secondRefresh))
    }

    @Test
    fun `local mutation invalidates pending refresh result`() {
        val gate = TtsEngineSnapshotGate()
        val pendingRefresh = gate.begin()

        gate.invalidate()

        assertEquals(false, gate.isCurrent(pendingRefresh))
    }

    @Test
    fun `enabled engine uses success status and keeps management metadata order`() {
        val item = TtsEngineListItemUiModel(
            id = "mossland",
            name = "Mossland",
            enabled = true,
            engineTypeText = "脚本",
            voiceCountText = "238 个发音人",
            reorderable = true,
            deletable = true
        )

        assertEquals(
            listOf(
                NgStatusTagVariant.SUCCESS,
                NgStatusTagVariant.INFO,
                NgStatusTagVariant.INFO
            ),
            item.statusTags("已启用", "已禁用").map(NgStatusTagSpec::variant)
        )
        assertEquals("脚本", item.statusTags("已启用", "已禁用")[1].text)
        assertEquals("238 个发音人", item.statusTags("已启用", "已禁用")[2].text)
        assertEquals(NgManagementTrailing.DRAG, item.trailing())
    }

    @Test
    fun `disabled system engine remains reorderable but cannot be deleted`() {
        val item = TtsEngineListItemUiModel(
            id = "system",
            name = "系统默认 TTS",
            enabled = false,
            engineTypeText = "系统",
            voiceCountText = "默认发音人",
            reorderable = true,
            deletable = false
        )

        assertEquals(
            NgStatusTagVariant.WARNING,
            item.statusTags("已启用", "已禁用").first().variant
        )
        assertEquals(NgManagementTrailing.DRAG, item.trailing())
        assertEquals(false, item.deletable)
    }

    @Test
    fun `search results never expose reorder`() {
        val item = TtsEngineListItemUiModel(
            id = "mossland",
            name = "Mossland",
            enabled = true,
            engineTypeText = "脚本",
            voiceCountText = "238 个发音人",
            reorderable = true,
            deletable = true
        )

        assertEquals(true, TtsEngineListScreenState().canRequestReorder(item))
        assertEquals(false, TtsEngineListScreenState(query = "moss").canRequestReorder(item))
    }
}
