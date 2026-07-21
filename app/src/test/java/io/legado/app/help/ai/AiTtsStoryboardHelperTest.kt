package io.legado.app.help.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiTtsStoryboardHelperTest {

    @Test
    fun duplicateTargetTextIsRemovedWithoutDroppingUsefulContext() {
        val result = AiTtsStoryboardHelper.sanitizePerformanceContext(
            context = listOf(
                "安秋月回答：“QQ1314……，我……我没有手机。”",
                "陈升刚问了她的 QQ 和手机号。",
                "她低声回答，仍有些局促。"
            ),
            targetText = "“QQ1314……，我……我没有手机。”",
            enabled = true
        )

        assertEquals(
            listOf("陈升刚问了她的 QQ 和手机号。", "她低声回答，仍有些局促。"),
            result
        )
    }

    @Test
    fun contextIsBoundedAndDisabledOutsidePerformanceDialogue() {
        val longItem = "情".repeat(100)
        val result = AiTtsStoryboardHelper.sanitizePerformanceContext(
            context = listOf("  ", longItem, longItem, "第二条", "第三条", "第四条"),
            targetText = "当前对白",
            enabled = true
        )

        assertEquals(3, result.size)
        assertEquals(80, result.first().length)
        assertEquals(emptyList<String>(), AiTtsStoryboardHelper.sanitizePerformanceContext(
            context = listOf("不应透传"),
            targetText = "当前对白",
            enabled = false
        ))
    }
}
