package com.example.iptvplayer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A user-provided playlist source: either an M3U/M3U8 url/text or an Xtream login. */
@Entity(tableName = "sources")
data class PlaylistSource(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: SourceType,
    val m3uUrl: String? = null,
    val serverUrl: String? = null,
    val username: String? = null,
    val password: String? = null,
    val isActive: Boolean = false,
    val importedAt: Long = System.currentTimeMillis()
)
