package io.legado.app.ui.config

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.ui.design.theme.NgBuiltInColorPresets
import io.legado.app.ui.design.theme.NgColorPreset
import io.legado.app.ui.design.theme.NgColorSystem
import io.legado.app.ui.widget.dialog.NgLongListBottomSheet
import io.legado.app.utils.dpToPx
import kotlin.math.min

internal fun showNgColorPresetSheet(
    context: Context,
    current: NgColorSystem,
    accentColor: Int,
    onAccentColor: Int,
    onSurfaceColor: Int,
    onSelected: (NgColorSystem) -> Unit
) {
    val sheet = NgLongListBottomSheet(
        context = context,
        searchHint = "",
        title = context.getString(R.string.ng_color_presets),
        showSearch = false,
        heightRatio = 0.62f
    )
    sheet.setContent(
        createPresetGrid(
            context = context,
            current = current,
            sheet = sheet,
            accentColor = accentColor,
            onAccentColor = onAccentColor,
            onSurfaceColor = onSurfaceColor,
            onSelected = onSelected
        )
    ) {}
    sheet.show()
}

private fun createPresetGrid(
    context: Context,
    current: NgColorSystem,
    sheet: NgLongListBottomSheet,
    accentColor: Int,
    onAccentColor: Int,
    onSurfaceColor: Int,
    onSelected: (NgColorSystem) -> Unit
): View = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER
    clipToPadding = false
    setPadding(0, 4.dpToPx(), 0, 10.dpToPx())

    NgBuiltInColorPresets.all.indices.chunked(PRESETS_PER_ROW).forEach { rowIndices ->
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                rowIndices.forEach { index ->
                    addView(
                        createPresetCell(
                            context = context,
                            preset = NgBuiltInColorPresets.all[index],
                            selected = NgBuiltInColorPresets.all[index].matches(current),
                            accentColor = accentColor,
                            onAccentColor = onAccentColor,
                            onSurfaceColor = onSurfaceColor,
                            onClick = {
                                onSelected(NgBuiltInColorPresets.all[index].applyTo(current))
                                sheet.dismiss()
                            }
                        ),
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            1f
                        )
                    )
                }
                repeat(PRESETS_PER_ROW - rowIndices.size) {
                    addView(
                        View(context),
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            1f
                        )
                    )
                }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
    }
}

private fun createPresetCell(
    context: Context,
    preset: NgColorPreset,
    selected: Boolean,
    accentColor: Int,
    onAccentColor: Int,
    onSurfaceColor: Int,
    onClick: () -> Unit
): View = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER
    isClickable = true
    isFocusable = true
    isSelected = selected
    contentDescription = context.getString(preset.nameRes)
    setPadding(3.dpToPx(), 4.dpToPx(), 3.dpToPx(), 4.dpToPx())

    addView(
        FrameLayout(context).apply {
            background = createPresetCardBackground(context, selected, accentColor)
            addView(
                PresetSwatchView(context, preset.lightSeed, preset.darkSeed),
                FrameLayout.LayoutParams(46.dpToPx(), 46.dpToPx(), Gravity.CENTER)
            )
            if (selected) {
                addView(
                    createSelectedIndicator(context, accentColor, onAccentColor),
                    FrameLayout.LayoutParams(
                        24.dpToPx(),
                        24.dpToPx(),
                        Gravity.END or Gravity.BOTTOM
                    )
                )
            }
        },
        LinearLayout.LayoutParams(68.dpToPx(), 68.dpToPx())
    )
    addView(
        TextView(context).apply {
            text = context.getString(preset.nameRes)
            gravity = Gravity.CENTER
            maxLines = 1
            textSize = 12f
            includeFontPadding = false
            setTextColor(
                if (selected) {
                    accentColor
                } else {
                    onSurfaceColor
                }
            )
        },
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            28.dpToPx()
        ).apply {
            topMargin = 7.dpToPx()
        }
    )
    setOnClickListener { onClick() }
}

private fun createPresetCardBackground(
    context: Context,
    selected: Boolean,
    accentColor: Int
): Drawable =
    GradientDrawable().apply {
        cornerRadius = 18.dpToPx().toFloat()
        setColor(ContextCompat.getColor(context, R.color.ng_surface_card))
        setStroke(
            (if (selected) 2 else 1).dpToPx(),
            if (selected) accentColor
            else ContextCompat.getColor(context, R.color.ng_card_stroke)
        )
    }

private fun createSelectedIndicator(
    context: Context,
    accentColor: Int,
    onAccentColor: Int
): View = ImageView(context).apply {
    setImageResource(R.drawable.ic_check)
    imageTintList = ColorStateList.valueOf(onAccentColor)
    setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
    background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(accentColor)
    }
}

private class PresetSwatchView(
    context: Context,
    private val lightColor: Int,
    private val darkColor: Int
) : View(context) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.dpToPx().toFloat()
        color = ContextCompat.getColor(context, R.color.ng_card_stroke)
    }
    private val clipPath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val strokeInset = outlinePaint.strokeWidth / 2f
        val radius = min(width, height) / 2f - strokeInset
        val centerX = width / 2f
        val centerY = height / 2f
        clipPath.reset()
        clipPath.addCircle(centerX, centerY, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)
        fillPaint.color = lightColor
        canvas.drawRect(0f, 0f, centerX, height.toFloat(), fillPaint)
        fillPaint.color = darkColor
        canvas.drawRect(centerX, 0f, width.toFloat(), height.toFloat(), fillPaint)
        canvas.restore()
        canvas.drawCircle(centerX, centerY, radius, outlinePaint)
    }
}

private const val PRESETS_PER_ROW = 4
