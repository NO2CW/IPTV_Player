package com.example.iptvplayer.data.import

import com.example.iptvplayer.data.model.Channel
import com.example.iptvplayer.data.model.ChannelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader

/**
 * Streaming M3U/M3U8 parser.
 *
 * Reads the file line-by-line on an IO dispatcher and flushes parsed channels to the
 * caller in small batches, so a playlist of 50k–200k entries never materializes as
 * objects in memory at once.
 */
class M3uImporter {

    data class ParsedStats(val total: Int, val live: Int, val movies: Int, val series: Int)

    private data class Meta(
        val name: String,
        val groupTitle: String?,
        val logoUrl: String?,
        val epgId: String?,
        val channelNumber: Int?,
        val type: ChannelType
    )

    suspend fun parse(
        reader: BufferedReader,
        sourceId: Long,
        onBatch: suspend (List<Channel>) -> Unit
    ): ParsedStats = withContext(Dispatchers.IO) {
        var total = 0
        var live = 0
        var movies = 0
        var series = 0

        val batch = ArrayList<Channel>(BATCH)
        var pending: Meta? = null

        reader.use { r ->
            for (line in r.lineSequence()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                    pending = parseExtInf(trimmed)
                    continue
                }
                if (trimmed.startsWith("#")) continue // comments / #EXTVLCOPT directives

                val meta = pending
                if (meta != null) {
                    val url = trimmed
                    if (url.isNotBlank()) {
                        batch += Channel(
                            name = meta.name,
                            streamUrl = url,
                            type = meta.type,
                            sourceId = sourceId,
                            groupTitle = meta.groupTitle,
                            logoUrl = meta.logoUrl,
                            epgId = meta.epgId,
                            channelNumber = meta.channelNumber
                        )
                        total++
                        when (meta.type) {
                            ChannelType.LIVE -> live++
                            ChannelType.MOVIES -> movies++
                            ChannelType.SERIES -> series++
                        }
                        if (batch.size >= BATCH) {
                            onBatch(batch.toList())
                            batch.clear()
                        }
                    }
                    pending = null
                }
            }
        }
        if (batch.isNotEmpty()) onBatch(batch.toList())
        ParsedStats(total, live, movies, series)
    }

    private val attrRe = Regex("""([\w.]+)="([^"]*)"""")
    private val nameRe = Regex(""",\s*(.*?)\s*$""")

    private fun parseExtInf(line: String): Meta {
        val attrs = attrRe.findAll(line)
            .associate { it.groupValues[1].lowercase() to it.groupValues[2] }

        var name = nameRe.find(line)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (name.isEmpty()) name = attrs["tvg-name"] ?: "Unknown"

        val group = attrs["group-title"]?.takeIf { it.isNotBlank() }
        val type = attrs["group-type"]
            ?.takeIf { it.isNotBlank() }
            ?.let { ChannelType.entries.firstOrNull { t -> t.name.equals(it, ignoreCase = true) } }
            ?: inferType(attrs, name, group)

        return Meta(
            name = name,
            groupTitle = group,
            logoUrl = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
            epgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
            channelNumber = attrs["tvg-chno"]?.toIntOrNull(),
            type = type
        )
    }

    private fun inferType(attrs: Map<String, String>, name: String, group: String?): ChannelType {
        val haystack = (group ?: "") + " " + name
        val lower = haystack.lowercase()
        return when {
            lower.contains("movie") || lower.contains("film") || lower.contains("vod") -> ChannelType.MOVIES
            lower.contains("series") || lower.contains("tv show") || lower.contains("tv-show") -> ChannelType.SERIES
            else -> ChannelType.LIVE
        }
    }

    companion object {
        const val BATCH = 500
    }
}
