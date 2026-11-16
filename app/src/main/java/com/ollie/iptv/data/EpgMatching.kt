package com.ollie.iptv.data
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.roundToInt

data class EpgMatchSuggestion(val xmltvId: String, val score: Int, val exactId: Boolean)
data class EpgAutoMatch(val xmltvId: String, val score: Int)

object EpgMatching {
    private val ignoredTokens = setOf("hd", "fhd", "uhd", "4k", "tv", "pl")
    fun normalize(value: String): String = Normalizer.normalize(value.replace("ł","l").replace("Ł","L"), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ").trim().split(Regex("\\s+"))
        .filter { it.isNotBlank() && it !in ignoredTokens }.joinToString(" ")

    private fun editSimilarity(a:String,b:String):Int {
        if(a.isBlank()||b.isBlank()) return 0
        val row=IntArray(b.length+1){it}
        for(i in 1..a.length) {
            var diagonal=row[0]; row[0]=i
            for(j in 1..b.length) {
                val above=row[j]; val cost=if(a[i-1]==b[j-1]) 0 else 1
                row[j]=minOf(row[j]+1,row[j-1]+1,diagonal+cost); diagonal=above
            }
        }
        val longest=max(a.length,b.length)
        return (((longest-row[b.length]).toDouble()/longest)*100.0).roundToInt()
    }
    private fun tokenSimilarity(a:String,b:String):Int {
        val x=a.split(" ").filter{it.isNotBlank()}.toSet()
        val y=b.split(" ").filter{it.isNotBlank()}.toSet()
        if(x.isEmpty()||y.isEmpty()) return 0
        return ((x.intersect(y).size*100.0)/(x union y).size).roundToInt()
    }
    fun suggest(stream:StreamEntity,channels:List<EpgChannelEntity>):List<EpgMatchSuggestion> {
        val exact=channels.filter{stream.tvgId.isNotBlank()&&it.xmltvId.equals(stream.tvgId,true)}
        if(exact.isNotEmpty()) return exact.distinctBy{it.xmltvId.lowercase(Locale.ROOT)}.map{EpgMatchSuggestion(it.xmltvId,100,true)}
        val target=normalize(stream.name); if(target.isBlank()) return emptyList()
        return channels.mapNotNull { ch ->
            val candidate=normalize(ch.displayName); if(candidate.isBlank()) return@mapNotNull null
            val score=if(candidate==target) 96 else max(editSimilarity(target,candidate),tokenSimilarity(target,candidate))
            if(score>=65) EpgMatchSuggestion(ch.xmltvId,score,false) else null
        }.groupBy{it.xmltvId.lowercase(Locale.ROOT)}.values.map{it.maxBy{m->m.score}}
            .sortedWith(compareByDescending<EpgMatchSuggestion>{it.score}.thenBy{it.xmltvId})
    }
    fun autoMatchAll(
        streams: List<StreamEntity>, channels: List<EpgChannelEntity>,
        minimumScore: Int = 91, minimumMargin: Int = 8
    ): Map<String, EpgAutoMatch> {
        data class Candidate(val xmltvId: String, val normalized: String)
        val byXmltvId = channels.groupBy { it.xmltvId.lowercase(Locale.ROOT) }
        val indexed = channels.mapNotNull { channel ->
            val name = normalize(channel.displayName)
            if (name.isBlank()) null else Candidate(channel.xmltvId, name)
        }
        val byPrefix = indexed.groupBy { it.normalized.take(2) }
        val byToken = mutableMapOf<String, MutableList<Candidate>>()
        indexed.forEach { candidate ->
            candidate.normalized.split(" ").filter { it.length >= 2 }.distinct().forEach { token ->
                byToken.getOrPut(token) { mutableListOf() }.add(candidate)
            }
        }
        val result = linkedMapOf<String, EpgAutoMatch>()
        streams.forEach { stream ->
            val exact = stream.tvgId.takeIf { it.isNotBlank() }
                ?.let { byXmltvId[it.lowercase(Locale.ROOT)]?.firstOrNull() }
            if (exact != null) {
                result[stream.id] = EpgAutoMatch(exact.xmltvId, 100)
                return@forEach
            }
            val target = normalize(stream.name)
            if (target.isBlank()) return@forEach
            val candidates = linkedMapOf<String, Candidate>()
            byPrefix[target.take(2)].orEmpty().forEach { candidates[it.xmltvId.lowercase(Locale.ROOT)] = it }
            target.split(" ").filter { it.length >= 2 }.distinct().forEach { token ->
                byToken[token].orEmpty().forEach { candidates[it.xmltvId.lowercase(Locale.ROOT)] = it }
            }
            val ranked = candidates.values.map { candidate ->
                val score = if (candidate.normalized == target) 96 else editSimilarity(target, candidate.normalized)
                EpgMatchSuggestion(candidate.xmltvId, score, false)
            }.filter { it.score >= minimumScore }
                .groupBy { it.xmltvId.lowercase(Locale.ROOT) }.values.map { group -> group.maxBy { it.score } }
                .sortedByDescending { it.score }
            val best = ranked.firstOrNull() ?: return@forEach
            val runner = ranked.getOrNull(1)
            if (runner == null || best.score - runner.score >= minimumMargin) {
                result[stream.id] = EpgAutoMatch(best.xmltvId, best.score)
            }
        }
        return result
    }

    fun autoMatch(stream: StreamEntity, channels: List<EpgChannelEntity>, minimumScore: Int = 91, minimumMargin: Int = 8): EpgAutoMatch? =
        autoMatchAll(listOf(stream), channels, minimumScore, minimumMargin)[stream.id]

    fun localDayRange(offset: Int, zoneId: String = "Europe/Warsaw", now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val start = Calendar.getInstance(TimeZone.getTimeZone(zoneId)).apply {
            timeInMillis = now; add(Calendar.DAY_OF_YEAR, offset.coerceIn(0, 6))
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        return start.timeInMillis to end.timeInMillis
    }
}
