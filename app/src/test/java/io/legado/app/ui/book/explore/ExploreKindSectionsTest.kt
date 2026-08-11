package io.legado.app.ui.book.explore

import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.entities.rule.FlexChildStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreKindSectionsTest {

    @Test
    fun `only blank full width url item is a section header`() {
        assertTrue(header("男生").isExploreSectionHeader())
        assertFalse(header("真实分类").copy(url = "/rank").isExploreSectionHeader())
        assertFalse(header("按钮").copy(type = ExploreKind.Type.button, action = "run()").isExploreSectionHeader())
        assertFalse(
            ExploreKind(
                title = "占位",
                style = FlexChildStyle(layout_flexBasisPercent = 0.2f)
            ).isExploreSectionHeader()
        )
    }

    @Test
    fun `reading assistant becomes three top level groups`() {
        val kinds = listOf(
            header("男生"),
            category("历史"),
            category("游戏"),
            header("女生"),
            category("短篇"),
            header("出版图书"),
            category("经典文学")
        )

        val result = buildExploreKindSections(kinds)

        assertTrue(result.useTopLevelGroups)
        assertEquals(ExploreKindSectionMode.HEADER_GROUPS, result.mode)
        assertEquals(listOf("男生", "女生", "出版图书"), result.sections.map { it.header?.title })
        assertEquals(listOf(2, 1, 1), result.sections.map { it.items.size })
    }

    @Test
    fun `leading openable categories become an ungrouped top level section`() {
        val result = buildExploreKindSections(
            listOf(
                category("字数排行"),
                category("最近更新"),
                header("男生分类推荐"),
                category("修真"),
                category("魔法"),
                header("女生分类推荐"),
                category("女强"),
                category("婚姻")
            )
        )

        assertTrue(result.useTopLevelGroups)
        assertEquals(ExploreKindSectionMode.HEADER_GROUPS, result.mode)
        assertEquals(
            listOf(null, "男生分类推荐", "女生分类推荐"),
            result.sections.map { it.header?.title }
        )
        assertEquals(listOf(2, 2, 2), result.sections.map { it.items.size })
    }

    @Test
    fun `selecting another group chooses its first openable category`() {
        val ungroupedKind = category("全本小说")
        val maleKind = category("修真")
        val result = buildExploreKindSections(
            listOf(
                ungroupedKind,
                category("日点击榜"),
                header("男生分类推荐"),
                maleKind,
                category("魔法"),
                header("女生分类推荐"),
                category("女强"),
                category("婚姻")
            )
        )

        assertEquals(maleKind, result.kindForSectionSelection(1, ungroupedKind))
        assertEquals(maleKind, result.kindForSectionSelection(1, maleKind))
        assertEquals(0, result.sectionIndexFor(ungroupedKind))
        assertEquals(1, result.sectionIndexFor(maleKind))
    }

    @Test
    fun `single leading category and one named section stay inline`() {
        val result = buildExploreKindSections(
            listOf(
                category("幻梦轻小说首页", basis = 1f),
                ExploreKind(
                    title = "全部",
                    style = FlexChildStyle(layout_flexGrow = 1f, layout_flexBasisPercent = 0.29f)
                ),
                header("以下为题材分类"),
                category("最新"),
                category("校园")
            )
        )

        assertFalse(result.useTopLevelGroups)
        assertEquals(ExploreKindSectionMode.INLINE, result.mode)
        assertEquals(listOf(null), result.sections.map { it.header?.title })
        assertEquals(
            listOf("幻梦轻小说首页", "最新", "校园"),
            result.sections.single().items.map(ExploreKind::title)
        )
    }

    @Test
    fun `detail separates functional controls from category sections`() {
        val result = buildExploreKindSections(
            listOf(
                ExploreKind(
                    title = "说明文字",
                    style = FlexChildStyle(layout_flexGrow = 1f, layout_flexBasisPercent = 0.2f)
                ),
                ExploreKind(
                    title = "空按钮",
                    type = ExploreKind.Type.button,
                    style = FlexChildStyle(layout_flexGrow = 1f, layout_flexBasisPercent = 0.2f)
                ),
                ExploreKind(title = "关键字", type = ExploreKind.Type.text),
                ExploreKind(title = "刷新", type = ExploreKind.Type.button, action = "run()"),
                header("单一分组"),
                category("历史")
            )
        )

        assertFalse(result.useTopLevelGroups)
        assertEquals(
            listOf("历史"),
            result.sections.flatMap { section -> section.items.map(ExploreKind::title) }
        )
        assertEquals(listOf("关键字", "刷新"), result.controls.map(ExploreKind::title))
    }

    @Test
    fun `blank legacy tail is removed from detail categories`() {
        val result = buildExploreKindSections(
            listOf(
                category("全部"),
                category("奇幻"),
                ExploreKind(title = "")
            )
        )

        assertEquals(listOf("全部", "奇幻"), result.sections.single().items.map(ExploreKind::title))
    }

    @Test
    fun `control only source keeps controls without creating categories`() {
        val result = buildExploreKindSections(
            listOf(
                ExploreKind(title = "关键字", type = ExploreKind.Type.text),
                ExploreKind(title = "搜索", type = ExploreKind.Type.button, action = "run()"),
                ExploreKind(title = "排序", type = ExploreKind.Type.select)
            )
        )

        assertTrue(result.sections.isEmpty())
        assertEquals(listOf("关键字", "搜索", "排序"), result.controls.map(ExploreKind::title))
    }

    @Test
    fun `repeated clickable full width roots become groups with all entry`() {
        val result = buildExploreKindSections(
            listOf(
                category("男生", basis = 1f),
                category("玄幻"),
                category("武侠"),
                category("女生", basis = 1f),
                category("言情"),
                category("女强"),
                category("二次元", basis = 1f),
                category("轻小说"),
                category("同人")
            )
        )

        assertTrue(result.useTopLevelGroups)
        assertEquals(ExploreKindSectionMode.CLICKABLE_ROOT_GROUPS, result.mode)
        assertEquals(listOf("男生", "女生", "二次元"), result.sections.map { it.header?.title })
        assertEquals(listOf("全部", "玄幻", "武侠"), result.sections.first().items.map {
            it.displaySectionLabel()
        })
        assertEquals(0.2f, result.sections.first().items.first().style().layout_flexBasisPercent)
        assertEquals("/男生", result.sections.first().items.first().url)
    }

    @Test
    fun `sparse or mixed clickable roots stay inline`() {
        val result = buildExploreKindSections(
            listOf(
                category("首页", basis = 1f),
                category("男生", basis = 1f),
                category("玄幻"),
                category("女生", basis = 1f),
                ExploreKind(title = "关键字", type = ExploreKind.Type.text)
            )
        )

        assertFalse(result.useTopLevelGroups)
        assertEquals(4, result.sections.single().items.size)
        assertEquals(listOf("关键字"), result.controls.map(ExploreKind::title))
    }

    @Test
    fun `functional leading content does not change category grouping`() {
        val result = buildExploreKindSections(
            listOf(
                ExploreKind(
                    title = "导入书源",
                    type = ExploreKind.Type.button,
                    action = "run()"
                ),
                header("男生"),
                category("历史"),
                header("女生"),
                category("言情")
            )
        )

        assertTrue(result.useTopLevelGroups)
        assertEquals(ExploreKindSectionMode.HEADER_GROUPS, result.mode)
        assertEquals(listOf("导入书源"), result.controls.map(ExploreKind::title))
    }

    @Test
    fun `explicit header grammar cannot be claimed by clickable roots`() {
        val result = buildExploreKindSections(
            listOf(
                category("首页", basis = 1f),
                header("男生"),
                category("玄幻"),
                category("武侠"),
                header("女生"),
                category("言情"),
                category("女强")
            )
        )

        assertFalse(result.useTopLevelGroups)
        assertEquals(ExploreKindSectionMode.INLINE, result.mode)
        assertEquals(listOf(null), result.sections.map { it.header?.title })
        assertEquals(
            listOf("首页", "玄幻", "武侠", "言情", "女强"),
            result.sections.single().items.map(ExploreKind::title)
        )
    }

    @Test
    fun `controls inside named section do not reject the category grammar`() {
        val result = buildExploreKindSections(
            listOf(
                header("男生"),
                category("玄幻"),
                ExploreKind(title = "排序", type = ExploreKind.Type.select),
                header("女生"),
                category("言情")
            )
        )

        assertTrue(result.useTopLevelGroups)
        assertEquals(ExploreKindSectionMode.HEADER_GROUPS, result.mode)
        assertEquals(listOf("排序"), result.controls.map(ExploreKind::title))
        assertEquals(
            listOf(listOf("玄幻"), listOf("言情")),
            result.sections.map { section -> section.items.map(ExploreKind::title) }
        )
    }

    @Test
    fun `full width clickable children reject clickable root grammar`() {
        val result = buildExploreKindSections(
            listOf(
                category("男生频道", basis = 1f),
                category("都市娱乐", basis = 1f),
                category("日榜"),
                category("女生频道", basis = 1f),
                category("现代言情", basis = 1f),
                category("月榜")
            )
        )

        assertFalse(result.useTopLevelGroups)
        assertEquals(ExploreKindSectionMode.INLINE, result.mode)
    }

    @Test
    fun `many valid groups are not rejected by their count`() {
        val kinds = buildList {
            repeat(12) { index ->
                add(header("分组$index"))
                add(category("分类$index"))
            }
        }

        val result = buildExploreKindSections(kinds)

        assertTrue(result.useTopLevelGroups)
        assertEquals(12, result.sections.size)
    }

    @Test
    fun `consecutive headings fall back to inline sections`() {
        val result = buildExploreKindSections(
            listOf(
                header("男频专区"),
                header("热门排行"),
                category("日榜"),
                header("最新排行"),
                category("今日")
            )
        )

        assertFalse(result.useTopLevelGroups)
        assertEquals(ExploreKindSectionMode.INLINE, result.mode)
        assertEquals(listOf(null), result.sections.map { it.header?.title })
        assertEquals(
            listOf("日榜", "今日"),
            result.sections.single().items.map(ExploreKind::title)
        )
    }

    @Test
    fun `nested parent headings flatten to one level and prefix duplicate children`() {
        val result = buildExploreKindSections(
            listOf(
                header("男生频道"),
                header("榜单"),
                category("男生必读"),
                category("男生潜力"),
                header("分类"),
                category("男生玄幻"),
                category("男生武侠"),
                header("女生频道"),
                header("榜单"),
                category("女生必读"),
                category("女生潜力"),
                header("分类"),
                category("女生言情"),
                category("女生古言")
            )
        )

        assertTrue(result.useTopLevelGroups)
        assertEquals(ExploreKindSectionMode.HEADER_GROUPS, result.mode)
        assertEquals(
            listOf("男生频道榜单", "男生频道分类", "女生频道榜单", "女生频道分类"),
            result.sections.map { it.header?.displaySectionLabel() }
        )
        assertEquals(listOf(2, 2, 2, 2), result.sections.map { it.items.size })
    }

    @Test
    fun `nested clickable roots flatten to nearest one level groups`() {
        val result = buildExploreKindSections(
            listOf(
                header("男生频道"),
                header("榜单"),
                category("必读榜"),
                category("潜力榜"),
                header("分类"),
                category("玄幻", basis = 1f),
                category("东方玄幻"),
                category("异世大陆"),
                category("武侠", basis = 1f),
                category("传统武侠"),
                category("现代武侠"),
                header("女生频道"),
                header("榜单"),
                category("女生必读"),
                category("女生潜力"),
                header("分类"),
                category("现代言情", basis = 1f),
                category("豪门总裁"),
                category("都市情缘")
            )
        )

        assertTrue(result.useTopLevelGroups)
        assertEquals(
            listOf("男生频道榜单", "玄幻", "武侠", "女生频道榜单", "现代言情"),
            result.sections.map { it.header?.displaySectionLabel() }
        )
        assertEquals(
            listOf("全部", "东方玄幻", "异世大陆"),
            result.sections[1].items.map(ExploreKind::displaySectionLabel)
        )
        assertEquals("/玄幻", result.sections[1].items.first().url)
    }

    @Test
    fun `clickable parent roots flatten without losing their own category`() {
        val result = buildExploreKindSections(
            listOf(
                header("排行榜单"),
                header("人气榜"),
                category("男频人气"),
                category("女频人气"),
                header("新书榜"),
                category("男频新书"),
                category("女频新书"),
                header("书籍分类"),
                category("男频", basis = 1f),
                category("都市", basis = 1f),
                category("都市连载"),
                category("都市完结"),
                category("玄幻", basis = 1f),
                category("玄幻连载"),
                category("玄幻完结"),
                category("女频", basis = 1f),
                category("现代言情", basis = 1f),
                category("言情连载"),
                category("言情完结"),
                category("古代言情", basis = 1f),
                category("古言连载"),
                category("古言完结")
            )
        )

        assertTrue(result.useTopLevelGroups)
        assertEquals(
            listOf("人气榜", "新书榜", "男频", "都市", "玄幻", "女频", "现代言情", "古代言情"),
            result.sections.map { it.header?.displaySectionLabel() }
        )
        assertEquals(
            18,
            result.sections.sumOf { section -> section.items.size }
        )
        assertEquals("/男频", result.sections[2].items.single().url)
        assertEquals("全部", result.sections[2].items.single().displaySectionLabel())
    }

    @Test
    fun `single parent with only two child sections stays inline`() {
        val result = buildExploreKindSections(
            listOf(
                header("频道"),
                header("热门排行"),
                category("日榜"),
                header("最新排行"),
                category("今日")
            )
        )

        assertFalse(result.useTopLevelGroups)
        assertEquals(ExploreKindSectionMode.INLINE, result.mode)
        assertEquals(listOf("日榜", "今日"), result.sections.single().items.map(ExploreKind::title))
    }

    @Test
    fun `duplicate headings do not become ambiguous top groups`() {
        val result = buildExploreKindSections(
            listOf(
                header("主题"),
                category("热血"),
                header("主题"),
                category("治愈")
            )
        )

        assertFalse(result.useTopLevelGroups)
        assertEquals(ExploreKindSectionMode.INLINE, result.mode)
        assertEquals(listOf(null), result.sections.map { it.header?.title })
        assertEquals(
            listOf("热血", "治愈"),
            result.sections.single().items.map(ExploreKind::title)
        )
    }

    @Test
    fun `detail url categories ignore legacy layout format and stay five columns`() {
        assertEquals(
            listOf(5, 2),
            calculateExploreDetailKindRows(List(7) { category("默认$it", basis = -1f) })
                .map { it.size }
        )
        assertEquals(
            listOf(5, 1),
            calculateExploreDetailKindRows(List(6) { category("四列$it", basis = 0.25f) })
                .map { it.size }
        )
        assertEquals(
            listOf(5),
            calculateExploreDetailKindRows(List(5) { category("五列$it", basis = 0.2f) })
                .map { it.size }
        )
        assertEquals(
            listOf(5, 1),
            calculateExploreDetailKindRows(
                List(6) { category("整行分类$it", basis = 1f, wrapBefore = true) }
            )
                .map { it.size }
        )
        assertEquals(
            List(6) { EXPLORE_DETAIL_MAX_SPAN / 5 },
            calculateExploreDetailKindRows(
                List(6) { category("特殊格式$it", basis = 1f, wrapBefore = true) }
            ).flatten().map { it.second }
        )
    }

    @Test
    fun `detail functional controls keep legacy span and wrapping`() {
        val controls = listOf(
            ExploreKind(
                title = "按钮一",
                type = ExploreKind.Type.button,
                action = "run()",
                style = FlexChildStyle(layout_flexBasisPercent = 0.5f)
            ),
            ExploreKind(
                title = "按钮二",
                type = ExploreKind.Type.button,
                action = "run()",
                style = FlexChildStyle(
                    layout_flexBasisPercent = 0.5f,
                    layout_wrapBefore = true
                )
            )
        )

        assertEquals(
            listOf(listOf(30), listOf(30)),
            calculateExploreDetailKindRows(controls).map { row -> row.map { it.second } }
        )
    }

    private fun header(title: String) = ExploreKind(
        title = title,
        style = FlexChildStyle(layout_flexGrow = 1f, layout_flexBasisPercent = 1f)
    )

    private fun category(
        title: String,
        basis: Float = 0.2f,
        wrapBefore: Boolean = false
    ) = ExploreKind(
        title = title,
        url = "/$title",
        style = FlexChildStyle(
            layout_flexGrow = 1f,
            layout_flexBasisPercent = basis,
            layout_wrapBefore = wrapBefore
        )
    )
}
