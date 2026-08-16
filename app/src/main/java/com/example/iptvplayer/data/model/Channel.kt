package com.example.iptvplayer.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single playable entry (live channel, movie, or series).
 *
 * This is the catalog record — NOT the playback queue. We only ever hand a single
 * [streamUrl] at a time to the video player, no matter how many rows live here.
 */
@Entity(
    tableName = "channels",
    indices = [
        Index("categoryId"),
        Index("sourceId"),
        Index("name"),
        Index("type"),
        Index("channelNumber"),
        Index(value = ["sourceId", "type", "groupTitle"])
    ]
)
data class Channel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val streamUrl: String,
    val type: ChannelType,
    val sourceId: Long,
    val groupTitle: String? = null,
    val categoryId: Long? = null,
    val logoUrl: String? = null,
    val epgId: String? = null,
    val channelNumber: Int? = null,
    val isFavorite: Boolean = false,
    val streamId: Long? = null,                // Xtream stream_id / series_id
    val lastWatchedAt: Long? = null
)
