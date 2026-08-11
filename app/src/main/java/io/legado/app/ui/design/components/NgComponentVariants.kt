package io.legado.app.ui.design.components

enum class NgSurfaceVariant {
    CANVAS,
    CARD,
    PANEL,
    OVERLAY
}

enum class NgButtonVariant {
    PRIMARY,
    TONAL,
    OUTLINE,
    DANGER,
    ON_IMAGE
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
    EDITOR,
    LONG_CONTENT
}

enum class NgStatusTagVariant {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
    NEUTRAL
}

enum class NgStatusTagStyle {
    REGULAR,
    COMPACT
}

enum class NgManagementTrailing {
    NONE,
    DRAG,
    MORE
}

data class NgStatusTagSpec(
    val text: CharSequence,
    val variant: NgStatusTagVariant,
    val style: NgStatusTagStyle = NgStatusTagStyle.REGULAR
)
