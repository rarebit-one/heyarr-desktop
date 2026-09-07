package one.rarebit.heyarr.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sun.jna.Native
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.rarebit.heyarr.desktop.heyarr.HeyarrApi
import one.rarebit.heyarr.desktop.heyarr.McpResult
import one.rarebit.heyarr.desktop.library.Episode
import one.rarebit.heyarr.desktop.library.PrimaryAsset
import one.rarebit.heyarr.desktop.library.Series
import one.rarebit.heyarr.desktop.mcp.Renderer
import one.rarebit.heyarr.desktop.playback.EmbeddedPlayer
import one.rarebit.heyarr.desktop.playback.PlayResult
import one.rarebit.heyarr.desktop.playback.PlayerState
import one.rarebit.heyarr.desktop.state.AppSession
import one.rarebit.heyarr.desktop.state.Toast
import one.rarebit.heyarr.desktop.state.rememberArtwork
import one.rarebit.heyarr.desktop.theme.LocalMediaTheme
import one.rarebit.heyarr.desktop.theme.MediaScope
import one.rarebit.heyarr.desktop.theme.MediaThemes
import one.rarebit.heyarr.desktop.theme.MediaType
import one.rarebit.heyarr.desktop.theme.Tokens
import one.rarebit.heyarr.desktop.ui.Route
import one.rarebit.heyarr.desktop.ui.components.Artwork
import one.rarebit.heyarr.desktop.ui.components.FilterChip
import one.rarebit.heyarr.desktop.ui.components.focusRing
import one.rarebit.heyarr.desktop.ui.components.GhostButton
import one.rarebit.heyarr.desktop.ui.components.IconButtonRound
import one.rarebit.heyarr.desktop.ui.components.Notice
import one.rarebit.heyarr.desktop.ui.components.Panel
import one.rarebit.heyarr.desktop.ui.components.SectionHeader
import one.rarebit.heyarr.desktop.ui.components.Skeleton
import java.awt.GraphicsEnvironment

/** The player's own state that outlives a recomposition: which file, the up-next list, the cast picker. */
class PlayerScreenState(val route: Route.Player) {
    val player = EmbeddedPlayer()
    var current by mutableStateOf(route)
    var started by mutableStateOf(false)
    var startError by mutableStateOf<String?>(null)
    var episodes by mutableStateOf<List<Episode>>(emptyList())
    var renderers by mutableStateOf<List<Renderer>?>(null)
    var castOpen by mutableStateOf(false)
    var controlsVisible by mutableStateOf(true)
    var popout by mutableStateOf(false)
    var tracksOpen by mutableStateOf(false)
}

/** The keys the player answers to — one table for the Compose window and the AWT canvas alike. */
object PlayerKeys {
    fun handle(key: Key, p: EmbeddedPlayer, onFullscreen: () -> Unit, onBack: () -> Unit, fullscreen: Boolean): Boolean = when (key) {
        Key.Spacebar, Key.K -> { p.togglePause(); true }
        Key.DirectionLeft, Key.J -> { p.seekBy(-10.0); true }
        Key.DirectionRight, Key.L -> { p.seekBy(10.0); true }
        Key.DirectionUp -> { p.setVolume(p.state.volume + 5); true }
        Key.DirectionDown -> { p.setVolume(p.state.volume - 5); true }
        Key.F -> { onFullscreen(); true }
        Key.M -> { p.toggleMute(); true }
        Key.S, Key.C -> { p.cycleSubtitle(); true }
        Key.Escape -> { if (fullscreen) onFullscreen() else onBack(); true }
        else -> false
    }

    fun fromAwt(code: Int): Key? = when (code) {
        java.awt.event.KeyEvent.VK_SPACE -> Key.Spacebar
        java.awt.event.KeyEvent.VK_LEFT -> Key.DirectionLeft
        java.awt.event.KeyEvent.VK_RIGHT -> Key.DirectionRight
        java.awt.event.KeyEvent.VK_UP -> Key.DirectionUp
        java.awt.event.KeyEvent.VK_DOWN -> Key.DirectionDown
        java.awt.event.KeyEvent.VK_F -> Key.F
        java.awt.event.KeyEvent.VK_M -> Key.M
        java.awt.event.KeyEvent.VK_S -> Key.S
        java.awt.event.KeyEvent.VK_C -> Key.C
        java.awt.event.KeyEvent.VK_K -> Key.K
        java.awt.event.KeyEvent.VK_J -> Key.J
        java.awt.event.KeyEvent.VK_L -> Key.L
        java.awt.event.KeyEvent.VK_ESCAPE -> Key.Escape
        else -> null
    }
}

/**
 * The embedded player. mpv renders into an AWT canvas we own; the transport below it
 * is ours: play/pause, ±10 s, a seek bar, time, volume, captions, cast to a renderer,
 * open in a separate mpv window, and a fullscreen toggle that is a first-class
 * button (F, or double-click the picture). "Up next" lists the work's other
 * episodes; the next one loads into the same player.
 */
@Composable
fun PlayerScreen(session: AppSession, state: PlayerScreenState, fullscreen: Boolean, onFullscreen: (Boolean) -> Unit, onBack: () -> Unit, onOpen: (Route) -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val route = state.current
    val type = route.typeHint
    val p = state.player
    val ps = p.state
    val headless = remember { GraphicsEnvironment.isHeadless() }

    DisposableEffect(Unit) { onDispose { p.close(); if (fullscreen) onFullscreen(false) } }
    LaunchedEffect(route.workId) {
        val a = session.api ?: return@LaunchedEffect
        session.io { a.assets(route.workId) }.onSuccess { list -> state.episodes = Series.seasons(list).flatMap { it.episodes } }
    }
    // The pop-out window was closed (or mpv died): come back inside and resume where it was.
    LaunchedEffect(p.exited) {
        if (p.exited && state.popout) { state.popout = false; state.started = false }
        else if (p.exited && !state.popout && state.started) { state.started = false }
    }
    // Auto-hide the controls in fullscreen while playing.
    LaunchedEffect(fullscreen, ps.paused, state.controlsVisible) {
        if (fullscreen && !ps.paused && state.controlsVisible) { delay(3500); state.controlsVisible = false }
        if (!fullscreen) state.controlsVisible = true
    }

    fun play(next: Route.Player) {
        state.current = next
        p.load(HeyarrApi.blobUrl(session.config.baseUrl, next.blobHash), next.title + (next.subtitle?.let { " — $it" } ?: ""))
    }
    fun toggleFullscreen() { onFullscreen(!fullscreen); state.controlsVisible = true }

    MediaScope(type) {
        val theme = LocalMediaTheme.current
        Column(modifier.fillMaxSize().background(if (fullscreen) Color.Black else Tokens.bgBase)) {
            if (!fullscreen) Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GhostButton(route.from, onBack, icon = Icons.Rounded.ArrowBack)
                Column(Modifier.weight(1f)) {
                    Text(route.title, style = MaterialTheme.typography.titleMedium, color = Tokens.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    route.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted, maxLines = 1) }
                }
                GhostButton(if (state.popout) "Pop back in" else "Pop out", {
                    scope.launch {
                        if (state.popout) { state.popout = false; state.started = false } // the surface re-creates and re-embeds
                        else {
                            state.popout = true
                            val accent = accentHex(MediaThemes.of(route.typeHint).accent)
                            val err = session.io {
                                if (p.isRunning) p.switchWindow(null)
                                else p.start(null, HeyarrApi.blobUrl(session.config.baseUrl, route.blobHash), session.config.bearerToken.trim(), route.title + (route.subtitle?.let { " — $it" } ?: ""), accent)
                            }.getOrNull()
                            if (err != null) { session.toast(Toast.Kind.ERROR, "Couldn't pop out", err); state.popout = false; state.started = false }
                        }
                    }
                }, icon = if (state.popout) Icons.Rounded.Fullscreen else Icons.Rounded.OpenInNew)
                IconButtonRound(Icons.Rounded.Cast, "Play on a renderer", { state.castOpen = !state.castOpen; if (state.renderers == null) scope.launch { session.api?.let { a -> session.io { a.renderers() }.onSuccess { state.renderers = it } } } })
                IconButtonRound(Icons.Rounded.Fullscreen, "Fullscreen (F)", ::toggleFullscreen, filled = true)
            }
            if (state.castOpen && !fullscreen) Box(Modifier.padding(horizontal = 24.dp)) { CastRow(session, state, route) }

            // ── the picture ─────────────────────────────────────────────────────
            val videoModifier = if (fullscreen) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().padding(horizontal = 24.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(Tokens.radiusCard))
            Box(videoModifier.background(Color.Black)) {
                when {
                    headless -> {
                        Artwork(null, type, Modifier.fillMaxSize(), glyphSize = 64.dp)
                        Text("mpv renders here", style = MaterialTheme.typography.labelMedium, color = Tokens.textMuted, modifier = Modifier.align(Alignment.Center).padding(top = 90.dp))
                    }
                    state.popout -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = Tokens.textMuted, modifier = Modifier.size(40.dp))
                        Text("Playing in a separate mpv window", style = MaterialTheme.typography.titleMedium, color = Tokens.textPrimary)
                        Text("The controls below still drive it; that window has mpv's own controls too.", style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted)
                    }
                    else -> VideoSurface(session, state, onToggleFullscreen = ::toggleFullscreen, onBack = onBack, fullscreen = fullscreen)
                }
                if (state.startError != null) Box(Modifier.fillMaxSize().background(Tokens.bgBase.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                    Notice("mpv could not start: ${state.startError}", tone = Tokens.danger, modifier = Modifier.padding(32.dp))
                }
            }

            // ── the transport ───────────────────────────────────────────────────
            if (state.controlsVisible || ps.paused || !fullscreen) Column(Modifier.fillMaxWidth().background(if (fullscreen) Color.Black else Tokens.bgBase).padding(horizontal = 24.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SeekBar(ps, onSeek = { p.seekFraction(it.toDouble()) })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButtonRound(Icons.Rounded.Replay10, "Back 10 seconds (←)", { p.seekBy(-10.0) }, size = 36.dp)
                    IconButtonRound(if (ps.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (ps.paused) "Play (space)" else "Pause (space)", { p.togglePause() }, size = 48.dp, filled = true)
                    IconButtonRound(Icons.Rounded.Forward10, "Forward 10 seconds (→)", { p.seekBy(10.0) }, size = 36.dp)
                    val next = nextEpisode(state)
                    if (next != null) IconButtonRound(Icons.Rounded.SkipNext, "Next: ${next.label}", { play(route.copy(assetId = next.asset.id, blobHash = next.asset.blobHash!!, subtitle = next.label)) }, size = 36.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("${clock(ps.position)} / ${clock(ps.duration)}", style = MaterialTheme.typography.labelLarge, color = Tokens.textPrimary)
                    if (ps.buffering) Text("buffering…", style = MaterialTheme.typography.labelSmall, color = Tokens.textMuted)
                    if (ps.eof) Text("finished", style = MaterialTheme.typography.labelSmall, color = Tokens.textMuted)
                    Spacer(Modifier.weight(1f))
                    TracksMenu(state, ps)
                    Spacer(Modifier.width(8.dp))
                    IconButtonRound(if (ps.muted || ps.volume <= 0) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, if (ps.muted) "Unmute (M)" else "Mute (M)", { p.toggleMute() }, size = 32.dp)
                    Slider(
                        value = (ps.volume / 100.0).toFloat().coerceIn(0f, 1.3f), onValueChange = { p.setVolume(it * 100.0) }, valueRange = 0f..1.3f,
                        modifier = Modifier.width(110.dp).semantics { contentDescription = "Volume ${ps.volume.toInt()}%" },
                        colors = SliderDefaults.colors(thumbColor = theme.accent, activeTrackColor = theme.accent, inactiveTrackColor = Tokens.surface3),
                    )
                    IconButtonRound(if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, if (fullscreen) "Exit fullscreen (Esc)" else "Fullscreen (F)", ::toggleFullscreen, size = 40.dp, filled = !fullscreen)
                }
                if (fullscreen) Text(route.title + (route.subtitle?.let { "  ·  $it" } ?: ""), style = MaterialTheme.typography.labelMedium, color = Tokens.textMuted)
            }

            // ── up next ─────────────────────────────────────────────────────────
            if (!fullscreen && state.episodes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                UpNext(session, state, route, onPlay = ::play)
            }
        }
    }
}

/** The AWT canvas mpv paints into. Resolved to an X11 window id with JNA once it is on screen. */
@Composable
private fun VideoSurface(session: AppSession, state: PlayerScreenState, onToggleFullscreen: () -> Unit, onBack: () -> Unit, fullscreen: Boolean) {
    val route = state.current
    val canvas = remember {
        java.awt.Canvas().apply {
            background = java.awt.Color.BLACK
            isFocusable = true
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    requestFocusInWindow()
                    if (e.clickCount == 2) onToggleFullscreen() else state.player.togglePause()
                }
                override fun mouseMoved(e: java.awt.event.MouseEvent) { state.controlsVisible = true }
            })
            addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
                override fun mouseMoved(e: java.awt.event.MouseEvent) { state.controlsVisible = true }
            })
            addKeyListener(object : java.awt.event.KeyAdapter() {
                override fun keyPressed(e: java.awt.event.KeyEvent) {
                    val k = PlayerKeys.fromAwt(e.keyCode) ?: return
                    if (PlayerKeys.handle(k, state.player, onToggleFullscreen, onBack, fullscreen)) e.consume()
                }
            })
        }
    }
    LaunchedEffect(state.started) {
        if (state.started) return@LaunchedEffect
        // The window id exists only once the canvas is realised on screen.
        repeat(60) { if (canvas.isDisplayable && canvas.isShowing) return@repeat; delay(50) }
        if (!canvas.isDisplayable) { state.startError = "the video surface never appeared"; return@LaunchedEffect }
        val wid = runCatching { Native.getComponentID(canvas) }.getOrElse { state.startError = "no native window id for the video surface (${it.message})"; return@LaunchedEffect }
        val resuming = state.player.isRunning
        val err = session.io {
            if (resuming) state.player.switchWindow(wid)
            else state.player.start(wid, HeyarrApi.blobUrl(session.config.baseUrl, route.blobHash), session.config.bearerToken.trim(), route.title + (route.subtitle?.let { " — $it" } ?: ""), accentHex(MediaThemes.of(route.typeHint).accent))
        }.getOrNull()
        state.started = true
        state.startError = err
        if (err == null && !resuming) state.player.play()
    }
    // AWT places heavyweight components in its own units (1× here, under XWayland), while the app
    // lays out at the chosen UI scale. SwingPanel converts with LocalDensity, so hand it AWT's.
    val awtDensity = remember { androidx.compose.ui.unit.Density(runCatching { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX.toFloat() }.getOrDefault(1f)) }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides awtDensity) {
        SwingPanel(background = Color.Black, modifier = Modifier.fillMaxSize(), factory = { canvas })
    }
}

/** Captions (and audio, when there is a choice) as a menu: Off, then every track by language name, title and origin. */
@Composable
private fun TracksMenu(state: PlayerScreenState, ps: PlayerState) {
    val theme = LocalMediaTheme.current
    val current = ps.subtitles.firstOrNull { it.id == ps.subtitleId }
    val label = when {
        ps.subtitles.isEmpty() -> "No captions"
        current == null -> "Captions off"
        else -> languageName(current.lang) ?: current.title ?: "Captions"
    }
    Box {
        val interaction = remember { MutableInteractionSource() }
        Row(
            Modifier.focusRing(interaction, RoundedCornerShape(Tokens.radiusButton)).clip(RoundedCornerShape(Tokens.radiusButton))
                .background(if (current != null) theme.tint(0.18f) else Tokens.surface2, RoundedCornerShape(Tokens.radiusButton))
                .border(Tokens.hairline, if (current != null) theme.accent else Tokens.border, RoundedCornerShape(Tokens.radiusButton))
                .clickable(interactionSource = interaction, indication = null, enabled = ps.subtitles.isNotEmpty() || ps.audio.size > 1) { state.tracksOpen = true }
                .semantics { contentDescription = "Captions and audio: $label" }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.ClosedCaption, contentDescription = null, tint = if (current != null) theme.accentGradientEnd else Tokens.textMuted, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (ps.subtitles.isEmpty()) Tokens.textDisabled else Tokens.textPrimary)
            if (ps.subtitles.size > 1) Text("+${ps.subtitles.size - 1}", style = MaterialTheme.typography.labelSmall, color = Tokens.textMuted)
        }
        androidx.compose.material3.DropdownMenu(expanded = state.tracksOpen, onDismissRequest = { state.tracksOpen = false }, modifier = Modifier.background(Tokens.surface3)) {
            MenuHeader("Captions")
            MenuRow("Off", selected = ps.subtitleId == null, detail = null) { state.player.setSubtitle(null); state.tracksOpen = false }
            for (t in ps.subtitles) MenuRow(
                languageName(t.lang) ?: t.title ?: "Track ${t.id}", selected = ps.subtitleId == t.id,
                detail = listOfNotNull(t.title?.takeIf { languageName(t.lang) != null }, t.lang?.uppercase(), if (t.external) "sidecar file" else "in the file").joinToString("  ·  "),
            ) { state.player.setSubtitle(t.id); state.tracksOpen = false }
            if (ps.audio.size > 1) {
                MenuHeader("Audio")
                for (t in ps.audio) MenuRow(languageName(t.lang) ?: t.title ?: "Track ${t.id}", selected = ps.audioId == t.id, detail = listOfNotNull(t.title, t.lang?.uppercase()).joinToString("  ·  ")) { state.player.setAudio(t.id); state.tracksOpen = false }
            }
        }
    }
}

@Composable
private fun MenuHeader(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Tokens.textDisabled, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
}

@Composable
private fun MenuRow(title: String, selected: Boolean, detail: String?, onClick: () -> Unit) {
    val theme = LocalMediaTheme.current
    androidx.compose.material3.DropdownMenuItem(
        text = {
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = if (selected) theme.accentGradientEnd else Tokens.textPrimary)
                if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.labelSmall, color = Tokens.textMuted)
            }
        },
        onClick = onClick,
        leadingIcon = { if (selected) Icon(Icons.Rounded.PlayArrow, contentDescription = "selected", tint = theme.accentGradientEnd, modifier = Modifier.size(14.dp)) else Spacer(Modifier.size(14.dp)) },
    )
}

/** `eng` / `en` / `spa` → "English" / "Spanish", via the JDK's locale tables; null when unknown. */
internal fun languageName(code: String?): String? {
    val c = code?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    if (c == "und") return null
    val direct = java.util.Locale.forLanguageTag(c).getDisplayLanguage(java.util.Locale.ENGLISH)
    if (direct.isNotBlank() && !direct.equals(c, ignoreCase = true)) return direct
    val byIso3 = java.util.Locale.getAvailableLocales().firstOrNull { runCatching { it.isO3Language }.getOrNull()?.equals(c, ignoreCase = true) == true }
    return byIso3?.getDisplayLanguage(java.util.Locale.ENGLISH)?.takeIf { it.isNotBlank() }
}

@Composable
private fun SeekBar(ps: PlayerState, onSeek: (Float) -> Unit) {
    val theme = LocalMediaTheme.current
    var dragging by remember { mutableStateOf<Float?>(null) }
    Slider(
        value = dragging ?: ps.fraction,
        onValueChange = { dragging = it },
        onValueChangeFinished = { dragging?.let(onSeek); dragging = null },
        modifier = Modifier.fillMaxWidth().height(24.dp).semantics { contentDescription = "Position ${clock(ps.position)} of ${clock(ps.duration)}" },
        colors = SliderDefaults.colors(thumbColor = theme.accentGradientEnd, activeTrackColor = theme.accent, inactiveTrackColor = Tokens.surface3),
        enabled = ps.duration > 0,
    )
}

@Composable
private fun CastRow(session: AppSession, state: PlayerScreenState, route: Route.Player) {
    val scope = rememberCoroutineScope()
    Panel("Play on a renderer", trailing = { GhostButton("Close", { state.castOpen = false }) }) {
        val r = state.renderers
        when {
            r == null -> Skeleton(Modifier.fillMaxWidth().height(36.dp))
            r.isEmpty() -> Text("No renderers found — a device that is off is not listed.", style = MaterialTheme.typography.bodyMedium, color = Tokens.textMuted)
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (x in r) FilterChip(x.name, false, {
                    val a = session.api ?: return@FilterChip
                    state.castOpen = false
                    scope.launch {
                        state.player.pause()
                        session.io { a.playHere(route.assetId, x.name, x.udn) }.onSuccess { res -> when (res) { is McpResult.Ok -> session.toast(Toast.Kind.SUCCESS, "Playing on ${x.name}", route.title); is McpResult.Refused -> session.refused(res) } }
                    }
                }, icon = Icons.Rounded.Cast)
            }
        }
    }
}

private fun nextEpisode(state: PlayerScreenState): Episode? {
    val eps = state.episodes.filter { it.isPlayable }
    val i = eps.indexOfFirst { it.asset.id == state.current.assetId }
    return if (i >= 0) eps.getOrNull(i + 1) else null
}

@Composable
private fun UpNext(session: AppSession, state: PlayerScreenState, route: Route.Player, onPlay: (Route.Player) -> Unit) {
    val theme = LocalMediaTheme.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Up next", subtitle = "${state.episodes.count { it.isPlayable }} episodes held")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            items(state.episodes.filter { it.isPlayable }, key = { it.asset.id }) { ep ->
                val playing = ep.asset.id == route.assetId
                val thumb by session.artwork.rememberArtwork(ep.thumbnailPath)
                val interaction = remember { MutableInteractionSource() }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.radiusInput)).background(if (playing) theme.tint(0.14f) else Tokens.surface1).border(Tokens.hairline, if (playing) theme.accent else Tokens.border, RoundedCornerShape(Tokens.radiusInput))
                        .clickable(interactionSource = interaction, indication = null) { if (!playing) onPlay(route.copy(assetId = ep.asset.id, blobHash = ep.asset.blobHash!!, subtitle = ep.label)) }
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp))) { Artwork(thumb, MediaType.SERIES, Modifier.fillMaxSize(), glyphSize = 16.dp) }
                    ep.code?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = theme.accentGradientEnd) }
                    Text(ep.title ?: ep.label, style = MaterialTheme.typography.titleSmall, color = if (playing) Tokens.textPrimary else Tokens.textMuted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ep.asset.sizeBytes?.let { Text(PrimaryAsset.formatBytes(it), style = MaterialTheme.typography.labelSmall, color = Tokens.textDisabled) }
                    if (playing) Text("playing", style = MaterialTheme.typography.labelSmall, color = theme.accentGradientEnd)
                }
            }
        }
    }
}

/** `#RRGGBB` for mpv's OSC options. */
internal fun accentHex(c: Color): String = "#%02X%02X%02X".format((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

internal fun clock(s: Double): String {
    val t = s.toLong().coerceAtLeast(0)
    val h = t / 3600; val m = (t % 3600) / 60; val sec = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
