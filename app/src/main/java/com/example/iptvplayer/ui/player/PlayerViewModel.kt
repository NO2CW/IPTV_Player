package com.example.iptvplayer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.iptvplayer.data.model.Channel
import com.example.iptvplayer.data.repository.ChannelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Ready(val channel: Channel) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}

class PlayerViewModel(private val repo: ChannelRepository) : ViewModel() {

    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val state = _state.asStateFlow()

    private var currentChannel: Channel? = null
    private var previousChannel: Channel? = null

    fun load(channelId: Long) {
        _state.value = PlayerUiState.Loading
        viewModelScope.launch {
            val channel = repo.channel(channelId)
            if (channel == null) {
                _state.value = PlayerUiState.Error("Channel not found")
            } else {
                if (currentChannel != null && currentChannel!!.id != channel.id) {
                    previousChannel = currentChannel
                }
                currentChannel = channel
                repo.markWatched(channelId)
                _state.value = PlayerUiState.Ready(channel)
            }
        }
    }

    /** TiviMate-style channel-number entry: jump to channel with the given number. */
    fun switchToNumber(number: Int) {
        val cur = currentChannel ?: return
        viewModelScope.launch {
            val found = repo.byChannelNumber(cur.sourceId, cur.type, number)
            if (found != null && found.id != cur.id) {
                previousChannel = cur
                currentChannel = found
                repo.markWatched(found.id)
                _state.value = PlayerUiState.Ready(found)
            }
        }
    }

    fun previousChannel() {
        val prev = previousChannel ?: return
        load(prev.id)
    }

    fun clearError() {
        _state.value = PlayerUiState.Loading
    }
}
