package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.theme.NgTheme

/**
 * Reading NG 底部操作栏按钮。
 *
 * 几何与图标文字布局对齐已经验收的 BookInfoActionButton；页面只选择语义 Variant，
 * 不再自行组合纯色大胶囊或临时透明度。
 */
@Composable
fun NgActionBarButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: NgButtonVariant = NgButtonVariant.OUTLINE
) {
    val colors = NgTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val background = Color.White.copy(alpha = 0.82f)
    val buttonModifier = modifier.height(42.dp)
    val contentPadding = PaddingValues(horizontal = 14.dp)

    val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            maxLines = 1
        )
    }

    when (variant) {
        NgButtonVariant.PRIMARY -> Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(colors.primary),
                contentColor = Color(colors.onPrimary)
            ),
            contentPadding = contentPadding,
            content = content
        )

        NgButtonVariant.DANGER -> NgOutlinedActionBarButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            background = background,
            accent = Color(colors.error),
            contentPadding = contentPadding,
            content = content
        )

        else -> NgOutlinedActionBarButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            background = background,
            accent = Color(colors.primary),
            contentPadding = contentPadding,
            content = content
        )
    }
}

@Composable
private fun NgOutlinedActionBarButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: RoundedCornerShape,
    background: Color,
    accent: Color,
    contentPadding: PaddingValues,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        border = BorderStroke(
            width = 1.dp,
            color = accent.copy(alpha = if (enabled) 1f else 0.38f)
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = background,
            contentColor = accent,
            disabledContainerColor = background.copy(alpha = 0.45f),
            disabledContentColor = accent.copy(alpha = 0.38f)
        ),
        contentPadding = contentPadding,
        content = content
    )
}
