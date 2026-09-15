package one.rarebit.heyarr.core.auth

/**
 * How a first-party client is acting against heyarr right now.
 *
 *  - [GUEST]  — an anonymous trusted-network lease ([Credential.Guest]): browse, play and
 *               fetch subtitles, but no writes and no encrypted personal state.
 *  - [ENROLLED] — the client presents a real credential (bearer/session/device). It may
 *               attempt everything; the server still has the final say (a read-only token
 *               is refused by rule, which the UI surfaces verbatim).
 *
 * Pure, so the UI-gating decision is unit-testable without a UI or a network — the homelab
 * analog holds: the client never *guesses* what a guest may do, it derives it.
 */
enum class ClientMode { GUEST, ENROLLED }

/** The [ClientMode] implied by the credential the client presents. */
fun Credential.mode(): ClientMode = when (this) {
    is Credential.Guest -> ClientMode.GUEST
    else -> ClientMode.ENROLLED
}
