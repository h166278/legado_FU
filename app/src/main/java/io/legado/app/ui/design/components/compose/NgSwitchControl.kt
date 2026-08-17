package io.legado.app.ui.design.components.compose

import androidx.appcompat.widget.SwitchCompat
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.applyTint

enum class NgSwitchControlVariant {
    REGULAR,
    COMPACT,
}

/**
 * View 与 Compose 共用同一套 NG Switch 视觉。
 *
 * 当前先桥接已验收的 SwitchCompat，避免迁移表单时重新设计开关尺寸和配色。
 */
@Composable
fun NgSwitchControl(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: NgSwitchControlVariant = NgSwitchControlVariant.REGULAR,
) {
    val primary = NgTheme.colors.primary
    val isDark = NgTheme.snapshot.isDark
    AndroidView(
        modifier = if (variant == NgSwitchControlVariant.COMPACT) {
            modifier
                .width(42.dp)
                .height(28.dp)
                .scale(0.82f)
        } else {
            modifier
        },
        factory = { context ->
            SwitchCompat(context).apply {
                showText = false
                if (variant == NgSwitchControlVariant.COMPACT) {
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(0, 0, 0, 0)
                }
            }
        },
        update = { switch ->
            switch.setOnCheckedChangeListener(null)
            switch.isEnabled = enabled
            switch.isChecked = checked
            switch.applyTint(primary, isDark)
            switch.setOnCheckedChangeListener { _, isChecked ->
                onCheckedChange?.invoke(isChecked)
            }
        }
    )
}
