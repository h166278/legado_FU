package io.legado.app.ui.book.read.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun ReadOfflineCacheDialogContent(
    title: String,
    startLabel: String,
    endLabel: String,
    start: String,
    end: String,
    cancelLabel: String,
    confirmLabel: String,
    onStartChanged: (String) -> Unit,
    onEndChanged: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    ReadConfigDialogSurface(
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 18.dp,
            end = 20.dp,
            bottom = 16.dp,
        ),
    ) {
        ReadConfigDialogTitle(title)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReadDialogTextField(
                value = start,
                onValueChange = { value -> onStartChanged(value.filter(Char::isDigit).take(5)) },
                modifier = Modifier.weight(1f),
                label = startLabel,
                keyboardType = KeyboardType.Number,
            )
            ReadDialogTextField(
                value = end,
                onValueChange = { value -> onEndChanged(value.filter(Char::isDigit).take(5)) },
                modifier = Modifier.weight(1f),
                label = endLabel,
                keyboardType = KeyboardType.Number,
            )
        }
        ReadDialogActions(
            cancelLabel = cancelLabel,
            confirmLabel = confirmLabel,
            onCancel = onCancel,
            onConfirm = onConfirm,
        )
    }
}

@Composable
internal fun ReadCharsetDialogContent(
    title: String,
    value: String,
    hint: String,
    options: List<String>,
    cancelLabel: String,
    confirmLabel: String,
    onValueChanged: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    ReadConfigDialogSurface(
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 18.dp,
            end = 20.dp,
            bottom = 16.dp,
        ),
    ) {
        ReadConfigDialogTitle(title)
        ReadDialogTextField(
            value = value,
            onValueChange = onValueChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            hint = hint,
        )
        val filteredOptions = options.filter {
            value.isBlank() || it.contains(value, ignoreCase = true)
        }.take(6)
        if (filteredOptions.isNotEmpty() && filteredOptions.none { it.equals(value, true) }) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 168.dp)
                    .padding(top = 5.dp),
            ) {
                items(filteredOptions) { option ->
                    Text(
                        text = option,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onValueChanged(option) }
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 14.sp,
                    )
                }
            }
        }
        ReadDialogActions(
            cancelLabel = cancelLabel,
            confirmLabel = confirmLabel,
            onCancel = onCancel,
            onConfirm = onConfirm,
        )
    }
}

@Composable
internal fun ReadSimulatedReadingDialogContent(
    title: String,
    enabledLabel: String,
    startDateLabel: String,
    startChapterLabel: String,
    dailyChaptersLabel: String,
    enabled: Boolean,
    startDate: String,
    startChapter: String,
    dailyChapters: String,
    cancelLabel: String,
    confirmLabel: String,
    onEnabledChanged: (Boolean) -> Unit,
    onStartDateClick: () -> Unit,
    onStartChapterChanged: (String) -> Unit,
    onDailyChaptersChanged: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    ReadConfigDialogSurface(
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 18.dp,
            end = 20.dp,
            bottom = 16.dp,
        ),
    ) {
        ReadConfigDialogTitle(title)
        val colors = NgTheme.colors
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(colors.surfaceContainerLow).copy(alpha = 0.46f))
                .padding(horizontal = 12.dp),
        ) {
            ReadConfigSwitchRow(
                title = enabledLabel,
                checked = enabled,
                onCheckedChange = onEnabledChanged,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.75.dp)
                    .background(Color(colors.outline).copy(alpha = 0.35f)),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clickable(role = Role.Button, onClick = onStartDateClick),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = startDateLabel,
                    color = Color(colors.onSurface),
                    fontSize = 14.sp,
                )
                Text(
                    text = startDate,
                    color = Color(colors.onSurfaceVariant),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReadDialogTextField(
                value = startChapter,
                onValueChange = {
                    onStartChapterChanged(it.filter(Char::isDigit).take(5))
                },
                modifier = Modifier.weight(1f),
                label = startChapterLabel,
                keyboardType = KeyboardType.Number,
            )
            ReadDialogTextField(
                value = dailyChapters,
                onValueChange = {
                    onDailyChaptersChanged(it.filter(Char::isDigit).take(5))
                },
                modifier = Modifier.weight(1f),
                label = dailyChaptersLabel,
                keyboardType = KeyboardType.Number,
            )
        }
        ReadDialogActions(
            cancelLabel = cancelLabel,
            confirmLabel = confirmLabel,
            onCancel = onCancel,
            onConfirm = onConfirm,
        )
    }
}

@Composable
private fun ReadDialogActions(
    cancelLabel: String,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NgFormActionButton(
            text = cancelLabel,
            onClick = onCancel,
            modifier = Modifier.weight(1f),
        )
        NgFormActionButton(
            text = confirmLabel,
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            variant = NgButtonVariant.PRIMARY,
        )
    }
}
