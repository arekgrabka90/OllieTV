package com.ollie.iptv.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.ParsePosition
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
        var start: Long? = null; var end: Long? = null
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
                XmlPullParser.END_TAG -> if (parser.name == "programme") {
                    val startUtc = start
                    val endUtc = end
                    if (channel.isNotBlank() && title.isNotBlank() && startUtc != null &&
                        endUtc != null && endUtc > startUtc
                    ) {
                        output += ProgrammeEntity(
                            id = "$channel-$startUtc-$endUtc",
                            channelId = channel,
                            title = title,
                            description = desc,
                            startUtc = startUtc,
                            endUtc = endUtc
                        )
                    }
                }
            }
            event = parser.next()
        }
        return output
    }

    /** Parses XMLTV timestamps strictly. Offset-less timestamps are interpreted as UTC. */
    private fun parseDate(value: String): Long? {
        val input = value.trim()
        val hasOffset = Regex("""^\d{14} [+-]\d{4}$""").matches(input)
        val isUtcWithoutOffset = Regex("""^\d{14}$""").matches(input)
        if (!hasOffset && !isUtcWithoutOffset) return null

        val pattern = if (hasOffset) "yyyyMMddHHmmss Z" else "yyyyMMddHHmmss"
        val format = SimpleDateFormat(pattern, Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val position = ParsePosition(0)
        val parsed = format.parse(input, position) ?: return null
        return parsed.time.takeIf { position.index == input.length && position.errorIndex < 0 }
    }
}
