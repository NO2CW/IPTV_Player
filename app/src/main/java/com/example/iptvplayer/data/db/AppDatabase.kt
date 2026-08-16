package com.example.iptvplayer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.iptvplayer.data.model.Category
import com.example.iptvplayer.data.model.Channel
import com.example.iptvplayer.data.model.HiddenGroup
import com.example.iptvplayer.data.model.PlaylistSource

@Database(
    entities = [Channel::class, Category::class, PlaylistSource::class, HiddenGroup::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun channelDao(): ChannelDao
    abstract fun categoryDao(): CategoryDao
    abstract fun playlistSourceDao(): PlaylistSourceDao
    abstract fun hiddenGroupDao(): HiddenGroupDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "iptv_player.db")
                .setJournalMode(JournalMode.TRUNCATE) // helps bulk atomic imports on big catalogs
                .fallbackToDestructiveMigration()
                .build()
    }
}
