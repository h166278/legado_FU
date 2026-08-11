package io.legado.app.ui.book.read.page.provider

import io.legado.app.ui.book.read.page.entities.ReadTitleSegment

/** 将 MD3 的标题分段配置转换为 NG 可直接排版的标题片段。 */
object ReadTitleStyleParser {

    fun parse(
        title: String,
        type: Int,
        distance: Int,
        flags: String,
        subTitleScale: Float,
    ): List<ReadTitleSegment> {
        if (title.isBlank()) return emptyList()
        val texts = when (type) {
            1 -> if (distance in 1 until title.length) {
                listOf(title.take(distance), title.drop(distance))
            } else {
                listOf(title)
            }
            2 -> splitAfterFlags(title, flags.split(',').map(String::trim))
            3 -> splitAfterRegex(title, flags)
            else -> listOf(title)
        }
        return texts.filter(String::isNotBlank).mapIndexed { index, text ->
            ReadTitleSegment(text.trim(), index == 0, if (index == 0) 1f else subTitleScale)
        }
    }

    private fun splitAfterFlags(title: String, flags: List<String>): List<String> {
        val activeFlags = flags.filter(String::isNotEmpty)
        if (activeFlags.isEmpty()) return listOf(title)
        val pattern = activeFlags.joinToString("|") { Regex.escape(it) }
        return splitAfterRegex(title, pattern)
    }

    private fun splitAfterRegex(title: String, pattern: String): List<String> {
        if (pattern.isBlank()) return listOf(title)
        return runCatching { title.split(Regex("(?<=$pattern)")) }
            .getOrDefault(listOf(title))
    }
}
