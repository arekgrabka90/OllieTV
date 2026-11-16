package com.ollie.iptv.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

data class XmlTvDocument(val channels: List<EpgChannelEntity>, val programmes: List<ProgrammeEntity>)

object XmlTvParser {
    fun parse(input: InputStream): List<ProgrammeEntity> = parseDocument(input, 0L).programmes

    fun parseDocument(input: InputStream, sourceId: Long): XmlTvDocument {
        val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input)
        buffered.mark(4)
        val magic = ByteArray(2)
        val count = buffered.read(magic)
        buffered.reset()
        val decoded = if (count == 2 && magic[0] == 0x1f.toByte() && magic[1] == 0x8b.toByte())
            GZIPInputStream(buffered) else buffered
        val parser = Xml.newPullParser()
        parser.setInput(decoded, null)
        val channels = mutableListOf<EpgChannelEntity>()
        val programmes = mutableListOf<ProgrammeEntity>()
        var event = parser.eventType
        var channelId = ""; var channelName = ""; var channelIcon = ""
        var programmeChannel = ""; var title = ""; var desc = ""
        val programmeChannelIds = linkedSetOf<String>()
        var start: Long? = null; var end: Long? = null
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "channel" -> { channelId = parser.getAttributeValue(null, "id").orEmpty().trim(); channelName = ""; channelIcon = "" }
                    "display-name" -> if (channelName.isBlank()) channelName = parser.nextText().trim()
                    "icon" -> if (channelIcon.isBlank()) channelIcon = parser.getAttributeValue(null, "src").orEmpty().trim()
                    "programme" -> {
                        programmeChannel = parser.getAttributeValue(null, "channel").orEmpty().trim()
                        if (programmeChannel.isNotBlank()) programmeChannelIds += programmeChannel
                        start = parseDate(parser.getAttributeValue(null, "start").orEmpty())
                        end = parseDate(parser.getAttributeValue(null, "stop").orEmpty())
                        title = ""; desc = ""
                    }
                    "title" -> title = parser.nextText().trim()
                    "desc" -> desc = parser.nextText().trim()
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "channel" -> if (channelId.isNotBlank()) channels += EpgChannelEntity(
                        id = "$sourceId|$channelId", sourceId = sourceId, xmltvId = channelId,
                        displayName = channelName, iconUrl = channelIcon
                    )
                    "programme" -> {
                        val a = start; val b = end
                        if (programmeChannel.isNotBlank() && title.isNotBlank() && a != null && b != null && b > a) {
                            programmes += ProgrammeEntity(
                                id = "$sourceId|$programmeChannel|$a|$b", channelId = programmeChannel,
                                title = title, description = desc, startUtc = a, endUtc = b
                            )
                        }
                    }
                }
            }
            event = parser.next()
        }
        val knownChannelIds = channels.mapTo(mutableSetOf()) { it.xmltvId }
        programmeChannelIds.filterNot { it in knownChannelIds }.forEach { xmltvId ->
            channels += EpgChannelEntity(id = "$sourceId|$xmltvId", sourceId = sourceId, xmltvId = xmltvId, displayName = xmltvId)
        }
        return XmlTvDocument(channels.distinctBy { it.id }, programmes.distinctBy { it.id })
    }

    private fun parseDate(value: String): Long? {
        val input = value.trim()
        val match = Regex("""^(\d{14})\s*([+-]\d{4})?$""").matchEntire(input) ?: return null
        val offset = match.groupValues[2]
        val normalized = if (offset.isNotBlank()) "${match.groupValues[1]} $offset" else match.groupValues[1]
        val hasOffset = offset.isNotBlank()
        val fmt = SimpleDateFormat(if (hasOffset) "yyyyMMddHHmmss Z" else "yyyyMMddHHmmss", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val pos = ParsePosition(0)
        val date = fmt.parse(normalized, pos) ?: return null
        return date.time.takeIf { pos.index == normalized.length && pos.errorIndex < 0 }
    }
}
