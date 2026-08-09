package io.legado.app.ui.main.explore

internal enum class ExploreLayoutMode(val value: Int) {
    LIST(0),
    GRID(1),
    GROUP_GRID(2);

    companion object {
        fun from(value: Int): ExploreLayoutMode {
            return entries.firstOrNull { it.value == value } ?: GRID
        }
    }
}
