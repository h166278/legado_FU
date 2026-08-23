package io.legado.app.help.config

import android.graphics.Typeface
import com.airbnb.lottie.FontAssetDelegate

/**
 * Prevents Lottie from falling back to a non-existent assets/fonts/<family>.ttf file.
 *
 * Lottie 6 calls the three-argument overload first and only then the legacy overload,
 * so both must return a typeface to keep drawing safe across imported animations.
 */
internal class AdvancedTitleFontAssetDelegate(
    private val preferredTypeface: () -> Typeface? = { null },
    private val preferredWeight: () -> Int = { 400 }
) : FontAssetDelegate() {

    override fun fetchFont(fontFamily: String): Typeface = resolve(fontFamily)

    override fun fetchFont(
        fontFamily: String,
        fontStyle: String,
        fontName: String
    ): Typeface = resolve(fontFamily)

    private fun resolve(fontFamily: String): Typeface {
        val weight = resolveWeight(fontFamily)
        runCatching { preferredTypeface() }.getOrNull()?.let {
            return if (weight > 0) weighted(it, weight) else it
        }
        val systemFamily = fontFamily.trim().ifEmpty { "sans-serif" }
        return runCatching {
            val base = Typeface.create(systemFamily, Typeface.NORMAL)
            if (weight > 0) weighted(base, weight) else base
        }.getOrNull() ?: Typeface.DEFAULT
    }

    /**
     * 从字体名解析字重：
     * - 新格式 `legado_advanced_title_weighted_700`：取数字后缀（数值已编码，动画重载后与设置一致）
     * - 旧格式 `legado_advanced_title_weighted`（无后缀）：回退到当前设置值
     * - 其它字体：400
     */
    private fun resolveWeight(fontFamily: String): Int {
        if (!fontFamily.startsWith(AdvancedTitleConfig.WEIGHTED_FONT_FAMILY)) return 400
        val suffix = fontFamily.substringAfterLast('_')
        return suffix.toIntOrNull()?.coerceIn(100, 900) ?: preferredWeight()
    }

    private fun weighted(typeface: Typeface, weight: Int): Typeface {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            // Static fonts may silently return the original face for numeric weights.
            // Use the platform's bold face as a visible fallback when no weight axis exists.
            val numeric = Typeface.create(typeface, weight, false)
            if (weight >= 700 && !supportsWeightAxis(typeface)) {
                return Typeface.create(typeface, Typeface.BOLD)
            }
            return numeric
        }
        return Typeface.create(typeface, if (weight >= 700) Typeface.BOLD else Typeface.NORMAL)
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.P)
    private fun supportsWeightAxis(typeface: Typeface): Boolean = runCatching {
        typeface.isSupportedAxes(intArrayOf(Typeface.WEIGHT_AXIS))
    }.getOrDefault(false)
}
