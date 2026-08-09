package io.legado.app.ui.book.source

import androidx.annotation.DrawableRes
import io.legado.app.R

object BookSourceGroupIcon {

    @DrawableRes
    fun resolve(group: String): Int {
        return when (group) {
            "小说" -> R.drawable.ic_cfg_source
            "漫画" -> R.drawable.ic_source_group_comic
            "音频" -> R.drawable.ic_bookshelf_dock_audio
            "视频" -> R.drawable.ic_bookshelf_dock_video
            "其它" -> R.drawable.ic_source_group_file
            else -> R.drawable.ic_groups
        }
    }
}
