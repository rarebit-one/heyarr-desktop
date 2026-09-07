package one.rarebit.heyarr.desktop.settings

import one.rarebit.heyarr.desktop.net.JsonScan
import java.io.File

/**
 * The persisted desktop configuration — the desktop analog of heyarr-mobile's
 * `settings.SettingsStore` (which is SharedPreferences-backed on the phone). Here it is
 * a single JSON file at `~/.config/heyarr-desktop/config.json` (XDG-ish; honours
 * `$XDG_CONFIG_HOME`).
 *
 * NOTE on the token: the bearer token IS a secret. This v1 slice persists it in
 * plaintext in the config file (`chmod 600`), which matches the "paste a token" flow
 * and is enough to prove the path. A later revision moves it to the OS secret store
 * (libsecret / GNOME Keyring via `secret-tool`, or KWallet) — see the desktop TODO.
 */
data class DesktopConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val bearerToken: String = "",
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://heyarr.br.thesim.family:7777"
    }
}

/** Read/write seam, so the resolution logic is JVM-testable with an in-memory fake. */
interface SettingsStore {
    fun load(): DesktopConfig
    fun save(config: DesktopConfig)
}

/** Non-persistent store for tests and previews. */
class InMemorySettingsStore(private var config: DesktopConfig = DesktopConfig()) : SettingsStore {
    override fun load(): DesktopConfig = config
    override fun save(config: DesktopConfig) { this.config = config }
}

/**
 * The file-backed store. Reads tolerantly with [JsonScan] (same dependency-free stance
 * as the network readers); writes a small, hand-escaped JSON object. Missing/corrupt
 * file → defaults, never a crash.
 */
class FileSettingsStore(
    private val file: File = defaultConfigFile(),
) : SettingsStore {

    override fun load(): DesktopConfig {
        val text = runCatching { if (file.exists()) file.readText() else null }.getOrNull()
            ?: return DesktopConfig()
        val obj = JsonScan.rootObject(text) ?: return DesktopConfig()
        return DesktopConfig(
            baseUrl = JsonScan.stringField(obj, "base_url")?.takeIf { it.isNotBlank() }
                ?: DesktopConfig.DEFAULT_BASE_URL,
            bearerToken = JsonScan.stringField(obj, "bearer_token").orEmpty(),
        )
    }

    override fun save(config: DesktopConfig) {
        file.parentFile?.mkdirs()
        val json = buildString {
            append("{\n")
            append("  \"base_url\": \"").append(escape(config.baseUrl)).append("\",\n")
            append("  \"bearer_token\": \"").append(escape(config.bearerToken)).append("\"\n")
            append("}\n")
        }
        file.writeText(json)
        // Best-effort tighten perms — the token is a secret. POSIX-only; ignored elsewhere.
        runCatching {
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
        }
    }

    private fun escape(s: String): String = buildString {
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }

    companion object {
        /** `$XDG_CONFIG_HOME/heyarr-desktop/config.json`, else `~/.config/heyarr-desktop/config.json`. */
        fun defaultConfigFile(): File {
            val xdg = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
            val base = if (xdg != null) File(xdg) else File(System.getProperty("user.home"), ".config")
            return File(File(base, "heyarr-desktop"), "config.json")
        }
    }
}
