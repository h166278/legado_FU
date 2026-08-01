package io.legado.app.ui.design.components.view

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.ui.design.components.NgStatusTagStyle
import io.legado.app.ui.design.components.NgStatusTagVariant

/** Reading NG 管理列表中统一的短状态标签。 */
class NgStatusTagView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        gravity = Gravity.CENTER
        includeFontPadding = false
        applyStyle(NgStatusTagStyle.REGULAR)
        setVariant(NgStatusTagVariant.INFO, NgStatusTagStyle.REGULAR)
    }

    fun bind(
        value: CharSequence,
        variant: NgStatusTagVariant,
        style: NgStatusTagStyle = NgStatusTagStyle.REGULAR
    ) {
        text = value
        applyStyle(style)
        setVariant(variant, style)
    }

    private fun applyStyle(style: NgStatusTagStyle) {
        when (style) {
            NgStatusTagStyle.REGULAR -> {
                minimumWidth = 44.dp
                minHeight = 24.dp
                setPadding(10.dp, 0, 10.dp, 0)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }

            NgStatusTagStyle.COMPACT -> {
                minimumWidth = 42.dp
                minHeight = 20.dp
                setPadding(8.dp, 0, 8.dp, 0)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            }
        }
    }

    private fun setVariant(variant: NgStatusTagVariant, style: NgStatusTagStyle) {
        val (backgroundRes, backgroundColorRes, textColorRes) = when (variant) {
            NgStatusTagVariant.INFO ->
                Triple(R.drawable.ng_bg_tag_info, R.color.ng_info_container, R.color.ng_info)
            NgStatusTagVariant.SUCCESS -> Triple(
                R.drawable.ng_bg_tag_success,
                R.color.ng_success_container,
                R.color.ng_success
            )
            NgStatusTagVariant.WARNING -> Triple(
                R.drawable.ng_bg_tag_warning,
                R.color.ng_warning_container,
                R.color.ng_warning
            )
            NgStatusTagVariant.ERROR ->
                Triple(R.drawable.ng_bg_tag_error, R.color.ng_error_container, R.color.ng_error)
            NgStatusTagVariant.NEUTRAL ->
                Triple(
                    R.drawable.ng_bg_tag_neutral,
                    R.color.ng_neutral_container,
                    R.color.ng_on_surface_variant
                )
        }
        background = when (style) {
            NgStatusTagStyle.REGULAR -> ContextCompat.getDrawable(context, backgroundRes)
            NgStatusTagStyle.COMPACT -> GradientDrawable().apply {
                cornerRadius = 7.dp.toFloat()
                setColor(ContextCompat.getColor(context, backgroundColorRes))
            }
        }
        setTextColor(ContextCompat.getColor(context, textColorRes))
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density + 0.5f).toInt()
}
