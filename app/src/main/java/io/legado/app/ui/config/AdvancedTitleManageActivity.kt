package io.legado.app.ui.config

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.help.config.AdvancedTitlePackageManager
import io.legado.app.utils.dpToPx
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdvancedTitleManageActivity : AppCompatActivity() {

    private lateinit var entriesContainer: LinearLayout

    private val importJson = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val json = runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        require(bytes.size <= AdvancedTitlePackageManager.MAX_JSON_BYTES) {
                            getString(R.string.advanced_title_too_large)
                        }
                        bytes.toString(Charsets.UTF_8)
                    } ?: error(getString(R.string.advanced_title_invalid_json))
                }
            }.getOrElse {
                toastOnUi(it.localizedMessage ?: getString(R.string.error))
                return@launch
            }
            requestNameAndImport(json)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.advanced_title_manage)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
        }
        root.addView(Button(this).apply {
            text = getString(R.string.advanced_title_import_title)
            setOnClickListener { importJson.launch(arrayOf("application/json", "text/plain", "*/*")) }
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.advanced_title_manage_summary)
            setPadding(0, 12.dpToPx(), 0, 8.dpToPx())
        })
        entriesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply {
            addView(entriesContainer)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refreshEntries()
    }

    private fun refreshEntries() {
        lifecycleScope.launch {
            val entries = runCatching {
                AdvancedTitlePackageManager.loadEntries()
            }.getOrElse {
                toastOnUi(it.localizedMessage ?: getString(R.string.error))
                return@launch
            }
            entriesContainer.removeAllViews()
            entries.forEach { entry ->
                val row = LinearLayout(this@AdvancedTitleManageActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 10.dpToPx(), 0, 10.dpToPx())
                }
                row.addView(TextView(this@AdvancedTitleManageActivity).apply {
                    text = buildString {
                        append(entry.name)
                        if (entry.id == AdvancedTitlePackageManager.activeId()) {
                            append("  ")
                            append(getString(R.string.advanced_title_current))
                        }
                    }
                    textSize = 17f
                })
                row.addView(TextView(this@AdvancedTitleManageActivity).apply {
                    text = if (entry.isBuiltin) {
                        getString(R.string.advanced_title_source_builtin)
                    } else {
                        getString(R.string.advanced_title_source_local)
                    }
                })
                row.addView(LinearLayout(this@AdvancedTitleManageActivity).apply {
                    gravity = Gravity.END
                    addView(Button(this@AdvancedTitleManageActivity).apply {
                        text = getString(R.string.advanced_title_apply)
                        isEnabled = entry.id != AdvancedTitlePackageManager.activeId()
                        setOnClickListener {
                            runCatching { AdvancedTitlePackageManager.apply(entry) }
                                .onSuccess {
                                    postEvent(EventBus.UP_CONFIG, arrayListOf(5))
                                    refreshEntries()
                                }
                                .onFailure { error ->
                                    toastOnUi(error.localizedMessage ?: getString(R.string.error))
                                }
                        }
                    })
                    if (!entry.isBuiltin) {
                        addView(Button(this@AdvancedTitleManageActivity).apply {
                            text = getString(R.string.delete)
                            setOnClickListener { confirmDelete(entry) }
                        })
                    }
                })
                entriesContainer.addView(row)
            }
        }
    }

    private fun requestNameAndImport(json: String) {
        val nameInput = EditText(this).apply {
            hint = getString(R.string.advanced_title_name)
            setText(getString(R.string.advanced_title_unnamed))
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.advanced_title_import_title))
            .setView(nameInput)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                lifecycleScope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            AdvancedTitlePackageManager.addOrUpdate(nameInput.text.toString(), json)
                        }
                    }.onSuccess {
                        refreshEntries()
                    }.onFailure { error ->
                        toastOnUi(error.localizedMessage ?: getString(R.string.error))
                    }
                }
            }
            .show()
    }

    private fun confirmDelete(entry: AdvancedTitlePackageManager.Entry) {
        AlertDialog.Builder(this)
            .setMessage(R.string.sure_del)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) { AdvancedTitlePackageManager.delete(entry) }
                    }.onSuccess {
                        postEvent(EventBus.UP_CONFIG, arrayListOf(5))
                        refreshEntries()
                    }.onFailure { error ->
                        toastOnUi(error.localizedMessage ?: getString(R.string.error))
                    }
                }
            }
            .show()
    }
}
