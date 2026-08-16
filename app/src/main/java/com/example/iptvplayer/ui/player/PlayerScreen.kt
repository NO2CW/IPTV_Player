package com.example.iptvplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.iptvplayer.AppViewModelProvider
import com.example.iptvplayer.data.model.Channel

@Composable
fun PlayerRoute(channelId: Long, onExit: () -> Unit) {
    val vm: PlayerViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(channelId) { vm.load(channelId) }

    PlayerScreen(
        state = state,
        onExit = onExit,
        onSwitchNumber = vm::switchToNumber,
        onPrevious = vm::previousChannel
    )
}

@Composable
private fun PlayerScreen(
    state: PlayerUiState,
    onExit: () -> Unit,
    onSwitchNumber: (Int) -> Unit,
    onPrevious: () -> Unit
) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_RESUME -> if (player.mediaItemCount > 0) player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (val s = state) {
        is PlayerUiState.Loading -> CenteredMessage("Loading…")

        is PlayerUiState.Error -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(s.message, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onExit) { Text("Back") }
        }

        is PlayerUiState.Ready -> {
            if (s.channel.streamUrl.isBlank()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Text("Series playback (episode picker) arrives in the next version.")
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onExit) { Text("Back") }
                }
            } else {
                PlayChannel(
                    player = player,
                    channel = s.channel,
                    onExit = onExit,
                    onSwitchNumber = onSwitchNumber,
                    onPrevious = onPrevious
                )
            }
        }
    }
}

private val aspectModes = listOf(
    "Fit" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
    "Zoom" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    "Stretch" to AspectRatioFrameLayout.RESIZE_MODE_FILL,
    "Fit W" to AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
    "Fit H" to AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT,
)

@Composable
private fun PlayChannel(
    player: ExoPlayer,
    channel: Channel,
    onExit: () -> Unit,
    onSwitchNumber: (Int) -> Unit,
    onPrevious: () -> Unit
) {
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var numberText by remember { mutableStateOf("") }

    LaunchedEffect(channel.id, channel.streamUrl) {
        player.setMediaItem(MediaItem.fromUri(channel.streamUrl))
        player.prepare()
        player.play()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { useController = true } },
            update = { view ->
                view.player = player
                view.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )
        Column(Modifier.align(Alignment.TopStart).padding(12.dp)) {
            Text(channel.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
            channel.channelNumber?.let { Text("CH $it", color = Color.LightGray) }
        }
        TextButton(onClick = onExit, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            Text("Back", color = Color.White)
        }

        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            aspectModes.forEach { (label, mode) ->
                TextButton(onClick = { resizeMode = mode }) {
                    Text(label, color = if (resizeMode == mode) MaterialTheme.colorScheme.primary else Color.White)
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onPrevious) { Text("↩ Prev", color = Color.White) }
            OutlinedTextField(
                value = numberText,
                onValueChange = { numberText = it.filter { c -> c.isDigit() } },
                label = { Text("Channel #") },
                singleLine = true,
                modifier = Modifier.width(140.dp)
            )
            Spacer(Modifier.width(6.dp))
            Button(onClick = {
                numberText.toIntOrNull()?.let {
                    onSwitchNumber(it)
                    numberText = ""
                }
            }) {
                Text("GO")
            }
        }
    }
}

@Composable
private fun CenteredMessage(msg: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(msg, color = Color.White)
    }
}
