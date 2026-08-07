package io.legado.app.ui.font

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.DialogFontSelectBinding
import io.legado.app.databinding.ItemFontBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.design.components.view.NgFloatingTabItem
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.cnCompare
import io.legado.app.utils.dpToPx
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.list
import io.legado.app.utils.listFileDocs
import io.legado.app.utils.putPrefString
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.invisible
import io.legado.app.utils.visible
import io.legado.app.utils.viewbindingdelegate.viewBinding
import java.io.File

/**
 * 字体选择对话框
 */
class FontSelectDialog : BaseDialogFragment(R.layout.dialog_font_select),
    FontAdapter.CallBack {
    private companion object {
        const val MODE_SYSTEM = 0
        const val MODE_CUSTOM = 1
    }

    private val fontRegex = Regex("(?i).*\\.[ot]tf")
    private val binding by viewBinding(DialogFontSelectBinding::bind)
    private var fontMode = MODE_SYSTEM
    private val adapter by lazy {
        FontAdapter(
            context = requireContext(),
            currentFilePath = { callBack?.curFontPath.orEmpty() },
            contentColor = ReadDrawerStyle.contentColor(requireContext()),
            callBack = this,
        )
    }
    private val systemFontAdapter by lazy {
        SystemFontAdapter(
            context = requireContext(),
            contentColor = ReadDrawerStyle.contentColor(requireContext()),
            onSelect = ::selectSystemFont,
        ).apply {
            setItems(resources.getStringArray(R.array.system_typefaces).indices.toList())
        }
    }
    private val selectFontDir = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            kotlin.runCatching {
                requireContext().contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.onFailure { error ->
                AppLog.put("保存字体目录读取权限失败\n${error.localizedMessage}", error)
            }
            putPrefString(PreferKey.fontFolder, it.toString())
            DocumentFile.fromTreeUri(requireContext(), it)?.let { doc ->
                loadFontFiles(FileDoc.fromDocumentFile(doc))
            } ?: loadFontFiles()
        }
    }

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(
            marginDp = 20,
            height = 360.dpToPx(),
            dimAmount = 0.4f,
        )
        parentFragment?.view?.let {
            ReadDrawerStyle.positionDialogAbove(dialog, it)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val contentColor = ReadDrawerStyle.contentColor(requireContext())
        binding.rootView.setBackgroundColor(Color.TRANSPARENT)
        ReadDrawerStyle.applyGlassBackground(
            view = binding.ngDialogBackground,
            radiusDp = 24,
        )
        binding.tvTitle.setTextColor(contentColor)
        binding.tvEmpty.setTextColor(contentColor)
        binding.tvAddDirectory.setTextColor(contentColor)
        binding.ivAddDirectory.setColorFilter(contentColor)
        binding.ivAddDirectoryChevron.setColorFilter(contentColor)
        binding.addDirectoryRow.setOnClickListener { openFolder() }
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        val selectedColor = requireContext().accentColor
        binding.fontModeTabs.setSurfaceAlpha(0.28f)
        binding.fontModeTabs.setContentColors(
            unselectedContentColor = contentColor,
            selectedContentColor = if (ColorUtils.isColorLight(selectedColor)) {
                Color.BLACK
            } else {
                Color.WHITE
            },
            selectedContainerColor = selectedColor,
        )
        fontMode = if (callBack?.curFontPath.isNullOrEmpty()) MODE_SYSTEM else MODE_CUSTOM
        binding.fontModeTabs.setItems(
            items = listOf(
                NgFloatingTabItem(text = getString(R.string.font_mode_system)),
                NgFloatingTabItem(text = getString(R.string.font_mode_custom)),
            ),
            selectedIndex = fontMode,
        ) { mode ->
            showFontMode(mode)
        }
        showFontMode(fontMode)

        val fontPath = getPrefString(PreferKey.fontFolder)
        if (fontPath.isNullOrEmpty()) {
            loadFontFiles()
        } else {
            if (fontPath.isContentScheme()) {
                val doc = DocumentFile.fromTreeUri(requireContext(), Uri.parse(fontPath))
                if (doc?.canRead() == true) {
                    loadFontFiles(FileDoc.fromDocumentFile(doc))
                } else {
                    loadFontFiles()
                }
            } else {
                File(fontPath).takeIf { it.canRead() }?.let {
                    loadFontFiles(FileDoc.fromFile(it))
                } ?: loadFontFiles()
            }
        }
    }

    private fun openFolder() {
        val initialUri = getPrefString(PreferKey.fontFolder)
            ?.takeIf { it.isContentScheme() }
            ?.let(Uri::parse)
        selectFontDir.launch(initialUri)
    }

    private fun getLocalFonts(): ArrayList<FileDoc> {
        val path = FileUtils.getPath(requireContext().externalFiles, "font")
        return File(path).listFileDocs {
            it.name.matches(fontRegex)
        }
    }

    private fun loadFontFiles(fileDoc: FileDoc? = null) {
        execute {
            val fontItems = fileDoc?.list {
                it.name.matches(fontRegex)
            } ?: ArrayList()
            mergeFontItems(fontItems, getLocalFonts())
        }.onSuccess {
            adapter.setItems(it)
            if (fontMode == MODE_CUSTOM) {
                binding.tvEmpty.isVisible = it.isEmpty()
            }
        }.onError {
            AppLog.put("加载字体文件失败\n${it.localizedMessage}", it)
            toastOnUi("getFontFiles:${it.localizedMessage}")
        }
    }

    private fun mergeFontItems(
        items1: ArrayList<FileDoc>,
        items2: ArrayList<FileDoc>
    ): List<FileDoc> {
        val items = ArrayList(items1)
        items2.forEach { item2 ->
            var isInFirst = false
            items1.forEach for1@{ item1 ->
                if (item2.name == item1.name) {
                    isInFirst = true
                    return@for1
                }
            }
            if (!isInFirst) {
                items.add(item2)
            }
        }
        return items.sortedWith { o1, o2 ->
            o1.name.cnCompare(o2.name)
        }
    }

    override fun onFontSelect(docItem: FileDoc) {
        callBack?.selectFont(docItem.toString())
        adapter.notifyDataSetChanged()
        systemFontAdapter.notifyDataSetChanged()
    }

    private fun selectSystemFont(index: Int) {
        AppConfig.systemTypefaces = index
        callBack?.selectFont("")
        systemFontAdapter.notifyDataSetChanged()
        adapter.notifyDataSetChanged()
    }

    private fun showFontMode(mode: Int) {
        fontMode = mode
        binding.addDirectoryRow.isVisible = mode == MODE_CUSTOM
        binding.recyclerView.adapter = if (mode == MODE_SYSTEM) systemFontAdapter else adapter
        binding.tvEmpty.isVisible = mode == MODE_CUSTOM && adapter.itemCount == 0
    }

    private val callBack: CallBack?
        get() = (parentFragment as? CallBack) ?: (activity as? CallBack)

    interface CallBack {
        fun selectFont(path: String)
        val curFontPath: String
    }

    private inner class SystemFontAdapter(
        context: Context,
        private val contentColor: Int,
        private val onSelect: (Int) -> Unit,
    ) : RecyclerAdapter<Int, ItemFontBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemFontBinding {
            return ItemFontBinding.inflate(inflater, parent, false)
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemFontBinding) {
            binding.root.setOnClickListener {
                getItem(holder.layoutPosition)?.let(onSelect)
            }
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemFontBinding,
            item: Int,
            payloads: MutableList<Any>,
        ) = binding.run {
            tvFont.text = resources.getStringArray(R.array.system_typefaces)[item]
            tvFont.setTextColor(contentColor)
            tvFont.typeface = when (item) {
                1 -> Typeface.SERIF
                2 -> Typeface.MONOSPACE
                else -> Typeface.SANS_SERIF
            }
            ivChecked.setColorFilter(requireContext().accentColor)
            ivFont.setColorFilter(contentColor)
            if (callBack?.curFontPath.isNullOrEmpty() && AppConfig.systemTypefaces == item) {
                ivChecked.visible()
            } else {
                ivChecked.invisible()
            }
        }
    }
}
