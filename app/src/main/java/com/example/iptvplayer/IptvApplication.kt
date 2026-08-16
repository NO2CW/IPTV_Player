package com.example.iptvplayer

import android.app.Application
import com.example.iptvplayer.di.AppContainer

class IptvApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
