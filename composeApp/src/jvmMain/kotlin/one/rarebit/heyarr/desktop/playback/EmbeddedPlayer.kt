package one.rarebit.heyarr.desktop.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import one.rarebit.heyarr.desktop.mcp.JsonWrite
import one.rarebit.heyarr.desktop.net.JsonScan
import java.io.File
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets

/** One subtitle or audio track as mpv lists it. */
data class MpvTrack(val id: Int, val type: String, val title: String?, val lang: String?, val selected: Boolean, val external: Boolean) {
    val label: String get() = listOfNotNull(lang?.uppercase(), title).joinToString(" · ").ifBlank { "#$id" }
}

/** What the player is doing right now — the only state the controls read. */
data class PlayerState(
    val loaded: Boolean = false,
    val paused: Boolean = true,
    val position: Double = 0.0,
    val duration: Double = 0.0,
    val volume: Double = 100.0,
    val muted: Boolean = false,
    val buffering: Boolean = false,
    val eof: Boolean = false,
    val subtitles: List<MpvTrack> = emptyList(),
    val audio: List<MpvTrack> = emptyList(),
    val subtitleId: Int? = null,
    val audioId: Int? = null,
    val error: String? = null,
    val title: String? = null,
) {
    val fraction: Float get() = if (duration > 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f
}

/**
 * mpv, embedded: the same mpv this box already has, rendering into an X11 window id we
 * own (`--wid`), with its own OSC and key bindings switched off and every control
 * driven over its JSON IPC socket. The UI draws the transport; mpv draws the frames.
 *
 * Why not libmpv or VLC: mpv is on PATH here, `--wid` has worked for a decade, and the
 * IPC surface is a dozen commands. The bearer token rides in
 * `--http-header-fields` as an argv element (no shell), never in a log line.
 *
 * Blocking I/O lives on a reader thread; state lands in a Compose `mutableStateOf`
 * so the controls recompose on every property change. Pure parsing (events →
 * state) is split into [PlayerEvents] and unit-tested.
 */
class EmbeddedPlayer(
    private val command: String = "mpv",
    private val spawn: (List<String>) -> Process = { argv ->
        ProcessBuilder(argv).apply {
            // --wid is an X11 embed. With WAYLAND_DISPLAY in its environment mpv picks its
            // Wayland backend, ignores the wid and opens a window of its own (verified on
            // Hyprland): the JVM is an XWayland client, so mpv must be one too.
            environment().remove("WAYLAND_DISPLAY")
            redirectErrorStream(true); redirectOutput(ProcessBuilder.Redirect.DISCARD)
        }.start()
    },
) {
    var state: PlayerState by mutableStateOf(PlayerState())
        private set

    private var process: Process? = null
    private var channel: SocketChannel? = null
    private var reader: Thread? = null
    private var socketPath: File? = null
    @Volatile private var closed = false

    val isRunning: Boolean get() = process?.isAlive == true && channel?.isOpen == true

    /** True when mpv runs in its own window rather than inside the app. */
    var poppedOut: Boolean = false
        private set
    /** Set when mpv went away on its own (the pop-out window was closed): the screen re-embeds and resumes. */
    var exited: Boolean by mutableStateOf(false)
        private set
    private var resumeAt: Double? = null
    private var lastUrl: String? = null
    private var lastToken: String? = null
    private var lastTitle: String? = null

    /**
     * Spawn mpv and load [url]. With a [wid] mpv renders into that X11 window (the
     * app's canvas); with none it opens its own window — the pop-out — and keeps its
     * OSC so that window is usable on its own, while the app's transport still drives
     * it over the same socket. Returns null on success, else a UI-safe reason.
     */
    fun start(wid: Long?, url: String, token: String, title: String): String? {
        close()
        closed = false
        poppedOut = wid == null
        lastUrl = url; lastToken = token; lastTitle = title
        val sock = File(System.getProperty("java.io.tmpdir"), "heyarr-mpv-${ProcessHandle.current().pid()}-${System.nanoTime()}.sock")
        socketPath = sock
        val argv = buildList {
            add(command)
            if (wid != null) add("--wid=$wid")
            add("--input-ipc-server=${sock.absolutePath}")
            addAll(listOf("--idle=yes", "--force-window=yes", "--keep-open=yes", "--cursor-autohide=no", "--no-terminal", "--msg-level=all=error"))
            if (wid != null) addAll(listOf("--osc=no", "--osd-level=0", "--osd-bar=no", "--input-default-bindings=no", "--input-vo-keyboard=no"))
            // The pop-out is picture only: the app's transport is the one UI in either mode.
            // Keyboard bindings stay on so the window answers space / arrows / f on its own.
            else addAll(listOf("--osc=no", "--osd-level=1", "--osd-bar=no", "--input-default-bindings=yes", "--input-vo-keyboard=yes", "--geometry=60%"))
            resumeAt?.let { if (it > 1.0) add("--start=$it") }
            resumeAt = null
            add("--http-header-fields=Authorization: Bearer $token")
            add("--title=$title")
            add(url)
        }
        exited = false
        process = try { spawn(argv) } catch (e: IOException) { return "mpv could not be started — is it installed and on PATH?" }
        // The socket appears once mpv is up; give it a few seconds.
        val deadline = System.currentTimeMillis() + 4000
        var ch: SocketChannel? = null
        while (System.currentTimeMillis() < deadline && ch == null) {
            if (process?.isAlive != true) return "mpv exited before it opened its control socket."
            ch = try {
                SocketChannel.open(StandardProtocolFamily.UNIX).also { it.connect(UnixDomainSocketAddress.of(sock.toPath())) }
            } catch (e: IOException) { Thread.sleep(80); null }
        }
        channel = ch ?: return "mpv started but its control socket never answered."
        state = PlayerState(title = title)
        reader = Thread({ readLoop(ch) }, "mpv-ipc").apply { isDaemon = true; start() }
        for ((i, prop) in OBSERVED.withIndex()) send("observe_property", i + 1, prop)
        return null
    }

    /** Replace what is playing (the token was given at start; mpv keeps its header option). */
    fun load(url: String, title: String) {
        lastUrl = url; lastTitle = title
        state = state.copy(loaded = false, position = 0.0, duration = 0.0, eof = false, error = null, title = title, subtitles = emptyList(), audio = emptyList())
        send("loadfile", url, "replace")
        send("set_property", "pause", false)
    }

    /**
     * Move playback between the app's surface and a window of mpv's own, keeping the
     * position, pause state and volume. mpv cannot re-parent a live window, so this
     * is a restart with a seek — the file is streamed, so it resumes in a moment.
     */
    fun switchWindow(wid: Long?): String? {
        val url = lastUrl ?: return "nothing is playing"
        val token = lastToken ?: return "nothing is playing"
        val title = lastTitle ?: ""
        val resume = state.copy()
        val err = start(wid, url, token, title) ?: run {
            if (resume.position > 1.0) send("set_property", "start", resume.position.toString())
            send("set_property", "volume", resume.volume)
            send("set_property", "mute", resume.muted)
            if (resume.position > 1.0) send("seek", resume.position, "absolute")
            send("set_property", "pause", resume.paused)
            null
        }
        return err
    }

    fun togglePause() = send("cycle", "pause")
    fun play() = send("set_property", "pause", false)
    fun pause() = send("set_property", "pause", true)
    fun seekBy(seconds: Double) = send("seek", seconds, "relative")
    fun seekTo(seconds: Double) = send("seek", seconds, "absolute")
    fun seekFraction(f: Double) { if (state.duration > 0) seekTo(f * state.duration) }
    fun setVolume(v: Double) = send("set_property", "volume", v.coerceIn(0.0, 130.0))
    fun toggleMute() = send("cycle", "mute")
    fun setSubtitle(id: Int?) = send("set_property", "sid", id ?: "no")
    fun cycleSubtitle() = send("cycle", "sub")
    fun setAudio(id: Int) = send("set_property", "aid", id)
    fun stop() = send("stop")

    fun close() {
        closed = true
        runCatching { send("quit") }
        runCatching { channel?.close() }
        channel = null
        process?.let { p -> if (!p.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly() }
        process = null
        socketPath?.delete()
        state = PlayerState()
    }

    private fun send(vararg command: Any?) {
        val ch = channel ?: return
        val line = JsonWrite.obj(mapOf("command" to command.toList())) + "\n"
        try {
            synchronized(ch) { ch.write(ByteBuffer.wrap(line.toByteArray(StandardCharsets.UTF_8))) }
        } catch (e: IOException) {
            if (!closed) state = state.copy(error = "lost the connection to mpv")
        }
    }

    private fun readLoop(ch: SocketChannel) {
        val buf = ByteBuffer.allocate(64 * 1024)
        val pending = StringBuilder()
        try {
            while (!closed && ch.read(buf).also { if (it < 0) { onGone(); return } } >= 0) {
                buf.flip()
                pending.append(StandardCharsets.UTF_8.decode(buf))
                buf.clear()
                var nl: Int
                while (pending.indexOf("\n").also { nl = it } >= 0) {
                    val line = pending.substring(0, nl).trim()
                    pending.delete(0, nl + 1)
                    if (line.isNotEmpty()) state = PlayerEvents.apply(state, line)
                }
            }
        } catch (e: IOException) {
            if (!closed) onGone()
        }
    }

    /** mpv closed its end (the user shut the pop-out, or it crashed): remember where it was. */
    private fun onGone() {
        if (closed) return
        resumeAt = state.position
        state = state.copy(error = null)
        exited = true
    }

    /** Where playback was when mpv went away, for the re-embed. */
    val resumePosition: Double? get() = resumeAt

    companion object {
        /** Properties observed in order; the index+1 is the observer id. */
        val OBSERVED = listOf("time-pos", "duration", "pause", "volume", "mute", "paused-for-cache", "eof-reached", "track-list", "sid", "aid", "media-title")
    }
}

/** Pure: fold one mpv IPC line into the state. */
object PlayerEvents {
    fun apply(state: PlayerState, line: String): PlayerState {
        val obj = JsonScan.rootObject(line) ?: return state
        val event = JsonScan.stringField(obj, "event") ?: return state
        return when (event) {
            "property-change" -> onProperty(state, JsonScan.stringField(obj, "name") ?: return state, obj)
            "file-loaded" -> state.copy(loaded = true, error = null, eof = false)
            "end-file" -> {
                val reason = JsonScan.stringField(obj, "reason")
                if (reason == "error") state.copy(error = JsonScan.stringField(obj, "file_error") ?: "playback failed", loaded = false)
                else if (reason == "eof") state.copy(eof = true) else state
            }
            else -> state
        }
    }

    private fun onProperty(s: PlayerState, name: String, obj: String): PlayerState = when (name) {
        "time-pos" -> num(obj)?.let { s.copy(position = it) } ?: s
        "duration" -> num(obj)?.let { s.copy(duration = it) } ?: s
        "pause" -> JsonScan.boolField(obj, "data")?.let { s.copy(paused = it) } ?: s
        "volume" -> num(obj)?.let { s.copy(volume = it) } ?: s
        "mute" -> JsonScan.boolField(obj, "data")?.let { s.copy(muted = it) } ?: s
        "paused-for-cache" -> JsonScan.boolField(obj, "data")?.let { s.copy(buffering = it) } ?: s
        "eof-reached" -> JsonScan.boolField(obj, "data")?.let { s.copy(eof = it) } ?: s
        "sid" -> s.copy(subtitleId = JsonScan.longField(obj, "data")?.toInt())
        "aid" -> s.copy(audioId = JsonScan.longField(obj, "data")?.toInt())
        "media-title" -> JsonScan.stringField(obj, "data")?.let { s.copy(title = it) } ?: s
        "track-list" -> {
            val tracks = JsonScan.objectsOf(JsonScan.arrayOf(obj, listOf("data")) ?: "[]", emptyList()).mapNotNull { t ->
                val id = JsonScan.longField(t, "id")?.toInt() ?: return@mapNotNull null
                MpvTrack(
                    id = id, type = JsonScan.stringField(t, "type") ?: "?", title = JsonScan.stringField(t, "title"), lang = JsonScan.stringField(t, "lang"),
                    selected = JsonScan.boolField(t, "selected") ?: false, external = JsonScan.boolField(t, "external") ?: false,
                )
            }
            s.copy(subtitles = tracks.filter { it.type == "sub" }, audio = tracks.filter { it.type == "audio" })
        }
        else -> s
    }

    private fun num(obj: String): Double? {
        val i = JsonScan.valueStart(obj, "data") ?: return null
        var j = i
        while (j < obj.length && (obj[j].isDigit() || obj[j] in ".-+eE")) j++
        return obj.substring(i, j).toDoubleOrNull()
    }
}
