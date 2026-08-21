package io.legado.app.lib.prefs

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import io.legado.app.R
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.widget.dialog.NgLongListBottomSheet
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatDrawable


class IconListPreference(context: Context, attrs: AttributeSet) : ListPreference(context, attrs) {
    private val iconNames: Array<CharSequence>
    private val entryDrawables = arrayListOf<Drawable?>()

    init {
        layoutResource = R.layout.view_preference
        widgetLayoutResource = R.layout.view_icon

        val attributes = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.IconListPreference,
            0,
            0
        )
        iconNames = try {
            attributes.getTextArray(R.styleable.IconListPreference_icons)
        } finally {
            attributes.recycle()
        }

        iconNames.forEach { iconName ->
            val resId = context.resources.getIdentifier(
                iconName.toString(),
                "mipmap",
                context.packageName
            )
            entryDrawables.add(runCatching { context.getCompatDrawable(resId) }.getOrNull())
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val preview = Preference.bindView<ImageView>(
            context,
            holder,
            icon,
            title,
            summary,
            widgetLayoutResource,
            R.id.preview,
            50,
            50
        )
        if (preview is ImageView) {
            preview.setImageDrawable(entryDrawables.getOrNull(findIndexOfValue(value)))
        }
    }

    override fun onClick() {
        val sheet = NgLongListBottomSheet(
            context = context,
            searchHint = "",
            title = title ?: context.getString(R.string.change_icon),
            showSearch = false,
            heightRatio = 0.48f
        )
        sheet.setContent(createIconGrid(sheet)) {}
        sheet.show()
    }

    private fun createIconGrid(sheet: NgLongListBottomSheet): View {
        val values = entryValues.orEmpty()
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
                                    index = index,
                                    newValue = values[index].toString(),
                                    sheet = sheet
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
        index: Int,
        newValue: String,
        sheet: NgLongListBottomSheet
    ): View {
        val selected = newValue == value
        return FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
            isSelected = selected
            contentDescription = "${context.getString(R.string.change_icon)} ${index + 1}"

            val iconCard = FrameLayout(context).apply {
                background = createIconCardBackground(selected)
                setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
                addView(
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageDrawable(entryDrawables.getOrNull(index))
                    },
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                if (selected) {
                    addView(createSelectedIndicator(), FrameLayout.LayoutParams(
                        24.dpToPx(),
                        24.dpToPx(),
                        Gravity.END or Gravity.BOTTOM
                    ))
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
                if (newValue == value || callChangeListener(newValue)) {
                    if (newValue != value) {
                        value = newValue
                        notifyChanged()
                    }
                    sheet.dismiss()
                }
            }
        }
    }

    private fun createIconCardBackground(selected: Boolean): Drawable {
        return GradientDrawable().apply {
            cornerRadius = 18.dpToPx().toFloat()
            setColor(ContextCompat.getColor(context, R.color.ng_surface_card))
            setStroke(
                (if (selected) 2 else 1).dpToPx(),
                if (selected) {
                    context.accentColor
                } else {
                    ContextCompat.getColor(context, R.color.ng_card_stroke)
                }
            )
        }
    }

    private fun createSelectedIndicator(): View {
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

    private companion object {
        const val ICONS_PER_ROW = 4
    }
}
