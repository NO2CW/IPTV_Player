package com.example.iptvplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.iptvplayer.data.model.PlaylistSource
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistSourceDao {

    @Insert
    suspend fun insert(source: PlaylistSource): Long

    @Update
    suspend fun update(source: PlaylistSource)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM sources ORDER BY importedAt DESC")
    fun all(): Flow<List<PlaylistSource>>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun byId(id: Long): PlaylistSource?

    @Query("UPDATE sources SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE sources SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: Long)

    @Query("SELECT * FROM sources LIMIT 1")
    suspend fun any(): PlaylistSource?
}
