package com.example.iptvplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.iptvplayer.data.model.Channel
import com.example.iptvplayer.data.model.ChannelType
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<Channel>)

    @Query("DELETE FROM channels WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: Long)

    @Query("DELETE FROM channels WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM channels")
    fun totalCount(): Flow<Long>

    @Query("SELECT COUNT(*) FROM channels WHERE sourceId = :sourceId")
    suspend fun countForSource(sourceId: Long): Long

    /** Lazy paged load of one category, ordered by channel number (like TiviMate). */
    @Query(
        "SELECT * FROM channels WHERE categoryId = :categoryId " +
            "ORDER BY (channelNumber IS NULL), channelNumber, name COLLATE NOCASE " +
            "LIMIT :limit OFFSET :offset"
    )
    suspend fun byCategory(categoryId: Long, limit: Int, offset: Int): List<Channel>

    @Query(
        "SELECT * FROM channels WHERE type = :type AND sourceId = :sourceId " +
            "AND name LIKE '%' || :query || '%' COLLATE NOCASE " +
            "AND (categoryId IS NULL OR categoryId NOT IN (" +
            "SELECT c.id FROM categories c " +
            "JOIN hidden_groups h ON h.sourceId = c.sourceId AND h.type = c.type AND h.name = c.name " +
            "WHERE c.sourceId = :sourceId)) " +
            "ORDER BY name COLLATE NOCASE LIMIT :limit OFFSET :offset"
    )
    suspend fun search(type: ChannelType, sourceId: Long, query: String, limit: Int, offset: Int): List<Channel>

    @Query("SELECT * FROM channels WHERE type = :type AND sourceId = :sourceId AND isFavorite = 1 ORDER BY name COLLATE NOCASE")
    fun favorites(type: ChannelType, sourceId: Long): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun byId(id: Long): Channel?

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND type = :type AND channelNumber = :number LIMIT 1")
    suspend fun byChannelNumber(sourceId: Long, type: ChannelType, number: Int): Channel?

    @Query("SELECT * FROM channels WHERE lastWatchedAt IS NOT NULL ORDER BY lastWatchedAt DESC LIMIT :limit")
    fun recentlyWatched(limit: Int = 20): Flow<List<Channel>>

    @Query("UPDATE channels SET isFavorite = :value WHERE id = :id")
    suspend fun setFavorite(id: Long, value: Boolean)

    @Query("UPDATE channels SET lastWatchedAt = :ts WHERE id = :id")
    suspend fun markWatched(id: Long, ts: Long)

    // --- category rebuild helpers (used after a streaming import) ---

    @Query("SELECT DISTINCT groupTitle FROM channels WHERE sourceId = :sourceId AND type = :type AND groupTitle IS NOT NULL")
    suspend fun distinctGroups(sourceId: Long, type: ChannelType): List<String>

    @Query("UPDATE channels SET categoryId = :categoryId WHERE sourceId = :sourceId AND type = :type AND groupTitle = :groupTitle")
    suspend fun assignCategory(sourceId: Long, type: ChannelType, groupTitle: String, categoryId: Long)

    @Query("SELECT COUNT(*) FROM channels WHERE categoryId = :categoryId")
    suspend fun countInCategory(categoryId: Long): Long
}
