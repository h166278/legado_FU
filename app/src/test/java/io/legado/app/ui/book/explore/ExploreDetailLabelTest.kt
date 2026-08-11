package io.legado.app.ui.book.explore

import org.junit.Assert.assertEquals
import org.junit.Test

class ExploreDetailLabelTest {

    @Test
    fun `removes icons emoticons and punctuation from detail labels`() {
        assertEquals("关注", sanitizeExploreDetailLabel("⭐ 关注"))
        assertEquals("推荐", sanitizeExploreDetailLabel("💯推荐"))
        assertEquals("常规小说推荐", sanitizeExploreDetailLabel("✅ 常规 小说 推荐 ✅"))
        assertEquals("最新企划约稿", sanitizeExploreDetailLabel("🆕 最新 企划 约稿 💰"))
        assertEquals("排行榜", sanitizeExploreDetailLabel("👑排行榜👑"))
        assertEquals("男生频道", sanitizeExploreDetailLabel("༺» ʚ 男生频道 ɞ «༻"))
        assertEquals("热度", sanitizeExploreDetailLabel("[热度]"))
        assertEquals("更新", sanitizeExploreDetailLabel("|更新|"))
    }

    @Test
    fun `keeps Chinese English and digits and removes separators`() {
        assertEquals("R18G男频2026", sanitizeExploreDetailLabel("R18G / 男频 - 2026"))
        assertEquals("", sanitizeExploreDetailLabel("⭐💯✅"))
    }
}
