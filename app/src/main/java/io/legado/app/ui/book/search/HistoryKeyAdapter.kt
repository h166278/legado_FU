package io.legado.app.ui.book.search

import android.graphics.Color
import android.view.ViewGroup
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.databinding.ItemFilletTextBinding
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.widget.anima.explosion_field.ExplosionField
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import splitties.views.onLongClick

class HistoryKeyAdapter(activity: SearchActivity, val callBack: CallBack) :
    RecyclerAdapter<SearchKeyword, ItemFilletTextBinding>(activity) {

    private val explosionField = ExplosionField.attach2Window(activity)
    private val tagColor = ColorUtils.stripAlpha(activity.accentColor)
    private val tagTextColor = Color.WHITE

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getViewBinding(parent: ViewGroup): ItemFilletTextBinding {
        return ItemFilletTextBinding.inflate(inflater, parent, false).also { binding ->
            binding.textView.backgroundTintList = null
            binding.textView.background = Selector.shapeBuild()
                .setDefaultBgColor(tagColor)
                .setPressedBgColor(ColorUtils.darkenColor(tagColor))
                .setCornerRadius(16.dpToPx())
                .create()
        }
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemFilletTextBinding,
        item: SearchKeyword,
        payloads: MutableList<Any>
    ) {
        binding.run {
            textView.text = item.word
            textView.setTextColor(tagTextColor)
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemFilletTextBinding) {
        holder.itemView.apply {
            setOnClickListener {
                getItemByLayoutPosition(holder.layoutPosition)?.let {
                    callBack.searchHistory(it.word)
                }
            }
            onLongClick {
                explosionField.explode(this, true)
                getItemByLayoutPosition(holder.layoutPosition)?.let {
                    callBack.deleteHistory(it)
                }
            }
        }
    }

    interface CallBack {
        fun searchHistory(key: String)
        fun deleteHistory(searchKeyword: SearchKeyword)
    }
}
