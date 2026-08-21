package io.legado.app.ui.book.read.page.provider

/** MD3 排版高亮规则命中后，附着在文字列上的运行时样式。 */
data class ReadCharStyle(
    val textColor: Int? = null,
    val textColorNight: Int? = null,
    val bgColor: Int? = null,
    val bgColorNight: Int? = null,
    val underlineMode: Int = 0,
    val underlineColor: Int? = null,
    val underlineColorNight: Int? = null,
    val underlineWidth: Float = 1f,
    val underlineOffset: Float = 2f,
    val underlineSvgPath: String = "",
    val bgImage: String = "",
    val bgImageFit: Int = 0,
    val bgImageScale: Float = 1f,
    val fontPath: String = "",
    val fontWeight: Int = 400,
    val isItalic: Boolean = false,
    val npLeft: Float = 0.1f,
    val npRight: Float = 0.1f,
    val npTop: Float = 0.1f,
    val npBottom: Float = 0.1f,
) {

    fun resolveTextColor(isNight: Boolean): Int? =
        if (isNight) textColorNight ?: textColor else textColor

    fun resolveBackgroundColor(isNight: Boolean): Int? =
        if (isNight) bgColorNight ?: bgColor else bgColor

    fun resolveUnderlineColor(isNight: Boolean): Int? =
        if (isNight) underlineColorNight ?: underlineColor else underlineColor
}
