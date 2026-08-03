package io.legado.app.ui.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor

data class NgActionPopupItem(
    val itemId: Int,
    val titleRes: Int = 0,
    val iconRes: Int = 0,
    val checked: Boolean = false,
    val dividerBefore: Boolean = false,
    val title: CharSequence? = null,
    val iconDrawable: Drawable? = null,
    val payload: Any? = null
)

class NgActionPopup(
    context: Context,
    items: List<NgActionPopupItem>,
    private val widthDp: Int = 152,
    onItemClick: (NgActionPopupItem) -> Unit
) : PopupWindow(
    resolveWidth(context, items, widthDp),
    ViewGroup.LayoutParams.WRAP_CONTENT
) {

    init {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 6.dpToPx(), 0, 6.dpToPx())
            background = GradientDrawable().apply {
                setColor(context.getCompatColor(R.color.ng_surface_soft))
                cornerRadius = 18.dpToPx().toFloat()
            }
        }
        items.forEach { item ->
            if (item.dividerBefore) {
                panel.addView(createDivider(context))
            }
            panel.addView(createActionRow(context, item) {
                dismiss()
                onItemClick(item)
            })
        }
        contentView = panel
        isFocusable = true
        isOutsideTouchable = true
        setBackgroundDrawable(ColorDrawable(0x00000000))
        elevation = 8.dpToPx().toFloat()
    }

    fun show(
        anchor: View,
        marginDp: Int = 8,
        verticalAnchorInsetDp: Int = 0
    ) {
        val margin = marginDp.dpToPx()
        val verticalAnchorInset = verticalAnchorInsetDp.dpToPx()
        val location = IntArray(2)
        val rootLocation = IntArray(2)
        anchor.getLocationOnScreen(location)
        anchor.rootView.getLocationOnScreen(rootLocation)
        val rootWidth = anchor.rootView.width
        val rootTop = rootLocation[1]
        val rootBottom = rootTop + anchor.rootView.height
        val maxX = (rootWidth - width - margin).coerceAtLeast(margin)
        val x = (location[0] + anchor.width - width).coerceIn(margin, maxX)
        contentView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupHeight = contentView.measuredHeight
        val visibleTop = location[1] + verticalAnchorInset
        val visibleBottom = location[1] + anchor.height - verticalAnchorInset
        val belowY = visibleBottom + margin
        val aboveY = visibleTop - popupHeight - margin
        val y = if (belowY + popupHeight > rootBottom - margin && aboveY >= rootTop + margin) {
            aboveY
        } else {
            belowY
                .coerceAtMost(rootBottom - popupHeight - margin)
                .coerceAtLeast(rootTop + margin)
        }
        showAtLocation(anchor.rootView, Gravity.NO_GRAVITY, x, y)
    }

    private fun createActionRow(
        context: Context,
        item: NgActionPopupItem,
        onClick: () -> Unit
    ): View {
        val color = context.getCompatColor(R.color.ng_on_surface)
        val textMaxWidth = (
            width - 12.dpToPx() - 20.dpToPx() - 10.dpToPx() - 12.dpToPx() -
                (if (item.checked) 30.dpToPx() else 0)
            ).coerceAtLeast(0)
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(12.dpToPx(), 0, 12.dpToPx(), 0)
            minimumHeight = 44.dpToPx()
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            addView(ImageView(context).apply {
                val drawable = item.iconDrawable ?: item.iconRes
                    .takeIf { it != 0 }
                    ?.let { ContextCompat.getDrawable(context, it) }
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(2.dpToPx(), 2.dpToPx(), 2.dpToPx(), 2.dpToPx())
                setImageDrawable(drawable?.mutate())
                setColorFilter(color)
                alpha = if (drawable == null) 0f else 1f
            }, LinearLayout.LayoutParams(20.dpToPx(), 20.dpToPx()).apply {
                marginEnd = 10.dpToPx()
            })
            addView(TextView(context).apply {
                text = item.title ?: context.getString(item.titleRes)
                setTextColor(color)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                includeFontPadding = false
                maxLines = 1
                maxWidth = textMaxWidth
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            if (item.checked) {
                addView(ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setPadding(2.dpToPx(), 2.dpToPx(), 2.dpToPx(), 2.dpToPx())
                    setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ng_ic_popup_selected))
                    setColorFilter(color)
                }, LinearLayout.LayoutParams(20.dpToPx(), 20.dpToPx()).apply {
                    marginStart = 10.dpToPx()
                })
            }
        }
    }

    private fun createDivider(context: Context): View {
        return View(context).apply {
            setBackgroundColor(context.getCompatColor(R.color.ng_outline))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                leftMargin = 12.dpToPx()
                rightMargin = 12.dpToPx()
                topMargin = 3.dpToPx()
                bottomMargin = 3.dpToPx()
            }
        }
    }

    companion object {
        private fun resolveWidth(
            context: Context,
            items: List<NgActionPopupItem>,
            widthDp: Int
        ): Int {
            if (widthDp > 0) return widthDp.dpToPx()
            val textPaint = TextPaint().apply {
                textSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    16f,
                    context.resources.displayMetrics
                )
            }
            val rowBaseWidth = 12.dpToPx() + 20.dpToPx() + 10.dpToPx() + 12.dpToPx()
            val selectedIndicatorWidth = 10.dpToPx() + 20.dpToPx()
            val contentWidth = items.maxOfOrNull { item ->
                textPaint.measureText(
                    item.title?.toString()
                        ?: item.titleRes.takeIf { it != 0 }?.let { context.getString(it) }
                        ?: ""
                ).toInt() + rowBaseWidth +
                    if (item.checked) selectedIndicatorWidth else 0
            } ?: 0
            val minWidth = 152.dpToPx()
            val maxWidth = (context.resources.displayMetrics.widthPixels - 16.dpToPx())
                .coerceAtMost(280.dpToPx())
                .coerceAtLeast(minWidth)
            return (contentWidth + 4.dpToPx()).coerceIn(minWidth, maxWidth)
        }
    }
}
