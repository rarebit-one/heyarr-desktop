package one.rarebit.heyarr.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import one.rarebit.heyarr.desktop.heyarr.HeyarrApi
import one.rarebit.heyarr.desktop.library.Episode
import one.rarebit.heyarr.desktop.playback.EmbeddedPlayer
import one.rarebit.heyarr.desktop.theme.MediaType
import one.rarebit.heyarr.desktop.ui.Route

/**
 * What is playing, app-wide. One mpv lives for the whole session (started by
 * `PlaybackHost` at the root), producing frames any screen may draw with `VideoSurface`.
 * So leaving the player screen does not stop playback: the now-playing bar shows the
 * same picture small, and music or a podcast keeps going while you browse. Closing
 * the bar is what stops it.
 */
class PlaybackSession {
    val player = EmbeddedPlayer()

    /** The item playing, null when idle. Same shape the player screen navigates with. */
    var current: Route.Player? by mutableStateOf(null)
        private set

    /** The work's playable episodes, for "next". */
    var queue: List<Episode> by mutableStateOf(emptyList())

    /** The player screen is showing; the bar hides itself then. */
    var onPlayerScreen: Boolean by mutableStateOf(false)

    var fullscreen: Boolean by mutableStateOf(false)
    /** Bumped by anything that should show the fullscreen transport again (a key, the pointer). */
    var controlsTick: Int by mutableStateOf(0)
        private set
    fun wakeControls() { controlsTick++ }
    var popout: Boolean by mutableStateOf(false)
    var startError: String? by mutableStateOf(null)

    val active: Boolean get() = current != null
    val type: MediaType get() = current?.typeHint ?: MediaType.MOVIE

    /** Begin (or replace) playback. The host starts mpv when one is needed. */
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

    /** Cleared by the host once it started mpv; set when a new item needs a start. */
    var pendingStart: Boolean by mutableStateOf(false)
    var baseUrl: String = ""
    var token: String = ""
    var accentHex: String = "#00935E"

    fun title(item: Route.Player): String = item.title + (item.subtitle?.let { " — $it" } ?: "")
}
