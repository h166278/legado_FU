package io.legado.app.ui.main.explore

import io.legado.app.data.entities.rule.ExploreKind
import kotlin.math.roundToInt

private const val EXPLORE_KIND_MAX_SPAN = 6

internal fun calculateExploreKindRows(
    kinds: List<ExploreKind>
): List<List<Pair<ExploreKind, Int>>> {
    val rows = mutableListOf<MutableList<Pair<ExploreKind, Int>>>()
    var currentRow = mutableListOf<Pair<ExploreKind, Int>>()
    var currentSpan = 0

    fun fillCurrentRowTail() {
        if (currentRow.isEmpty()) return
        val remainingSpan = EXPLORE_KIND_MAX_SPAN - currentSpan
        if (remainingSpan <= 0) return
        val hasUniformSpans = currentRow.map { it.second }.distinct().size == 1
        if (hasUniformSpans && currentRow.size > 1) {
            val sharedSpan = remainingSpan / currentRow.size
            var extraSpan = remainingSpan % currentRow.size
            currentRow.indices.forEach { index ->
                val (kind, span) = currentRow[index]
                val addition = sharedSpan + if (extraSpan > 0) {
                    extraSpan -= 1
                    1
                } else {
                    0
                }
                currentRow[index] = kind to (span + addition)
            }
        } else {
            val (kind, span) = currentRow.last()
            currentRow[currentRow.lastIndex] = kind to (span + remainingSpan)
        }
        currentSpan += remainingSpan
    }

    kinds.forEach { kind ->
        val style = kind.style()
        val span = when {
            style.layout_wrapBefore || style.layout_flexBasisPercent >= 1f -> {
                EXPLORE_KIND_MAX_SPAN
            }

            style.layout_flexBasisPercent > 0f -> {
                (EXPLORE_KIND_MAX_SPAN * style.layout_flexBasisPercent)
                    .roundToInt()
                    .coerceIn(1, EXPLORE_KIND_MAX_SPAN)
            }

            style.layout_flexGrow > 0f -> 3
            else -> 2
        }
        if ((style.layout_wrapBefore && currentRow.isNotEmpty()) ||
            currentSpan + span > EXPLORE_KIND_MAX_SPAN
        ) {
            fillCurrentRowTail()
            rows.add(currentRow)
            currentRow = mutableListOf()
            currentSpan = 0
        }
        currentRow.add(kind to span)
        currentSpan += span
        if (currentSpan >= EXPLORE_KIND_MAX_SPAN) {
            rows.add(currentRow)
            currentRow = mutableListOf()
            currentSpan = 0
        }
    }
    if (currentRow.isNotEmpty()) {
        fillCurrentRowTail()
        rows.add(currentRow)
    }
    return rows
}
