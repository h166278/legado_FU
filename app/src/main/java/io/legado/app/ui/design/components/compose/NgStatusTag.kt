package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.NgStatusTagSpec
import io.legado.app.ui.design.components.NgStatusTagStyle
import io.legado.app.ui.design.components.NgStatusTagVariant

/** 与 View 版 NgStatusTagView 共用尺寸和语义的 Compose 状态标签。 */
@Composable
fun NgStatusTag(
    spec: NgStatusTagSpec,
    modifier: Modifier = Modifier
) {
    NgStatusTag(
        text = spec.text.toString(),
        variant = spec.variant,
        style = spec.style,
        modifier = modifier
    )
}

@Composable
fun NgStatusTag(
    text: String,
    variant: NgStatusTagVariant,
    modifier: Modifier = Modifier,
    style: NgStatusTagStyle = NgStatusTagStyle.REGULAR
) {
    val metrics = when (style) {
        NgStatusTagStyle.REGULAR -> TagMetrics(
            minWidth = 44,
            height = 24,
            horizontalPadding = 10,
            cornerRadius = 12,
            textSize = 12,
            lineHeight = 15
        )

        NgStatusTagStyle.COMPACT -> TagMetrics(
            minWidth = 42,
            height = 20,
            horizontalPadding = 8,
            cornerRadius = 7,
            textSize = 11,
            lineHeight = 13
        )
    }
    val (containerColor, contentColor) = tagColors(variant)
    Box(
        modifier = modifier
            .height(metrics.height.dp)
            .defaultMinSize(minWidth = metrics.minWidth.dp)
            .clip(RoundedCornerShape(metrics.cornerRadius.dp))
            .background(containerColor)
            .padding(horizontal = metrics.horizontalPadding.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = metrics.textSize.sp,
            lineHeight = metrics.lineHeight.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun tagColors(variant: NgStatusTagVariant): Pair<Color, Color> {
    return when (variant) {
        NgStatusTagVariant.INFO ->
            colorResource(R.color.ng_info_container) to colorResource(R.color.ng_info)
        NgStatusTagVariant.SUCCESS ->
            colorResource(R.color.ng_success_container) to colorResource(R.color.ng_success)
        NgStatusTagVariant.WARNING ->
            colorResource(R.color.ng_warning_container) to colorResource(R.color.ng_warning)
        NgStatusTagVariant.ERROR ->
            colorResource(R.color.ng_error_container) to colorResource(R.color.ng_error)
        NgStatusTagVariant.NEUTRAL ->
            colorResource(R.color.ng_neutral_container) to
                colorResource(R.color.ng_on_surface_variant)
    }
}

private data class TagMetrics(
    val minWidth: Int,
    val height: Int,
    val horizontalPadding: Int,
    val cornerRadius: Int,
    val textSize: Int,
    val lineHeight: Int
)
