package com.ollie.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.time.Instant
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class XmlTvParserTest {
    @Test fun parsesProgrammeAndConvertsExplicitOffsetToUtc() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <tv><programme start="20241101120000 +0100" stop="20241101130000 +0100" channel="news.uk">
            <title>Midday News</title><desc>World &amp; local news</desc></programme></tv>""".trimIndent()

        val result = XmlTvParser.parse(ByteArrayInputStream(xml.toByteArray()))

        assertEquals(1, result.size)
        assertEquals("news.uk", result.single().channelId)
        assertEquals("Midday News", result.single().title)
        assertEquals("World & local news", result.single().description)
        assertEquals(Instant.parse("2024-11-01T11:00:00Z").toEpochMilli(), result.single().startUtc)
        assertEquals(Instant.parse("2024-11-01T12:00:00Z").toEpochMilli(), result.single().endUtc)
    }

    @Test fun parsesOffsetlessXmlTvDatesAsUtc() {
        val xml = """<tv><programme start="20240101120000" stop="20240101123000" channel="c1"><title>Programme</title></programme></tv>"""
        val result = XmlTvParser.parse(ByteArrayInputStream(xml.toByteArray()))
        assertEquals(Instant.parse("2024-01-01T12:00:00Z").toEpochMilli(), result.single().startUtc)
    }

    @Test fun parsesChannelMetadataAndGzipXmltv() {
        val xml = """<tv><channel id="news.uk"><display-name>News UK</display-name><icon src="https://example.test/logo.png"/></channel><programme start="20240101120000 +0000" stop="20240101130000 +0000" channel="news.uk"><title>Headlines</title></programme></tv>"""
        val bytes = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(xml.toByteArray()) }
        }.toByteArray()
        val result = XmlTvParser.parseDocument(ByteArrayInputStream(bytes), 42L)
        assertEquals("news.uk", result.channels.single().xmltvId)
        assertEquals("News UK", result.channels.single().displayName)
        assertEquals("https://example.test/logo.png", result.channels.single().iconUrl)
        assertEquals("news.uk", result.programmes.single().channelId)
        assertTrue(result.programmes.single().id.startsWith("42|"))
    }

    @Test fun skipsMissingTitleInvalidRangeAndMalformedDates() {
        val xml = """<tv>
            <programme start="20240101120000 +0000" stop="20240101130000 +0000" channel="missing-title"><desc>no title</desc></programme>
            <programme start="20240101130000 +0000" stop="20240101120000 +0000" channel="reverse"><title>Reverse</title></programme>
            <programme start="20241399129999 +0000" stop="20241399139999 +0000" channel="bad-date"><title>Bad date</title></programme>
            <programme start="not-a-date" stop="20240101130000 +0000" channel="bad-start"><title>Bad start</title></programme>
        </tv>"""
        val result = XmlTvParser.parse(ByteArrayInputStream(xml.toByteArray()))
        assertTrue(result.isEmpty())
    }
}
