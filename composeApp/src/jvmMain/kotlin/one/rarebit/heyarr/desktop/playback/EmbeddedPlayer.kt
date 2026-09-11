package one.rarebit.heyarr.desktop.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import one.rarebit.heyarr.desktop.mcp.JsonWrite
import one.rarebit.heyarr.desktop.net.JsonScan
import com.sun.jna.Pointer
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
 * mpv, embedded: libmpv inside this process, decoding into memory frames the picture
 * composable draws ([MpvRenderer]), with every control driven over mpv's JSON IPC
 * socket — the same socket a pop-out `mpv` process offers, so one transport drives
 * both. The UI draws the transport over the picture; mpv only ever produces frames.
 *
 * Why libmpv and not `--wid`: an X window embedded over Compose sits above everything
 * the app draws, so nothing could overlay the picture, and it only worked through
 * XWayland at all. A memory frame is a Compose element like any other. The bearer
 * token rides in `http-header-fields` as an option (no shell), never in a log line.
 *
 * Blocking I/O lives on a reader thread; state lands in a Compose `mutableStateOf`
 * so the controls recompose on every property change. Pure parsing (events →
 * state) is split into [PlayerEvents] and unit-tested.
 */
class EmbeddedPlayer(
    private val command: String = "mpv",
    private val spawn: (List<String>) -> Process = { argv ->
        ProcessBuilder(argv).apply { redirectErrorStream(true); redirectOutput(ProcessBuilder.Redirect.DISCARD) }.start()
    },
) {
    var state: PlayerState by mutableStateOf(PlayerState())
        private set

    /** The in-process renderer while embedded; the picture composable reads its frames. */
    internal var renderer: MpvRenderer? by mutableStateOf(null)
        private set

    private var lib: MpvLib? = null
    private var handle: Pointer? = null
    private var process: Process? = null
    private var channel: SocketChannel? = null
    private var reader: Thread? = null
    private var socketPath: File? = null
    @Volatile private var closed = false

    val isRunning: Boolean get() = (handle != null || process?.isAlive == true) && channel?.isOpen == true

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
    /** External subtitle URLs already `sub-add`ed to the current file, so re-applying is idempotent. */
    private val appliedSubs = mutableListOf<String>()

    /**
     * Start mpv and load [url]. [embedded] runs libmpv in-process, rendering into the
     * app; otherwise an `mpv` process opens its own window — the pop-out — while the
     * app's transport still drives it over the same socket. Returns null on success,
     * else a UI-safe reason.
     */
    fun start(embedded: Boolean, url: String, token: String, title: String): String? {
        close()
        closed = false
        poppedOut = !embedded
        lastUrl = url; lastToken = token; lastTitle = title
        val tmp = File(System.getProperty("java.io.tmpdir"))
        sweepStaleSockets(tmp)
        val sock = File(tmp, "heyarr-mpv-${ProcessHandle.current().pid()}-${System.nanoTime()}.sock")
        socketPath = sock
        val start = resumeAt?.takeIf { it > 1.0 }
        resumeAt = null
        exited = false
        val err = if (embedded) startInProcess(sock, token, title, start) else startProcess(sock, url, token, title, start)
        if (err != null) { close(); return err }
        // The socket appears once mpv is up; a window under XWayland can take a moment.
        val deadline = System.currentTimeMillis() + 12_000
        var ch: SocketChannel? = null
        while (System.currentTimeMillis() < deadline && ch == null) {
            if (!embedded && process?.isAlive != true) { close(); return "mpv exited before it opened its control socket." }
            ch = try {
                SocketChannel.open(StandardProtocolFamily.UNIX).also { it.connect(UnixDomainSocketAddress.of(sock.toPath())) }
            } catch (e: IOException) { Thread.sleep(80); null }
        }
        channel = ch ?: run { close(); return "mpv started but its control socket never answered." }
        state = PlayerState(title = title)
        reader = Thread({ readLoop(ch) }, "mpv-ipc").apply { isDaemon = true; start() }
        for ((i, prop) in OBSERVED.withIndex()) send("observe_property", i + 1, prop)
        if (embedded) send("loadfile", url)
        return null
    }

    /** libmpv in this process: no window, no OSD of its own, frames through [MpvRenderer]. */
    private fun startInProcess(sock: File, token: String, title: String, start: Double?): String? {
        val lib = MpvLib.loaded.getOrElse { return it.message ?: "libmpv is not installed" }
        val h = lib.mpv_create() ?: return "libmpv could not create a player"
        val options = listOf(
            "vo" to "libmpv", "input-ipc-server" to sock.absolutePath, "idle" to "yes", "keep-open" to "yes", "terminal" to "no",
            // `auto-copy`, not `no`: 4K HEVC software-decodes far too slowly here and stutters. auto-copy
            // decodes on the GPU (the expensive part) then copies frames back to system memory, which is what
            // this frame-readback renderer needs — plain `auto` would hand back GPU-only frames it cannot read.
            // It falls back to software when no hardware decoder is available, so it is safe on every machine.
            "msg-level" to "all=error", "osc" to "no", "osd-level" to "0", "input-default-bindings" to "no", "hwdec" to "auto-copy",
            "http-header-fields" to "Authorization: Bearer $token", "force-media-title" to title,
        ) + listOfNotNull(start?.let { "start" to it.toString() })
        for ((k, v) in options) lib.mpv_set_option_string(h, k, v)
        val rc = lib.mpv_initialize(h)
        if (rc < 0) { lib.mpv_terminate_destroy(h); return "libmpv: ${lib.mpv_error_string(rc)}" }
        this.lib = lib; handle = h
        renderer = try { MpvRenderer(lib, h) } catch (e: IllegalStateException) { return e.message }
        return null
    }

    /** A window of mpv's own. Picture only: the app's transport is the one UI in either mode; its keys still work on their own. */
    private fun startProcess(sock: File, url: String, token: String, title: String, start: Double?): String? {
        val argv = buildList {
            add(command)
            add("--input-ipc-server=${sock.absolutePath}")
            addAll(listOf("--idle=yes", "--force-window=yes", "--keep-open=yes", "--no-terminal", "--msg-level=all=error"))
            addAll(listOf("--osc=no", "--osd-level=1", "--osd-bar=no", "--input-default-bindings=yes", "--input-vo-keyboard=yes", "--geometry=60%", "--input-conf=${inputConf().absolutePath}"))
            start?.let { add("--start=$it") }
            add("--http-header-fields=Authorization: Bearer $token")
            add("--title=$title")
            add(url)
        }
        process = try { spawn(argv) } catch (e: IOException) { return "mpv could not be started — is it installed and on PATH?" }
        return null
    }

    /** Replace what is playing (the token was given at start; mpv keeps its header option). */
    fun load(url: String, title: String) {
        lastUrl = url; lastTitle = title
        appliedSubs.clear()   // a new file drops the old file's external subtitles
        state = state.copy(loaded = false, position = 0.0, duration = 0.0, eof = false, error = null, title = title, subtitles = emptyList(), audio = emptyList())
        send("set_property", "force-media-title", title)
        send("loadfile", url)
        send("set_property", "pause", false)
    }

    /**
     * Move playback between the app and a window of mpv's own, keeping the position,
     * pause state and volume. A live player cannot change hosts, so this is a restart
     * with a seek — the file is streamed, so it resumes in a moment.
     */
    fun switchTo(embedded: Boolean): String? {
        val url = lastUrl ?: return "nothing is playing"
        val token = lastToken ?: return "nothing is playing"
        val title = lastTitle ?: ""
        val resume = state.copy()
        resumeAt = resume.position
        val err = start(embedded, url, token, title) ?: run {
            send("set_property", "volume", resume.volume)
            send("set_property", "mute", resume.muted)
            send("set_property", "pause", resume.paused)
            null
        }
        return err
    }

    /** Where playback was when mpv went away, for the re-embed. */
    val resumePosition: Double? get() = resumeAt

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

    /**
     * Attach heyarr subtitle-sidecar blob URLs to the CURRENT file as external tracks
     * (mpv `sub-add`). They ride the same `http-header-fields` bearer the video does, so
     * the authenticated blob fetch just works, and land in `track-list` as `external`
     * tracks — exactly what the CC picker labels "sidecar file". The first is selected so
     * captions show; the rest are added selectable-but-off. Idempotent and a no-op until
     * the socket is up, so callers may invoke it on both start and queue-load without
     * double-adding; [load] clears the set when the file changes.
     */
    fun addExternalSubtitles(urls: List<String>) {
        if (channel?.isOpen != true) return
        for (u in urls) {
            if (u.isBlank() || u in appliedSubs) continue
            send("sub-add", u, if (appliedSubs.isEmpty()) "select" else "auto")
            appliedSubs.add(u)
        }
    }
    fun setAudio(id: Int) = send("set_property", "aid", id)
    fun stop() = send("stop")

    fun close() {
        closed = true
        runCatching { send("quit") }
        runCatching { channel?.close() }
        channel = null
        // Order matters: the render context must go before the core (render.h), and the core before the socket file.
        renderer?.let { r -> runCatching { r.close() } }
        renderer = null
        handle?.let { h -> lib?.let { l -> runCatching { l.mpv_terminate_destroy(h) } } }
        handle = null
        process?.let { p -> if (!p.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly() }
        process = null
        socketPath?.delete()
        appliedSubs.clear()
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

    /**
     * Key bindings for the pop-out: mpv's defaults plus a horizontal wheel that follows
     * natural scrolling (a two-finger swipe to the right moves forward), and a wheel
     * over the picture that seeks rather than changes volume.
     */
    private fun inputConf(): File {
        val f = File(System.getProperty("java.io.tmpdir"), "heyarr-mpv-input.conf")
        f.writeText(
            """
            WHEEL_LEFT  seek 5
            WHEEL_RIGHT seek -5
            WHEEL_UP    seek 10
            WHEEL_DOWN  seek -10
            SPACE       cycle pause
            f           cycle fullscreen
            ESC         set fullscreen no
            """.trimIndent() + "\n",
        )
        return f
    }

    companion object {
        /** Sockets left by app processes that are gone (a kill, a crash); ours are named by pid. */
        internal fun sweepStaleSockets(dir: File) {
            dir.listFiles { f -> f.name.startsWith("heyarr-mpv-") && f.name.endsWith(".sock") }?.forEach { f ->
                val pid = f.name.removePrefix("heyarr-mpv-").substringBefore('-').toLongOrNull() ?: return@forEach
                if (pid != ProcessHandle.current().pid() && !ProcessHandle.of(pid).map { it.isAlive }.orElse(false)) f.delete()
            }
        }

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
