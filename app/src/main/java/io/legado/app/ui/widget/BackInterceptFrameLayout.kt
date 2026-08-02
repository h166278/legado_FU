package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.FrameLayout

/** 在输入法消费旧版系统 Back 前，把事件交给页面处理。 */
class BackInterceptFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var onPreImeBack: (() -> Unit)? = null

    fun setOnPreImeBackListener(listener: (() -> Unit)?) {
        onPreImeBack = listener
    }

    override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK &&
            event.action == KeyEvent.ACTION_UP &&
            !event.isCanceled
        ) {
            onPreImeBack?.invoke()
            return true
        }
        return super.dispatchKeyEventPreIme(event)
    }
}
