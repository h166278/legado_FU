package io.legado.app.help.config

enum class BookshelfTopBarStyle(val value: Int) {
    TRADITIONAL(0),
    FLOATING_DOCK(1);

    companion object {
        fun fromValue(value: Int): BookshelfTopBarStyle {
            return entries.firstOrNull { it.value == value } ?: TRADITIONAL
        }
    }
}
