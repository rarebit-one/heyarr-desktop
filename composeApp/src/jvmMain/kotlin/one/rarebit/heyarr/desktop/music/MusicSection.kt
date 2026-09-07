package one.rarebit.heyarr.desktop.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.rarebit.heyarr.desktop.catalog.Grouping
import one.rarebit.heyarr.desktop.library.Work
import one.rarebit.heyarr.desktop.playback.PlayResult
import one.rarebit.heyarr.desktop.ui.BrowseHeader
import one.rarebit.heyarr.desktop.ui.BrowseRow
import one.rarebit.heyarr.desktop.ui.SectionEnv

/**
 * MUSIC: artists → albums (works) → tracks (audio assets). Plain Compose state, network on
 * `Dispatchers.IO`, matching `App.kt`. Play one track via [SectionEnv.player]; queue a whole
 * album (its ordered track blobs) via `player.playAll`.
 */
@Composable
fun MusicSection(env: SectionEnv) {
    val scope = rememberCoroutineScope()

    var artists by remember { mutableStateOf<List<Grouping>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    var artist by remember { mutableStateOf<String?>(null) }
    var albums by remember { mutableStateOf<List<Work>>(emptyList()) }
    var albumsLoading by remember { mutableStateOf(false) }
    var albumsStatus by remember { mutableStateOf<String?>(null) }

    var album by remember { mutableStateOf<Work?>(null) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var tracksLoading by remember { mutableStateOf(false) }
    var tracksStatus by remember { mutableStateOf<String?>(null) }
    var playStatus by remember { mutableStateOf<String?>(null) }

    fun client() = MusicClient(env.transport, env.baseUrl, env.credential)

    fun loadArtists() {
        if (!env.hasToken) { status = "Set a bearer token in Settings first."; return }
        loading = true; status = null
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { client().listArtists() } }
            loading = false; loaded = true
            result.onSuccess { artists = it; status = if (it.isEmpty()) "No artists." else "${it.size} artists." }
                .onFailure { status = it.message ?: "Failed to load artists." }
        }
    }

    fun openArtist(name: String) {
        artist = name; album = null; albums = emptyList(); albumsStatus = null; albumsLoading = true
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { client().listAlbums(name) } }
            albumsLoading = false
            result.onSuccess { albums = it; albumsStatus = if (it.isEmpty()) "No works for this artist." else null }
                .onFailure { albumsStatus = it.message ?: "Failed to load works." }
        }
    }

    fun openAlbum(work: Work) {
        album = work; tracks = emptyList(); tracksStatus = null; playStatus = null; tracksLoading = true
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { client().listTracks(work.id) } }
            tracksLoading = false
            result.onSuccess { tracks = it; tracksStatus = if (it.isEmpty()) "No playable audio for this work." else null }
                .onFailure { tracksStatus = it.message ?: "Failed to load tracks." }
        }
    }

    fun play(hashes: List<String>, label: String) {
        if (hashes.isEmpty()) { playStatus = "Nothing to play."; return }
        playStatus = "Launching mpv…"
        scope.launch {
            val result = withContext(Dispatchers.IO) { env.player.playAll(env.baseUrl, hashes, env.token) }
            playStatus = when (result) {
                is PlayResult.Launched -> "Playing $label in mpv."
                is PlayResult.Failed -> result.message
            }
        }
    }

    LaunchedEffect(Unit) { if (!loaded && !loading) loadArtists() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        when {
            album != null -> {
                val a = album!!
                BrowseHeader(
                    onBack = { album = null; playStatus = null },
                    backLabel = "← ${artist ?: "Artist"}",
                    loading = tracksLoading,
                    status = tracksStatus,
                )
                Text(a.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
                val playable = tracks.mapNotNull { it.blobHash }
                Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { play(playable, "album") }, enabled = playable.isNotEmpty()) {
                        Text("Queue album (${playable.size})")
                    }
                }
                playStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp)) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tracks) { t ->
                        BrowseRow(t.title, t.subtitle, onClick = { t.blobHash?.let { play(listOf(it), t.title) } })
                    }
                }
            }
            artist != null -> {
                BrowseHeader(
                    onBack = { artist = null },
                    backLabel = "← Artists",
                    loading = albumsLoading,
                    status = albumsStatus,
                )
                Text(artist!!, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(albums) { w -> BrowseRow(w.title, w.subtitle, onClick = { openAlbum(w) }) }
                }
            }
            else -> {
                BrowseHeader(onRefresh = { loadArtists() }, loading = loading, status = status)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(artists) { g ->
                        BrowseRow(g.name, "${g.workCount} works", onClick = { openArtist(g.name) })
                    }
                }
            }
        }
    }
}
