package com.example.iptvplayer.data.repository

import com.example.iptvplayer.data.db.AppDatabase
import com.example.iptvplayer.data.import.M3uImporter
import com.example.iptvplayer.data.import.XtreamImporter
import com.example.iptvplayer.data.model.Channel
import com.example.iptvplayer.data.model.ChannelType
import com.example.iptvplayer.data.model.Category
import com.example.iptvplayer.data.model.HiddenGroup
import com.example.iptvplayer.data.model.PlaylistSource
import com.example.iptvplayer.data.model.SourceType
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.Reader
import java.io.StringReader

/** Central data access for the catalog, cache, search and imports. */
class ChannelRepository(
    private val db: AppDatabase,
    private val okHttp: OkHttpClient
) {
    private val channelDao = db.channelDao()
    private val categoryDao = db.categoryDao()
    private val sourceDao = db.playlistSourceDao()
    private val hiddenGroupDao = db.hiddenGroupDao()
    private val m3u = M3uImporter()
    private val xtream = XtreamImporter(okHttp)

    // ---- read side ----------------------------------------------------------

    fun totalCount(): Flow<Long> = channelDao.totalCount()
    fun totalCategories(): Flow<Long> = categoryDao.totalCount()
    fun sources(): Flow<List<PlaylistSource>> = sourceDao.all()
    suspend fun source(id: Long): PlaylistSource? = sourceDao.byId(id)
    fun categoriesFor(sourceId: Long): Flow<List<Category>> = categoryDao.allForSource(sourceId)

    /** Make [id] the single active source (the one shown in the browser/player). */
    suspend fun setActiveSource(id: Long) {
        sourceDao.clearActive()
        sourceDao.setActive(id)
    }

    /** Remove a source and all of its channels, categories and hidden-group rules. */
    suspend fun deleteSource(id: Long) {
        channelDao.deleteBySource(id)
        categoryDao.deleteBySource(id)
        hiddenGroupDao.deleteBySource(id)
        sourceDao.deleteById(id)
    }
    fun favorites(type: ChannelType, sourceId: Long) = channelDao.favorites(type, sourceId)
    fun recentlyWatched() = channelDao.recentlyWatched(20)
    suspend fun channel(id: Long): Channel? = channelDao.byId(id)
    suspend fun byChannelNumber(sourceId: Long, type: ChannelType, number: Int): Channel? =
        channelDao.byChannelNumber(sourceId, type, number)

    suspend fun markWatched(id: Long) = channelDao.markWatched(id, System.currentTimeMillis())
    suspend fun setFavorite(id: Long, value: Boolean) = channelDao.setFavorite(id, value)

    // ---- hidden groups (exclude groups/categories) ---------------------------

    fun hiddenGroups(sourceId: Long): Flow<List<HiddenGroup>> = hiddenGroupDao.forSource(sourceId)
    suspend fun hideGroup(sourceId: Long, type: ChannelType, name: String) =
        hiddenGroupDao.hide(HiddenGroup(sourceId, type, name))
    suspend fun unhideGroup(sourceId: Long, type: ChannelType, name: String) =
        hiddenGroupDao.unhide(sourceId, type, name)

    /**
     * Lazy paged item load for a category. Passing [ChannelColumns.ALL] as categoryId
     * returns every channel of [type] for the source (paged), so huge catalogs never
     * render all rows at once.
     */
    suspend fun items(
        categoryId: Long?,
        type: ChannelType,
        sourceId: Long,
        query: String,
        limit: Int,
        offset: Int
    ): List<Channel> {
        val effectiveQuery = query.trim()
        val searchAll = categoryId == ChannelColumns.ALL
        return when {
            searchAll -> channelDao.search(type, sourceId, effectiveQuery, limit, offset)
            categoryId != null -> channelDao.byCategory(categoryId, limit, offset)
            else -> emptyList()
        }
    }

    object ChannelColumns {
        const val ALL: Long = -1L
    }

    // ---- source management --------------------------------------------------

    private suspend fun createSource(
        name: String,
        type: SourceType,
        fill: (PlaylistSource) -> PlaylistSource = { it }
    ): Long {
        sourceDao.clearActive()
        val base = PlaylistSource(name = name, type = type, isActive = true)
        return sourceDao.insert(fill(base))
    }

    suspend fun importM3uUrl(name: String, url: String, onProgress: (String, Int) -> Unit): PlaylistSource {
        val trimmedUrl = url.trim()
        require(trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")) {
            "Not a valid http(s) URL"
        }
        val sourceId = createSource(name, SourceType.M3U) { it.copy(m3uUrl = trimmedUrl) }
        val request = Request.Builder().url(trimmedUrl).header("User-Agent", "Mozilla/5.0").build()
        okHttp.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Empty response body")
            importFromReader(sourceId, body.charStream(), name, onProgress)
        }
        return sourceDao.byId(sourceId) ?: throw IOException("Import failed")
    }

    suspend fun importM3uText(name: String, text: String, onProgress: (String, Int) -> Unit): PlaylistSource {
        require(text.trim().startsWith("#EXTM3U")) { "Pasted text is not an M3U playlist" }
        val sourceId = createSource(name, SourceType.M3U)
        importFromReader(sourceId, StringReader(text), name, onProgress)
        return sourceDao.byId(sourceId) ?: throw IOException("Import failed")
    }

    private suspend fun importFromReader(
        sourceId: Long,
        reader: Reader,
        label: String,
        onProgress: (String, Int) -> Unit
    ) {
        prepareSourceImport(sourceId)
        val stats = m3u.parse(reader.buffered(), sourceId) { batch ->
            channelDao.insertAll(batch)
            onProgress("Importing ${batch.size}…", 0)
        }
        rebuildCategoriesLocked(sourceId)
        onProgress("Done — ${stats.total} channels imported", -1)
    }

    private suspend fun prepareSourceImport(sourceId: Long) {
        channelDao.deleteBySource(sourceId)
        categoryDao.deleteBySource(sourceId)
        hiddenGroupDao.deleteBySource(sourceId)
    }

    // ---- Xtream import ------------------------------------------------------

    suspend fun importXtream(
        name: String,
        server: String,
        username: String,
        password: String,
        onProgress: (String, Int) -> Unit
    ): PlaylistSource {
        val sourceId = createSource(name, SourceType.XTREAM) {
            it.copy(serverUrl = server.trim(), username = username.trim(), password = password)
        }
        prepareSourceImport(sourceId)
        importXtreamLocked(sourceId, server, username, password, onProgress)
        return sourceDao.byId(sourceId) ?: throw IOException("Import failed")
    }

    private suspend fun importXtreamLocked(
        sourceId: Long,
        server: String,
        user: String,
        pass: String,
        onProgress: (String, Int) -> Unit
    ) {
        val liveCat = importXtreamCategories(sourceId, xtream.liveCategories(server, user, pass))
        val vodCat = importXtreamCategories(sourceId, xtream.vodCategories(server, user, pass))
        val seriesCat = importXtreamCategories(sourceId, xtream.seriesCategories(server, user, pass))

        var processed = 0
        xtream.liveStreams(server, user, pass) { list ->
            channelDao.insertAll(list.map { it.toChannel(sourceId, liveCat) })
            processed += list.size
            onProgress("Importing Live TV… $processed", 0)
        }
        xtream.vodStreams(server, user, pass) { list ->
            channelDao.insertAll(list.map { it.toChannel(sourceId, vodCat) })
            processed += list.size
            onProgress("Importing Movies… $processed", 0)
        }
        xtream.seriesStreams(server, user, pass) { list ->
            channelDao.insertAll(list.map { it.toChannel(sourceId, seriesCat) })
            processed += list.size
            onProgress("Importing Series… $processed", 0)
        }

        onProgress("Done — ${channelDao.countForSource(sourceId)} channels imported", -1)
    }

    private suspend fun importXtreamCategories(
        sourceId: Long,
        serverCats: List<XtreamImporter.XCategory>
    ): Map<Long, Long> {
        val type = serverCats.firstOrNull()?.type ?: ChannelType.LIVE
        val byName = categoryDao.byTypeSnapshot(sourceId, type.name).associateBy { it.name }
        val map = mutableMapOf<Long, Long>()
        for (sc in serverCats) {
            val existing = byName[sc.name]
            if (existing != null) {
                map[sc.serverId] = existing.id
            } else {
                val ids = categoryDao.insertAll(listOf(Category(sourceId = sourceId, type = type, name = sc.name)))
                map[sc.serverId] = ids.first()
            }
        }
        return map
    }

    /** Rebuilds category rows + counts from the group-titles present after an M3U import. */
    private suspend fun rebuildCategoriesLocked(sourceId: Long) {
        for (type in ChannelType.entries) {
            val groups = channelDao.distinctGroups(sourceId, type)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            val existing = categoryDao.byTypeSnapshot(sourceId, type.name).associateBy { it.name }
            val wanted = groups.toSet()

            for (cat in existing.values) {
                if (cat.name !in wanted) {
                    channelDao.assignCategory(sourceId, type, cat.name, 0L)
                    categoryDao.deleteById(cat.id)
                }
            }

            for (group in wanted) {
                val cat = existing[group]
                val id = if (cat != null) cat.id else {
                    categoryDao.insertAll(listOf(Category(sourceId = sourceId, type = type, name = group))).first()
                }
                channelDao.assignCategory(sourceId, type, group, id)
                categoryDao.updateCount(id, channelDao.countInCategory(id).toInt())
            }
        }
    }

    private fun XtreamImporter.XStream.toChannel(sourceId: Long, catMap: Map<Long, Long>): Channel = Channel(
        name = name,
        streamUrl = url,
        type = type,
        sourceId = sourceId,
        categoryId = categoryServerId?.let { catMap[it] },
        logoUrl = logo,
        epgId = epgId,
        channelNumber = number,
        streamId = streamId
    )
}


