package com.ollie.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    @Test fun parsesQuotedAttributesAndChannelMetadata() {
        val input = """#EXTM3U
#EXTINF:-1 tvg-id="news.uk" tvg-logo="https://img.example/logo.png" group-title="News & Current Affairs",BBC News HD
https://stream.example/live.m3u8
""".trimIndent()

        val result = M3uParser.parse(input)

        assertEquals(1, result.size)
        assertEquals("BBC News HD", result.single().name)
        assertEquals("https://stream.example/live.m3u8", result.single().url)
        assertEquals("News & Current Affairs", result.single().group)
        assertEquals("https://img.example/logo.png", result.single().logo)
        assertEquals("news.uk", result.single().tvgId)
    }

    @Test fun parsesSingleQuotedAndUnquotedAttributesAndCatchup() {
        val input = """#EXTINF:-1 group-title='Sports' tvg-id=sport.one catchup=default catchup-days=7,Channel One
https://stream.example/one.ts
#EXTINF:-1 catchup=none,Channel Two
https://stream.example/two.ts
"""

        val result = M3uParser.parse(input)

        assertEquals(2, result.size)
        assertTrue(result[0].catchup)
        assertEquals(7, result[0].catchupDays)
        assertFalse(result[1].catchup)
    }

    @Test fun ignoresCommentsBlankLinesAndOrphanUrls() {
        val input = """https://orphan.example/live.ts
#EXTM3U

#EXTVLCOPT:http-user-agent=Example
#EXTINF:-1,Valid channel
#EXTGRP:Ignored metadata
https://valid.example/live.ts
# comment
"""

        val result = M3uParser.parse(input)

        assertEquals(1, result.size)
        assertEquals("Valid channel", result.single().name)
        assertEquals("https://valid.example/live.ts", result.single().url)
    }

    @Test fun returnsEmptyForPlaylistWithoutEntries() {
        assertTrue(M3uParser.parse("#EXTM3U\n#EXTVLCOPT:network-caching=1000\n").isEmpty())
    }

    @Test fun parsesLargePlaylistWithoutDroppingEntries() {
        val input = buildString {
            appendLine("#EXTM3U")
            repeat(10_000) { index ->
                appendLine("#EXTINF:-1 tvg-id=channel.$index,Channel $index")
                appendLine("https://stream.example/$index.ts")
            }
        }
        val result = M3uParser.parse(input)
        assertEquals(10_000, result.size)
        assertEquals("Channel 9999", result.last().name)
    }

    @Test fun idsAreDeterministicAndSensitiveToInput() {
        assertEquals(M3uParser.id("same"), M3uParser.id("same"))
        assertNotEquals(M3uParser.id("one"), M3uParser.id("two"))
        assertEquals(24, M3uParser.id("same").length)
    }
}
