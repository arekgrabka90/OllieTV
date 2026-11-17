package com.ollie.iptv.ui.live

import com.ollie.iptv.data.StreamEntity
import com.ollie.iptv.ui.LiveSortMode

/**
 * Central live-channel filtering.
 *
 * Category keys:
 * - All channels
 * - Uncategorized
 * - playlist:<playlistId>::<categoryName>
 * - legacy plain category names
 */
object LiveChannelFilter {

    private fun normalizedCategory(value: String): String {
        val clean = value.trim()
        return if (
            clean.isBlank() ||
            clean.equals("All channels", ignoreCase = true)
        ) {
            "Uncategorized"
        } else {
            clean
        }
    }

    private fun matchesCategory(
        item: StreamEntity,
        category: String
    ): Boolean {
        if (category == "All channels") return true

        if (category.startsWith("playlist:")) {
            val payload = category.removePrefix("playlist:")
            val separator = payload.indexOf("::")
            if (separator <= 0) return false

            val playlistId = payload.substring(0, separator).toLongOrNull()
                ?: return false
            val expectedCategory = payload.substring(separator + 2)

            return item.playlistId == playlistId &&
                normalizedCategory(item.category).equals(
                    expectedCategory,
                    ignoreCase = true
                )
        }

        if (category.equals("Uncategorized", ignoreCase = true)) {
            return normalizedCategory(item.category) == "Uncategorized"
        }

        return normalizedCategory(item.category).equals(
            category.trim(),
            ignoreCase = true
        )
    }

    fun apply(
        items: List<StreamEntity>,
        query: String,
        category: String,
        sort: LiveSortMode
    ): List<StreamEntity> {
        val cleanQuery = query.trim()

        val filtered = items.asSequence()
            .filter { matchesCategory(it, category) }
            .filter { item ->
                cleanQuery.isBlank() ||
                    item.name.contains(cleanQuery, ignoreCase = true) ||
                    item.category.contains(cleanQuery, ignoreCase = true) ||
                    item.tvgId.contains(cleanQuery, ignoreCase = true)
            }
            .toList()

        return when (sort) {
            LiveSortMode.SOURCE -> filtered

            LiveSortMode.NAME_ASC ->
                filtered.sortedWith(
                    compareBy<StreamEntity>(
                        { it.name.lowercase() },
                        { it.id }
                    )
                )

            LiveSortMode.NAME_DESC ->
                filtered.sortedWith(
                    compareByDescending<StreamEntity> { it.name.lowercase() }
                        .thenBy { it.id }
                )

            LiveSortMode.CATEGORY ->
                filtered.sortedWith(
                    compareBy<StreamEntity>(
                        { normalizedCategory(it.category).lowercase() },
                        { it.name.lowercase() },
                        { it.id }
                    )
                )
        }
    }
}
