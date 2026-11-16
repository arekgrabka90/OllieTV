package com.ollie.iptv.ui.live

import com.ollie.iptv.data.StreamEntity
import com.ollie.iptv.ui.LiveSortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveChannelFilterTest {
    @Test fun filtersByCategoryAndCaseInsensitiveQuery() {
        val items = listOf(
            StreamEntity("1", 1, "World News", "https://one", category = "News"),
            StreamEntity("2", 1, "Sports HD", "https://two", category = "Sports"),
            StreamEntity("3", 1, "Local News", "https://three", category = "News")
        )

        val result = LiveChannelFilter.apply(items, "WORLD", "News", LiveSortMode.SOURCE)

        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test fun sortsNamesInBothDirections() {
        val items = listOf(
            StreamEntity("b", 1, "Bravo", "https://b"),
            StreamEntity("a", 1, "Alpha", "https://a")
        )

        assertEquals(
            listOf("Alpha", "Bravo"),
            LiveChannelFilter.apply(items, "", "All channels", LiveSortMode.NAME_ASC).map { it.name }
        )
        assertEquals(
            listOf("Bravo", "Alpha"),
            LiveChannelFilter.apply(items, "", "All channels", LiveSortMode.NAME_DESC).map { it.name }
        )
    }

    @Test fun handlesTenThousandChannelsWithoutChangingTheSourceList() {
        val items = (0 until 10_000).map { index ->
            StreamEntity(
                id = index.toString(),
                playlistId = 1,
                name = "Channel $index",
                url = "https://example.test/$index",
                category = if (index % 2 == 0) "News" else "Sports"
            )
        }

        val result = LiveChannelFilter.apply(items, "Channel 99", "News", LiveSortMode.NAME_ASC)

        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.category == "News" && it.name.contains("Channel 99") })
        assertEquals(10_000, items.size)
    }
}
