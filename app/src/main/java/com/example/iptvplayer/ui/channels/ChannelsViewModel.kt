package com.example.iptvplayer.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.iptvplayer.data.model.Channel
import com.example.iptvplayer.data.model.ChannelType
import com.example.iptvplayer.data.model.HiddenGroup
import com.example.iptvplayer.data.model.PlaylistSource
import com.example.iptvplayer.data.repository.ChannelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A category row in the UI; null [id] represents the synthetic "All Channels" item. */
data class CategoryUi(val id: Long?, val name: String, val count: Int)

data class ChannelsUiState(
    val sourceId: Long? = null,
    val sources: List<PlaylistSource> = emptyList(),
    val type: ChannelType = ChannelType.LIVE,
    val categories: List<CategoryUi> = emptyList(),
    val selectedCategoryId: Long? = null,
    val items: List<Channel> = emptyList(),
    val query: String = "",
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    /** Names of groups the user has hidden for the current type (for restore). */
    val hidden: List<String> = emptyList()
)

class ChannelsViewModel(private val repo: ChannelRepository) : ViewModel() {

    private val _state = MutableStateFlow(ChannelsUiState())
    val state = _state.asStateFlow()

    private var activeSourceId: Long? = null
    private var itemsJob: Job? = null
    private var searchJob: Job? = null
    private var hiddenJob: Job? = null
    private val _hiddenAll = MutableStateFlow<List<HiddenGroup>>(emptyList())

    /** Rows fetched per page so scrolling a huge category stays smooth and lazy. */
    private val pageSize = 50

    init {
        viewModelScope.launch {
            repo.sources().collect { sources ->
                _state.update { it.copy(sources = sources) }
                val active = sources.firstOrNull { it.isActive } ?: sources.firstOrNull()
                when {
                    active != null && active.id != activeSourceId -> onSourceSelected(active.id)
                    active == null -> {
                        activeSourceId = null
                        hiddenJob?.cancel()
                        _state.update {
                            it.copy(sourceId = null, categories = emptyList(), items = emptyList(), hidden = emptyList())
                        }
                    }
                }
            }
        }
    }

    private fun onSourceSelected(sourceId: Long) {
        activeSourceId = sourceId
        _state.update { it.copy(sourceId = sourceId, query = "", selectedCategoryId = null) }
        hiddenJob?.cancel()
        hiddenJob = viewModelScope.launch {
            repo.hiddenGroups(sourceId).collect { hidden ->
                _hiddenAll.value = hidden
                refreshHiddenNames()
            }
        }
        reloadCategories()
        reloadItems()
    }

    fun onSelectSource(id: Long) {
        viewModelScope.launch { repo.setActiveSource(id) }
    }

    fun onDeleteSource(id: Long) {
        viewModelScope.launch {
            repo.deleteSource(id)
            // If anything remains and nothing is active, promote the first list.
            val remaining = repo.sources().first()
            if (remaining.isNotEmpty() && remaining.none { it.isActive }) {
                repo.setActiveSource(remaining.first().id)
            }
        }
    }

    fun onTypeSelected(type: ChannelType) {
        _state.update { it.copy(type = type, selectedCategoryId = null, query = "", endReached = false) }
        refreshHiddenNames()
        reloadCategories()
        reloadItems()
    }

    fun onCategorySelected(categoryId: Long?) {
        _state.update { it.copy(selectedCategoryId = categoryId, endReached = false) }
        reloadItems()
    }

    fun onHide(category: CategoryUi) {
        val sourceId = _state.value.sourceId ?: return
        val type = _state.value.type
        if (category.id == null) return // cannot hide "All Channels"
        viewModelScope.launch {
            repo.hideGroup(sourceId, type, category.name)
            reloadCategories()
            reloadItems()
        }
    }

    fun onUnhide(name: String) {
        val sourceId = _state.value.sourceId ?: return
        val type = _state.value.type
        viewModelScope.launch {
            repo.unhideGroup(sourceId, type, name)
            reloadCategories()
            reloadItems()
        }
    }

    private fun refreshHiddenNames() {
        val type = _state.value.type
        val names = _hiddenAll.value.filter { it.type == type }.map { it.name }
        _state.update { it.copy(hidden = names) }
    }

    fun onSearchQuery(q: String) {
        val s = _state.value
        if (s.query == q) return
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250)
            reloadItems()
        }
    }

    fun loadMore() {
        val s = _state.value
        val sourceId = s.sourceId ?: return
        if (s.loadingMore || s.endReached) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            val more = repo.items(s.selectedCategoryId, s.type, sourceId, s.query, pageSize, s.items.size)
            val merged = (s.items + more).distinctBy { it.id }
            val endReached = more.size < pageSize
            _state.update {
                it.copy(items = merged, loadingMore = false, endReached = endReached)
            }
        }
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            repo.setFavorite(channel.id, !channel.isFavorite)
        }
    }

    fun onPlay(channel: Channel) {
        viewModelScope.launch { repo.markWatched(channel.id) }
    }

    private fun reloadCategories() {
        val sourceId = _state.value.sourceId ?: return
        viewModelScope.launch {
            val allCats = repo.categoriesFor(sourceId).first()
            val type = _state.value.type
            val ofType = allCats.filter { it.type == type }
            val total = ofType.sumOf { it.count }
            val list = buildList {
                add(CategoryUi(id = null, name = "All Channels", count = total))
                addAll(ofType.map { CategoryUi(id = it.id, name = it.name, count = it.count) })
            }
            _state.update { it.copy(categories = list) }
        }
    }

    private fun reloadItems() {
        itemsJob?.cancel()
        val s = _state.value
        val sourceId = s.sourceId ?: return
        _state.update { it.copy(items = emptyList(), endReached = false, loadingMore = false) }
        itemsJob = viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                repo.items(s.selectedCategoryId, s.type, sourceId, s.query, pageSize, 0)
            }
            _state.update { it.copy(items = list, endReached = list.size < pageSize) }
        }
    }
}
