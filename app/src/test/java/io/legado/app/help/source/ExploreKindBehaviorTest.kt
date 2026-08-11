package io.legado.app.help.source

import io.legado.app.data.entities.rule.ExploreKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ExploreKindBehaviorTest {

    @Test
    fun renderRoleMatchesExploreListBehavior() {
        val kinds = listOf(
            ExploreKind(title = "分类", url = "/books"),
            ExploreKind(title = "按钮", type = "button", action = "run()"),
            ExploreKind(title = "输入", type = "text"),
            ExploreKind(title = "切换", type = "toggle"),
            ExploreKind(title = "选择", type = "select"),
            ExploreKind(title = "ERROR:解析失败", url = "stack"),
            ExploreKind(title = "纯文字"),
            ExploreKind(title = "不可点击按钮", type = "button")
        )

        assertEquals(
            listOf(
                ExploreKindRenderRole.CATEGORY,
                ExploreKindRenderRole.BUTTON,
                ExploreKindRenderRole.TEXT_INPUT,
                ExploreKindRenderRole.TOGGLE,
                ExploreKindRenderRole.SELECT,
                ExploreKindRenderRole.ERROR,
                ExploreKindRenderRole.PASSIVE,
                ExploreKindRenderRole.PASSIVE
            ),
            kinds.map(ExploreKind::renderRole)
        )
    }
}
