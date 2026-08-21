package io.legado.app.ui.design.components

enum class NgSurfaceVariant {
    CANVAS,
    CARD,
    PANEL,
    OVERLAY
}

enum class NgButtonVariant {
    PRIMARY,
    PRIMARY_LIGHT_CONTENT,
    TONAL,
    OUTLINE,
    DANGER,
    ON_IMAGE
}

enum class NgButtonShapeVariant {
    PILL,
    ROUNDED,
}

enum class NgSettingsTrailing {
    NONE,
    CHEVRON,
    SWITCH,
    VALUE,
    CUSTOM
}

enum class NgDialogVariant {
    STANDARD,
    CONFIRMATION,
    COMPACT_CONFIRMATION,
    EDITOR,
    LONG_CONTENT
}

enum class NgStatusTagVariant {
    PRIMARY,
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
    NEUTRAL
}

enum class NgStatusTagStyle {
    REGULAR,
    COMPACT,
    INLINE
}

enum class NgManagementTrailing {
    NONE,
    DRAG,
    MORE
}

enum class NgManagementListCardVariant {
    DEFAULT,
    COMPACT_GRID
}

enum class NgFilterChipGroupVariant {
    WRAP,
    TWO_ROW_RAIL
}

data class NgStatusTagSpec(
    val text: CharSequence,
    val variant: NgStatusTagVariant,
    val style: NgStatusTagStyle = NgStatusTagStyle.REGULAR
)
