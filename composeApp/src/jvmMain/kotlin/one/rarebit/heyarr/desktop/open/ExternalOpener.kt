package one.rarebit.heyarr.desktop.open

import java.io.File
import java.io.IOException

/**
 * The "hand this file to the system's default app" seam — the desktop twin of the
 * [one.rarebit.heyarr.desktop.playback.Player] seam. On this box the backend is
 * `xdg-open <path>`, which the desktop environment routes to the registered handler
 * (a browser for `.html`, an e-reader for `.epub`/`.pdf`/`.cbz`, …).
 *
 * The interface exists so the UI depends on a seam a test can fake (asserting the exact
 * argv) and so a second backend (a bundled reader, `open` on macOS, …) is a new
 * [ExternalOpener], not a rewrite.
 */
interface ExternalOpener {

    /** Open [file] in the system's default application for its type. Blocking (spawns + returns). */
    fun open(file: File): OpenResult
}

/** The outcome of an [ExternalOpener.open] — a value the UI renders, never a thrown exception. */
sealed interface OpenResult {
    /** The opener process was launched. */
    object Opened : OpenResult

    /** Nothing was launched; [message] is a UI-safe reason. */
    data class Failed(val message: String) : OpenResult
}

/**
 * The [ExternalOpener] backend for Linux: `xdg-open`. The spawn and the PATH lookup are
 * injected exactly like [one.rarebit.heyarr.desktop.playback.MpvPlayer], so the argv this
 * would spawn is asserted in a JVM test WITHOUT launching anything.
 *
 * The spawned argv is exactly `xdg-open <absolute path>` (one argv slot each; no shell).
 */
class XdgOpen(
    private val command: String = DEFAULT_COMMAND,
    private val available: () -> Boolean = { onPath(command) },
    private val spawn: (List<String>) -> Unit = { argv -> ProcessBuilder(argv).start() },
) : ExternalOpener {

    override fun open(file: File): OpenResult {
        if (!available()) return OpenResult.Failed(NOT_FOUND)
        return try {
            spawn(argv(command, file))
            OpenResult.Opened
        } catch (e: IOException) {
            OpenResult.Failed(NOT_FOUND)
        }
    }

    companion object {
        const val DEFAULT_COMMAND = "xdg-open"

        const val NOT_FOUND =
            "xdg-open was not found. Install xdg-utils and make sure it is on your PATH."

        /** The exact argv [open] spawns — pure, so a test asserts it without launching. */
        fun argv(command: String, file: File): List<String> = listOf(command, file.absolutePath)

        /** True when [command] resolves to an executable file (absolute path, or on `$PATH`). */
        private fun onPath(command: String): Boolean {
            if (command.contains(File.separatorChar)) {
                val f = File(command)
                return f.isFile && f.canExecute()
            }
            val path = System.getenv("PATH") ?: return false
            return path.split(File.pathSeparatorChar).any { dir ->
                dir.isNotBlank() && File(dir, command).let { it.isFile && it.canExecute() }
            }
        }
    }
}
