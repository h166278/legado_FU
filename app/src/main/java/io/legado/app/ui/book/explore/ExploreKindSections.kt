package io.legado.app.ui.book.explore

import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.entities.rule.ExploreKind.Type
import io.legado.app.help.source.isInteractiveExploreControl
import io.legado.app.help.source.isOpenableExploreCategory
import kotlin.math.roundToInt

internal const val EXPLORE_DETAIL_MAX_SPAN = 60
private const val EXPLORE_DETAIL_DEFAULT_SPAN = 12
private const val EXPLORE_CLICKABLE_ROOT_MIN_CHILDREN = 2
private const val EXPLORE_HEADER_GROUP_MIN_NAMED_SECTIONS = 2
private const val EXPLORE_UNGROUPED_MIN_ITEMS = 2
private const val EXPLORE_FLATTENED_SINGLE_PARENT_MIN_CHILD_SECTIONS = 3
private const val EXPLORE_FLATTENED_PARENT_MIN_CHILD_SECTIONS = 2

internal enum class ExploreKindSectionMode {
    INLINE,
    HEADER_GROUPS,
    CLICKABLE_ROOT_GROUPS
}

internal data class ExploreKindSection(
    val header: ExploreKind?,
    val items: List<ExploreKind>
)

private data class FlattenedHeaderSection(
    val nearestHeader: ExploreKind,
    val parentHeader: ExploreKind?,
    val items: List<ExploreKind>
)

internal data class ExploreKindSections(
    val sections: List<ExploreKindSection>,
    val controls: List<ExploreKind>,
    val mode: ExploreKindSectionMode
) {
    val useTopLevelGroups: Boolean
        get() = mode != ExploreKindSectionMode.INLINE
}

internal fun ExploreKindSections.sectionIndexFor(kind: ExploreKind?): Int {
    return sections.indexOfFirst { section -> kind in section.items }
        .takeIf { it >= 0 }
        ?: 0
}

internal fun ExploreKindSections.kindForSectionSelection(
    sectionIndex: Int,
    currentKind: ExploreKind?
): ExploreKind? {
    val sectionItems = sections.getOrNull(sectionIndex)?.items.orEmpty()
    return currentKind?.takeIf { current ->
        current.isOpenableExploreCategory() && current in sectionItems
    } ?: sectionItems.firstOrNull(ExploreKind::isOpenableExploreCategory)
}

internal fun ExploreKind.isExploreSectionHeader(): Boolean {
    val label = viewName?.takeIf { it.isNotBlank() } ?: title
    return type == Type.url &&
            url.isNullOrBlank() &&
            action.isNullOrBlank() &&
            label.isNotBlank() &&
            style().layout_flexBasisPercent >= 1f
}

internal fun buildExploreKindSections(kinds: List<ExploreKind>): ExploreKindSections {
    val controls = kinds.filter(ExploreKind::isInteractiveExploreControl)
    if (kinds.isEmpty()) {
        return ExploreKindSections(
            sections = emptyList(),
            controls = emptyList(),
            mode = ExploreKindSectionMode.INLINE
        )
    }

    val categoryKinds = kinds.filter { kind ->
        kind.isExploreSectionHeader() || kind.isOpenableExploreCategory()
    }
    if (categoryKinds.isEmpty()) {
        return ExploreKindSections(
            sections = emptyList(),
            controls = controls,
            mode = ExploreKindSectionMode.INLINE
        )
    }

    // Explicit blank-url headers and clickable full-width roots are separate
    // grammars. Once explicit headers exist, only the header grammar may claim
    // the list; otherwise a root-like item could steal or reshape that tree.
    if (categoryKinds.any(ExploreKind::isExploreSectionHeader)) {
        val sections = buildHeaderSections(categoryKinds)
        val groupedSections = sections.takeIf(::isSupportedHeaderGroupStructure)
            ?: buildFlattenedHeaderSections(sections)
        return ExploreKindSections(
            sections = groupedSections ?: buildInlineCategorySections(categoryKinds),
            controls = controls,
            mode = if (groupedSections != null) {
                ExploreKindSectionMode.HEADER_GROUPS
            } else {
                ExploreKindSectionMode.INLINE
            }
        )
    }

    buildClickableRootSections(categoryKinds)?.let { sections ->
        return ExploreKindSections(
            sections = sections,
            controls = controls,
            mode = ExploreKindSectionMode.CLICKABLE_ROOT_GROUPS
        )
    }

    return ExploreKindSections(
        sections = buildInlineCategorySections(categoryKinds),
        controls = controls,
        mode = ExploreKindSectionMode.INLINE
    )
}

private fun buildFlattenedHeaderSections(
    sourceSections: List<ExploreKindSection>
): List<ExploreKindSection>? {
    if (sourceSections.any { it.header == null }) return null

    val parentSectionIndices = sourceSections.indices.filter { index ->
        sourceSections[index].header != null && sourceSections[index].items.isEmpty()
    }
    if (parentSectionIndices.isEmpty()) return null

    val childCounts = parentSectionIndices.mapIndexed { position, parentIndex ->
        val nextParentIndex = parentSectionIndices.getOrNull(position + 1) ?: sourceSections.size
        sourceSections.subList(parentIndex + 1, nextParentIndex).count { section ->
            section.header != null && section.items.isNotEmpty()
        }
    }
    val hasSupportedParentShape = if (parentSectionIndices.size == 1) {
        childCounts.single() >= EXPLORE_FLATTENED_SINGLE_PARENT_MIN_CHILD_SECTIONS
    } else {
        childCounts.all { count ->
            count >= EXPLORE_FLATTENED_PARENT_MIN_CHILD_SECTIONS
        }
    }
    if (!hasSupportedParentShape) return null

    var parentHeader: ExploreKind? = null
    val candidates = mutableListOf<FlattenedHeaderSection>()
    sourceSections.forEach { section ->
        val header = section.header ?: return null
        if (section.items.isEmpty()) {
            parentHeader = header
            return@forEach
        }
        if (section.items.any { item -> !item.isOpenableExploreCategory() }) return null

        val rootSections = buildNestedClickableRootSections(section.items)
        if (rootSections != null) {
            rootSections.forEach { rootSection ->
                candidates += FlattenedHeaderSection(
                    nearestHeader = rootSection.header ?: return null,
                    parentHeader = parentHeader ?: header,
                    items = rootSection.items
                )
            }
        } else {
            candidates += FlattenedHeaderSection(
                nearestHeader = header,
                parentHeader = parentHeader,
                items = section.items
            )
        }
    }
    if (candidates.size < EXPLORE_HEADER_GROUP_MIN_NAMED_SECTIONS) return null

    val nearestLabels = candidates.map { candidate ->
        sanitizeExploreDetailLabel(candidate.nearestHeader.displaySectionLabel())
    }
    if (nearestLabels.any(String::isBlank)) return null
    val nearestLabelCounts = nearestLabels.groupingBy { it }.eachCount()

    val result = candidates.mapIndexed { index, candidate ->
        val nearestLabel = nearestLabels[index]
        val finalLabel = if (nearestLabelCounts.getValue(nearestLabel) == 1) {
            nearestLabel
        } else {
            val parentLabel = candidate.parentHeader
                ?.displaySectionLabel()
                ?.let(::sanitizeExploreDetailLabel)
                ?.takeIf(String::isNotBlank)
                ?: return null
            parentLabel + nearestLabel
        }
        ExploreKindSection(
            header = candidate.nearestHeader.withSectionLabel(finalLabel),
            items = candidate.items
        )
    }
    val finalLabels = result.mapNotNull { section ->
        section.header?.displaySectionLabel()
    }
    return result.takeIf {
        finalLabels.size == result.size && finalLabels.distinct().size == finalLabels.size
    }
}

private fun buildNestedClickableRootSections(
    kinds: List<ExploreKind>
): List<ExploreKindSection>? {
    val rootIndices = kinds.indices.filter { index ->
        val kind = kinds[index]
        kind.isOpenableExploreCategory() && styleMakesFullWidth(kind)
    }
    if (rootIndices.isEmpty() || rootIndices.first() != 0) return null

    val rootSections = rootIndices.mapIndexed { rootPosition, rootIndex ->
        val nextRootIndex = rootIndices.getOrNull(rootPosition + 1) ?: kinds.size
        val root = kinds[rootIndex]
        val children = kinds.subList(rootIndex + 1, nextRootIndex)
        ExploreKindSection(
            header = root,
            items = listOf(root.asClickableRootAllItem()) + children
        )
    }
    val directRootsSupported = rootSections.all { section ->
        val children = section.items.drop(1)
        children.size >= EXPLORE_CLICKABLE_ROOT_MIN_CHILDREN &&
                children.all { child ->
                    child.isOpenableExploreCategory() && !styleMakesFullWidth(child)
                }
    }
    if (directRootsSupported) return rootSections

    val parentRootIndices = rootSections.indices.filter { index ->
        rootSections[index].items.size == 1
    }
    if (parentRootIndices.isEmpty()) return null
    val childCounts = parentRootIndices.mapIndexed { position, parentIndex ->
        val nextParentIndex = parentRootIndices.getOrNull(position + 1) ?: rootSections.size
        rootSections.subList(parentIndex + 1, nextParentIndex).count { section ->
            section.items.size > 1
        }
    }
    val hasSupportedParentShape = if (parentRootIndices.size == 1) {
        childCounts.single() >= EXPLORE_FLATTENED_SINGLE_PARENT_MIN_CHILD_SECTIONS
    } else {
        childCounts.all { count ->
            count >= EXPLORE_FLATTENED_PARENT_MIN_CHILD_SECTIONS
        }
    }
    if (!hasSupportedParentShape) return null
    if (rootSections.filterIndexed { index, _ -> index !in parentRootIndices }.any { section ->
            val children = section.items.drop(1)
            children.size < EXPLORE_CLICKABLE_ROOT_MIN_CHILDREN ||
                    children.any { child ->
                        !child.isOpenableExploreCategory() || styleMakesFullWidth(child)
                    }
        }
    ) {
        return null
    }

    var parentRoot: ExploreKind? = null
    val candidates = rootSections.map { section ->
        val root = section.header ?: return null
        if (section.items.size == 1) {
            parentRoot = root
            FlattenedHeaderSection(root, null, section.items)
        } else {
            FlattenedHeaderSection(root, parentRoot, section.items)
        }
    }
    val nearestLabels = candidates.map { candidate ->
        sanitizeExploreDetailLabel(candidate.nearestHeader.displaySectionLabel())
    }
    if (nearestLabels.any(String::isBlank)) return null
    val nearestLabelCounts = nearestLabels.groupingBy { it }.eachCount()
    val result = candidates.mapIndexed { index, candidate ->
        val nearestLabel = nearestLabels[index]
        val finalLabel = if (nearestLabelCounts.getValue(nearestLabel) == 1) {
            nearestLabel
        } else {
            val parentLabel = candidate.parentHeader
                ?.displaySectionLabel()
                ?.let(::sanitizeExploreDetailLabel)
                ?.takeIf(String::isNotBlank)
                ?: return null
            parentLabel + nearestLabel
        }
        ExploreKindSection(
            header = candidate.nearestHeader.withSectionLabel(finalLabel),
            items = candidate.items
        )
    }
    val finalLabels = result.mapNotNull { section -> section.header?.displaySectionLabel() }
    return result.takeIf {
        finalLabels.size == result.size && finalLabels.distinct().size == finalLabels.size
    }
}

private fun buildInlineCategorySections(
    kinds: List<ExploreKind>
): List<ExploreKindSection> {
    val categories = kinds.filter(ExploreKind::isOpenableExploreCategory)
    return if (categories.isEmpty()) {
        emptyList()
    } else {
        listOf(ExploreKindSection(header = null, items = categories))
    }
}

private fun buildHeaderSections(
    kinds: List<ExploreKind>
): List<ExploreKindSection> {
    val sections = mutableListOf<ExploreKindSection>()
    var header: ExploreKind? = null
    var items = mutableListOf<ExploreKind>()

    fun appendSection() {
        if (header != null || items.isNotEmpty()) {
            sections += ExploreKindSection(header, items.toList())
        }
    }

    kinds.forEach { kind ->
        if (kind.isExploreSectionHeader()) {
            appendSection()
            header = kind
            items = mutableListOf()
        } else {
            items += kind
        }
    }
    appendSection()
    return sections
}

private fun isSupportedHeaderGroupStructure(
    sections: List<ExploreKindSection>
): Boolean {
    val leadingUngroupedSection = sections.firstOrNull()?.takeIf { section ->
        section.header == null &&
                section.items.size >= EXPLORE_UNGROUPED_MIN_ITEMS &&
                section.items.all(ExploreKind::isOpenableExploreCategory)
    }
    if (sections.firstOrNull()?.header == null && leadingUngroupedSection == null) {
        return false
    }
    val namedSections = if (leadingUngroupedSection != null) {
        sections.drop(1)
    } else {
        sections
    }
    val groupLabels = namedSections.mapNotNull { it.header?.displaySectionLabel() }
    return namedSections.size >= EXPLORE_HEADER_GROUP_MIN_NAMED_SECTIONS &&
            namedSections.all { section ->
                section.header != null &&
                        section.items.isNotEmpty() &&
                        section.items.all(ExploreKind::isOpenableExploreCategory)
            } &&
            groupLabels.size == namedSections.size &&
            groupLabels.distinct().size == groupLabels.size
}

private fun buildClickableRootSections(
    kinds: List<ExploreKind>
): List<ExploreKindSection>? {
    val rootIndices = kinds.indices.filter { index ->
        val kind = kinds[index]
        kind.isOpenableExploreCategory() && styleMakesFullWidth(kind)
    }
    if (rootIndices.size < 2 || rootIndices.first() != 0) return null

    val sections = rootIndices.mapIndexed { rootPosition, rootIndex ->
        val nextRootIndex = rootIndices.getOrNull(rootPosition + 1) ?: kinds.size
        val root = kinds[rootIndex]
        val children = kinds.subList(rootIndex + 1, nextRootIndex)
        ExploreKindSection(
            header = root,
            items = listOf(root.asClickableRootAllItem()) + children
        )
    }
    val labels = sections.mapNotNull { section -> section.header?.displaySectionLabel() }
    val allSectionsAreUnambiguous = sections.all { section ->
        val children = section.items.drop(1)
        children.size >= EXPLORE_CLICKABLE_ROOT_MIN_CHILDREN &&
                children.all { child ->
                    child.isOpenableExploreCategory() && !styleMakesFullWidth(child)
                }
    }
    if (!allSectionsAreUnambiguous || labels.distinct().size != labels.size) return null
    return sections
}

private fun styleMakesFullWidth(kind: ExploreKind): Boolean {
    return kind.style().layout_flexBasisPercent >= 1f
}

private fun ExploreKind.asClickableRootAllItem(): ExploreKind {
    return copy(
        viewName = "'全部'",
        style = style().copy(
            layout_flexGrow = 1f,
            layout_flexBasisPercent = 0.2f,
            layout_wrapBefore = false
        )
    )
}

private fun ExploreKind.withSectionLabel(label: String): ExploreKind {
    return copy(viewName = "'$label'")
}

internal fun ExploreKind.displaySectionLabel(): String {
    val viewLabel = viewName
    return if (viewLabel != null &&
        viewLabel.length >= 2 &&
        viewLabel.first() == '\'' &&
        viewLabel.last() == '\''
    ) {
        viewLabel.substring(1, viewLabel.lastIndex).trim()
    } else {
        title.trim()
    }
}

internal fun calculateExploreDetailKindRows(
    kinds: List<ExploreKind>
): List<List<Pair<ExploreKind, Int>>> {
    val rows = mutableListOf<MutableList<Pair<ExploreKind, Int>>>()
    var currentRow = mutableListOf<Pair<ExploreKind, Int>>()
    var currentSpan = 0

    fun appendCurrentRow() {
        if (currentRow.isNotEmpty()) rows += currentRow
        currentRow = mutableListOf()
        currentSpan = 0
    }

    kinds.forEach { kind ->
        val style = kind.style()
        val isCategory = kind.isOpenableExploreCategory()
        val span = when {
            isCategory -> EXPLORE_DETAIL_DEFAULT_SPAN
            style.layout_flexBasisPercent >= 1f -> EXPLORE_DETAIL_MAX_SPAN
            style.layout_flexBasisPercent > 0f -> {
                (EXPLORE_DETAIL_MAX_SPAN * style.layout_flexBasisPercent)
                    .roundToInt()
                    .coerceIn(1, EXPLORE_DETAIL_MAX_SPAN)
            }

            else -> EXPLORE_DETAIL_DEFAULT_SPAN
        }
        if ((!isCategory && style.layout_wrapBefore && currentRow.isNotEmpty()) ||
            currentSpan + span > EXPLORE_DETAIL_MAX_SPAN
        ) {
            appendCurrentRow()
        }
        currentRow += kind to span
        currentSpan += span
        if (currentSpan >= EXPLORE_DETAIL_MAX_SPAN) appendCurrentRow()
    }
    appendCurrentRow()
    return rows
}
