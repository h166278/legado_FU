package io.legado.app.ui.design.components.compose

import androidx.appcompat.widget.SwitchCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.applyTint

@Composable
fun NgSettingsSectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, bottom = 8.dp),
        color = Color(NgTheme.colors.primary),
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun NgSettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

@Composable
fun NgSettingsIcon(
    painter: Painter,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colorResource(R.color.ng_settings_icon_bg))
            .padding(7.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            tint = Color(NgTheme.colors.primary)
        )
    }
}

@Composable
fun NgSettingsItem(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    trailing: NgSettingsTrailing = NgSettingsTrailing.CHEVRON,
    checked: Boolean = false,
    value: String? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    customTrailing: (@Composable RowScope.() -> Unit)? = null
) {
    val itemShape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(itemShape)
            .background(colorResource(R.color.ng_settings_item))
            .border(0.6.dp, colorResource(R.color.ng_settings_item_stroke), itemShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, top = 10.dp, end = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                color = Color(NgTheme.colors.onSurface)
                    .copy(alpha = if (enabled) 1f else 0.45f),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = NgTheme.typography.itemTitleSp.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Normal
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    color = Color(NgTheme.colors.onSurfaceVariant)
                        .copy(alpha = if (enabled) 1f else 0.45f),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = NgTheme.typography.summarySp.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        when (trailing) {
            NgSettingsTrailing.NONE -> Unit
            NgSettingsTrailing.CHEVRON -> Text(
                text = "›",
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 30.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1
            )
            NgSettingsTrailing.SWITCH -> NgSettingsSwitch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange
            )
            NgSettingsTrailing.VALUE -> Text(
                text = value.orEmpty(),
                color = Color(NgTheme.colors.onSurfaceVariant),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = NgTheme.typography.bodySp.sp,
                    fontWeight = FontWeight.Normal
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            NgSettingsTrailing.CUSTOM -> customTrailing?.invoke(this)
        }
    }
}

@Composable
private fun NgSettingsSwitch(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?
) {
    val primary = NgTheme.colors.primary
    val isDark = NgTheme.snapshot.isDark
    AndroidView(
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
