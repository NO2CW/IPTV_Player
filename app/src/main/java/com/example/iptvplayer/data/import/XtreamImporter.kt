package com.example.iptvplayer.data.import

import com.example.iptvplayer.data.model.ChannelType
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

/**
 * Client for an Xtream "server + username + password" account.
 *
 * All endpoints go through the standard `player_api.php` API. Responses are parsed with a
 * cursor (JsonReader) rather than loaded wholesale, so a provider with 100k+ VOD entries or
 * series metadata does not blow up memory during import.
 */
class XtreamImporter(private val okHttp: OkHttpClient) {

    data class XCategory(val serverId: Long, val name: String, val type: ChannelType)

    data class XStream(
        val streamId: Long,
        val name: String,
        val url: String,
        val type: ChannelType,
        val logo: String? = null,
        val epgId: String? = null,
        val number: Int? = null,
        val categoryServerId: Long? = null
    )

    private data class Credentials(
        val server: String,
        val user: String,
        val pass: String
    )

    private fun normalizeServer(server: String): String {
        var s = server.trim()
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        return s.trimEnd('/')
    }

    private fun api(cred: Credentials, action: String): String {
        val enc = { v: String -> URLEncoder.encode(v, "UTF-8") }
        return "${cred.server}/player_api.php?username=${enc(cred.user)}&password=${enc(cred.pass)}&action=$action"
    }

    // ---- category fetching -------------------------------------------------

    suspend fun liveCategories(server: String, user: String, pass: String): List<XCategory> =
        fetchArray(api(Credentials(normalizeServer(server), user, pass), "get_live_categories")) { e ->
            XCategory(e["category_id"]?.toLongOrNull() ?: 0L, e["category_name"] ?: "Untitled", ChannelType.LIVE)
        }

    suspend fun vodCategories(server: String, user: String, pass: String): List<XCategory> =
        fetchArray(api(Credentials(normalizeServer(server), user, pass), "get_vod_categories")) { e ->
            XCategory(e["category_id"]?.toLongOrNull() ?: 0L, e["category_name"] ?: "Untitled", ChannelType.MOVIES)
        }

    suspend fun seriesCategories(server: String, user: String, pass: String): List<XCategory> =
        fetchArray(api(Credentials(normalizeServer(server), user, pass), "get_series_categories")) { e ->
            XCategory(e["category_id"]?.toLongOrNull() ?: 0L, e["category_name"] ?: "Untitled", ChannelType.SERIES)
        }
    // ---- stream fetching (streamed in batches) ------------------------------

    suspend fun liveStreams(server: String, user: String, pass: String, onBatch: suspend (List<XStream>) -> Unit) {
        val cred = Credentials(normalizeServer(server), user, pass)
        val base = cred.server
        fetchArrayBatched(api(cred, "get_live_streams"), PREFETCH, { e ->
            val id = e["stream_id"]?.toLongOrNull() ?: 0L
            if (id == 0L) null
            else XStream(
                streamId = id,
                name = e["name"] ?: "Channel $id",
                url = "$base/live/${cred.user}/${cred.pass}/$id.m3u8",
                type = ChannelType.LIVE,
                logo = e["stream_icon"]?.takeIf { it.isNotBlank() },
                epgId = e["epg_channel_id"]?.takeIf { it.isNotBlank() },
                number = e["num"]?.toIntOrNull(),
                categoryServerId = e["category_id"]?.toLongOrNull()
            )
        }, onBatch)
    }

    suspend fun vodStreams(server: String, user: String, pass: String, onBatch: suspend (List<XStream>) -> Unit) {
        val cred = Credentials(normalizeServer(server), user, pass)
        val base = cred.server
        fetchArrayBatched(api(cred, "get_vod_streams"), PREFETCH, { e ->
            val id = e["stream_id"]?.toLongOrNull() ?: 0L
            if (id == 0L) null
            else XStream(
                streamId = id,
                name = e["name"] ?: "Movie $id",
                url = "$base/movie/${cred.user}/${cred.pass}/$id.${e["container_extension"] ?: "mp4"}",
                type = ChannelType.MOVIES,
                logo = e["stream_icon"]?.takeIf { it.isNotBlank() },
                categoryServerId = e["category_id"]?.toLongOrNull()
            )
        }, onBatch)
    }

    suspend fun seriesStreams(server: String, user: String, pass: String, onBatch: suspend (List<XStream>) -> Unit) {
        val cred = Credentials(normalizeServer(server), user, pass)
        fetchArrayBatched(api(cred, "get_series"), PREFETCH, { e ->
            val id = e["series_id"]?.toLongOrNull() ?: 0L
            if (id == 0L) null
            else XStream(
                streamId = id,
                name = e["name"] ?: "Series $id",
                // Series need an episode choice for playback; catalog entry only in v1.
                url = "",
                type = ChannelType.SERIES,
                logo = e["cover"]?.takeIf { it.isNotBlank() },
                categoryServerId = e["category_id"]?.toLongOrNull()
            )
        }, onBatch)
    }

    // ---- generic streamed JSON array parser ----------------------------------

    private suspend fun <T> fetchArray(url: String, map: (Map<String, String>) -> T): List<T> {
        val out = ArrayList<T>()
        fetchArrayBatched(url, Int.MAX_VALUE, { map(it) }, { group -> out += group })
        return out
    }

    /**
     * Streams a JSON top-level array, accumulating mapped elements into a list that is
     * handed to [onBatch] every [flushEvery] elements to keep memory bounded.
     */
    private suspend fun <T> fetchArrayBatched(
        url: String,
        flushEvery: Int,
        map: (Map<String, String>) -> T?,
        onBatch: suspend (List<T>) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url)
                .header("User-Agent", "IPTVPlayer/1.0")
                .build()

            okHttp.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Xtream API HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("empty body")
                val reader = JsonReader(body.charStream())

                val buffer = ArrayList<T>()
                try {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        val element = extractFlat(reader)
                        val mapped = map(element)
                        if (mapped != null) {
                            buffer += mapped
                            if (buffer.size >= flushEvery) {
                                onBatch(buffer)
                                buffer.clear()
                            }
                        }
                    }
                    reader.endArray()
                } finally {
                    if (buffer.isNotEmpty()) onBatch(buffer)
                }
            }
        }
    }

    /** Reads the next JSON object into a flat map of scalar fields, skipping nested objects/arrays. */
    private fun extractFlat(reader: JsonReader): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            when (reader.peek()) {
                JsonToken.STRING -> out[name] = reader.nextString()
                JsonToken.BOOLEAN -> { reader.nextBoolean() }
                JsonToken.NUMBER -> out[name] = reader.nextLong().toString()
                JsonToken.NULL -> reader.nextNull()
                JsonToken.BEGIN_ARRAY, JsonToken.BEGIN_OBJECT -> reader.skipValue()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return out
    }

    companion object {
        /** Number of stream records buffered before being handed to the repository/DB. */
        private const val PREFETCH = 500
    }
}

