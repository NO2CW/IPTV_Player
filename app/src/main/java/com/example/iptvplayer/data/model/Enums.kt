package com.example.iptvplayer.data.model

/** The kind of content a channel belongs to. Xtream exposes this directly; M3U infers it. */
enum class ChannelType(val label: String) {
    LIVE("Live TV"),
    MOVIES("Movies"),
    SERIES("Series");

    companion object {
        fun from(value: String?): ChannelType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LIVE
    }
}

/** How a playlist source is loaded. */
enum class SourceType {
    M3U,      // an M3U/M3U8 url or pasted text
    XTREAM    // server + username + password (player_api.php)
}
