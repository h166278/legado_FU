package io.legado.app.ui.book.read.config

import android.content.Context
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.Window
import androidx.activity.ComponentDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.legado.app.constant.PreferKey
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.getPrefString
import io.legado.app.utils.hideSoftInput
import io.legado.app.utils.putPrefString
import io.legado.app.utils.setLayout


class PageKeyDialog(context: Context) : ComponentDialog(context) {

    private var prevKeys by mutableStateOf(context.getPrefString(PreferKey.prevKeys).orEmpty())
    private var nextKeys by mutableStateOf(context.getPrefString(PreferKey.nextKeys).orEmpty())
    private var focusedField: FocusedField? = null

    override fun onStart() {
        super.onStart()
        window?.run {
            setBackgroundDrawableResource(android.R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
        }
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(
            ComposeView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    NgAppTheme(
                        snapshot = ReadDrawerStyle.themeSnapshot(context),
                        updateSystemBars = false,
                    ) {
                        PageKeyDialogContent(
                            prevKeys = prevKeys,
                            nextKeys = nextKeys,
                            onPrevKeysChanged = { prevKeys = it },
                            onNextKeysChanged = { nextKeys = it },
                            onPrevFocusChanged = { updateFocus(FocusedField.PREV, it) },
                            onNextFocusChanged = { updateFocus(FocusedField.NEXT, it) },
                            onReset = {
                                prevKeys = ""
                                nextKeys = ""
                            },
                            onConfirm = {
                                context.putPrefString(PreferKey.prevKeys, prevKeys)
                                context.putPrefString(PreferKey.nextKeys, nextKeys)
                                dismiss()
                            },
                        )
                    }
                }
            }
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_DEL) {
            when (focusedField) {
                FocusedField.PREV -> prevKeys = appendKeyCode(prevKeys, keyCode)
                FocusedField.NEXT -> nextKeys = appendKeyCode(nextKeys, keyCode)
                null -> return super.onKeyDown(keyCode, event)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun dismiss() {
        super.dismiss()
        currentFocus?.hideSoftInput()
    }

    private fun updateFocus(field: FocusedField, focused: Boolean) {
        if (focused) {
            focusedField = field
        } else if (focusedField == field) {
            focusedField = null
        }
    }

    private fun appendKeyCode(value: String, keyCode: Int): String {
        return if (value.isEmpty() || value.endsWith(",")) {
            value + keyCode
        } else {
            "$value,$keyCode"
        }
    }

    private enum class FocusedField {
        PREV,
        NEXT,
    }

}
