package one.rarebit.heyarr.desktop.feeds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import one.rarebit.heyarr.desktop.library.WorkDetailClient
import one.rarebit.heyarr.desktop.ui.BrowseHeader
import one.rarebit.heyarr.desktop.ui.BrowseRow
import one.rarebit.heyarr.desktop.ui.SectionEnv

/**
 * FEEDS (the Archive): followed sources → a source's archived items → **Open**. An item
 * carries no blob of its own, so Open resolves the item's `work_id`
 * (`GET /works/{id}` → `primary_asset`), downloads the archived blob (a single-file HTML
 * article, ADR-0063) to a temp file and hands it to `xdg-open` (opens in the browser). An
 * item that is not archived yet, or a work with no held bytes, says so — no crash.
 */
@Composable
fun FeedsSection(env: SectionEnv) {
    val scope = rememberCoroutineScope()

    var sources by remember { mutableStateOf<List<FollowedSource>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    var source by remember { mutableStateOf<FollowedSource?>(null) }
    var itemsList by remember { mutableStateOf<List<FollowedItem>>(emptyList()) }
    var itemsLoading by remember { mutableStateOf(false) }
    var itemsStatus by remember { mutableStateOf<String?>(null) }
    var openStatus by remember { mutableStateOf<String?>(null) }
    var openingId by remember { mutableStateOf<String?>(null) }

    fun feedsClient() = FeedsClient(env.transport, env.baseUrl, env.credential)

    fun loadSources() {
        if (!env.hasToken) { status = "Set a bearer token in Settings first."; return }
        loading = true; status = null
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { feedsClient().listSources() } }
            loading = false; loaded = true
            result.onSuccess { sources = it; status = if (it.isEmpty()) "No followed sources." else "${it.size} sources." }
                .onFailure { status = it.message ?: "Failed to load followed sources." }
        }
    }

    fun openSource(s: FollowedSource) {
        source = s; itemsList = emptyList(); itemsStatus = null; openStatus = null; itemsLoading = true
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { feedsClient().listItems(s.id) } }
            itemsLoading = false
            result.onSuccess { itemsList = it; itemsStatus = if (it.isEmpty()) "No archived items." else null }
                .onFailure { itemsStatus = it.message ?: "Failed to load items." }
        }
    }

    fun openItem(item: FollowedItem) {
        if (!item.archived) { openStatus = "“${item.title}” is not archived yet."; return }
        val workId = item.workId
        if (workId.isNullOrBlank()) { openStatus = "This item has no linked work to open."; return }
        openingId = item.id; openStatus = "Opening “${item.title}”…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val detail = runCatching {
                    WorkDetailClient(env.transport, env.baseUrl, env.credential).getWorkDetail(workId)
                }.getOrElse { return@withContext it.message ?: "Failed to resolve the item." }
                val asset = detail?.primaryAsset
                when {
                    detail == null -> "This item's work no longer exists."
                    asset == null || asset.blobHash.isBlank() -> "No archived bytes held for this item yet."
                    else -> env.openExternally.open(
                        baseUrl = env.baseUrl,
                        blobHash = asset.blobHash,
                        token = env.token,
                        filename = null,
                        mime = asset.mime ?: "text/html",
                        displayName = item.title,
                    )
                }
            }
            openingId = null; openStatus = result
        }
    }

    LaunchedEffect(Unit) { if (!loaded && !loading) loadSources() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        val current = source
        if (current == null) {
            BrowseHeader(onRefresh = { loadSources() }, loading = loading, status = status)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sources) { s -> BrowseRow(s.title, s.subtitle, onClick = { openSource(s) }) }
            }
        } else {
            BrowseHeader(onBack = { source = null; openStatus = null }, backLabel = "← Feeds", loading = itemsLoading, status = itemsStatus)
            Text(current.title, style = MaterialTheme.typography.headlineSmall)
            current.feedRef?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))
            }
            openStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp)) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(itemsList) { item ->
                    val tick = if (item.archived) "✓ " else "· "
                    BrowseRow(tick + item.title, item.subtitle, onClick = { openItem(item) })
                }
            }
        }
    }
}
