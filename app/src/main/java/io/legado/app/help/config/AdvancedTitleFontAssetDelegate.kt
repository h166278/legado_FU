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
        val isWeightedTitle = fontFamily == AdvancedTitleConfig.WEIGHTED_FONT_FAMILY
        val weight = if (isWeightedTitle) {
            runCatching { preferredWeight() }.getOrDefault(400).coerceIn(100, 900)
        } else {
            400
        }
        runCatching { preferredTypeface() }.getOrNull()?.let {
            return if (isWeightedTitle) weighted(it, weight) else it
        }
        val systemFamily = fontFamily.trim().ifEmpty { "sans-serif" }
        return runCatching {
            val base = Typeface.create(systemFamily, Typeface.NORMAL)
            if (isWeightedTitle) weighted(base, weight) else base
        }.getOrNull() ?: Typeface.DEFAULT
    }

    private fun weighted(typeface: Typeface, weight: Int): Typeface =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            Typeface.create(typeface, weight, false)
        } else {
            Typeface.create(typeface, if (weight >= 700) Typeface.BOLD else Typeface.NORMAL)
        }
}
