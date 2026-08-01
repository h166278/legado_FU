package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.legado.app.ui.design.components.NgSurfaceVariant
import io.legado.app.ui.design.theme.NgTheme

@Composable
fun NgSurface(
    modifier: Modifier = Modifier,
    variant: NgSurfaceVariant = NgSurfaceVariant.CARD,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val color = when (variant) {
        NgSurfaceVariant.CANVAS -> NgTheme.colors.background
        NgSurfaceVariant.CARD -> NgTheme.colors.cardContainer
        NgSurfaceVariant.PANEL -> NgTheme.colors.surfaceContainerHigh
        NgSurfaceVariant.OVERLAY -> NgTheme.colors.dialogContainer
    }
    val shape = when (variant) {
        NgSurfaceVariant.CANVAS -> NgTheme.shapes.smallDp
        NgSurfaceVariant.CARD -> NgTheme.shapes.mediumDp
        NgSurfaceVariant.PANEL -> NgTheme.shapes.largeDp
        NgSurfaceVariant.OVERLAY -> NgTheme.shapes.extraLargeDp
    }
    val elevation = when (variant) {
        NgSurfaceVariant.CANVAS,
        NgSurfaceVariant.CARD,
        NgSurfaceVariant.PANEL -> NgTheme.effects.cardElevationDp
        NgSurfaceVariant.OVERLAY -> NgTheme.effects.overlayElevationDp
    }
    val alpha = when (variant) {
        NgSurfaceVariant.CANVAS -> 1f
        NgSurfaceVariant.CARD -> NgTheme.effects.containerAlpha
        NgSurfaceVariant.PANEL -> (NgTheme.effects.containerAlpha + 0.14f).coerceAtMost(1f)
        NgSurfaceVariant.OVERLAY -> NgTheme.effects.dialogAlpha
    }.takeIf { !NgTheme.snapshot.isEInk } ?: 1f
    val border = if (variant == NgSurfaceVariant.CANVAS) {
        null
    } else {
        BorderStroke(
            1.dp,
            Color(
                if (NgTheme.snapshot.isEInk) {
                    NgTheme.colors.outline
                } else {
                    NgTheme.colors.outlineVariant
                }
            ).copy(
                alpha = when {
                    NgTheme.snapshot.isEInk -> 1f
                    variant == NgSurfaceVariant.OVERLAY -> 0.35f
                    else -> 0.25f
                }
            )
        )
    }

    Surface(
        modifier = modifier,
        color = Color(color).copy(alpha = alpha),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(shape.dp),
        shadowElevation = elevation.dp,
        border = border
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun NgCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    NgSurface(
        modifier = modifier,
        variant = NgSurfaceVariant.CARD,
        contentPadding = contentPadding,
        content = content
    )
}
