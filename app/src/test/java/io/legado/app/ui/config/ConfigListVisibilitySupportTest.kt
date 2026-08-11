package io.legado.app.ui.config

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigListVisibilitySupportTest {

    private data class Item(val id: String, val enabled: Boolean)

    @Test
    fun `hides disabled items until explicitly requested`() {
        val items = listOf(
            Item("enabled", true),
            Item("disabled", false)
        )

        assertEquals(
            listOf("enabled"),
            ConfigListVisibilitySupport.visibleItems(items, false, Item::enabled).map(Item::id)
        )
        assertEquals(
            listOf("enabled", "disabled"),
            ConfigListVisibilitySupport.visibleItems(items, true, Item::enabled).map(Item::id)
        )
    }

    @Test
    fun `reorders visible items without removing hidden disabled items`() {
        val enabledFirst = Item("enabled-first", true)
        val disabledFirst = Item("disabled-first", false)
        val enabledSecond = Item("enabled-second", true)
        val disabledSecond = Item("disabled-second", false)
        val allItems = listOf(enabledFirst, disabledFirst, enabledSecond, disabledSecond)

        val merged = ConfigListVisibilitySupport.mergeVisibleOrder(
            allItems = allItems,
            reorderedVisibleItems = listOf(enabledSecond, enabledFirst),
            showDisabled = false,
            isEnabled = Item::enabled
        )

        assertEquals(
            listOf("enabled-second", "disabled-first", "enabled-first", "disabled-second"),
            merged.map(Item::id)
        )
    }

    @Test
    fun `keeps original list when visible reorder is incomplete`() {
        val allItems = listOf(
            Item("enabled-first", true),
            Item("disabled", false),
            Item("enabled-second", true)
        )

        val merged = ConfigListVisibilitySupport.mergeVisibleOrder(
            allItems = allItems,
            reorderedVisibleItems = listOf(allItems.first()),
            showDisabled = false,
            isEnabled = Item::enabled
        )

        assertEquals(allItems, merged)
    }
}
