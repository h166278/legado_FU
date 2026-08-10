package io.legado.app.ui.rss.article

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.data.entities.RssReadRecord
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.rss.RssEmptyState
import io.legado.app.ui.rss.read.ReadRss
import io.legado.app.utils.setLayout
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReadRecordDialog(private val origin: String? = null) : DialogFragment() {

    private val viewModel by viewModels<RssSortViewModel>()
    private var records by mutableStateOf<List<RssReadRecord>>(emptyList())

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
                ReadRecordPanel(
                    records = records,
                    onDismiss = ::dismiss,
                    onOpen = { record ->
                        ReadRss.readRss(requireActivity() as AppCompatActivity, record)
                        dismiss()
                    },
                    onClear = {
                        viewModel.deleteAllRecord(origin)
                        records = emptyList()
                    }
                )
            }
        }
        lifecycleScope.launch {
            records = withContext(IO) { viewModel.getRecords(origin) }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}

@Composable
private fun ReadRecordPanel(
    records: List<RssReadRecord>,
    onDismiss: () -> Unit,
    onOpen: (RssReadRecord) -> Unit,
    onClear: () -> Unit
) {
    var confirmClear by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(NgTheme.colors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.read_record),
                    modifier = Modifier.weight(1f),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .clickable(enabled = records.isNotEmpty()) { confirmClear = true }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_outline_delete),
                        contentDescription = stringResource(R.string.clear),
                        tint = Color(NgTheme.colors.onSurface)
                    )
                }
            }
            if (records.isEmpty()) {
                RssEmptyState(
                    stringResource(R.string.empty),
                    Modifier.heightIn(min = 180.dp)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(records, key = { it.origin + '\u0000' + it.record }) { record ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(record) },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(NgTheme.colors.surfaceContainerLow)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    text = record.title.orEmpty().ifBlank { record.record },
                                    color = Color(NgTheme.colors.onSurface),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = record.record,
                                    modifier = Modifier.padding(top = 4.dp),
                                    color = Color(NgTheme.colors.onSurfaceVariant),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
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
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.read_record)) },
            text = {
                Text("${stringResource(R.string.sure_del)}\n${records.size} ${stringResource(R.string.read_record)}")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClear()
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
