package com.example.iptvplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.iptvplayer.data.model.ChannelType
import com.example.iptvplayer.data.model.HiddenGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface HiddenGroupDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun hide(group: HiddenGroup)

    @Query("DELETE FROM hidden_groups WHERE sourceId = :sourceId AND type = :type AND name = :name")
    suspend fun unhide(sourceId: Long, type: ChannelType, name: String)

    @Query("SELECT * FROM hidden_groups WHERE sourceId = :sourceId ORDER BY type ASC, name COLLATE NOCASE")
    fun forSource(sourceId: Long): Flow<List<HiddenGroup>>

    @Query("DELETE FROM hidden_groups WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: Long)
}
