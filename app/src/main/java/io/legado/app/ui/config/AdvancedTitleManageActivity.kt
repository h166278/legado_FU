package io.legado.app.ui.config

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.help.config.AdvancedTitleConfig
import io.legado.app.help.config.AdvancedTitleFontAssetDelegate
import io.legado.app.help.config.AdvancedTitleNetworkImportPolicy
import io.legado.app.help.config.AdvancedTitlePackageManager
import io.legado.app.help.http.okHttpClient
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

class AdvancedTitleManageActivity : AppCompatActivity() {

    private lateinit var entriesContainer: LinearLayout

    private val importJson = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val json = runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        AdvancedTitleNetworkImportPolicy.readUtf8Bounded(input)
                    } ?: error(getString(R.string.advanced_title_invalid_json))
                }
            }.getOrElse {
                toastOnUi(it.localizedMessage ?: getString(R.string.error))
                return@launch
            }
            requestNameAndSave(json)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.advanced_title_manage)
        setContentView(createContent())
    }

    override fun onResume() {
        super.onResume()
        refreshEntries()
    }

    private fun createContent(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(20.dpToPx(), 12.dpToPx(), 20.dpToPx(), 18.dpToPx())
        addView(TextView(this@AdvancedTitleManageActivity).apply {
            text = getString(R.string.advanced_title_manage_summary)
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
            setPadding(0, 10.dpToPx(), 0, 18.dpToPx())
        })
        entriesContainer = LinearLayout(this@AdvancedTitleManageActivity).apply {
            orientation = LinearLayout.VERTICAL
        }
        addView(ScrollView(this@AdvancedTitleManageActivity).apply {
            isFillViewport = true
            addView(entriesContainer)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(Button(this@AdvancedTitleManageActivity).apply {
            text = getString(R.string.advanced_title_add)
            setOnClickListener { showAddSheet() }
            // 背景跟随当前主题主色（用户自定义主题色 / 深浅主题切换时自动更新）
            val primary = ThemeStore.primaryColor(this@AdvancedTitleManageActivity)
            background = Selector.shapeBuild()
                .setCornerRadius(12.dpToPx())
                .setDefaultBgColor(primary)
                .setPressedBgColor(ColorUtils.darkenColor(primary))
                .create()
            setTextColor(
                if (ColorUtils.isColorLight(primary)) {
                    android.graphics.Color.BLACK
                } else {
                    android.graphics.Color.WHITE
                }
            )
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dpToPx()).apply {
            topMargin = 12.dpToPx()
        })
    }

    private fun refreshEntries() {
        lifecycleScope.launch {
            val entries = runCatching { AdvancedTitlePackageManager.loadEntries() }.getOrElse {
                toastOnUi(it.localizedMessage ?: getString(R.string.error))
                return@launch
            }
            val entryTemplates = withContext(Dispatchers.IO) {
                entries.map { entry -> entry to runCatching { AdvancedTitlePackageManager.readTemplate(entry) }.getOrNull() }
            }
            entriesContainer.removeAllViews()
            entryTemplates.forEach { (entry, template) ->
                entriesContainer.addView(
                    createEntryCard(entry, template),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { bottomMargin = 10.dpToPx() },
                )
            }
        }
    }

    private fun createEntryCard(
        entry: AdvancedTitlePackageManager.Entry,
        template: String?,
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setOnClickListener { showStyleEditor(entry) }
        gravity = Gravity.CENTER_VERTICAL
        setPadding(14.dpToPx(), 14.dpToPx(), 10.dpToPx(), 14.dpToPx())
        setBackgroundResource(R.drawable.advanced_title_card_background)
        addView(LottieAnimationView(context).apply {
            layoutParams = LinearLayout.LayoutParams(92.dpToPx(), 72.dpToPx())
            // 必须设置字体委托：Lottie 文本层绘制时按 fFamily 从 assets 加载
            // fonts/<family>.ttf，APK 无此文件会抛 "Font asset not found" 导致
            // 绘制阶段（onDraw）崩溃——这是模版预览闪退的根因
            setFontAssetDelegate(AdvancedTitleFontAssetDelegate())
            // 预览解析失败不应导致管理页崩溃，降级为空白预览
            runCatching {
                template?.let { setAnimationFromJson(AdvancedTitleConfig.stripTextLayers(it), entry.id) }
                repeatCount = LottieDrawable.INFINITE
                if (template != null) playAnimation()
            }
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = entry.name
                textSize = 18f
                maxLines = 2
            })
            addView(TextView(context).apply {
                val source = getString(
                    if (entry.isBuiltin) {
                        R.string.advanced_title_source_builtin
                    } else {
                        R.string.advanced_title_source_local
                    }
                )
                text = if (entry.id == AdvancedTitlePackageManager.activeId()) {
                    getString(R.string.advanced_title_current_source, source)
                } else {
                    source
                }
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
                setPadding(0, 6.dpToPx(), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        Button(context).apply {
            val active = entry.id == AdvancedTitlePackageManager.activeId()
            text = getString(if (active) R.string.advanced_title_applied else R.string.advanced_title_apply)
            isEnabled = !active
            setOnClickListener { applyEntry(entry) }
        }.also { addView(it) }
        TextView(context).apply {
            text = "⋮"
            textSize = 28f
            gravity = Gravity.CENTER
            contentDescription = getString(R.string.more)
            setPadding(10.dpToPx(), 0, 0, 0)
            visibility = if (entry.isBuiltin) View.GONE else View.VISIBLE
            setOnClickListener { showEntryMenu(this, entry) }
        }.also { addView(it, LinearLayout.LayoutParams(40.dpToPx(), 48.dpToPx())) }
    }

    private fun showStyleEditor(entry: AdvancedTitlePackageManager.Entry) {
        val config = entry.config
        val fields = listOf(
            getString(R.string.advanced_title_font_weight) to (config.normalizedFontWeightOrNull()
                ?: AdvancedTitleConfig.fontWeight).toString(),
            getString(R.string.advanced_title_font_size) to (config.normalizedFontSizeScaleOrNull()
                ?: AdvancedTitleConfig.fontSizeScale).toString(),
            getString(R.string.advanced_title_top_spacing) to (config.normalizedTitleTopSpacingOrNull()
                ?: 0).toString(),
            getString(R.string.advanced_title_bottom_spacing) to (config.normalizedTitleBottomSpacingOrNull()
                ?: 0).toString(),
            getString(R.string.advanced_title_text_color) to (config.normalizedTextColorOrNull()
                ?: AdvancedTitleConfig.textColor ?: 0).toString()
        )
        val inputs = fields.map { (label, value) ->
            EditText(this).apply {
                hint = label
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setText(value)
                selectAll()
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 0)
            inputs.forEach { addView(it, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )) }
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.advanced_title_style_edit, entry.name))
            .setView(content)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                runCatching {
                    fun EditText.number(default: Int): Int = text.toString().toIntOrNull() ?: default
                    val weight = inputs[0].number(AdvancedTitleConfig.fontWeight).coerceIn(100, 900)
                    val scale = inputs[1].number(AdvancedTitleConfig.fontSizeScale).coerceIn(50, 200)
                    val top = inputs[2].number(0).coerceIn(0, 200)
                    val bottom = inputs[3].number(0).coerceIn(0, 200)
                    AdvancedTitlePackageManager.addOrUpdate(
                        name = entry.name,
                        json = AdvancedTitlePackageManager.readTemplate(entry),
                        oldEntry = entry,
                        fontWeight = weight,
                        fontSizeScale = scale,
                        titleTopSpacing = top,
                        titleBottomSpacing = bottom
                    )
                }.onSuccess {
                    if (entry.id == AdvancedTitlePackageManager.activeId()) {
                        AdvancedTitlePackageManager.apply(it)
                        postEvent(EventBus.UP_CONFIG, arrayListOf(5))
                    }
                    refreshEntries()
                }.onFailure { toastOnUi(it.localizedMessage ?: getString(R.string.error)) }
            }
            .show()
    }

    private fun applyEntry(entry: AdvancedTitlePackageManager.Entry) {
        runCatching { AdvancedTitlePackageManager.apply(entry) }
            .onSuccess {
                postEvent(EventBus.UP_CONFIG, arrayListOf(5))
                refreshEntries()
            }
            .onFailure { toastOnUi(it.localizedMessage ?: getString(R.string.error)) }
    }

    private fun showEntryMenu(anchor: View, entry: AdvancedTitlePackageManager.Entry) {
        PopupMenu(this, anchor).apply {
            menu.add(R.string.delete)
            setOnMenuItemClickListener {
                confirmDelete(entry)
                true
            }
            show()
        }
    }

    private fun showAddSheet() {
        val dialog = Dialog(this, R.style.dialog_style)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 16.dpToPx(), 24.dpToPx(), 24.dpToPx())
            setBackgroundResource(R.drawable.ng_bg_dialog)
            addView(TextView(context).apply {
                text = getString(R.string.advanced_title_add)
                textSize = 22f
                setPadding(0, 0, 0, 12.dpToPx())
            })
            addAddAction(R.string.advanced_title_create_builtin) {
                dialog.dismiss()
                requestBuiltinCopyName()
            }
            addAddAction(R.string.advanced_title_import_file) {
                dialog.dismiss()
                importJson.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
            addAddAction(R.string.advanced_title_import_network) {
                dialog.dismiss()
                requestNetworkImport()
            }
        }
        dialog.setContentView(content)
        dialog.window?.let { win ->
            val lp = win.attributes
            lp.width = (resources.displayMetrics.widthPixels * 0.8f).toInt()
            win.setAttributes(lp)
        }
        dialog.show()
    }

    private fun LinearLayout.addAddAction(label: Int, action: () -> Unit) {
        addView(TextView(context).apply {
            text = getString(label)
            textSize = 18f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 12.dpToPx(), 0, 12.dpToPx())
            minimumHeight = 52.dpToPx()
            setOnClickListener { action() }
        })
    }

    private fun requestBuiltinCopyName() {
        val json = runCatching {
            AdvancedTitlePackageManager.readTemplate(AdvancedTitlePackageManager.builtinEntry())
        }.getOrElse {
            toastOnUi(it.localizedMessage ?: getString(R.string.error))
            return
        }
        requestNameAndSave(
            json = json,
            defaultName = getString(R.string.advanced_title_builtin_copy),
        )
    }

    private fun requestNetworkImport() {
        val url = EditText(this).apply { hint = getString(R.string.advanced_title_network_url) }
        AlertDialog.Builder(this)
            .setTitle(R.string.advanced_title_import_network)
            .setView(url)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                lifecycleScope.launch {
                    val json = runCatching {
                        withContext(Dispatchers.IO) {
                            val address = AdvancedTitleNetworkImportPolicy.requireHttps(url.text.toString())
                            okHttpClient.newCall(Request.Builder().url(address).build()).execute().use { response ->
                                require(response.isSuccessful) { "HTTP ${response.code}" }
                                AdvancedTitleNetworkImportPolicy.readUtf8Bounded(
                                    requireNotNull(response.body).byteStream()
                                )
                            }
                        }
                    }.getOrElse {
                        toastOnUi(it.localizedMessage ?: getString(R.string.error))
                        return@launch
                    }
                    requestNameAndSave(json)
                }
            }
            .show()
    }

    private fun requestNameAndSave(
        json: String,
        defaultName: String = getString(R.string.advanced_title_unnamed),
    ) {
        val nameInput = EditText(this).apply {
            hint = getString(R.string.advanced_title_name)
            setText(defaultName)
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.advanced_title_import_title)
            .setView(nameInput)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                lifecycleScope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            AdvancedTitlePackageManager.addOrUpdate(nameInput.text.toString(), json)
                        }
                    }.onSuccess { refreshEntries() }
                        .onFailure { toastOnUi(it.localizedMessage ?: getString(R.string.error)) }
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
                    runCatching { withContext(Dispatchers.IO) { AdvancedTitlePackageManager.delete(entry) } }
                        .onSuccess {
                            postEvent(EventBus.UP_CONFIG, arrayListOf(5))
                            refreshEntries()
                        }
                        .onFailure { toastOnUi(it.localizedMessage ?: getString(R.string.error)) }
                }
            }
            .show()
    }
}
