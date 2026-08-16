package com.example.iptvplayer.data.db

import androidx.room.TypeConverter
import com.example.iptvplayer.data.model.ChannelType
import com.example.iptvplayer.data.model.SourceType

class Converters {
    @TypeConverter
    fun channelTypeToString(value: ChannelType): String = value.name

    @TypeConverter
    fun stringToChannelType(value: String): ChannelType = ChannelType.from(value)

    @TypeConverter
    fun sourceTypeToString(value: SourceType): String = value.name

    @TypeConverter
    fun stringToSourceType(value: String): SourceType = SourceType.valueOf(value)
}
