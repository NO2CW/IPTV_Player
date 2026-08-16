package com.example.iptvplayer.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iptvplayer.AppViewModelProvider

private enum class SourceMode { M3U, XTREAM }

@Composable
fun SetupRoute(onNavigateChannels: () -> Unit) {
    val vm: SetupViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val busy by vm.busy.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.importDone.collect { onNavigateChannels() }
    }

    SetupScreen(
        busy = busy,
        progress = progress,
        error = error,
        onM3uUrl = { name, url -> vm.importM3uUrl(name, url) },
        onM3uText = { name, text -> vm.importM3uText(name, text) },
        onXtream = { n, s, u, p -> vm.importXtream(n, s, u, p) }
    )
}

@Composable
private fun SetupScreen(
    busy: Boolean,
    progress: String,
    error: String?,
    onM3uUrl: (String, String) -> Unit,
    onM3uText: (String, String) -> Unit,
    onXtream: (String, String, String, String) -> Unit
) {
    var mode by remember { mutableStateOf(SourceMode.M3U) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("IPTV Player", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Add your playlist or account", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = mode == SourceMode.M3U,
                onClick = { mode = SourceMode.M3U },
                label = { Text("M3U") }
            )
            FilterChip(
                selected = mode == SourceMode.XTREAM,
                onClick = { mode = SourceMode.XTREAM },
                label = { Text("Server login") }
            )
        }
        Spacer(Modifier.height(24.dp))

        when (mode) {
            SourceMode.M3U -> M3uFields(busy = busy, onUrl = onM3uUrl, onText = onM3uText)
            SourceMode.XTREAM -> XtreamFields(busy = busy, onXtream = onXtream)
        }

        if (busy) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.width(28.dp).height(28.dp), strokeWidth = 3.dp)
                Text(progress)
            }
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun M3uFields(
    busy: Boolean,
    onUrl: (String, String) -> Unit,
    onText: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("My Playlist") }
    var url by remember { mutableStateOf("") }
    var pasted by remember { mutableStateOf("") }

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(0.6f)
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        label = { Text("M3U / M3U8 URL (http/https)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(0.6f)
    )
    Spacer(Modifier.height(8.dp))
    Text("…or paste the M3U text below:", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = pasted,
        onValueChange = { pasted = it },
        label = { Text("Pasted M3U (optional)") },
        modifier = Modifier.fillMaxWidth(0.6f).height(160.dp)
    )
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            enabled = !busy && url.isNotBlank(),
            onClick = { onUrl(name.ifBlank { "My Playlist" }, url.trim()) }
        ) {
            Text("Import from URL")
        }
        Button(
            enabled = !busy && pasted.trim().startsWith("#EXTM3U"),
            onClick = { onText(name.ifBlank { "My Playlist" }, pasted) }
        ) {
            Text("Import pasted")
        }
    }
}

@Composable
private fun XtreamFields(
    busy: Boolean,
    onXtream: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("My Provider") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    val enabled = !busy && server.isNotBlank() && user.isNotBlank() && pass.isNotBlank()

    OutlinedTextField(
        value = name, onValueChange = { name = it }, label = { Text("Name") },
        singleLine = true, modifier = Modifier.fillMaxWidth(0.6f)
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = server,
        onValueChange = { server = it },
        label = { Text("Server (host or full URL)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(0.6f)
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = user, onValueChange = { user = it }, label = { Text("Username") },
            singleLine = true, modifier = Modifier.width(260.dp)
        )
        OutlinedTextField(
            value = pass, onValueChange = { pass = it }, label = { Text("Password") },
            singleLine = true, modifier = Modifier.width(260.dp)
        )
    }
    Spacer(Modifier.height(16.dp))
    Button(enabled = enabled, onClick = {
        onXtream(name.ifBlank { "My Provider" }, server.trim(), user.trim(), pass)
    }) {
        Text("Import account")
    }
}
