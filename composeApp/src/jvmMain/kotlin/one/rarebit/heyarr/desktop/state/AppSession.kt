package one.rarebit.heyarr.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.rarebit.heyarr.desktop.auth.Credential
import one.rarebit.heyarr.desktop.heyarr.HeyarrApi
import one.rarebit.heyarr.desktop.heyarr.McpResult
import one.rarebit.heyarr.desktop.heyarr.QualityProfile
import one.rarebit.heyarr.desktop.mcp.McpRefusedException
import one.rarebit.heyarr.desktop.mcp.McpTransportException
import one.rarebit.heyarr.desktop.net.HttpTransport
import one.rarebit.heyarr.desktop.open.OpenExternally
import one.rarebit.heyarr.desktop.playback.Player
import one.rarebit.heyarr.desktop.settings.DesktopConfig
import one.rarebit.heyarr.desktop.settings.SettingsStore
import one.rarebit.heyarr.desktop.theme.Appearance

/** Whether heyarr can be reached right now — drives the offline banner. */
enum class Connection { UNKNOWN, ONLINE, OFFLINE, UNAUTHORIZED, UNCONFIGURED }

/** A typed toast: what happened, and — for a refusal — the tool and its rule text, verbatim. */
data class Toast(
    val id: Long,
    val kind: Kind,
    val title: String,
    val detail: String? = null,
    val tool: String? = null,
) {
    enum class Kind { INFO, SUCCESS, ERROR, REFUSED }
}

/**
 * App-wide state every screen shares: the saved connection, the [HeyarrApi] built from
 * it, connectivity, the library-status index, quality profiles, and the toast queue.
 * Plain Compose state (the org's stance for this app; a ViewModel layer comes with the
 * shared module). Network work is launched on [Dispatchers.IO] through [io].
 */
class AppSession(
    private val settings: SettingsStore,
    private val transport: HttpTransport,
    val player: Player,
    val openExternally: OpenExternally,
    private val scope: CoroutineScope,
    artworkLoader: ArtworkLoader? = null,
    externalMetadata: ExternalMetadata? = null,
) {
    var config: DesktopConfig by mutableStateOf(settings.load())
        private set

    var appearance: Appearance by mutableStateOf(Appearance())

    val api: HeyarrApi? get() = config.bearerToken.trim().takeIf { it.isNotEmpty() }?.let {
        HeyarrApi(transport, config.baseUrl, Credential.Bearer(it))
    }

    /** App-wide playback: one mpv for the session, surface owned by the shell. */
    val playback = PlaybackSession()

    val artwork: ArtworkLoader = artworkLoader ?: ArtworkLoader({ config.baseUrl }, { config.bearerToken.trim() })
    val external: ExternalMetadata = externalMetadata ?: ExternalMetadata(enabled = { config.externalMetadata })
    val recent = RecentSearches(RecentSearches.defaultFile())

    var connection: Connection by mutableStateOf(if (config.bearerToken.isBlank()) Connection.UNCONFIGURED else Connection.UNKNOWN)
        private set
    var lastLatencyMs: Long? by mutableStateOf(null)
        private set
    var lastOkAt: Long? by mutableStateOf(null)
        private set
    var lastFailure: String? by mutableStateOf(null)
        private set
    var probes: Int by mutableStateOf(0)
        private set
    var failures: Int by mutableStateOf(0)
        private set

    var index: LibraryIndex by mutableStateOf(LibraryIndex.EMPTY)
        private set
    var indexLoading: Boolean by mutableStateOf(false)
        private set

    var profiles: List<QualityProfile> by mutableStateOf(emptyList())
        private set

    val toasts = mutableStateListOf<Toast>()
    private var toastSeq = 0L
    private var heartbeat: Job? = null

    // ── config ───────────────────────────────────────────────────────────────────

    fun save(updated: DesktopConfig) {
        settings.save(updated)
        config = updated
        artwork.reset()
        connection = if (updated.bearerToken.isBlank()) Connection.UNCONFIGURED else Connection.UNKNOWN
        refreshIndex()
        startHeartbeat()
    }

    // ── connectivity ─────────────────────────────────────────────────────────────

    fun startHeartbeat() {
        heartbeat?.cancel()
        heartbeat = scope.launch {
            while (isActive) {
                probe()
                delay(if (connection == Connection.ONLINE) 30_000 else 8_000)
            }
        }
    }

    suspend fun probe() {
        val a = api
        if (a == null) { connection = Connection.UNCONFIGURED; return }
        val t0 = System.nanoTime()
        val ok = withContext(Dispatchers.IO) { runCatching { a.ping() }.getOrDefault(false) }
        probes++
        lastLatencyMs = (System.nanoTime() - t0) / 1_000_000
        if (ok) { lastOkAt = System.currentTimeMillis(); connection = Connection.ONLINE } else { failures++; connection = Connection.OFFLINE }
    }

    /** Called by any screen whose call died on the transport — flips the banner immediately. */
    fun noteTransportFailure(e: McpTransportException) {
        failures++
        lastFailure = e.message
        connection = if (e.status == 401 || e.status == 403) Connection.UNAUTHORIZED else Connection.OFFLINE
    }

    fun noteSuccess() { lastOkAt = System.currentTimeMillis(); if (connection != Connection.ONLINE) connection = Connection.ONLINE }

    // ── library index + profiles ─────────────────────────────────────────────────

    fun refreshIndex() {
        val a = api ?: run { index = LibraryIndex.EMPTY; return }
        scope.launch {
            indexLoading = true
            val result = io { a.desired() }
            indexLoading = false
            result.onSuccess { index = LibraryIndex(it) }
        }
        if (profiles.isEmpty()) scope.launch { io { a.qualityProfiles() }.onSuccess { profiles = it } }
    }

    /** Optimistic want: the row flips to Wanted at once and rolls back with the refusal on failure. */
    fun want(workId: String, title: String, profile: String, onDone: (McpResult<*>?) -> Unit = {}) {
        val a = api ?: return
        val before = index
        index = index.withPendingWant(workId, profiles.firstOrNull { it.name == profile }?.id)
        scope.launch {
            val result = io { a.wantWork(workId, profile) }
            result.onFailure { index = before; onDone(null) }
            result.onSuccess { r ->
                when (r) {
                    is McpResult.Ok -> { toast(Toast.Kind.SUCCESS, "Wanted “$title”", "Measured against the ${profile} profile."); refreshIndex() }
                    is McpResult.Refused -> { index = before; refused(r) }
                }
                onDone(r)
            }
        }
    }

    // ── errors → toasts ──────────────────────────────────────────────────────────

    /** Run [block] on IO, turning a transport failure into the offline banner + an error toast. */
    suspend fun <T> io(block: () -> T): Result<T> = withContext(Dispatchers.IO) { runCatching(block) }.also { r ->
        r.onSuccess { noteSuccess() }
        r.onFailure { e ->
            when (e) {
                is McpTransportException -> { noteTransportFailure(e); toast(Toast.Kind.ERROR, "Can't reach heyarr", e.message) }
                is McpRefusedException -> toast(Toast.Kind.REFUSED, "heyarr refused", e.error.message, e.error.tool)
                else -> toast(Toast.Kind.ERROR, "Something went wrong", e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun refused(r: McpResult.Refused) = toast(Toast.Kind.REFUSED, "Refused by ${r.tool}", r.message, r.tool)

    fun toast(kind: Toast.Kind, title: String, detail: String? = null, tool: String? = null) {
        val t = Toast(++toastSeq, kind, title, detail, tool)
        toasts.add(t)
        scope.launch { delay(if (kind == Toast.Kind.REFUSED || kind == Toast.Kind.ERROR) 9_000 else 4_500); toasts.remove(t) }
    }

    fun dismiss(toast: Toast) { toasts.remove(toast) }
}
