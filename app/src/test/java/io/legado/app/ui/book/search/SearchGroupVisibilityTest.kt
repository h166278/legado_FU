package io.legado.app.ui.book.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchGroupVisibilityTest {

    @Test
    fun keepsContentTypesAndCustomGroups() {
        val groups = listOf("小说", "漫画", "音频", "视频", "其它", "自用精品")

        assertEquals(groups, SearchGroupVisibility.visibleGroups(groups))
    }

    @Test
    fun hidesTechnicalAutoGroupsAndSharedBaseUrls() {
        val groups = listOf(
            "有登录入口",
            "无搜索",
            "有发现",
            "事件监听",
            "WebView",
            "有验证码",
            "http://api.example.com",
            "https://api.example.com:8443",
            "https://example.com/custom"
        )

        assertEquals(
            listOf("https://example.com/custom"),
            SearchGroupVisibility.visibleGroups(groups)
        )
    }

    @Test
    fun hidesSourceCheckGroups() {
        val groups = listOf(
            "域名失效",
            "搜索链接规则为空",
            "搜索失效",
            "发现规则为空",
            "发现失效",
            "小说目录失效",
            "漫画正文失效",
            "校验超时",
            "js失效",
            "网站失效",
            "人工复核"
        )

        assertEquals(
            listOf("人工复核"),
            SearchGroupVisibility.visibleGroups(groups)
        )
    }

    @Test
    fun removesHiddenGroupsFromSavedScopeButKeepsSingleSourceScope() {
        assertEquals(
            "小说,自用精品",
            SearchGroupVisibility.visibleScope("小说,WebView,http://api.example.com,自用精品")
        )
        assertEquals(
            "示例书源::https://example.com/source",
            SearchGroupVisibility.visibleScope("示例书源::https://example.com/source")
        )
    }
}
