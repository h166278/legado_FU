package io.legado.app.ui.book.search

internal object SearchGroupVisibility {

    private val technicalAutoGroups = setOf(
        "有登录入口",
        "无搜索",
        "有发现",
        "事件监听",
        "WebView",
        "有验证码"
    )

    private val sourceCheckGroups = setOf(
        "域名失效",
        "搜索链接规则为空",
        "搜索失效",
        "发现规则为空",
        "发现失效",
        "校验超时",
        "js失效",
        "网站失效"
    )

    private val sharedBaseUrlGroupRegex = Regex(
        """^https?://[^/?#]+/?$""",
        RegexOption.IGNORE_CASE
    )

    fun visibleGroups(groups: List<String>): List<String> {
        return groups.filter(::isVisible)
    }

    fun visibleScope(scope: String): String {
        if (scope.contains("::")) return scope
        return scope.split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filter(::isVisible)
            .joinToString(",")
    }

    private fun isVisible(group: String): Boolean {
        return group !in technicalAutoGroups &&
                group !in sourceCheckGroups &&
                !group.endsWith("目录失效") &&
                !group.endsWith("正文失效") &&
                !sharedBaseUrlGroupRegex.matches(group)
    }
}
