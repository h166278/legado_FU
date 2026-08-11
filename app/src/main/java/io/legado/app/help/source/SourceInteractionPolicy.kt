package io.legado.app.help.source

import io.legado.app.exception.NoStackTraceException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Restricts foreground UI started by book-source scripts in a specific coroutine tree.
 */
class SourceInteractionPolicy(
    blockDialogs: Boolean
) : AbstractCoroutineContextElement(Key) {

    private val blockDialogsState = AtomicBoolean(blockDialogs)

    val blockDialogs: Boolean
        get() = blockDialogsState.get()

    fun updateBlockDialogs(blockDialogs: Boolean) {
        blockDialogsState.set(blockDialogs)
    }

    companion object Key : CoroutineContext.Key<SourceInteractionPolicy>
}

class SourceInteractionBlockedException(action: String) :
    NoStackTraceException("已禁止书源弹窗：$action")
