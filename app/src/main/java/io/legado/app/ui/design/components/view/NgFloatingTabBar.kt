package io.legado.app.ui.design.components.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.ImageViewCompat
import io.legado.app.R
import io.legado.app.ui.design.theme.NgThemeResolver
import kotlin.math.roundToInt

data class NgFloatingTabItem(
    val text: CharSequence? = null,
    @DrawableRes val iconRes: Int? = null,
    @DrawableRes val selectedIconRes: Int? = null,
    val iconDrawable: Drawable? = null,
    val tintIcon: Boolean = true,
    val iconSizeDp: Int = 24,
    val count: Int? = null,
    val contentDescription: CharSequence? = text
)

enum class NgFloatingTabBarVariant {
    DETAIL,
    CONTENT_OVERLAY
}

/** 复用角色池视觉的底部悬浮分段栏，业务页面只提供语义项和选中索引。 */
class NgFloatingTabBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private data class TabContent(
        val icon: AppCompatImageView?,
        val label: AppCompatTextView?,
        val badge: AppCompatTextView?,
        @param:DrawableRes val iconRes: Int?,
        @param:DrawableRes val selectedIconRes: Int?,
        val iconDrawable: Drawable?,
        val tintIcon: Boolean,
    )

    private var items: List<NgFloatingTabItem> = emptyList()
    private var onTabSelected: ((Int) -> Unit)? = null
    var selectedIndex: Int = 0
        private set

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        minimumHeight = 48.dp
        setPadding(3.dp, 3.dp, 3.dp, 3.dp)
        setBackgroundResource(R.drawable.ng_bg_character_tabs)
    }

    fun setVariant(variant: NgFloatingTabBarVariant) {
        setBackgroundResource(
            when (variant) {
                NgFloatingTabBarVariant.DETAIL -> R.drawable.ng_bg_character_tabs
                NgFloatingTabBarVariant.CONTENT_OVERLAY ->
                    R.drawable.ng_bg_floating_tabs_overlay
            }
        )
    }

    /** 只调整 Dock 表面，不改变图标和文字的透明度。 */
    fun setSurfaceAlpha(alpha: Float) {
        val surfaceColor = ContextCompat.getColor(context, R.color.ng_floating_dock_surface)
        background = GradientDrawable().apply {
            cornerRadius = 12.dp.toFloat()
            setColor(
                ColorUtils.setAlphaComponent(
                    surfaceColor,
                    (alpha.coerceIn(0f, 1f) * 255).roundToInt()
                )
            )
        }
    }

    fun setItems(
        items: List<NgFloatingTabItem>,
        selectedIndex: Int = 0,
        onTabSelected: (Int) -> Unit
    ) {
        this.items = items
        this.onTabSelected = onTabSelected
        removeAllViews()
        items.forEachIndexed { index, item ->
            addView(
                createTab(index, item),
                LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            )
        }
        select(selectedIndex, notify = false)
    }

    fun select(index: Int, notify: Boolean = false) {
        if (items.isEmpty()) return
        selectedIndex = index.coerceIn(items.indices)
        refreshStyles()
        if (notify) onTabSelected?.invoke(selectedIndex)
    }

    private fun createTab(index: Int, item: NgFloatingTabItem): LinearLayout {
        val labelText = when {
            item.count != null && !item.text.isNullOrEmpty() -> "${item.text}\n${item.count}"
            else -> item.text ?: ""
        }
        val initialIcon = item.iconDrawable ?: (item.iconRes ?: item.selectedIconRes)?.let { iconRes ->
            context.getDrawable(iconRes)
        }
        val icon = initialIcon?.let { drawable ->
            AppCompatImageView(context).apply {
                setImageDrawable(drawable.newDrawable())
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
        }
        val label = labelText.takeIf { it.isNotEmpty() }?.let { text ->
            AppCompatTextView(context).apply {
                this.text = text
                gravity = Gravity.CENTER
                includeFontPadding = false
                textSize = 13f
            }
        }
        val badge = if (item.text.isNullOrEmpty() && item.count != null && icon != null) {
            AppCompatTextView(context).apply {
                text = item.count.coerceAtMost(99).let { count ->
                    if (item.count > 99) "99+" else count.toString()
                }
                gravity = Gravity.CENTER
                includeFontPadding = false
                minWidth = 16.dp
                setPadding(3.dp, 0, 3.dp, 0)
                textSize = 9f
            }
        } else {
            null
        }
        val iconHost = if (icon != null && badge != null) {
            FrameLayout(context).apply {
                addView(
                    icon,
                    FrameLayout.LayoutParams(
                        item.iconSizeDp.dp,
                        item.iconSizeDp.dp,
                        Gravity.CENTER
                    )
                )
                addView(
                    badge,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        16.dp,
                        Gravity.TOP or Gravity.END
                    )
                )
            }
        } else {
            icon
        }
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            contentDescription = item.contentDescription
            isClickable = true
            isFocusable = true
            setPadding(10.dp, 0, 10.dp, 0)
            iconHost?.let {
                val size = if (badge != null) {
                    maxOf(32.dp, item.iconSizeDp.dp)
                } else {
                    item.iconSizeDp.dp
                }
                addView(it, LayoutParams(size, size))
            }
            label?.let {
                addView(
                    it,
                    LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                        if (icon != null) marginStart = 5.dp
                    }
                )
            }
            tag = TabContent(
                icon = icon,
                label = label,
                badge = badge,
                iconRes = item.iconRes,
                selectedIconRes = item.selectedIconRes,
                iconDrawable = item.iconDrawable,
                tintIcon = item.tintIcon,
            )
            setOnClickListener { select(index, notify = true) }
        }
    }

    private fun refreshStyles() {
        val colors = NgThemeResolver.resolve(context).colors
        for (index in 0 until childCount) {
            val tab = getChildAt(index) as LinearLayout
            val content = tab.tag as TabContent
            val selected = index == selectedIndex
            val contentColor = if (selected) colors.primary else colors.onSurface
            tab.isSelected = selected
            content.label?.setTextColor(contentColor)
            content.label?.typeface = Typeface.defaultFromStyle(
                if (selected) Typeface.BOLD else Typeface.NORMAL
            )
            content.badge?.apply {
                setTextColor(colors.onPrimary)
                background = GradientDrawable().apply {
                    cornerRadius = 8.dp.toFloat()
                    setColor(colors.primary)
                }
            }
            tab.background = if (selected) {
                GradientDrawable().apply {
                    cornerRadius = 10.dp.toFloat()
                    setColor(colors.selectedContainer)
                }
            } else {
                null
            }
            content.icon?.let { icon ->
                if (content.iconDrawable != null) {
                    icon.setImageDrawable(content.iconDrawable.newDrawable())
                } else {
                    val iconRes = if (selected) {
                        content.selectedIconRes ?: content.iconRes
                    } else {
                        content.iconRes ?: content.selectedIconRes
                    }
                    iconRes?.let(icon::setImageResource)
                }
                ImageViewCompat.setImageTintList(
                    icon,
                    ColorStateList.valueOf(contentColor).takeIf { content.tintIcon },
                )
            }
        }
    }

    private fun Drawable.newDrawable(): Drawable =
        constantState?.newDrawable(resources)?.mutate() ?: mutate()

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density + 0.5f).toInt()
}
