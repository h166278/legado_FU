package io.legado.app.ui.main.explore

import androidx.collection.LruCache
import io.legado.app.utils.InfoMap

object ExploreInfoStore {
    val infoMapList = LruCache<String, InfoMap>(99)
}
