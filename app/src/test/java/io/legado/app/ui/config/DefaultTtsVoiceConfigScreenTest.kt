package io.legado.app.ui.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultTtsVoiceConfigScreenTest {

    private val narrator = card(
        slot = DefaultTtsVoiceSlot.NARRATOR,
        role = DefaultTtsVoiceAvatarRole.NARRATOR,
        title = "默认旁白"
    )
    private val dialogueMale = card(
        slot = DefaultTtsVoiceSlot.DIALOGUE_MALE,
        role = DefaultTtsVoiceAvatarRole.MALE,
        title = "默认对白男",
        fallbackTag = "兜底"
    )
    private val dialogueFemale = card(
        slot = DefaultTtsVoiceSlot.DIALOGUE_FEMALE,
        role = DefaultTtsVoiceAvatarRole.FEMALE,
        title = "默认对白女",
        fallbackTag = "兜底"
    )

    @Test
    fun `screen keeps the accepted three card order`() {
        val state = DefaultTtsVoiceConfigScreenState(
            narrator = narrator,
            dialogueMale = dialogueMale,
            dialogueFemale = dialogueFemale
        )

        assertEquals(
            listOf(
                DefaultTtsVoiceSlot.NARRATOR,
                DefaultTtsVoiceSlot.DIALOGUE_MALE,
                DefaultTtsVoiceSlot.DIALOGUE_FEMALE
            ),
            state.cards.map(DefaultTtsVoiceCardUiModel::slot)
        )
    }

    @Test
    fun `each fixed slot maps to a distinct host action`() {
        assertSame(
            DefaultTtsVoiceConfigScreenAction.NarratorClicked,
            DefaultTtsVoiceSlot.NARRATOR.toScreenAction()
        )
        assertSame(
            DefaultTtsVoiceConfigScreenAction.DialogueMaleClicked,
            DefaultTtsVoiceSlot.DIALOGUE_MALE.toScreenAction()
        )
        assertSame(
            DefaultTtsVoiceConfigScreenAction.DialogueFemaleClicked,
            DefaultTtsVoiceSlot.DIALOGUE_FEMALE.toScreenAction()
        )
    }

    @Test
    fun `disabled or explicitly non clickable cards cannot dispatch`() {
        assertTrue(narrator.isInteractive)
        assertFalse(narrator.copy(enabled = false).isInteractive)
        assertFalse(narrator.copy(clickable = false).isInteractive)
    }

    private fun card(
        slot: DefaultTtsVoiceSlot,
        role: DefaultTtsVoiceAvatarRole,
        title: String,
        fallbackTag: String? = null
    ) = DefaultTtsVoiceCardUiModel(
        slot = slot,
        title = title,
        summary = "未设置",
        avatarText = title.takeLast(1),
        avatarRole = role,
        fallbackTag = fallbackTag
    )
}
