package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.design.theme.NgColorMath
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.NgThemeSnapshot
import io.legado.app.help.config.ReadFloatingAppearanceConfig
import io.legado.app.help.config.ReadFloatingColorStyle
import io.legado.app.ui.book.read.ReadFloatingPalette
import kotlin.math.max

/**
 * NG 的透明玻璃承载面。
 *
 * 组件只负责材质、裁切和内容承载，不负责决定抽屉结构。调用方若提供 [backdrop]，
 * 必须传入已经与当前界面对齐的背景内容，不能把整张主题图再次缩放到局部区域。
 */
@Composable
fun NgGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = NgGlassDefaults.shape(),
    style: NgGlassStyle = NgGlassDefaults.style(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    backdrop: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
        contentColor = style.contentColor,
        shadowElevation = style.shadowElevation
    ) {
        Box(
            modifier = Modifier.clip(shape),
            propagateMinConstraints = true
        ) {
            if (backdrop != null) {
                val backdropModifier = if (style.blurRadius > 0.dp) {
                    Modifier
                        .matchParentSize()
                        .blur(
                            radius = style.blurRadius,
                            edgeTreatment = BlurredEdgeTreatment.Unbounded
                        )
                } else {
                    Modifier.matchParentSize()
                }
                Box(
                    modifier = backdropModifier,
                    content = backdrop
                )
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .ngGlassLayer(shape, style)
            )

            Column(
                modifier = Modifier.padding(contentPadding),
                content = content
            )
        }
    }
}

@Immutable
data class NgGlassStyle(
    val containerTop: Color,
    val containerBottom: Color,
    val accentGlow: Color,
    val borderColor: Color,
    val edgeHighlight: Color,
    val surfaceGloss: Color,
    val depthEdge: Color,
    val contentColor: Color,
    val blurRadius: Dp,
    val shadowElevation: Dp,
    val borderWidth: Dp,
    val highlightWidth: Dp
)

object NgGlassDefaults {

    /**
     * 默认透明度使用主题的 drawerAlpha，对应阅读菜单的轻液态玻璃层级。
     * 密集内容以后可以显式传入 dialogAlpha，但不在组件里绑定具体业务结构。
     */
    @Composable
    fun style(
        containerAlpha: Float = NgTheme.effects.drawerAlpha
    ): NgGlassStyle {
        val snapshot = NgTheme.snapshot
        return remember(snapshot, containerAlpha) {
            resolveNgGlassStyle(snapshot, containerAlpha)
        }
    }

    /**
     * 阅读主菜单这类悬浮在高对比正文上的独立玻璃卡。
     *
     * 保留主题色雾化和液态边缘，直接透出承载面后方的实时内容。阅读菜单不提供截图
     * backdrop，也不对静态副本做模糊。
     */
    @Composable
    fun floatingStyle(
        transparencyPercent: Int? = null,
        primaryStrengthPercent: Int? = null,
        colorStyle: ReadFloatingColorStyle = ReadFloatingColorStyle.VIBRANT,
    ): NgGlassStyle {
        val snapshot = NgTheme.snapshot
        return remember(snapshot, transparencyPercent, primaryStrengthPercent, colorStyle) {
            resolveNgFloatingGlassStyle(
                snapshot = snapshot,
                transparencyPercent = transparencyPercent,
                primaryStrengthPercent = primaryStrengthPercent,
                colorStyle = colorStyle,
            )
        }
    }

    @Composable
    fun shape(): Shape = RoundedCornerShape(NgTheme.shapes.dialogDp.dp)
}

internal fun resolveNgGlassStyle(
    snapshot: NgThemeSnapshot,
    requestedContainerAlpha: Float = snapshot.effects.drawerAlpha
): NgGlassStyle {
    val colors = snapshot.colors
    val containerAlpha = if (snapshot.isEInk) {
        1f
    } else {
        requestedContainerAlpha.coerceIn(0f, 1f)
    }
    val containerTop = NgColorMath.blend(
        colors.drawerContainer,
        colors.surface,
        0.18f
    )
    val containerBottom = NgColorMath.blend(
        colors.drawerContainer,
        colors.surfaceTint,
        if (snapshot.isDark) 0.08f else 0.05f
    )
    val highlightBase = if (snapshot.isDark) {
        NgColorMath.blend(colors.surface, colors.onSurface, 0.38f)
    } else {
        NgColorMath.blend(colors.surface, 0xFFFFFFFF.toInt(), 0.72f)
    }
    val accentAlpha = when {
        snapshot.isEInk -> 0f
        snapshot.isDark -> 0.08f
        else -> 0.10f
    }

    return NgGlassStyle(
        containerTop = Color(
            NgColorMath.withAlpha(
                containerTop,
                (containerAlpha + 0.06f).coerceAtMost(1f)
            )
        ),
        containerBottom = Color(
            NgColorMath.withAlpha(containerBottom, containerAlpha)
        ),
        accentGlow = Color(
            NgColorMath.withAlpha(colors.surfaceTint, accentAlpha)
        ),
        borderColor = Color(
            NgColorMath.withAlpha(
                colors.outlineVariant,
                when {
                    snapshot.isEInk -> 1f
                    snapshot.isDark -> 0.26f
                    else -> 0.32f
                }
            )
        ),
        edgeHighlight = Color(
            NgColorMath.withAlpha(
                highlightBase,
                when {
                    snapshot.isEInk -> 0f
                    snapshot.isDark -> 0.28f
                    else -> 0.44f
                }
            )
        ),
        surfaceGloss = Color(
            NgColorMath.withAlpha(
                highlightBase,
                when {
                    snapshot.isEInk -> 0f
                    snapshot.isDark -> 0.10f
                    else -> 0.18f
                }
            )
        ),
        depthEdge = Color(
            NgColorMath.withAlpha(
                if (snapshot.isDark) 0xFF000000.toInt() else colors.onSurface,
                when {
                    snapshot.isEInk -> 0f
                    snapshot.isDark -> 0.18f
                    else -> 0.08f
                }
            )
        ),
        contentColor = Color(colors.onSurface),
        blurRadius = if (snapshot.effects.blurEnabled) {
            snapshot.effects.blurRadiusDp.dp
        } else {
            0.dp
        },
        shadowElevation = if (snapshot.isEInk) 0.dp else 2.dp,
        borderWidth = if (snapshot.isEInk) 1.dp else 0.6.dp,
        highlightWidth = if (snapshot.isEInk) 0.dp else 1.dp
    )
}

internal fun resolveNgFloatingGlassStyle(
    snapshot: NgThemeSnapshot,
    transparencyPercent: Int? = null,
    primaryStrengthPercent: Int? = null,
    colorStyle: ReadFloatingColorStyle = ReadFloatingColorStyle.VIBRANT,
): NgGlassStyle {
    if (snapshot.isEInk) {
        return resolveNgGlassStyle(snapshot, requestedContainerAlpha = 1f)
    }
    val colors = snapshot.colors
    val base = resolveNgGlassStyle(
        snapshot = snapshot,
        requestedContainerAlpha = if (snapshot.isDark) 0.84f else 0.86f
    )
    val normalizedPrimaryStrength = primaryStrengthPercent?.let(
        ReadFloatingAppearanceConfig::normalizePercent
    )
    val surfaceColors = ReadFloatingPalette.resolveSurfaceColors(
        snapshot = snapshot,
        primaryStrengthPercent = normalizedPrimaryStrength,
    )
    val semanticColors = ReadFloatingPalette.resolveSemanticColors(
        snapshot = snapshot,
        primaryStrengthPercent = normalizedPrimaryStrength,
        colorStyle = colorStyle,
    )
    val containerTop = surfaceColors.top
    val containerBottom = surfaceColors.bottom
    val highlightBase = if (snapshot.isDark) {
        NgColorMath.blend(colors.surface, colors.onSurface, 0.46f)
    } else {
        0xFFFFFFFF.toInt()
    }
    val defaultTopAlpha = if (snapshot.isDark) 0.84f else 0.80f
    val defaultBottomAlpha = if (snapshot.isDark) 0.80f else 0.76f
    val targetTopAlpha = transparencyPercent?.let {
        ReadFloatingAppearanceConfig.floatingSurfaceAlpha(it, defaultTopAlpha)
    } ?: defaultTopAlpha
    val targetBottomAlpha = transparencyPercent?.let {
        ReadFloatingAppearanceConfig.floatingSurfaceAlpha(it, defaultBottomAlpha)
    } ?: defaultBottomAlpha
    val defaultAccentAlpha = if (snapshot.isDark) 0.12f else 0.10f
    val accentColor = if (normalizedPrimaryStrength == null) colors.surfaceTint else containerBottom
    return base.copy(
        containerTop = Color(
            NgColorMath.withAlpha(
                containerTop,
                targetTopAlpha
            )
        ),
        containerBottom = Color(
            NgColorMath.withAlpha(
                containerBottom,
                targetBottomAlpha
            )
        ),
        accentGlow = Color(
            NgColorMath.withAlpha(
                accentColor,
                defaultAccentAlpha
            )
        ),
        borderColor = Color(
            NgColorMath.withAlpha(
                highlightBase,
                if (snapshot.isDark) 0.42f else 0.80f
            )
        ),
        edgeHighlight = Color(
            NgColorMath.withAlpha(
                highlightBase,
                if (snapshot.isDark) 0.62f else 0.98f
            )
        ),
        surfaceGloss = Color(
            NgColorMath.withAlpha(
                highlightBase,
                if (snapshot.isDark) 0.16f else 0.14f
            )
        ),
        depthEdge = Color(
            NgColorMath.withAlpha(
                semanticColors.outline,
                if (snapshot.isDark) 0.20f else 0.10f
            )
        ),
        contentColor = Color(semanticColors.content),
        blurRadius = 0.dp,
        shadowElevation = 0.dp,
        borderWidth = 0.6.dp,
        highlightWidth = 0.8.dp
    )
}

private fun Modifier.ngGlassLayer(
    shape: Shape,
    style: NgGlassStyle
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val outlinePath = when (outline) {
        is Outline.Rectangle -> null
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Generic -> outline.path
    }
    val diagonalEnd = Offset(size.width, size.height)
    val containerBrush = Brush.linearGradient(
        colors = listOf(style.containerTop, style.containerBottom),
        start = Offset.Zero,
        end = diagonalEnd
    )
    val accentBrush = Brush.radialGradient(
        colors = listOf(style.accentGlow, Color.Transparent),
        center = Offset(size.width * 0.10f, size.height * 0.04f),
        radius = max(size.width, size.height).coerceAtLeast(1f) * 0.82f
    )
    val glossBrush = Brush.verticalGradient(
        colors = listOf(
            style.surfaceGloss,
            style.surfaceGloss.copy(alpha = style.surfaceGloss.alpha * 0.35f),
            Color.Transparent
        ),
        startY = 0f,
        endY = (size.height * 0.62f).coerceAtLeast(1f)
    )
    val highlightBrush = Brush.linearGradient(
        colors = listOf(
            style.edgeHighlight,
            style.edgeHighlight.copy(alpha = style.edgeHighlight.alpha * 0.35f),
            Color.Transparent
        ),
        start = Offset.Zero,
        end = diagonalEnd
    )
    val depthBrush = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            Color.Transparent,
            style.depthEdge
        ),
        start = Offset.Zero,
        end = diagonalEnd
    )
    val depthFillBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to Color.Transparent,
            0.70f to Color.Transparent,
            1.00f to style.depthEdge
        )
    )
    val softHighlightBrush = Brush.linearGradient(
        colors = listOf(
            style.edgeHighlight.copy(alpha = style.edgeHighlight.alpha * 0.34f),
            style.edgeHighlight.copy(alpha = style.edgeHighlight.alpha * 0.12f),
            Color.Transparent
        ),
        start = Offset.Zero,
        end = diagonalEnd
    )

    onDrawBehind {
        drawGlassOutline(outline, outlinePath, containerBrush)
        if (style.accentGlow.alpha > 0f) {
            drawGlassOutline(outline, outlinePath, accentBrush)
        }
        if (style.surfaceGloss.alpha > 0f) {
            drawGlassOutline(outline, outlinePath, glossBrush)
        }
        if (style.depthEdge.alpha > 0f) {
            drawGlassOutline(outline, outlinePath, depthFillBrush)
        }
        if (style.highlightWidth > 0.dp && style.edgeHighlight.alpha > 0f) {
            drawGlassOutline(
                outline = outline,
                outlinePath = outlinePath,
                brush = softHighlightBrush,
                drawStyle = Stroke(width = style.highlightWidth.toPx() * 3f)
            )
        }
        if (style.borderWidth > 0.dp) {
            drawGlassOutline(
                outline = outline,
                outlinePath = outlinePath,
                brush = SolidColor(style.borderColor),
                drawStyle = Stroke(width = style.borderWidth.toPx())
            )
        }
        if (style.highlightWidth > 0.dp && style.edgeHighlight.alpha > 0f) {
            drawGlassOutline(
                outline = outline,
                outlinePath = outlinePath,
                brush = highlightBrush,
                drawStyle = Stroke(width = style.highlightWidth.toPx())
            )
        }
        if (style.borderWidth > 0.dp && style.depthEdge.alpha > 0f) {
            drawGlassOutline(
                outline = outline,
                outlinePath = outlinePath,
                brush = depthBrush,
                drawStyle = Stroke(width = style.borderWidth.toPx())
            )
        }
    }
}

private fun DrawScope.drawGlassOutline(
    outline: Outline,
    outlinePath: Path?,
    brush: Brush,
    drawStyle: DrawStyle = Fill
) {
    when (outline) {
        is Outline.Rectangle -> drawRect(
            brush = brush,
            topLeft = outline.rect.topLeft,
            size = outline.rect.size,
            style = drawStyle
        )

        is Outline.Rounded,
        is Outline.Generic -> drawPath(
            path = requireNotNull(outlinePath),
            brush = brush,
            style = drawStyle
        )
    }
}
