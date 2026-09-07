package one.rarebit.heyarr.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntRect
import one.rarebit.heyarr.desktop.heyarr.HeyarrApi
import one.rarebit.heyarr.desktop.library.Episode
import one.rarebit.heyarr.desktop.playback.EmbeddedPlayer
import one.rarebit.heyarr.desktop.theme.MediaType
import one.rarebit.heyarr.desktop.ui.Route

/**
 * What is playing, app-wide. One mpv lives for the whole session, rendering into ONE
 * native surface the shell owns (see `VideoSurfaceHost`); screens only say where that
 * surface should be. So leaving the player screen does not stop playback: the surface
 * shrinks into the now-playing bar's thumbnail, and music or a podcast keeps going
 * while you browse. Coming back grows it again. Closing the bar is what stops it.
 */
class PlaybackSession {
    val player = EmbeddedPlayer()

    /** The item playing, null when idle. Same shape the player screen navigates with. */
    var current: Route.Player? by mutableStateOf(null)
        private set

    /** The work's playable episodes, for "next". */
    var queue: List<Episode> by mutableStateOf(emptyList())

    /** Where the native surface should sit, in window pixels; null hides it (1×1 off-screen). */
    var surfaceBounds: IntRect? by mutableStateOf(null)

    /** The player screen is showing (the surface is large); the bar hides itself then. */
    var onPlayerScreen: Boolean by mutableStateOf(false)

    var fullscreen: Boolean by mutableStateOf(false)
    /** Transport visibility in fullscreen; any mouse movement over the surface brings it back. */
    var controlsVisible: Boolean by mutableStateOf(true)
    var popout: Boolean by mutableStateOf(false)
    var startError: String? by mutableStateOf(null)
    var surfaceReady: Boolean by mutableStateOf(false)

    val active: Boolean get() = current != null
    val type: MediaType get() = current?.typeHint ?: MediaType.MOVIE

    /** Begin (or replace) playback. The surface host starts mpv once it has a window id. */
    fun play(item: Route.Player) {
        val same = current?.assetId == item.assetId
        current = item
        startError = null
        if (same && player.isRunning) { player.play(); return }
        if (player.isRunning && !popout) player.load(HeyarrApi.blobUrl(baseUrl, item.blobHash), title(item))
        else pendingStart = true
    }

    fun next(): Route.Player? {
        val c = current ?: return null
        val eps = queue.filter { it.isPlayable }
        val i = eps.indexOfFirst { it.asset.id == c.assetId }
        if (i < 0) return null
        val n = eps.getOrNull(i + 1) ?: return null
        val blob = n.asset.blobHash ?: return null
        return c.copy(assetId = n.asset.id, blobHash = blob, subtitle = n.label)
    }

    fun stop() {
        player.close()
        current = null
        queue = emptyList()
        pendingStart = false
        fullscreen = false
        popout = false
    }

    /** Set by the surface host after it started mpv; cleared when a new item needs a start. */
    var pendingStart: Boolean by mutableStateOf(false)
    var baseUrl: String = ""
    var token: String = ""
    var accentHex: String = "#00935E"

    fun title(item: Route.Player): String = item.title + (item.subtitle?.let { " — $it" } ?: "")
}
