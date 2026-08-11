package io.legado.app.ui.config

import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.help.tts.TtsVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsVoiceFilterSupportTest {

    @Test
    fun `normalizes and orders language labels`() {
        val voices = listOf(
            TtsVoice(id = "1", name = "English", language = "en-US"),
            TtsVoice(id = "2", name = "Chinese", language = "zh-CN/yue"),
            TtsVoice(id = "3", name = "Japanese", language = "ja-JP")
        )

        assertEquals(
            listOf("中", "英", "日", "粤"),
            TtsVoiceFilterSupport.availableLanguageLabels(voices)
        )
    }

    @Test
    fun `normalizes known genders`() {
        assertEquals("男", TtsVoiceFilterSupport.genderLabel("male"))
        assertEquals("女", TtsVoiceFilterSupport.genderLabel("Woman"))
        assertEquals(null, TtsVoiceFilterSupport.genderLabel("neutral"))
    }

    @Test
    fun `voice search matches display name only`() {
        val voice = TtsVoice(
            id = "voice-001",
            name = "书卷文雅",
            language = "zh-CN",
            gender = "female",
            style = "高冷",
            tags = listOf("青年")
        )
        val option = TtsVoiceOption(
            engine = TtsEngineSetting(
                id = "mossland",
                name = "Mossland",
                type = TtsEngineType.SCRIPT
            ),
            voice = voice,
            systemDefault = false
        )

        assertTrue(TtsVoiceFilterSupport.matchesName(voice, "书卷"))
        assertFalse(TtsVoiceFilterSupport.matchesName(voice, "高冷"))
        assertFalse(TtsVoiceFilterSupport.matchesName(voice, "voice-001"))
        assertTrue(option.matchesName("书卷"))
        assertFalse(option.matchesName("高冷"))
        assertFalse(option.matchesName("青年"))
        assertFalse(option.matchesName("Mossland"))
        assertFalse(option.matchesName("voice-001"))
    }
}
