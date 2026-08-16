package com.example.iptvplayer.di

import android.content.Context
import com.example.iptvplayer.data.db.AppDatabase
import com.example.iptvplayer.data.repository.ChannelRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual service locator — small app, no DI framework needed. */
class AppContainer(context: Context) {

    val database: AppDatabase = AppDatabase.build(context.applicationContext)

    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    val repository: ChannelRepository = ChannelRepository(database, okHttp)
}
