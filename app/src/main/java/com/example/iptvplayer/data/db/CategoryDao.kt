package com.example.iptvplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.iptvplayer.data.model.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<Category>): List<Long>

    @Query("DELETE FROM categories WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: Long)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "SELECT * FROM categories c WHERE c.sourceId = :sourceId AND c.type = :type " +
            "AND NOT EXISTS (SELECT 1 FROM hidden_groups h WHERE h.sourceId = c.sourceId AND h.type = c.type AND h.name = c.name) " +
            "ORDER BY c.name COLLATE NOCASE"
    )
    fun byType(sourceId: Long, type: String): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE sourceId = :sourceId AND type = :type")
    suspend fun byTypeSnapshot(sourceId: Long, type: String): List<Category>


    @Query(
        "SELECT * FROM categories c WHERE c.sourceId = :sourceId " +
            "AND NOT EXISTS (SELECT 1 FROM hidden_groups h WHERE h.sourceId = c.sourceId AND h.type = c.type AND h.name = c.name) " +
            "ORDER BY c.type ASC, c.name COLLATE NOCASE"
    )
    fun allForSource(sourceId: Long): Flow<List<Category>>

    @Query("UPDATE categories SET count = :count WHERE id = :id")
    suspend fun updateCount(id: Long, count: Int)

    @Query("SELECT COUNT(*) FROM categories")
    fun totalCount(): Flow<Long>
}
