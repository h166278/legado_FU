package io.legado.app.ui.design.components.compose

import androidx.appcompat.widget.SwitchCompat
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.applyTint

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
    enabled: Boolean = true
) {
    val primary = NgTheme.colors.primary
    val isDark = NgTheme.snapshot.isDark
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SwitchCompat(context).apply {
                showText = false
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
