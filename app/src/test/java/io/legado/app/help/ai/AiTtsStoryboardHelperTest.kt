package io.legado.app.help.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiTtsStoryboardHelperTest {

    @Test
    fun narratedQuoteReferencesAreNotHintedAsDialogue() {
        val recalled = "那句“靠你了”总往她心窝子钻，钻得她暖暖热热。"
        val summarized = "电话里，沈言卿娇憨甜脆地说了好长一串“很想”。"
        val direct = "“很想很想很想很想……”"
        val colonDialogue = "陈升问：“有多想？”"
        val thought = "她心想：“不能再这样了。”"

        assertEquals("narrator", quoteHint(recalled))
        assertEquals("narrator", quoteHint(summarized))
        assertEquals("character", quoteHint(direct))
        assertEquals("character", quoteHint(colonDialogue))
        assertEquals("thought", quoteHint(thought))
        assertEquals("narrator", AiTtsStoryboardHelper.routedRoleType("narrator", "character"))
        assertEquals("character", AiTtsStoryboardHelper.routedRoleType("character", "character"))
    }

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

    @Test
    fun actorCapabilityIncludesSceneAndLoadsModulesInStableOrder() {
        val capabilities = AiTtsStoryboardHelper.resolveStoryboardSkillCapabilities(
            declaredCapabilities = setOf("performance_instruction")
        )

        assertEquals(listOf("scene_context", "performance_instruction"), capabilities)
        assertEquals(
            listOf(
                "skills/tts_storyboard/modules/protocol.md",
                "skills/tts_storyboard/modules/base-routing.md",
                "skills/tts_storyboard/modules/scene-context.md",
                "skills/tts_storyboard/modules/performance-instruction.md"
            ),
            AiTtsStoryboardHelper.storyboardSkillAssets(capabilities)
        )
        assertEquals(
            listOf("scene_context"),
            AiTtsStoryboardHelper.resolveStoryboardSkillCapabilities(
                declaredCapabilities = setOf("scene_context")
            )
        )
        assertEquals(
            emptyList<String>(),
            AiTtsStoryboardHelper.resolveStoryboardSkillCapabilities(
                declaredCapabilities = emptySet()
            )
        )
    }

    @Test
    fun performanceInstructionIsShortAndProviderNeutral() {
        assertEquals(
            "刚哭过，开口迟疑，后半句逐渐变轻",
            AiTtsStoryboardHelper.sanitizePerformanceInstruction(
                instruction = "  刚哭过，开口迟疑，后半句逐渐变轻  ",
                targetText = "我生活费丢了。",
                enabled = true
            )
        )
        assertEquals(
            "",
            AiTtsStoryboardHelper.sanitizePerformanceInstruction(
                instruction = "我生活费丢了。",
                targetText = "我生活费丢了。",
                enabled = true
            )
        )
        assertEquals(
            "",
            AiTtsStoryboardHelper.sanitizePerformanceInstruction(
                instruction = "开口迟疑",
                targetText = "我生活费丢了。",
                enabled = false
            )
        )
    }

    private fun quoteHint(text: String): String {
        val start = text.indexOf('“')
        val end = text.indexOf('”', start + 1) + 1
        return AiTtsStoryboardHelper.quoteRoleHint(text, start, end)
    }
}
