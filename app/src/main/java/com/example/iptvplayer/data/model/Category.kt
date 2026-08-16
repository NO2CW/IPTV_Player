package com.example.iptvplayer.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A logical group ("category") of channels for one source.
 * Unique per (sourceId, type, name) so the same group name in Live vs Movies stays separate.
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["sourceId", "type", "name"], unique = true)]
)
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val type: ChannelType,
    val name: String,
    val count: Int = 0
)
