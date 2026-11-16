package com.ollie.iptv.data

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class XtreamClientTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    @Test fun fetchLiveMapsCategoriesAndStreamMetadataAndEncodesCredentials() {
        server.enqueue(MockResponse().setBody("""[{"category_id":"4","category_name":"News"}]"""))
        server.enqueue(MockResponse().setBody("""[{"stream_id":42,"name":"News HD","category_id":"4","stream_icon":"https://img.example/news.png","epg_channel_id":"news.uk","tv_archive":1,"tv_archive_duration":7}]"""))

        val client = XtreamClient(server.url("/").toString(), "user name", "p@ss&word")
        val streams = client.fetchLive()

        assertEquals(1, streams.size)
        assertEquals("News", streams.single().category)
        assertEquals("News HD", streams.single().name)
        assertEquals("news.uk", streams.single().tvgId)
        assertTrue(streams.single().catchup)
        assertEquals(7, streams.single().catchupDays)
        val first = server.takeRequest()
        assertEquals("get_live_categories", first.requestUrl?.queryParameter("action"))
        assertEquals("user name", first.requestUrl?.queryParameter("username"))
        assertEquals("p@ss&word", first.requestUrl?.queryParameter("password"))
        assertEquals("get_live_streams", server.takeRequest().requestUrl?.queryParameter("action"))
    }

    @Test fun skipsMalformedEntriesWithoutStreamIds() {
        server.enqueue(MockResponse().setBody("[]"))
        server.enqueue(MockResponse().setBody("""[{"name":"missing id"},{"stream_id":9,"name":"Valid"}]"""))
        val streams = XtreamClient(server.url("/").toString(), "u", "p").fetchLive()
        assertEquals(1, streams.size)
        assertEquals(9, streams.single().xtreamId)
    }

    @Test fun reportsProviderHttpErrors() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))
        val client = XtreamClient(server.url("/").toString(), "u", "p")
        val error = runCatching { client.fetch("get_live_streams") }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("HTTP 401"))
    }
}
