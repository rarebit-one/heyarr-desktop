package one.rarebit.heyarr.desktop.ui

import one.rarebit.heyarr.desktop.auth.Credential
import one.rarebit.heyarr.desktop.net.HttpTransport
import one.rarebit.heyarr.desktop.open.OpenExternally
import one.rarebit.heyarr.desktop.playback.Player

/**
 * The concrete pieces a section (Music / Books / Feeds) needs to do its network + playback
 * + open work, bundled so the section composables take one argument. Rebuilt whenever the
 * saved config changes (a new base URL or token).
 */
class SectionEnv(
    val baseUrl: String,
    val token: String,
    val transport: HttpTransport,
    val player: Player,
    val openExternally: OpenExternally,
) {
    val credential: Credential get() = Credential.Bearer(token)
    val hasToken: Boolean get() = token.isNotBlank()
}
