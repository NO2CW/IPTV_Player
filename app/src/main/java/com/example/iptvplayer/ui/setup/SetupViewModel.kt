package com.example.iptvplayer.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.iptvplayer.data.repository.ChannelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SetupViewModel(private val repo: ChannelRepository) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _progress = MutableStateFlow("")
    val progress = _progress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _importDone = MutableSharedFlow<Unit>()
    val importDone: SharedFlow<Unit> = _importDone.asSharedFlow()

    fun clearError() { _error.value = null }

    fun importM3uUrl(name: String, url: String) =
        startImport { repo.importM3uUrl(name, url, progress) }

    fun importM3uText(name: String, text: String) =
        startImport { repo.importM3uText(name, text, progress) }

    fun importXtream(name: String, server: String, username: String, password: String) =
        startImport { repo.importXtream(name, server, username, password, progress) }

    private fun startImport(block: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            _progress.value = ""
            try {
                // Repository does blocking network reads; never run on the main thread.
                withContext(Dispatchers.IO) { block() }
                _importDone.emit(Unit)
            } catch (e: Exception) {
                _error.value = e.message ?: "Import failed"
            } finally {
                _busy.value = false
            }
        }
    }

    private fun progress(): (String, Int) -> Unit = { msg, _ -> _progress.value = msg }
}
