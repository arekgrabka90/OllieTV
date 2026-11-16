package com.ollie.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
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
    @Test fun exactNormalizedNameCanBeAutoMatchedAboveNinetyPercent() {
        val stream = StreamEntity("s", 1, "TVP Łódź HD", "https://example.test")
        val channels = listOf(EpgChannelEntity("a", 1, "tvp.lodz", "TVP Lodz"))
        val match = EpgMatching.autoMatch(stream, channels)
        assertNotNull(match)
        assertEquals("tvp.lodz", match!!.xmltvId)
        assertTrue(match.score > 90)
    }
    @Test fun closeRunnerUpPreventsAutomaticEpgAssignment() {
        val stream = StreamEntity("s", 1, "Discovery Channel", "https://example.test")
        val channels = listOf(EpgChannelEntity("a", 1, "a", "Discovery Channel"), EpgChannelEntity("b", 1, "b", "Discovery Channe"))
        assertNull(EpgMatching.autoMatch(stream, channels))
    }
    @Test fun lowConfidenceNameDoesNotAutoMatch() {
        val stream = StreamEntity("s", 1, "National Geographic", "https://example.test")
        val channels = listOf(EpgChannelEntity("a", 1, "a", "National Geographic Wild"))
        assertNull(EpgMatching.autoMatch(stream, channels))
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
