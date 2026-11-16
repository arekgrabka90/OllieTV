package com.ollie.iptv.recording

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule

class HlsRecorderTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    @Test fun recordsClearTransportStreamSegmentsOnce() {
        val first = byteArrayOf(0x47, 0x01, 0x02)
        val second = byteArrayOf(0x47, 0x03, 0x04)
        server.enqueue(MockResponse().setBody("""#EXTM3U
#EXT-X-TARGETDURATION:4
#EXTINF:4.0,
one.ts
#EXTINF:4.0,
two.ts
#EXT-X-ENDLIST
"""))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(first)))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(second)))
        val output = temporaryFolder.newFile("recording.ts")

        HlsRecorder.record(server.url("/index.m3u8").toString(), output, { true }, 0L) {}

        assertArrayEquals(first + second, output.readBytes())
        assertTrue(server.takeRequest().path!!.endsWith("/index.m3u8"))
        assertTrue(server.takeRequest().path!!.endsWith("/one.ts"))
        assertTrue(server.takeRequest().path!!.endsWith("/two.ts"))
    }

    @Test fun resolvesFirstVariantFromMasterPlaylist() {
        val segment = byteArrayOf(0x47, 0x05)
        server.enqueue(MockResponse().setBody("""#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=1000000
variant/index.m3u8
"""))
        server.enqueue(MockResponse().setBody("""#EXTM3U
#EXTINF:4.0,
segment.ts
#EXT-X-ENDLIST
"""))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(segment)))
        val output = temporaryFolder.newFile("variant.ts")

        HlsRecorder.record(server.url("/master.m3u8").toString(), output, { true }, 0L) {}

        assertArrayEquals(segment, output.readBytes())
        assertTrue(server.takeRequest().path!!.endsWith("/master.m3u8"))
        assertTrue(server.takeRequest().path!!.endsWith("/variant/index.m3u8"))
        assertTrue(server.takeRequest().path!!.endsWith("/variant/segment.ts"))
    }

    @Test fun rejectsEncryptedHls() {
        server.enqueue(MockResponse().setBody("""#EXTM3U
#EXT-X-KEY:METHOD=AES-128,URI="key.bin"
#EXTINF:4.0,
segment.ts
#EXT-X-ENDLIST
"""))
        val output = temporaryFolder.newFile("encrypted.ts")
        val error = runCatching {
            HlsRecorder.record(server.url("/encrypted.m3u8").toString(), output, { true }, 0L) {}
        }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("Encrypted HLS"))
    }

    @Test fun rejectsFragmentedMp4Hls() {
        server.enqueue(MockResponse().setBody("""#EXTM3U
#EXT-X-MAP:URI="init.mp4"
#EXTINF:4.0,
segment.m4s
#EXT-X-ENDLIST
"""))
        val output = temporaryFolder.newFile("fmp4.ts")
        val error = runCatching {
            HlsRecorder.record(server.url("/fmp4.m3u8").toString(), output, { true }, 0L) {}
        }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("fMP4/CMAF"))
    }
}
