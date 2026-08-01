package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.theme.NgTheme

@Composable
fun NgButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: NgButtonVariant = NgButtonVariant.PRIMARY,
    content: @Composable RowScope.() -> Unit
) {
    val colors = NgTheme.colors
    when (variant) {
        NgButtonVariant.PRIMARY -> Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(colors.primary),
                contentColor = Color(colors.onPrimary)
            ),
            content = content
        )

        NgButtonVariant.TONAL -> FilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = Color(colors.selectedContainer),
                contentColor = Color(colors.onSurface)
            ),
            content = content
        )

        NgButtonVariant.OUTLINE -> OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(colors.primary)
            ),
            content = content
        )

        NgButtonVariant.DANGER -> Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(colors.error),
                contentColor = Color(colors.onError)
            ),
            content = content
        )

        NgButtonVariant.ON_IMAGE -> Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black.copy(alpha = 0.56f),
                contentColor = Color.White
            ),
            content = content
        )
    }
}

@Composable
fun NgIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        content = content
    )
}
