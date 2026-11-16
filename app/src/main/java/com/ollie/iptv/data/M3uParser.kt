package com.ollie.iptv.data

import java.security.MessageDigest

object M3uParser {
    data class Parsed(
        val name: String, val url: String, val group: String,
        val logo: String, val tvgId: String, val catchup: Boolean, val catchupDays: Int
    )

    fun parse(text: String): List<Parsed> {
        val out = mutableListOf<Parsed>()
        var name: String? = null
        var group = ""; var logo = ""; var tvgId = ""
        var catchup = false; var days = 0
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() || line.equals("#EXTM3U", true) -> Unit
                line.startsWith("#EXTINF", true) -> {
                    val comma = line.indexOf(',')
                    val attrs = if (comma >= 0) line.substring(0, comma) else line
                    name = if (comma >= 0) line.substring(comma + 1).trim() else "Unknown channel"
                    group = attr(attrs, "group-title").orEmpty()
                    logo = attr(attrs, "tvg-logo").orEmpty()
                    tvgId = attr(attrs, "tvg-id").orEmpty()
                    catchup = attr(attrs, "catchup")?.let { it != "0" && !it.equals("none", true) } ?: false
                    days = attr(attrs, "catchup-days")?.toIntOrNull() ?: 0
                }
                line.startsWith("#") -> Unit
                name != null -> {
                    out += Parsed(name!!, line, group, logo, tvgId, catchup, days)
                    name = null; group = ""; logo = ""; tvgId = ""; catchup = false; days = 0
                }
            }
        }
        return out
    }

    private fun attr(source: String, key: String): String? {
        val pattern = """(?:^|\s)$key=(?:"([^"]*)"|'([^']*)'|([^\s]*))"""
        val m = Regex(pattern, RegexOption.IGNORE_CASE).find(source) ?: return null
        return m.groups[1]?.value ?: m.groups[2]?.value ?: m.groups[3]?.value
    }

    fun id(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
}
