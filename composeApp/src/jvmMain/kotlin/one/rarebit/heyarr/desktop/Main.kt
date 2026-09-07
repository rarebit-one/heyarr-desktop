package one.rarebit.heyarr.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import one.rarebit.heyarr.desktop.net.JdkHttpTransport
import one.rarebit.heyarr.desktop.playback.MpvPlayer
import one.rarebit.heyarr.desktop.settings.FileSettingsStore
import one.rarebit.heyarr.desktop.ui.App

/**
 * Desktop entry point (`compose.desktop.application { mainClass = "…MainKt" }`).
 *
 * Wires the concrete platform pieces — the JDK HttpTransport actual, the file-backed
 * SettingsStore and the mpv player — and hands them to the shared [App] composable.
 * This is the only place that names concretes; everything below the UI depends on
 * interfaces, so the shared-module extraction later is a move, not a rewrite.
 */
fun main() = application {
    val state = rememberWindowState(width = 1280.dp, height = 800.dp)
    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        title = "Heyarr",
    ) {
        App(
            settings = FileSettingsStore(),
            transport = JdkHttpTransport(),
            player = MpvPlayer(),
            onFullscreen = { on -> state.placement = if (on) WindowPlacement.Fullscreen else WindowPlacement.Floating },
        )
    }
}
