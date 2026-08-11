package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.theme.NgTheme

data class NgFormSelectOption(
    val label: String,
    val value: String
)

/**
 * NG 紧凑表单字段。
 *
 * 标签、34dp 输入框、焦点描边和文字层级与当前 View 表单保持一致，
 * 业务页面只提供字段含义和值，不再自行拼装标签和输入框。
 */
@Composable
fun NgFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onFocusLost: () -> Unit = {},
    trailingContent: (@Composable () -> Unit)? = null
) {
    val colors = NgTheme.colors
    val shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    var wasFocused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused) {
            wasFocused = true
        } else if (wasFocused) {
            wasFocused = false
            onFocusLost()
        }
    }
    val borderColor = when {
        isError -> Color(colors.error)
        focused -> Color(colors.primary)
        else -> Color(colors.outline)
    }
    val contentAlpha = if (enabled) 1f else 0.45f

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(start = 12.dp),
            color = Color(colors.onSurfaceVariant).copy(alpha = contentAlpha),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(34.dp),
            enabled = enabled,
            readOnly = readOnly,
            singleLine = true,
            textStyle = TextStyle(
                color = Color(colors.onSurface).copy(alpha = contentAlpha),
                fontSize = 13.sp,
                lineHeight = 16.sp
            ),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            cursorBrush = SolidColor(Color(colors.primary)),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(shape)
                        .background(
                            Color(
                                if (enabled) colors.inputContainer
                                else colors.surfaceContainerLow
                            )
                        )
                        .border(
                            width = if (focused || isError) 1.5.dp else 1.dp,
                            color = borderColor.copy(alpha = contentAlpha),
                            shape = shape
                        )
                        .padding(start = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty() && !placeholder.isNullOrBlank()) {
                            Text(
                                text = placeholder,
                                color = Color(colors.onSurfaceVariant).copy(alpha = 0.75f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                    if (trailingContent != null) {
                        trailingContent()
                    } else {
                        Box(Modifier.size(10.dp))
                    }
                }
            }
        )
        if (!supportingText.isNullOrBlank()) {
            Text(
                text = supportingText,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 12.dp),
                color = Color(if (isError) colors.error else colors.onSurfaceVariant),
                fontSize = 12.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun NgPasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hiddenIcon: Painter,
    visibleIcon: Painter,
    showPasswordDescription: String,
    hidePasswordDescription: String,
    modifier: Modifier = Modifier,
    visibilityResetKey: Any? = null,
    enabled: Boolean = true,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onFocusLost: () -> Unit = {}
) {
    var passwordVisible by rememberSaveable(visibilityResetKey) { mutableStateOf(false) }
    val colors = NgTheme.colors
    NgFormField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        keyboardActions = keyboardActions,
        onFocusLost = onFocusLost,
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingContent = {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clickable(enabled = enabled) {
                        passwordVisible = !passwordVisible
                    }
                    .padding(7.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = if (passwordVisible) visibleIcon else hiddenIcon,
                    contentDescription = if (passwordVisible) {
                        hidePasswordDescription
                    } else {
                        showPasswordDescription
                    },
                    tint = Color(colors.onSurfaceVariant)
                )
            }
        }
    )
}

/**
 * NG 紧凑选择字段。
 *
 * 保持与文本字段相同的 34dp 几何，界面显示 label，回调只返回稳定 value。
 */
@Composable
fun NgFormSelectField(
    label: String,
    selectedValue: String,
    options: List<NgFormSelectOption>,
    onValueChange: (String) -> Unit,
    arrowIcon: Painter,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = NgTheme.colors
    val shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp)
    val density = LocalDensity.current
    var expanded by remember { mutableStateOf(false) }
    var fieldWidthPx by remember { mutableStateOf(0) }
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label
        ?: selectedValue
    val contentAlpha = if (enabled) 1f else 0.45f

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.padding(start = 12.dp),
            color = Color(colors.onSurfaceVariant).copy(alpha = contentAlpha),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .onGloballyPositioned { fieldWidthPx = it.size.width }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(shape)
                    .background(
                        Color(
                            if (enabled) colors.inputContainer
                            else colors.surfaceContainerLow
                        )
                    )
                    .border(1.dp, Color(colors.outline), shape)
                    .clickable(enabled = enabled && options.isNotEmpty()) {
                        expanded = true
                    }
                    .semantics {
                        role = Role.Button
                        contentDescription = label
                        stateDescription = selectedLabel
                    }
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedLabel,
                    modifier = Modifier.weight(1f),
                    color = Color(colors.onSurface).copy(alpha = contentAlpha),
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    painter = arrowIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(colors.onSurfaceVariant).copy(alpha = contentAlpha)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(
                    with(density) { fieldWidthPx.toDp() }
                ),
                shape = shape,
                containerColor = Color(colors.surface),
                tonalElevation = 0.dp,
                shadowElevation = 4.dp
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        modifier = Modifier
                            .height(44.dp)
                            .semantics {
                                selected = option.value == selectedValue
                            },
                        text = {
                            Text(
                                text = option.label,
                                color = Color(colors.onSurface),
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            expanded = false
                            onValueChange(option.value)
                        },
                        contentPadding = PaddingValues(horizontal = 18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NgFormSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 17.sp,
            lineHeight = 21.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        NgSwitchControl(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * 表单操作区跟随内容自然排列，不将按钮固定到页面底部。
 */
@Composable
fun NgFormActionGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
fun NgFormActionRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun NgFormActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: NgButtonVariant = NgButtonVariant.OUTLINE
) {
    val colors = NgTheme.colors
    val primary = Color(colors.primary)
    val shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp)
    val containerColor = when (variant) {
        NgButtonVariant.PRIMARY -> primary
        NgButtonVariant.TONAL -> Color(colors.selectedContainer)
        NgButtonVariant.DANGER -> Color(colors.error)
        NgButtonVariant.ON_IMAGE -> Color.Black.copy(alpha = 0.56f)
        NgButtonVariant.OUTLINE -> Color(colors.surface)
    }
    val contentColor = when (variant) {
        NgButtonVariant.PRIMARY -> Color(colors.onPrimary)
        NgButtonVariant.TONAL -> Color(colors.onSurface)
        NgButtonVariant.DANGER -> Color(colors.onError)
        NgButtonVariant.ON_IMAGE -> Color.White
        NgButtonVariant.OUTLINE -> primary
    }
    Button(
        onClick = onClick,
        modifier = modifier
            .height(36.dp)
            .widthIn(min = 76.dp),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.45f),
            disabledContentColor = contentColor.copy(alpha = 0.55f)
        ),
        border = if (variant == NgButtonVariant.OUTLINE) {
            BorderStroke(1.dp, primary)
        } else {
            null
        },
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
