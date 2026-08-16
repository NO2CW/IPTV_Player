package com.example.iptvplayer.data.model

import androidx.room.Entity
import androidx.room.Index

/** A category/group the user has chosen to hide (e.g. "exclude all Arabic folders"). */
@Entity(
    tableName = "hidden_groups",
    primaryKeys = ["sourceId", "type", "name"],
    indices = [Index("sourceId")]
)
data class HiddenGroup(
    val sourceId: Long,
    val type: ChannelType,
    val name: String
)
