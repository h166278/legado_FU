package io.legado.app.ui.book.explore

internal fun sanitizeExploreDetailLabel(value: String): String {
    val result = StringBuilder(value.length)
    var index = 0

    while (index < value.length) {
        val codePoint = Character.codePointAt(value, index)
        index += Character.charCount(codePoint)
        if (codePoint.isExploreDetailLabelCharacter()) {
            result.appendCodePoint(codePoint)
        }
    }

    return result.toString()
}

private fun Int.isExploreDetailLabelCharacter(): Boolean {
    return this in 'A'.code..'Z'.code ||
            this in 'a'.code..'z'.code ||
            Character.isDigit(this) ||
            this == 0x3007 ||
            this in 0x3400..0x4DBF ||
            this in 0x4E00..0x9FFF ||
            this in 0xF900..0xFAFF ||
            this in 0x20000..0x2FA1F ||
            this in 0x30000..0x323AF
}
