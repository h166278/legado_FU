package io.legado.app.help.source

import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.entities.rule.ExploreKind.Type

/**
 * The role used by the production Explore renderer.
 *
 * Keep this as the single source of truth for list rendering, detail rendering
 * and debug/audit exports. Layout width and labels must not change an item's
 * interaction semantics.
 */
internal enum class ExploreKindRenderRole(val wireName: String) {
    CATEGORY("category"),
    BUTTON("button"),
    TEXT_INPUT("text_input"),
    TOGGLE("toggle"),
    SELECT("select"),
    ERROR("error"),
    PASSIVE("passive"),
    UNSUPPORTED("unsupported")
}

internal fun ExploreKind.renderRole(): ExploreKindRenderRole {
    return when {
        type == Type.url && title.startsWith("ERROR:") -> ExploreKindRenderRole.ERROR
        type == Type.url && !url.isNullOrBlank() -> ExploreKindRenderRole.CATEGORY
        type == Type.button && !action.isNullOrBlank() -> ExploreKindRenderRole.BUTTON
        type == Type.text -> ExploreKindRenderRole.TEXT_INPUT
        type == Type.toggle -> ExploreKindRenderRole.TOGGLE
        type == Type.select -> ExploreKindRenderRole.SELECT
        type == Type.url || type == Type.button -> ExploreKindRenderRole.PASSIVE
        else -> ExploreKindRenderRole.UNSUPPORTED
    }
}

internal fun ExploreKind.isSupportedExploreKind(): Boolean {
    return renderRole() != ExploreKindRenderRole.UNSUPPORTED
}

internal fun ExploreKind.isOpenableExploreCategory(): Boolean {
    return renderRole() == ExploreKindRenderRole.CATEGORY
}

internal fun ExploreKind.isInteractiveExploreControl(): Boolean {
    return when (renderRole()) {
        ExploreKindRenderRole.BUTTON,
        ExploreKindRenderRole.TEXT_INPUT,
        ExploreKindRenderRole.TOGGLE,
        ExploreKindRenderRole.SELECT -> true

        else -> false
    }
}
