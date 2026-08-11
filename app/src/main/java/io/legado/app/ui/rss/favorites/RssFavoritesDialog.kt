package io.legado.app.ui.rss.favorites

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import io.legado.app.R
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssStar
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgButton
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.setLayout

class RssFavoritesDialog() : DialogFragment() {

    constructor(rssArticle: RssArticle) : this() {
        arguments = Bundle().apply {
            putString("title", rssArticle.title)
            putString("group", rssArticle.group)
        }
    }

    constructor(rssStar: RssStar) : this() {
        arguments = Bundle().apply {
            putString("title", rssStar.title)
            putString("group", rssStar.group)
        }
    }

    private var title by mutableStateOf("")
    private var group by mutableStateOf("")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        title = arguments?.getString("title").orEmpty()
        group = arguments?.getString("group").orEmpty()
        (view as ComposeView).setContent {
            NgAppTheme(updateSystemBars = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(NgTheme.colors.background)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.favorite),
                            color = Color(NgTheme.colors.onSurface),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        NgFormField(
                            label = stringResource(R.string.title),
                            value = title,
                            onValueChange = { title = it }
                        )
                        NgFormField(
                            label = stringResource(R.string.group),
                            value = group,
                            onValueChange = { group = it }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NgButton(
                                onClick = {
                                    callback?.deleteFavorite()
                                    dismiss()
                                },
                                variant = NgButtonVariant.DANGER,
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.delete)) }
                            TextButton(
                                onClick = ::dismiss,
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.cancel)) }
                            NgButton(
                                onClick = {
                                    callback?.updateFavorite(
                                        title.takeIf(String::isNotBlank),
                                        group.takeIf(String::isNotBlank)
                                    )
                                    dismiss()
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.ok)) }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private val callback get() = (parentFragment as? Callback) ?: (activity as? Callback)

    interface Callback {
        fun updateFavorite(title: String?, group: String?)
        fun deleteFavorite()
    }
}
