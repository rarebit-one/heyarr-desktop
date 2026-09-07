package one.rarebit.heyarr.desktop.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import one.rarebit.heyarr.desktop.library.WorkDetailClient
import one.rarebit.heyarr.desktop.ui.BrowseHeader
import one.rarebit.heyarr.desktop.ui.BrowseRow
import one.rarebit.heyarr.desktop.ui.SectionEnv

/**
 * BOOKS: authors → book works → an **Open / Read** action. The book blob is authenticated,
 * so opening resolves the work (`GET /works/{id}` → `primary_asset`), downloads the blob to
 * a temp file and hands it to the system reader via `xdg-open`
 * ([SectionEnv.openExternally]). No-blob (only-wanted) and a missing opener surface as a
 * status line, never a crash.
 */
@Composable
fun BooksSection(env: SectionEnv) {
    val scope = rememberCoroutineScope()

    var authors by remember { mutableStateOf<List<Grouping>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    var author by remember { mutableStateOf<String?>(null) }
    var books by remember { mutableStateOf<List<Work>>(emptyList()) }
    var booksLoading by remember { mutableStateOf(false) }
    var booksStatus by remember { mutableStateOf<String?>(null) }

    var book by remember { mutableStateOf<Work?>(null) }
    var openStatus by remember { mutableStateOf<String?>(null) }
    var opening by remember { mutableStateOf(false) }

    fun booksClient() = BooksClient(env.transport, env.baseUrl, env.credential)

    fun loadAuthors() {
        if (!env.hasToken) { status = "Set a bearer token in Settings first."; return }
        loading = true; status = null
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { booksClient().listAuthors() } }
            loading = false; loaded = true
            result.onSuccess { authors = it; status = if (it.isEmpty()) "No authors." else "${it.size} authors." }
                .onFailure { status = it.message ?: "Failed to load authors." }
        }
    }

    fun openAuthor(name: String) {
        author = name; book = null; books = emptyList(); booksStatus = null; booksLoading = true
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { booksClient().listBooks(name) } }
            booksLoading = false
            result.onSuccess { books = it; booksStatus = if (it.isEmpty()) "No books for this author." else null }
                .onFailure { booksStatus = it.message ?: "Failed to load books." }
        }
    }

    fun openBook(work: Work) {
        book = work; openStatus = "Resolving…"; opening = true
        scope.launch {
            val status = withContext(Dispatchers.IO) {
                val detail = runCatching {
                    WorkDetailClient(env.transport, env.baseUrl, env.credential).getWorkDetail(work.id)
                }.getOrElse { return@withContext it.message ?: "Failed to resolve the book." }
                val asset = detail?.primaryAsset
                when {
                    detail == null -> "This work no longer exists."
                    asset == null || asset.blobHash.isBlank() ->
                        "No file to open yet — this book is wanted but not held."
                    else -> env.openExternally.open(
                        baseUrl = env.baseUrl,
                        blobHash = asset.blobHash,
                        token = env.token,
                        filename = null,
                        mime = asset.mime,
                        displayName = work.title,
                    )
                }
            }
            opening = false; openStatus = status
        }
    }

    LaunchedEffect(Unit) { if (!loaded && !loading) loadAuthors() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        when {
            book != null -> {
                val b = book!!
                BrowseHeader(onBack = { book = null; openStatus = null }, backLabel = "← ${author ?: "Author"}", loading = opening)
                Text(b.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 4.dp))
                if (b.subtitle.isNotBlank()) {
                    Text(b.subtitle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
                }
                Button(onClick = { openBook(b) }, enabled = !opening) { Text("Open / Read") }
                openStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp)) }
            }
            author != null -> {
                BrowseHeader(onBack = { author = null }, backLabel = "← Authors", loading = booksLoading, status = booksStatus)
                Text(author!!, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(books) { w -> BrowseRow(w.title, w.subtitle, onClick = { openBook(w) }) }
                }
            }
            else -> {
                BrowseHeader(onRefresh = { loadAuthors() }, loading = loading, status = status)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(authors) { g -> BrowseRow(g.name, "${g.workCount} books", onClick = { openAuthor(g.name) }) }
                }
            }
        }
    }
}
