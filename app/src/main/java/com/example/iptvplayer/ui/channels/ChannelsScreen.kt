package com.example.iptvplayer.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.iptvplayer.AppViewModelProvider
import com.example.iptvplayer.data.model.Channel
import com.example.iptvplayer.data.model.ChannelType
import com.example.iptvplayer.data.model.PlaylistSource

@Composable
fun ChannelsRoute(onPlay: (Channel) -> Unit, onAddSource: () -> Unit) {
    val vm: ChannelsViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val state by vm.state.collectAsStateWithLifecycle()

    ChannelsScreen(
        state = state,
        onTypeSelected = vm::onTypeSelected,
        onCategorySelected = vm::onCategorySelected,
        onSearch = vm::onSearchQuery,
        onLoadMore = vm::loadMore,
        onToggleFavorite = vm::toggleFavorite,
        onHide = vm::onHide,
        onUnhide = vm::onUnhide,
        onSelectSource = vm::onSelectSource,
        onDeleteSource = vm::onDeleteSource,
        onAddSource = onAddSource,
        onPlay = onPlay
    )
}

@Composable
private fun ChannelsScreen(
    state: ChannelsUiState,
    onTypeSelected: (ChannelType) -> Unit,
    onCategorySelected: (Long?) -> Unit,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onHide: (CategoryUi) -> Unit,
    onUnhide: (String) -> Unit,
    onSelectSource: (Long) -> Unit,
    onDeleteSource: (Long) -> Unit,
    onAddSource: () -> Unit,
    onPlay: (Channel) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        TopBar(
            type = state.type,
            query = state.query,
            sources = state.sources,
            onTypeSelected = onTypeSelected,
            onSearch = onSearch,
            onSelectSource = onSelectSource,
            onAddSource = onAddSource,
            onDeleteSource = onDeleteSource
        )
        if (state.sourceId == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No playlist loaded", color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onAddSource) { Text("Add a playlist") }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                CategoryRail(
                    categories = state.categories,
                    hidden = state.hidden,
                    selected = state.selectedCategoryId,
                    onSelect = onCategorySelected,
                    onHide = onHide,
                    onUnhide = onUnhide,
                    modifier = Modifier.width(300.dp).fillMaxHeight()
                )
                ChannelList(
                    channels = state.items,
                    endReached = state.endReached,
                    onLoadMore = onLoadMore,
                    onToggleFavorite = onToggleFavorite,
                    onPlay = onPlay
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    type: ChannelType,
    query: String,
    sources: List<PlaylistSource>,
    onTypeSelected: (ChannelType) -> Unit,
    onSearch: (String) -> Unit,
    onSelectSource: (Long) -> Unit,
    onAddSource: () -> Unit,
    onDeleteSource: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Channels", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            OutlinedTextField(
                value = query,
                onValueChange = onSearch,
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier.width(320.dp)
            )
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = onAddSource) {
                Text("＋ Add list", color = MaterialTheme.colorScheme.primary)
            }
            if (sources.isNotEmpty()) {
                TextButton(onClick = {
                    sources.firstOrNull { it.isActive }?.id?.let(onDeleteSource)
                }) {
                    Text("Delete list", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (sources.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sources) { src ->
                    FilterChip(
                        selected = src.isActive,
                        onClick = { onSelectSource(src.id) },
                        label = { Text(src.name) }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(ChannelType.entries) { t ->
                FilterChip(selected = type == t, onClick = { onTypeSelected(t) }, label = { Text(t.label) })
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Color.Gray)
    }
}

@Composable
private fun CategoryRail(
    categories: List<CategoryUi>,
    hidden: List<String>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    onHide: (CategoryUi) -> Unit,
    onUnhide: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LazyColumn(state = listState, modifier = modifier.padding(horizontal = 12.dp)) {
        items(categories) { cat ->
            val isSelected = cat.id == selected
            Surface(
                onClick = { onSelect(cat.id) },
                shape = RoundedCornerShape(6.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).focusable()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = cat.name,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        color = if (isSelected) Color.White else Color.LightGray
                    )
                    Text("${cat.count}", color = if (isSelected) Color.White else Color.Gray)
                    if (cat.id != null) {
                        TextButton(onClick = { onHide(cat) }) { Text("✕", color = Color.Gray) }
                    }
                }
            }
        }

        if (hidden.isNotEmpty()) {
            item {
                Text(
                    "Hidden groups",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            items(hidden) { name ->
                Surface(
                    onClick = { onUnhide(name) },
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).focusable()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(name, color = Color.DarkGray, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("↺ restore", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelList(
    channels: List<Channel>,
    endReached: Boolean,
    onLoadMore: () -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onPlay: (Channel) -> Unit
) {
    val listState = rememberLazyListState()

    // Trigger paging when the user scrolls near the bottom of the currently loaded page.
    val shouldLoadMore by derivedStateOf {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        channels.isNotEmpty() && !endReached && lastVisible >= channels.size - 8
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 12.dp)) {
        items(channels, key = { it.id }) { channel ->
            ChannelRow(
                channel = channel,
                onToggleFavorite = onToggleFavorite,
                onPlay = onPlay
            )
        }
    }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    onToggleFavorite: (Channel) -> Unit,
    onPlay: (Channel) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = { onPlay(channel) },
        shape = RoundedCornerShape(6.dp),
        color = if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.30f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            channel.channelNumber?.let {
                Text("$it", color = Color.Gray, modifier = Modifier.width(44.dp))
            }
            LogoThumb(url = channel.logoUrl, name = channel.name)
            Spacer(Modifier.width(10.dp))
            Text(
                text = channel.name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                color = if (isFocused) Color.White else Color.White
            )
            Text(
                text = if (channel.isFavorite) "★" else "☆",
                color = if (channel.isFavorite) Color(0xFFFFC107) else Color.Gray,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@Composable
private fun LogoThumb(url: String?, name: String) {
    if (url.isNullOrBlank()) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(name.firstOrNull()?.uppercase() ?: "•", color = Color.Gray)
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = name,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.DarkGray)
        )
    }
}


