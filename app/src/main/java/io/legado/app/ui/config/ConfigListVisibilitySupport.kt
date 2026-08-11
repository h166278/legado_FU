package io.legado.app.ui.config

internal object ConfigListVisibilitySupport {

    fun <T> visibleItems(
        allItems: List<T>,
        showDisabled: Boolean,
        isEnabled: (T) -> Boolean
    ): List<T> {
        return if (showDisabled) allItems else allItems.filter(isEnabled)
    }

    fun <T> mergeVisibleOrder(
        allItems: List<T>,
        reorderedVisibleItems: List<T>,
        showDisabled: Boolean,
        isEnabled: (T) -> Boolean
    ): List<T> {
        if (showDisabled) {
            return reorderedVisibleItems.takeIf { it.size == allItems.size } ?: allItems
        }
        val visibleCount = allItems.count(isEnabled)
        if (visibleCount != reorderedVisibleItems.size) {
            return allItems
        }
        val reordered = reorderedVisibleItems.iterator()
        return allItems.map { item ->
            if (isEnabled(item)) reordered.next() else item
        }
    }
}
