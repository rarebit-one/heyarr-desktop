package one.rarebit.heyarr.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.rarebit.heyarr.desktop.auth.Credential
import one.rarebit.heyarr.desktop.library.LibraryClient
import one.rarebit.heyarr.desktop.library.Work
import one.rarebit.heyarr.desktop.library.WorkDetail
import one.rarebit.heyarr.desktop.library.WorkDetailClient
import one.rarebit.heyarr.desktop.net.HttpTransport
import one.rarebit.heyarr.desktop.playback.MpvPlayer
import one.rarebit.heyarr.desktop.playback.PlayResult
import one.rarebit.heyarr.desktop.playback.Player
import one.rarebit.heyarr.desktop.settings.DesktopConfig
import one.rarebit.heyarr.desktop.settings.SettingsStore

/**
 * The whole v1 UI: two tabs, Settings and Library. State is held in plain Compose
 * `mutableStateOf` (a ViewModel layer comes with the shared module); network work runs
 * on `Dispatchers.IO` — the blocking [HttpTransport] contract.
 *
 * The Library tab is a master/detail: the list of works, and — when a row is clicked —
 * a detail pane that resolves the work's playable file (`GET /works/{id}`) and hands it
 * to the [player] (mpv) on **Play**.
 */
@Composable
fun App(
    settings: SettingsStore,
    transport: HttpTransport,
    player: Player = MpvPlayer(),
) {
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf(settings.load()) }
    var selectedTab by remember { mutableStateOf(0) }

    // Library state.
    var works by remember { mutableStateOf<List<Work>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    // Detail state — the selected work and its resolved file.
    var selected by remember { mutableStateOf<Work?>(null) }
    var detail by remember { mutableStateOf<WorkDetail?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var detailStatus by remember { mutableStateOf<String?>(null) }
    var playStatus by remember { mutableStateOf<String?>(null) }

    fun refreshLibrary() {
        val token = config.bearerToken.trim()
        if (token.isEmpty()) {
            status = "Set a bearer token in Settings first."
            selectedTab = 0
            return
        }
        loading = true
        status = null
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    LibraryClient(transport, config.baseUrl, Credential.Bearer(token)).listWorks()
                }
            }
            loading = false
            result
                .onSuccess {
                    works = it
                    status = if (it.isEmpty()) "No works returned." else "${it.size} works."
                }
                .onFailure { status = it.message ?: "Failed to load library." }
        }
    }

    fun openWork(work: Work) {
        selected = work
        detail = null
        playStatus = null
        detailLoading = true
        detailStatus = null
        val token = config.bearerToken.trim()
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    WorkDetailClient(transport, config.baseUrl, Credential.Bearer(token)).getWorkDetail(work.id)
                }
            }
            detailLoading = false
            result
                .onSuccess {
                    detail = it
                    detailStatus = when {
                        it == null -> "This work no longer exists."
                        !it.isPlayable -> "No playable file for this work."
                        else -> null
                    }
                }
                .onFailure { detailStatus = it.message ?: "Failed to load work detail." }
        }
    }

    fun play(detail: WorkDetail) {
        val asset = detail.primaryAsset ?: return
        val token = config.bearerToken.trim()
        playStatus = "Launching mpv…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                player.play(config.baseUrl, asset.blobHash, token)
            }
            playStatus = when (result) {
                is PlayResult.Launched -> "Playing in mpv."
                is PlayResult.Failed -> result.message
            }
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Settings") })
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; if (works.isEmpty()) refreshLibrary() },
                        text = { Text("Library") },
                    )
                }
                when (selectedTab) {
                    0 -> SettingsScreen(
                        config = config,
                        onSave = { updated ->
                            settings.save(updated)
                            config = updated
                            status = "Saved."
                        },
                    )
                    else -> {
                        val current = selected
                        if (current == null) {
                            LibraryScreen(
                                works = works,
                                loading = loading,
                                status = status,
                                onRefresh = { refreshLibrary() },
                                onOpen = { openWork(it) },
                            )
                        } else {
                            WorkDetailScreen(
                                work = current,
                                detail = detail,
                                loading = detailLoading,
                                status = detailStatus,
                                playStatus = playStatus,
                                onBack = { selected = null; detail = null; playStatus = null },
                                onPlay = { detail?.let { play(it) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    config: DesktopConfig,
    onSave: (DesktopConfig) -> Unit,
) {
    var baseUrl by remember(config) { mutableStateOf(config.baseUrl) }
    var token by remember(config) { mutableStateOf(config.bearerToken) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("heyarr connection", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Base URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Bearer token (heyarr_<id>_<secret>)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onSave(DesktopConfig(baseUrl = baseUrl.trim(), bearerToken = token.trim())) }) {
                Text("Save")
            }
            Button(onClick = { baseUrl = DesktopConfig.DEFAULT_BASE_URL }) {
                Text("Reset URL to default")
            }
        }
        Text(
            "Saved to ~/.config/heyarr-desktop/config.json. Voidbind device/QR login is " +
                "stubbed until voidbind-client is resolvable (needs a read:packages token).",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun LibraryScreen(
    works: List<Work>,
    loading: Boolean,
    status: String?,
    onRefresh: () -> Unit,
    onOpen: (Work) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(onClick = onRefresh, enabled = !loading) { Text("Refresh") }
            if (loading) {
                CircularProgressIndicator(Modifier.width(24.dp))
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(works) { work -> WorkRow(work, onClick = { onOpen(work) }) }
            }
        }
    }
}

@Composable
private fun WorkRow(work: Work, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(work.title, style = MaterialTheme.typography.titleMedium)
            if (work.subtitle.isNotBlank()) {
                Spacer(Modifier.width(4.dp))
                Text(work.subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WorkDetailScreen(
    work: Work,
    detail: WorkDetail?,
    loading: Boolean,
    status: String?,
    playStatus: String?,
    onBack: () -> Unit,
    onPlay: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("← Library") }
            if (loading) CircularProgressIndicator(Modifier.width(24.dp))
        }

        Text(work.title, style = MaterialTheme.typography.headlineSmall)
        val meta = listOfNotNull(work.year?.toString(), work.kind, work.artist ?: work.author)
            .joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(meta, style = MaterialTheme.typography.bodyMedium)
        }

        val playable = detail?.isPlayable == true
        val asset = detail?.primaryAsset
        if (asset != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Playable file", style = MaterialTheme.typography.titleSmall)
                    if (asset.summary.isNotBlank()) {
                        Text(asset.summary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onPlay, enabled = playable) { Text("Play") }
            playStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }

        // A non-play status: no asset, work gone, or a fetch failure.
        if (playStatus == null) {
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }

        Spacer(Modifier.height(4.dp))
    }
}
