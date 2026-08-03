package io.legado.app.ui.widget.image

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.graphics.ColorUtils
import io.legado.app.R
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor

class AccentStrokeImageButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageButton(context, attrs, defStyleAttr) {

    private val useSurfaceBackground: Boolean

    init {
        val typedArray = context.obtainStyledAttributes(
            attrs,
            intArrayOf(R.attr.useSurfaceBackground)
        )
        useSurfaceBackground = typedArray.getBoolean(0, false)
        typedArray.recycle()
        upStyle()
    }

    private fun upStyle() {
        val accentColor = if (isInEditMode) {
            context.getCompatColor(R.color.accent)
        } else {
            ThemeStore.accentColor(context)
        }
        imageTintList = ColorStateList.valueOf(accentColor)
        val backgroundBuilder = Selector.shapeBuild()
            .setCornerRadius(12.dpToPx())
            .setStrokeWidth(1.dpToPx())
            .setDefaultStrokeColor(accentColor)
        if (useSurfaceBackground) {
            val white = context.getCompatColor(R.color.white)
            backgroundBuilder
                .setDefaultBgColor(ColorUtils.setAlphaComponent(white, 219))
                .setPressedBgColor(ColorUtils.setAlphaComponent(white, 245))
        } else {
            backgroundBuilder.setPressedBgColor(ColorUtils.setAlphaComponent(accentColor, 24))
        }
        background = backgroundBuilder.create()
    }
}
