package io.legado.app.ui.font

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.view.ViewGroup
import androidx.annotation.ColorInt
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.databinding.ItemFontBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.*
import java.io.File
import java.net.URLDecoder

class FontAdapter(
    context: Context,
    private val currentFilePath: () -> String,
    @ColorInt private val contentColor: Int,
    val callBack: CallBack,
) :
    RecyclerAdapter<FileDoc, ItemFontBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemFontBinding {
        return ItemFontBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemFontBinding,
        item: FileDoc,
        payloads: MutableList<Any>
    ) {
        binding.run {
            kotlin.runCatching {
                val typeface: Typeface? = if (item.isContentScheme) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.contentResolver
                            .openFileDescriptor(item.uri, "r")?.use {
                                Typeface.Builder(it.fileDescriptor).build()
                            }
                    } else {
                        Typeface.createFromFile(RealPathUtil.getPath(context, item.uri))
                    }
                } else {
                    Typeface.createFromFile(item.uri.path!!)
                }
                tvFont.typeface = typeface
            }.onFailure {
                it.printOnDebug()
                AppLog.put("读取字体 ${item.name} 出错\n${it.localizedMessage}", it, true)
            }
            tvFont.text = item.name
            tvFont.setTextColor(contentColor)
            ivFont.setColorFilter(contentColor)
            ivChecked.setColorFilter(context.accentColor)
            root.setOnClickListener { callBack.onFontSelect(item) }
            if (item.name == currentFontName()) {
                ivChecked.visible()
            } else {
                ivChecked.invisible()
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemFontBinding) {
        holder.itemView.setOnClickListener {
            getItem(holder.layoutPosition)?.let {
                callBack.onFontSelect(it)
            }
        }
    }

    interface CallBack {
        fun onFontSelect(docItem: FileDoc)
    }

    private fun currentFontName(): String? {
        val path = currentFilePath()
        if (path.isEmpty()) return null
        return kotlin.runCatching {
            URLDecoder.decode(path, "utf-8")
        }.getOrNull()?.substringAfterLast(File.separator)
    }
}
