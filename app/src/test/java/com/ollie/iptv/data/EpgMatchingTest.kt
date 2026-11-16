package com.ollie.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EpgMatchingTest {
    @Test fun warsawSpringDstDayHas23Hours() {
        val now = Instant.parse("2026-03-29T12:00:00Z").toEpochMilli()
        val (start, end) = EpgMatching.localDayRange(0, "Europe/Warsaw", now)
        assertEquals(23L * 60L * 60L * 1000L, end - start)
    }
    @Test fun warsawAutumnDstDayHas25Hours() {
        val now = Instant.parse("2026-10-25T12:00:00Z").toEpochMilli()
        val (start, end) = EpgMatching.localDayRange(0, "Europe/Warsaw", now)
        assertEquals(25L * 60L * 60L * 1000L, end - start)
    }
    @Test fun channelNameNormalizationIgnoresDiacriticsAndHdSuffix() {
        assertEquals(EpgMatching.normalize("TVP Łódź HD"), EpgMatching.normalize("tvp lodz"))
    }
    @Test fun ambiguousNameMatchesRemainSuggestionsRatherThanAutoAssignments() {
        val stream = StreamEntity("s", 1, "News HD", "https://example.test", tvgId = "")
        val channels = listOf(
            EpgChannelEntity("a", 1, "news.a", "News"),
            EpgChannelEntity("b", 1, "news.b", "News")
        )
        assertTrue(EpgMatching.suggest(stream, channels).size > 1)
        assertTrue(EpgMatching.suggest(stream, channels).none { it.exactId })
    }
}
