package io.legado.app.help.config

import kotlin.math.roundToInt

/**
 * 全局 NG 抽屉的用户外观参数。
 *
 * 颜色始终来自当前 NG 主题；这里仅保存用户对材质强度与几何形态的选择。
 */
object NgDrawerAppearanceConfig {

    const val DEFAULT_TRANSPARENCY_PERCENT = 20
    const val DEFAULT_PRIMARY_STRENGTH_PERCENT = 50
    const val DEFAULT_HORIZONTAL_MARGIN_DP = 0
    const val DEFAULT_CORNER_RADIUS_DP = 28

    const val MIN_PERCENT = 0
    const val MAX_PERCENT = 100
    const val MIN_HORIZONTAL_MARGIN_DP = 0
    const val MAX_HORIZONTAL_MARGIN_DP = 32
    const val HORIZONTAL_MARGIN_STEP_DP = 2
    const val HORIZONTAL_MARGIN_SLIDER_STEPS =
        (MAX_HORIZONTAL_MARGIN_DP - MIN_HORIZONTAL_MARGIN_DP) /
            HORIZONTAL_MARGIN_STEP_DP - 1
    const val MIN_CORNER_RADIUS_DP = 0
    const val MAX_CORNER_RADIUS_DP = 40
    const val CORNER_RADIUS_STEP_DP = 2
    const val CORNER_RADIUS_SLIDER_STEPS =
        (MAX_CORNER_RADIUS_DP - MIN_CORNER_RADIUS_DP) /
            CORNER_RADIUS_STEP_DP - 1

    fun normalizePercent(value: Int): Int = value.coerceIn(MIN_PERCENT, MAX_PERCENT)

    fun strengthFraction(value: Int): Double =
        normalizePercent(value) / MAX_PERCENT.toDouble()

    /** 0% 不透明，100% 完全透明；保留默认值之前较平缓的变化手感。 */
    fun surfaceAlpha(
        transparencyPercent: Int,
        defaultAlpha: Float,
    ): Float {
        val transparency = normalizePercent(transparencyPercent)
        val baseline = defaultAlpha.coerceIn(0f, 1f)
        return if (transparency <= DEFAULT_TRANSPARENCY_PERCENT) {
            val linearProgress = transparency / DEFAULT_TRANSPARENCY_PERCENT.toFloat()
            val progress = linearProgress * linearProgress
            1f + (baseline - 1f) * progress
        } else {
            val remaining = (MAX_PERCENT - transparency).toFloat()
            val range = (MAX_PERCENT - DEFAULT_TRANSPARENCY_PERCENT).toFloat()
            baseline * remaining / range
        }.coerceIn(0f, 1f)
    }

    fun normalizeHorizontalMarginDp(value: Int): Int = normalizeSteppedValue(
        value = value,
        minimum = MIN_HORIZONTAL_MARGIN_DP,
        maximum = MAX_HORIZONTAL_MARGIN_DP,
        step = HORIZONTAL_MARGIN_STEP_DP,
    )

    fun normalizeCornerRadiusDp(value: Int): Int = normalizeSteppedValue(
        value = value,
        minimum = MIN_CORNER_RADIUS_DP,
        maximum = MAX_CORNER_RADIUS_DP,
        step = CORNER_RADIUS_STEP_DP,
    )

    private fun normalizeSteppedValue(
        value: Int,
        minimum: Int,
        maximum: Int,
        step: Int,
    ): Int {
        val clamped = value.coerceIn(minimum, maximum)
        return ((clamped - minimum).toFloat() / step)
            .roundToInt()
            .times(step)
            .plus(minimum)
    }
}
