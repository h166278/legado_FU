package io.legado.app.ui.book.explore

internal enum class ExploreShowLayoutMode(val value: Int) {
    LIST(0),
    GRID(1);

    companion object {
        fun from(value: Int): ExploreShowLayoutMode {
            return entries.firstOrNull { it.value == value } ?: LIST
        }
    }
}
