package com.ollie.iptv.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object XmlTvParser {
    fun parse(input: InputStream): List<ProgrammeEntity> {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        val output = mutableListOf<ProgrammeEntity>()
        var event = parser.eventType
        var channel = ""; var title = ""; var desc = ""
        var start = 0L; var end = 0L
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "programme" -> {
                        channel = parser.getAttributeValue(null, "channel").orEmpty()
                        start = parseDate(parser.getAttributeValue(null, "start").orEmpty())
                        end = parseDate(parser.getAttributeValue(null, "stop").orEmpty())
                        title = ""; desc = ""
                    }
                    "title" -> title = parser.nextText()
                    "desc" -> desc = parser.nextText()
                }
                XmlPullParser.END_TAG -> if (parser.name == "programme" && channel.isNotBlank() && title.isNotBlank() && end > start) {
                    output += ProgrammeEntity(
                        id = "$channel-$start-$end",
                        channelId = channel,
                        title = title,
                        description = desc,
                        startUtc = start,
                        endUtc = end
                    )
                }
            }
            event = parser.next()
        }
        return output
    }

    private fun parseDate(value: String): Long = runCatching {
        val format = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        val normalized = if (value.length >= 20) value.substring(0, 14) + " " + value.substring(15, 20) else value
        format.parse(normalized)!!.time
    }.getOrElse {
        runCatching {
            val format = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.parse(value.take(14))!!.time
        }.getOrDefault(0L)
    }
}
