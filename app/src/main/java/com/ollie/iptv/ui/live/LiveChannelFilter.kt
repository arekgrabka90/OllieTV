package com.ollie.iptv.ui.live

import com.ollie.iptv.data.StreamEntity
import com.ollie.iptv.ui.LiveSortMode

/** Pure list transformation, executed on Dispatchers.Default by MainViewModel. */
object LiveChannelFilter {
    fun apply(
        items: List<StreamEntity>,
        query: String,
        category: String,
        sort: LiveSortMode
    ): List<StreamEntity> {
        val filtered = items.asSequence()
            .filter { category == "All channels" || it.category == category }
            .filter { query.isBlank() || it.name.contains(query, true) || it.category.contains(query, true) }
            .toList()
        return when (sort) {
            LiveSortMode.SOURCE -> filtered
            LiveSortMode.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            LiveSortMode.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            LiveSortMode.CATEGORY -> filtered.sortedWith(
                compareBy<StreamEntity>({ it.category.lowercase() }, { it.name.lowercase() })
            )
        }
    }
}
