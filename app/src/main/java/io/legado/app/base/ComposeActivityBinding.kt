package io.legado.app.base

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.viewbinding.ViewBinding

/** 单一 Compose 页面在现有 BaseActivity 生命周期中的轻量宿主。 */
class ComposeActivityBinding private constructor(
    val composeView: ComposeView,
) : ViewBinding {

    override fun getRoot() = composeView

    companion object {
        fun inflate(inflater: LayoutInflater): ComposeActivityBinding {
            return ComposeActivityBinding(
                ComposeView(inflater.context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                },
            )
        }
    }
}
