package io.legado.app.help.config

/** 书架首页的顶层内容形态。 */
enum class BookshelfHomeMode(val value: Int) {
    BOOKS(0),
    GROUP_GRID(1);

    companion object {
        fun fromValue(value: Int): BookshelfHomeMode {
            return entries.firstOrNull { it.value == value } ?: BOOKS
        }
    }
}

/**
 * 书架可选择的四种布局。每种布局拥有独立配置，切换布局时不会覆盖其它布局的设置。
 */
enum class BookshelfLayoutMode(
    val value: Int,
    internal val preferenceName: String,
) {
    LIST(0, "list"),
    COMPACT(1, "compact"),
    GRID(2, "grid"),
    GROUP_GRID(3, "group_grid");

    companion object {
        fun fromBooksLayoutValue(value: Int): BookshelfLayoutMode {
            return when (value) {
                1 -> COMPACT
                in 2..Int.MAX_VALUE -> GRID
                else -> LIST
            }
        }
    }
}

data class BookshelfLayoutProfile(
    val columns: Int,
    val innerColumns: Int,
    val showBookName: Int,
    val coverRadius: Int,
    val spacing: Int,
    val showUnread: Boolean,
    val showLastUpdateTime: Boolean,
    val sort: Int,
) {

    fun normalized(mode: BookshelfLayoutMode): BookshelfLayoutProfile {
        return copy(
            columns = if (mode == BookshelfLayoutMode.GROUP_GRID) {
                columns.coerceIn(2, 4)
            } else {
                columns.coerceIn(2, 6)
            },
            innerColumns = innerColumns.coerceIn(2, 6),
            showBookName = showBookName.coerceIn(0, 2),
            coverRadius = coverRadius.coerceIn(MIN_COVER_RADIUS, MAX_COVER_RADIUS),
            spacing = spacing.coerceIn(0, 60),
            sort = sort.coerceIn(0, 5),
        )
    }

    companion object {
        fun default(mode: BookshelfLayoutMode): BookshelfLayoutProfile {
            return BookshelfLayoutProfile(
                columns = if (mode == BookshelfLayoutMode.GROUP_GRID) 4 else 3,
                innerColumns = 4,
                showBookName = 0,
                coverRadius = DEFAULT_COVER_RADIUS,
                spacing = 12,
                showUnread = true,
                showLastUpdateTime = false,
                sort = 0,
            )
        }

        const val MIN_COVER_RADIUS = 0
        const val MAX_COVER_RADIUS = 12
        const val DEFAULT_COVER_RADIUS = 6
    }
}

internal object BookshelfLayoutProfilePreferences {
    private val fields = listOf(
        "columns",
        "inner_columns",
        "show_book_name",
        "cover_radius",
        "spacing",
        "show_unread",
        "show_last_update_time",
        "sort",
    )

    val keys: Set<String> = BookshelfLayoutMode.entries
        .flatMap { mode -> fields.map { field -> key(mode, field) } }
        .toSet()

    fun columns(mode: BookshelfLayoutMode) = key(mode, "columns")
    fun innerColumns(mode: BookshelfLayoutMode) = key(mode, "inner_columns")
    fun showBookName(mode: BookshelfLayoutMode) = key(mode, "show_book_name")
    fun coverRadius(mode: BookshelfLayoutMode) = key(mode, "cover_radius")
    fun spacing(mode: BookshelfLayoutMode) = key(mode, "spacing")
    fun showUnread(mode: BookshelfLayoutMode) = key(mode, "show_unread")
    fun showLastUpdateTime(mode: BookshelfLayoutMode) = key(mode, "show_last_update_time")
    fun sort(mode: BookshelfLayoutMode) = key(mode, "sort")

    private fun key(mode: BookshelfLayoutMode, field: String): String {
        return "bookshelf_layout_${mode.preferenceName}_$field"
    }
}
