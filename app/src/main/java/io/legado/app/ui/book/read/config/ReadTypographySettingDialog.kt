package io.legado.app.ui.book.read.config

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentDialog
import androidx.annotation.ColorInt
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.ColorUtils
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.design.components.view.NgDiscreteStepBar
import io.legado.app.ui.design.components.view.NgFloatingTabBar
import io.legado.app.ui.design.components.view.NgFloatingTabItem
import io.legado.app.ui.widget.dialog.applyNgWindow

internal object ReadTypographySettingDialog {

    fun showDiscrete(
        context: Context,
        avoidView: View,
        title: String,
        stepLabels: List<String>,
        currentValues: List<String>,
        selectedIndex: Int,
        currentValueTextSizeSp: Float = 30f,
        applyCurrentValueStyle: (TextView, Int) -> Unit = { _, _ -> },
        onSelectionChanged: (Int) -> Unit,
    ) {
        if (stepLabels.size < 2) return
        val safeIndex = selectedIndex.coerceIn(stepLabels.indices)
        val palette = palette(context)
        val root = createRoot(context)
        root.addView(titleView(context, title, palette.foreground))

        val currentValue = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(palette.foreground)
            textSize = currentValueTextSizeSp
            includeFontPadding = false
        }
        fun updateCurrent(index: Int) {
            currentValue.text = currentValues.getOrElse(index) { stepLabels[index] }
            currentValue.typeface = Typeface.DEFAULT
            applyCurrentValueStyle(currentValue, index)
        }
        updateCurrent(safeIndex)
        root.addView(currentValue, matchWrapParams().apply {
            topMargin = 28.dp(context)
            bottomMargin = 18.dp(context)
        })

        val stepBar = NgDiscreteStepBar(context).apply {
            stepCount = stepLabels.size
            this.selectedIndex = safeIndex
            stepColor = palette.accent
            contentDescription = "$title ${currentValues.getOrElse(safeIndex) { stepLabels[safeIndex] }}"
            onSelectedIndexChanged = { index ->
                updateCurrent(index)
                contentDescription = "$title ${currentValues.getOrElse(index) { stepLabels[index] }}"
                onSelectionChanged(index)
            }
        }
        root.addView(stepBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            46.dp(context),
        ))
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            stepLabels.forEach { label ->
                addView(TextView(context).apply {
                    text = label
                    gravity = Gravity.CENTER
                    setTextColor(palette.secondary)
                    textSize = if (stepLabels.size >= 8) 10f else 13f
                    maxLines = 1
                    includeFontPadding = false
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }, matchWrapParams().apply {
            topMargin = 8.dp(context)
        })
        showDialog(context, root, avoidView)
    }

    fun showChineseConverter(
        context: Context,
        avoidView: View,
        title: String,
        labels: List<String>,
        selectedIndex: Int,
        onSelectedIndexChanged: (Int) -> Unit,
    ) {
        if (labels.isEmpty()) return
        val palette = palette(context)
        val root = createRoot(context)
        root.addView(titleView(context, title, palette.foreground))
        root.addView(TextView(context).apply {
            text = context.getString(R.string.chinese_converter_hint)
            setTextColor(palette.secondary)
            textSize = 14f
            gravity = Gravity.CENTER
            includeFontPadding = false
        }, matchWrapParams().apply {
            topMargin = 10.dp(context)
            bottomMargin = 24.dp(context)
        })

        var dialog: Dialog? = null
        val dock = NgFloatingTabBar(context).apply {
            setSurfaceAlpha(0.28f)
            setContentColors(
                unselectedContentColor = palette.foreground,
                selectedContentColor = if (ColorUtils.calculateLuminance(palette.accent) > 0.5) {
                    Color.BLACK
                } else {
                    Color.WHITE
                },
                selectedContainerColor = palette.accent,
            )
            setItems(
                items = labels.map { NgFloatingTabItem(text = it) },
                selectedIndex = selectedIndex.coerceIn(labels.indices),
            ) { index ->
                onSelectedIndexChanged(index)
                dialog?.dismiss()
            }
        }
        root.addView(dock, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            42.dp(context),
        ))
        dialog = showDialog(context, root, avoidView)
    }

    private fun createRoot(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24.dp(context), 26.dp(context), 24.dp(context), 26.dp(context))
            setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun titleView(context: Context, title: String, @ColorInt color: Int): TextView {
        return TextView(context).apply {
            text = title
            setTextColor(color)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
    }

    private fun showDialog(context: Context, root: LinearLayout, avoidView: View): Dialog {
        val glassBackground = ComposeView(context).apply {
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            ReadDrawerStyle.applyGlassBackground(
                view = this,
                radiusDp = 24,
                disposeOnDetachedFromWindow = true,
            )
        }
        val container = GlassDialogLayout(
            context = context,
            glassView = glassBackground,
            contentView = root,
        ).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        return ComponentDialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(container)
            setCanceledOnTouchOutside(true)
            show()
            applyNgWindow(marginDp = 20)
            ReadDrawerStyle.positionDialogAbove(this, avoidView)
        }
    }

    private fun matchWrapParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun palette(context: Context): Palette {
        val night = ReadBookConfig.isNightTheme && !AppConfig.isEInkMode
        val foreground = if (night) Color.WHITE else Color.rgb(45, 43, 40)
        return if (night) {
            Palette(
                foreground = foreground,
                secondary = ColorUtils.setAlphaComponent(foreground, 190),
                accent = ReadDrawerStyle.accentColor(context),
            )
        } else {
            Palette(
                foreground = foreground,
                secondary = ColorUtils.setAlphaComponent(foreground, 170),
                accent = ReadDrawerStyle.accentColor(context),
            )
        }
    }

    private fun Int.dp(context: Context): Int {
        return (this * context.resources.displayMetrics.density + 0.5f).toInt()
    }

    /**
     * 高度只由内容层决定，玻璃层在内容测量完成后再铺满，避免 MATCH_PARENT 背景
     * 反向把 WRAP_CONTENT Dialog 撑到窗口可用高度。
     */
    private class GlassDialogLayout(
        context: Context,
        private val glassView: View,
        private val contentView: View,
    ) : ViewGroup(context) {

        init {
            addView(glassView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(contentView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val contentWidthSpec = getChildMeasureSpec(
                widthMeasureSpec,
                paddingLeft + paddingRight,
                LayoutParams.MATCH_PARENT,
            )
            val contentHeightSpec = getChildMeasureSpec(
                heightMeasureSpec,
                paddingTop + paddingBottom,
                LayoutParams.WRAP_CONTENT,
            )
            contentView.measure(contentWidthSpec, contentHeightSpec)

            val measuredWidth = resolveSize(
                contentView.measuredWidth + paddingLeft + paddingRight,
                widthMeasureSpec,
            )
            val measuredHeight = resolveSize(
                contentView.measuredHeight + paddingTop + paddingBottom,
                heightMeasureSpec,
            )
            setMeasuredDimension(measuredWidth, measuredHeight)

            val childWidth = (measuredWidth - paddingLeft - paddingRight).coerceAtLeast(0)
            val childHeight = (measuredHeight - paddingTop - paddingBottom).coerceAtLeast(0)
            contentView.measure(
                MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY),
            )
            glassView.measure(
                MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY),
            )
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            val childLeft = paddingLeft
            val childTop = paddingTop
            val childRight = width - paddingRight
            val childBottom = height - paddingBottom
            glassView.layout(childLeft, childTop, childRight, childBottom)
            contentView.layout(childLeft, childTop, childRight, childBottom)
        }
    }

    private data class Palette(
        @ColorInt val foreground: Int,
        @ColorInt val secondary: Int,
        @ColorInt val accent: Int,
    )
}
