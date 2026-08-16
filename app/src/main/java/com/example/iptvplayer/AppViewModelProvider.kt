package com.example.iptvplayer

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.iptvplayer.di.AppContainer
import com.example.iptvplayer.ui.channels.ChannelsViewModel
import com.example.iptvplayer.ui.player.PlayerViewModel
import com.example.iptvplayer.ui.setup.SetupViewModel

/** Central factory that wires every ViewModel to the app container. */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { SetupViewModel(app().repository) }
        initializer { ChannelsViewModel(app().repository) }
        initializer { PlayerViewModel(app().repository) }
    }

    private fun CreationExtras.app(): AppContainer {
        val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
            ?: error("Application not available in CreationExtras")
        return (application as IptvApplication).container
    }
}
