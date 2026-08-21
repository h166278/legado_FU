package io.legado.app.ui.association

import android.content.DialogInterface
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormSwitchRow
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.rss.RssEmptyState
import io.legado.app.ui.widget.dialog.CodeDialog
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment

/** 导入 RSS 源。解析和覆盖策略沿用原 ViewModel，预览与选项改为 Compose。 */
class ImportRssSourceDialog() : DialogFragment(), CodeDialog.Callback {

    constructor(source: String, finishOnDismiss: Boolean = false) : this() {
        arguments = Bundle().apply {
            putString("source", source)
            putBoolean("finishOnDismiss", finishOnDismiss)
        }
    }

    private val viewModel by viewModels<ImportRssSourceViewModel>()
    private var sources by mutableStateOf<List<RssSource>>(
        emptyList(),
        referentialEqualityPolicy()
    )
    private var selectedIndices by mutableStateOf<Set<Int>>(emptySet())
    private var loading by mutableStateOf(true)
    private var importing by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var groupDialog by mutableStateOf(false)
    private var keepName by mutableStateOf(AppConfig.importKeepName)
    private var keepGroup by mutableStateOf(AppConfig.importKeepGroup)
    private var keepEnable by mutableStateOf(AppConfig.importKeepEnable)
    private var showComment by mutableStateOf(AppConfig.importShowComment)

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
                ImportRssSourcePanel(
                    sources = sources,
                    localSources = viewModel.checkSources,
                    selectedIndices = selectedIndices,
                    loading = loading,
                    importing = importing,
                    error = error,
                    keepName = keepName,
                    keepGroup = keepGroup,
                    keepEnable = keepEnable,
                    showComment = showComment,
                    onKeepNameChange = {
                        keepName = it
                        putPrefBoolean(PreferKey.importKeepName, it)
                    },
                    onKeepGroupChange = {
                        keepGroup = it
                        putPrefBoolean(PreferKey.importKeepGroup, it)
                    },
                    onKeepEnableChange = {
                        keepEnable = it
                        AppConfig.importKeepEnable = it
                    },
                    onShowCommentChange = {
                        showComment = it
                        AppConfig.importShowComment = it
                    },
                    onToggle = ::toggleSelection,
                    onToggleAll = ::toggleAll,
                    onEdit = ::editSource,
                    onGroup = { groupDialog = true },
                    onDismiss = { dismissAllowingStateLoss() },
                    onImport = ::importSelected
                )
                if (groupDialog) {
                    ImportRssGroupDialog(
                        initialName = viewModel.groupName.orEmpty(),
                        initialAdd = viewModel.isAddGroup,
                        suggestions = appDb.rssSourceDao.allGroups().toList(),
                        onDismiss = { groupDialog = false },
                        onConfirm = { name, add ->
                            viewModel.groupName = name.takeIf(String::isNotBlank)
                            viewModel.isAddGroup = add
                            groupDialog = false
                        }
                    )
                }
            }
        }
        viewModel.errorLiveData.observe(viewLifecycleOwner) {
            loading = false
            error = it
        }
        viewModel.successLiveData.observe(viewLifecycleOwner) {
            loading = false
            if (it > 0) {
                sources = viewModel.allSources.toList()
                selectedIndices = viewModel.selectStatus.indices
                    .filterTo(linkedSetOf()) { index -> viewModel.selectStatus[index] }
            } else {
                error = getString(R.string.wrong_format)
            }
        }
        val source = arguments?.getString("source")
        if (source.isNullOrEmpty()) {
            dismiss()
        } else {
            viewModel.importSource(source)
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (arguments?.getBoolean("finishOnDismiss") == true) activity?.finish()
    }

    private fun toggleSelection(index: Int) {
        if (index !in sources.indices) return
        selectedIndices = selectedIndices.toMutableSet().apply {
            if (!add(index)) remove(index)
        }
        viewModel.selectStatus[index] = index in selectedIndices
    }

    private fun toggleAll() {
        selectedIndices = if (selectedIndices.size == sources.size) {
            emptySet()
        } else {
            sources.indices.toSet()
        }
        viewModel.selectStatus.indices.forEach {
            viewModel.selectStatus[it] = it in selectedIndices
        }
    }

    private fun editSource(index: Int) {
        val source = sources.getOrNull(index) ?: return
        showDialogFragment(
            CodeDialog(
                GSON.toJson(source),
                disableEdit = false,
                requestId = index.toString()
            )
        )
    }

    private fun importSelected() {
        if (importing) return
        importing = true
        viewModel.importSelect {
            importing = false
            dismissAllowingStateLoss()
        }
    }

    override fun onCodeSave(code: String, requestId: String?) {
        val index = requestId?.toIntOrNull() ?: return
        GSON.fromJsonObject<RssSource>(code).getOrNull()?.let { edited ->
            viewModel.allSources[index] = edited
            sources = sources.toMutableList().apply { set(index, edited) }
        }
    }
}

@Composable
private fun ImportRssSourcePanel(
    sources: List<RssSource>,
    localSources: List<RssSource?>,
    selectedIndices: Set<Int>,
    loading: Boolean,
    importing: Boolean,
    error: String?,
    keepName: Boolean,
    keepGroup: Boolean,
    keepEnable: Boolean,
    showComment: Boolean,
    onKeepNameChange: (Boolean) -> Unit,
    onKeepGroupChange: (Boolean) -> Unit,
    onKeepEnableChange: (Boolean) -> Unit,
    onShowCommentChange: (Boolean) -> Unit,
    onToggle: (Int) -> Unit,
    onToggleAll: () -> Unit,
    onEdit: (Int) -> Unit,
    onGroup: () -> Unit,
    onDismiss: () -> Unit,
    onImport: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 760.dp),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        color = Color(NgTheme.colors.surface)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.import_rss_source),
                    modifier = Modifier.weight(1f),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onGroup) { Text(stringResource(R.string.group)) }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                NgFormSwitchRow(stringResource(R.string.keep_original_name), keepName, onKeepNameChange)
                NgFormSwitchRow(stringResource(R.string.keep_group), keepGroup, onKeepGroupChange)
                NgFormSwitchRow(stringResource(R.string.keep_enable), keepEnable, onKeepEnableChange)
                NgFormSwitchRow(
                    stringResource(R.string.show_source_comment),
                    showComment,
                    onShowCommentChange
                )
            }
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(32.dp),
                    color = Color(NgTheme.colors.primary)
                )
                error != null -> RssEmptyState(error, Modifier.heightIn(min = 180.dp))
                else -> LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(sources, key = { index, _ -> index }) { index, source ->
                        val local = localSources.getOrNull(index)
                        var commentExpanded by remember(index, source.sourceUrl) {
                            mutableStateOf(false)
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(index) },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(NgTheme.colors.surfaceContainerLow)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = index in selectedIndices,
                                    onCheckedChange = { onToggle(index) }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = source.sourceName,
                                        color = Color(NgTheme.colors.onSurface),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (showComment && !source.sourceComment.isNullOrBlank()) {
                                        Text(
                                            text = source.sourceComment.orEmpty(),
                                            modifier = Modifier.clickable {
                                                commentExpanded = !commentExpanded
                                            },
                                            color = Color(NgTheme.colors.onSurfaceVariant),
                                            fontSize = 12.sp,
                                            maxLines = if (commentExpanded) Int.MAX_VALUE else 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Text(
                                    text = when {
                                        local == null -> "新增"
                                        source.lastUpdateTime > local.lastUpdateTime -> "更新"
                                        else -> "已有"
                                    },
                                    color = Color(NgTheme.colors.primary),
                                    fontSize = 12.sp
                                )
                                TextButton(onClick = { onEdit(index) }) {
                                    Text(stringResource(R.string.open))
                                }
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onToggleAll, modifier = Modifier.weight(1f)) {
                    Text("${selectedIndices.size} / ${sources.size}")
                }
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(
                    onClick = onImport,
                    enabled = selectedIndices.isNotEmpty() && !importing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (importing) stringResource(R.string.importing)
                        else stringResource(R.string.ok)
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportRssGroupDialog(
    initialName: String,
    initialAdd: Boolean,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var add by remember(initialAdd) { mutableStateOf(initialAdd) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.diy_edit_source_group)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NgFormField(
                    label = stringResource(R.string.group_name),
                    value = name,
                    onValueChange = { name = it }
                )
                if (suggestions.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(suggestions, key = { it }) { suggestion ->
                            Surface(
                                onClick = { name = suggestion },
                                shape = RoundedCornerShape(14.dp),
                                color = Color(NgTheme.colors.surfaceContainerLow)
                            ) {
                                Text(
                                    text = suggestion,
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp,
                                        vertical = 7.dp
                                    ),
                                    color = Color(NgTheme.colors.onSurface),
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                NgFormSwitchRow(
                    title = stringResource(R.string.add_group),
                    checked = add,
                    onCheckedChange = { add = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), add) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
