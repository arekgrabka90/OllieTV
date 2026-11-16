package com.ollie.iptv.data

import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class EpgMatchSuggestion(val xmltvId: String, val score: Int, val exactId: Boolean)

object EpgMatching {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)
        .replace(Regex("\\b(hd|fhd|uhd|4k|tv|pl)\\b"), " ")
        .replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")

    fun suggest(stream: StreamEntity, channels: List<EpgChannelEntity>): List<EpgMatchSuggestion> {
        val exact = channels.filter { stream.tvgId.isNotBlank() && it.xmltvId.equals(stream.tvgId, true) }
        if (exact.isNotEmpty()) return exact.map { EpgMatchSuggestion(it.xmltvId, 100, true) }
        val target = normalize(stream.name)
        if (target.isBlank()) return emptyList()
        return channels.mapNotNull { channel ->
            val candidate = normalize(channel.displayName)
            val score = when {
                candidate.isBlank() -> 0
                candidate == target -> 95
                candidate.startsWith(target) || target.startsWith(candidate) -> 80
                candidate.contains(target) || target.contains(candidate) -> 65
                else -> 0
            }
            if (score >= 65) EpgMatchSuggestion(channel.xmltvId, score, false) else null
        }.sortedWith(compareByDescending<EpgMatchSuggestion> { it.score }.thenBy { it.xmltvId })
    }

    fun localDayRange(offset: Int, zoneId: String = "Europe/Warsaw", now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val start = Calendar.getInstance(TimeZone.getTimeZone(zoneId)).apply {
            timeInMillis = now; add(Calendar.DAY_OF_YEAR, offset.coerceIn(0, 6))
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        return start.timeInMillis to end.timeInMillis
    }
}
