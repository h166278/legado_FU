package io.legado.app.ui.widget

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor

data class NgExpandableActionPopupItem(
    val itemId: Int,
    val titleRes: Int = 0,
    val iconRes: Int = 0,
    val checked: Boolean = false,
    val dividerBefore: Boolean = false,
    val title: CharSequence? = null,
    val payload: Any? = null,
    val children: List<NgExpandableActionPopupItem> = emptyList()
)

/**
 * Reading NG 可原位展开的 View 操作菜单。
 *
 * 子项与一级项使用同一水平栅格；内容超过屏幕时在菜单内部滚动。
 */
class NgExpandableActionPopup(
    private val context: Context,
    private val items: List<NgExpandableActionPopupItem>,
    widthDp: Int = 152,
    private val onItemClick: (NgExpandableActionPopupItem) -> Unit
) : PopupWindow(
    widthDp.dpToPx(),
    ViewGroup.LayoutParams.WRAP_CONTENT
) {

    private val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 6.dpToPx(), 0, 6.dpToPx())
    }
    private val scrollView = ScrollView(context).apply {
        isFillViewport = false
        isVerticalScrollBarEnabled = false
        background = GradientDrawable().apply {
            setColor(context.getCompatColor(R.color.ng_surface_soft))
            cornerRadius = 18.dpToPx().toFloat()
        }
        addView(
            panel,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }
    private val expandedItemIds = mutableSetOf<Int>()
    private var anchor: View? = null

    init {
        contentView = scrollView
        isFocusable = true
        isOutsideTouchable = true
        setBackgroundDrawable(ColorDrawable(0x00000000))
        elevation = 8.dpToPx().toFloat()
        rebuildRows()
    }

    fun show(anchor: View) {
        this.anchor = anchor
        val bounds = resolveBounds(anchor)
        height = bounds.height
        showAtLocation(anchor.rootView, Gravity.NO_GRAVITY, bounds.x, bounds.y)
    }

    private fun rebuildRows() {
        panel.removeAllViews()
        addRows(items)
        anchor?.takeIf { isShowing }?.let { currentAnchor ->
            val bounds = resolveBounds(currentAnchor)
            update(bounds.x, bounds.y, width, bounds.height)
        }
    }

    private fun addRows(rows: List<NgExpandableActionPopupItem>) {
        rows.forEach { item ->
            if (item.dividerBefore) {
                panel.addView(createDivider())
            }
            val isExpanded = item.itemId in expandedItemIds
            panel.addView(createActionRow(item, isExpanded))
            if (isExpanded) {
                addRows(item.children)
            }
        }
    }

    private fun createActionRow(
        item: NgExpandableActionPopupItem,
        isExpanded: Boolean
    ): View {
        val color = context.getCompatColor(R.color.ng_on_surface)
        val hasChildren = item.children.isNotEmpty()
        val trailingWidth = when {
            hasChildren && item.checked -> 56.dpToPx()
            hasChildren || item.checked -> 30.dpToPx()
            else -> 0
        }
        val textMaxWidth = (
            width - 12.dpToPx() - 20.dpToPx() - 10.dpToPx() - 12.dpToPx() - trailingWidth
            ).coerceAtLeast(0)
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(12.dpToPx(), 0, 12.dpToPx(), 0)
            minimumHeight = 44.dpToPx()
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (hasChildren) {
                    if (!expandedItemIds.add(item.itemId)) {
                        expandedItemIds.remove(item.itemId)
                    }
                    rebuildRows()
                } else {
                    dismiss()
                    onItemClick(item)
                }
            }
            addView(createIcon(item.iconRes, color), iconLayoutParams())
            addView(TextView(context).apply {
                text = item.title ?: context.getString(item.titleRes)
                setTextColor(color)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                includeFontPadding = false
                maxLines = 1
                maxWidth = textMaxWidth
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ))
            if (item.checked) {
                addView(
                    createIcon(R.drawable.ng_ic_popup_selected, color),
                    trailingIconLayoutParams(hasChildren)
                )
            }
            if (hasChildren) {
                addView(
                    createIcon(
                        if (isExpanded) R.drawable.ic_arrow_down else R.drawable.ic_arrow_right,
                        color
                    ),
                    trailingIconLayoutParams(false)
                )
            }
        }
    }

    private fun createIcon(iconRes: Int, color: Int) = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(2.dpToPx(), 2.dpToPx(), 2.dpToPx(), 2.dpToPx())
        setImageDrawable(ContextCompat.getDrawable(context, iconRes)?.mutate())
        setColorFilter(color)
    }

    private fun iconLayoutParams() = LinearLayout.LayoutParams(
        20.dpToPx(),
        20.dpToPx()
    ).apply {
        marginEnd = 10.dpToPx()
    }

    private fun trailingIconLayoutParams(hasFollowingIcon: Boolean) = LinearLayout.LayoutParams(
        20.dpToPx(),
        20.dpToPx()
    ).apply {
        marginStart = 10.dpToPx()
        if (hasFollowingIcon) marginEnd = 6.dpToPx()
    }

    private fun createDivider(): View {
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

    private fun resolveBounds(anchor: View): PopupBounds {
        val margin = 8.dpToPx()
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
        val availableHeight = (rootBottom - rootTop - margin * 2).coerceAtLeast(0)
        val popupHeight = contentView.measuredHeight.coerceAtMost(availableHeight)
        val belowY = location[1] + anchor.height + margin
        val aboveY = location[1] - popupHeight - margin
        val y = if (belowY + popupHeight > rootBottom - margin && aboveY >= rootTop + margin) {
            aboveY
        } else {
            belowY
                .coerceAtMost(rootBottom - popupHeight - margin)
                .coerceAtLeast(rootTop + margin)
        }
        return PopupBounds(x, y, popupHeight)
    }

    private data class PopupBounds(
        val x: Int,
        val y: Int,
        val height: Int
    )
}
