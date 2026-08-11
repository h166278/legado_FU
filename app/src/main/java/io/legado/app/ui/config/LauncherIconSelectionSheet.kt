package io.legado.app.ui.config

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.widget.dialog.NgLongListBottomSheet
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatDrawable

internal object LauncherIconSelectionSheet {

    fun show(
        context: Context,
        currentValue: String,
        onSelected: (String) -> Unit
    ) {
        val values = context.resources.getStringArray(R.array.icons)
        val drawables = values.map { iconName ->
            val resId = context.resources.getIdentifier(
                iconName,
                "mipmap",
                context.packageName
            )
            runCatching { context.getCompatDrawable(resId) }.getOrNull()
        }
        val sheet = NgLongListBottomSheet(
            context = context,
            searchHint = "",
            title = context.getString(R.string.change_icon),
            showSearch = false,
            heightRatio = 0.48f
        )
        sheet.setContent(
            createIconGrid(
                context = context,
                values = values,
                drawables = drawables,
                currentValue = currentValue,
                sheet = sheet,
                onSelected = onSelected
            )
        ) {}
        sheet.show()
    }

    private fun createIconGrid(
        context: Context,
        values: Array<String>,
        drawables: List<Drawable?>,
        currentValue: String,
        sheet: NgLongListBottomSheet,
        onSelected: (String) -> Unit
    ): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipToPadding = false
            setPadding(0, 4.dpToPx(), 0, 10.dpToPx())

            values.indices.chunked(ICONS_PER_ROW).forEach { rowIndices ->
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER

                        rowIndices.forEach { index ->
                            addView(
                                createIconCell(
                                    context = context,
                                    index = index,
                                    value = values[index],
                                    drawable = drawables.getOrNull(index),
                                    selected = values[index] == currentValue,
                                    sheet = sheet,
                                    onSelected = onSelected
                                ),
                                LinearLayout.LayoutParams(
                                    0,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    1f
                                )
                            )
                        }
                        repeat(ICONS_PER_ROW - rowIndices.size) {
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
    }

    private fun createIconCell(
        context: Context,
        index: Int,
        value: String,
        drawable: Drawable?,
        selected: Boolean,
        sheet: NgLongListBottomSheet,
        onSelected: (String) -> Unit
    ): View {
        return FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
            isSelected = selected
            contentDescription = "${context.getString(R.string.change_icon)} ${index + 1}"

            val iconCard = FrameLayout(context).apply {
                background = createIconCardBackground(context, selected)
                setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
                addView(
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageDrawable(drawable)
                    },
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                if (selected) {
                    addView(
                        createSelectedIndicator(context),
                        FrameLayout.LayoutParams(
                            24.dpToPx(),
                            24.dpToPx(),
                            Gravity.END or Gravity.BOTTOM
                        )
                    )
                }
            }
            addView(
                iconCard,
                FrameLayout.LayoutParams(
                    78.dpToPx(),
                    78.dpToPx(),
                    Gravity.CENTER
                )
            )
            setOnClickListener {
                if (!selected) onSelected(value)
                sheet.dismiss()
            }
        }
    }

    private fun createIconCardBackground(context: Context, selected: Boolean): Drawable {
        return GradientDrawable().apply {
            cornerRadius = 18.dpToPx().toFloat()
            setColor(ContextCompat.getColor(context, R.color.ng_surface_card))
            setStroke(
                (if (selected) 2 else 1).dpToPx(),
                if (selected) context.accentColor
                else ContextCompat.getColor(context, R.color.ng_card_stroke)
            )
        }
    }

    private fun createSelectedIndicator(context: Context): View {
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_check)
            imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.ng_on_primary)
            )
            setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(context.accentColor)
            }
        }
    }

    private const val ICONS_PER_ROW = 4
}
