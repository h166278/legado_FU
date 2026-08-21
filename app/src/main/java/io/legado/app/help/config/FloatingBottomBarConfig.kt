package io.legado.app.help.config

import kotlin.math.roundToInt

object FloatingBottomBarConfig {

    const val AUTOMATIC_BOTTOM_DISTANCE_PX = -1
    const val MIN_BOTTOM_DISTANCE_PX = 0
    const val MAX_BOTTOM_DISTANCE_PX = 100
    const val BOTTOM_DISTANCE_STEP_PX = 5
    const val BOTTOM_DISTANCE_SLIDER_STEPS =
        (MAX_BOTTOM_DISTANCE_PX - MIN_BOTTOM_DISTANCE_PX) / BOTTOM_DISTANCE_STEP_PX - 1
    const val DEFAULT_TRANSPARENCY_PERCENT = 40
    const val MIN_TRANSPARENCY_PERCENT = 0
    const val MAX_TRANSPARENCY_PERCENT = 100

    private const val DEFAULT_BOTTOM_DISTANCE_DP = 12

    /** 未主动调整时保持旧版 12dp 的底部距离；显式值始终按物理像素保存。 */
    fun resolveBottomDistancePx(storedDistancePx: Int, density: Float): Int {
        if (storedDistancePx != AUTOMATIC_BOTTOM_DISTANCE_PX) {
            return normalizeBottomDistancePx(storedDistancePx)
        }
        val safeDensity = density.takeIf { it > 0f } ?: 1f
        return (DEFAULT_BOTTOM_DISTANCE_DP * safeDensity).roundToInt()
            .coerceIn(MIN_BOTTOM_DISTANCE_PX, MAX_BOTTOM_DISTANCE_PX)
    }

    fun normalizeBottomDistancePx(value: Int): Int {
        val clamped = value.coerceIn(MIN_BOTTOM_DISTANCE_PX, MAX_BOTTOM_DISTANCE_PX)
        return (
            (clamped - MIN_BOTTOM_DISTANCE_PX).toFloat() / BOTTOM_DISTANCE_STEP_PX
        ).roundToInt() * BOTTOM_DISTANCE_STEP_PX + MIN_BOTTOM_DISTANCE_PX
    }

    fun normalizeTransparencyPercent(value: Int): Int {
        return value.coerceIn(MIN_TRANSPARENCY_PERCENT, MAX_TRANSPARENCY_PERCENT)
    }

    fun surfaceAlpha(transparencyPercent: Int): Float {
        return 1f - normalizeTransparencyPercent(transparencyPercent) / 100f
    }
}
