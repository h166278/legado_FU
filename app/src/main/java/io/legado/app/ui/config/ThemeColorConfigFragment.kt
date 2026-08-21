package io.legado.app.ui.config

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.help.config.NgColorConfigStore
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgColorSystem
import io.legado.app.utils.postEvent

class ThemeColorConfigFragment : BaseFragment(R.layout.fragment_theme_color_config),
    ConfigBackHandler {

    private var hasChanges = false

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        activity?.setTitle(R.string.ng_custom_colors)
        (view as ComposeView).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val flow = NgColorConfigStore.observe(requireContext())
                val observed by flow.collectAsState()
                val colors = observed ?: NgColorConfigStore.current(requireContext())
                NgAppTheme {
                    ThemeColorConfigScreen(
                        colors = colors,
                        onColorsChanged = ::updateColors
                    )
                }
            }
        }
    }

    override fun onConfigBackPressed(): Boolean {
        if (!hasChanges) return false
        hasChanges = false
        parentFragmentManager.popBackStack()
        requireActivity().window.decorView.post {
            postEvent(EventBus.RECREATE, "")
        }
        return true
    }

    private fun updateColors(colors: NgColorSystem) {
        if (colors == NgColorConfigStore.current(requireContext())) return
        hasChanges = true
        NgColorConfigStore.update(requireContext(), colors)
    }
}
