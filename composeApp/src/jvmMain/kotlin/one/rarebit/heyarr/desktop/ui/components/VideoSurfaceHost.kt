package one.rarebit.heyarr.desktop.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import com.sun.jna.Native
import kotlinx.coroutines.delay
import one.rarebit.heyarr.desktop.heyarr.HeyarrApi
import one.rarebit.heyarr.desktop.state.AppSession
import one.rarebit.heyarr.desktop.ui.screens.PlayerKeys
import java.awt.GraphicsEnvironment

/**
 * The one native surface mpv paints into, owned by the shell and never recreated.
 * It is placed where the current screen asked (`PlaybackSession.surfaceBounds`): the
 * player's picture box, the now-playing bar's thumbnail, the whole window in
 * fullscreen, or a hidden pixel when nothing wants it. Heavyweight AWT sits above
 * Compose, so nothing may overlap it — the screens keep those regions empty.
 *
 * Placement is done in AWT's own units (system density), because that is what
 * SwingPanel hands the peer; Compose's density may be the user's UI-scale override.
 */
@Composable
fun VideoSurfaceHost(session: AppSession, onToggleFullscreen: () -> Unit, onBack: () -> Unit) {
    val playback = session.playback
    if (GraphicsEnvironment.isHeadless()) return
    val item = playback.current ?: return
    if (playback.popout) return
    val bounds = playback.surfaceBounds ?: IntRect(-4, -4, -3, -3)
    val awtScale = remember { runCatching { GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX.toFloat() }.getOrDefault(1f) }
    val composeDensity = LocalDensity.current
    val canvas = remember {
        java.awt.Canvas().apply {
            background = java.awt.Color.BLACK
            isFocusable = true
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    requestFocusInWindow()
                    if (e.clickCount == 2) onToggleFullscreen() else if (playback.onPlayerScreen) playback.player.togglePause()
                }
            })
            addKeyListener(object : java.awt.event.KeyAdapter() {
                override fun keyPressed(e: java.awt.event.KeyEvent) {
                    val k = PlayerKeys.fromAwt(e.keyCode) ?: return
                    if (PlayerKeys.handle(k, playback.player, onToggleFullscreen, onBack, playback.fullscreen)) e.consume()
                }
            })
        }
    }
    // Start mpv into the canvas once it is realised; restart after a pop-out returns or an item needs a fresh process.
    LaunchedEffect(item.assetId, playback.pendingStart) {
        if (!playback.pendingStart && playback.player.isRunning) return@LaunchedEffect
        repeat(60) { if (canvas.isDisplayable && canvas.isShowing) return@repeat; delay(50) }
        if (!canvas.isDisplayable) { playback.startError = "the video surface never appeared"; return@LaunchedEffect }
        val wid = runCatching { Native.getComponentID(canvas) }.getOrElse { playback.startError = "no native window id for the video surface (${it.message})"; return@LaunchedEffect }
        val url = HeyarrApi.blobUrl(playback.baseUrl, item.blobHash)
        val err = session.io {
            if (playback.player.isRunning) playback.player.switchWindow(wid) else playback.player.start(wid, url, playback.token, playback.title(item), playback.accentHex)
        }.getOrNull()
        playback.pendingStart = false
        playback.startError = err
        playback.surfaceReady = err == null
        if (err == null) playback.player.play()
    }
    // Bounds are window pixels. SwingPanel converts dp → peer units with LocalDensity, and the
    // peer lives in AWT units (the system scale, 1x here), so the panel is laid out under that
    // density: a pixel offset stays a pixel, and a pixel size becomes the same number of dp.
    val awt = Density(awtScale)
    val w = with(awt) { (bounds.width.coerceAtLeast(1) / awtScale).toInt().toDp() }
    val h = with(awt) { (bounds.height.coerceAtLeast(1) / awtScale).toInt().toDp() }
    CompositionLocalProvider(LocalDensity provides awt) {
        Box(Modifier.offset { IntOffset(bounds.left, bounds.top) }.size(w, h)) {
            SwingPanel(background = Color.Black, modifier = Modifier.size(w, h), factory = { canvas })
        }
    }
}
