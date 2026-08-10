package io.legado.app.ui.rss.source.manage

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.setLayout
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

class GroupManageDialog : DialogFragment() {

    private val viewModel: RssSourceViewModel by activityViewModels()
    private var groups by mutableStateOf<List<String>>(emptyList())
    private var editingGroup by mutableStateOf<String?>(null)
    private var addingGroup by mutableStateOf(false)

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
        (view as ComposeView).setContent {
            NgAppTheme(updateSystemBars = false) {
                GroupManagePanel(
                    groups = groups,
                    onAdd = { addingGroup = true },
                    onEdit = { editingGroup = it },
                    onDelete = viewModel::delGroup,
                    onDismiss = { dismissAllowingStateLoss() }
                )
                if (addingGroup) {
                    RssSourceTextDialog(
                        title = stringResource(R.string.add_group),
                        onDismiss = { addingGroup = false },
                        onConfirm = {
                            if (it.isNotBlank()) viewModel.addGroup(it.trim())
                            addingGroup = false
                        }
                    )
                }
                editingGroup?.let { oldGroup ->
                    RssSourceTextDialog(
                        title = stringResource(R.string.group_edit),
                        initialValue = oldGroup,
                        onDismiss = { editingGroup = null },
                        onConfirm = {
                            viewModel.upGroup(oldGroup, it.trim().ifBlank { null })
                            editingGroup = null
                        }
                    )
                }
            }
        }
        lifecycleScope.launch {
            appDb.rssSourceDao.flowGroups().conflate().collect { groups = it }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.9f)
    }
}

@Composable
private fun GroupManagePanel(
    groups: List<String>,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(22.dp),
        color = Color(NgTheme.colors.surface)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.group_manage),
                    modifier = Modifier.weight(1f),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = stringResource(R.string.add_group),
                        tint = Color(NgTheme.colors.onSurface)
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(groups, key = { it }) { group ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(NgTheme.colors.surfaceContainerLow)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = group,
                                modifier = Modifier.weight(1f),
                                color = Color(NgTheme.colors.onSurface),
                                fontSize = 15.sp
                            )
                            TextButton(onClick = { onEdit(group) }) {
                                Text(stringResource(R.string.edit))
                            }
                            TextButton(onClick = { onDelete(group) }) {
                                Text(stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) { Text(stringResource(R.string.close)) }
        }
    }
}
