package one.rarebit.heyarr.desktop.login

import one.rarebit.heyarr.desktop.auth.Credential

/**
 * The login seam. The app depends only on [LoginProvider]; how a [Credential] is
 * obtained (pasted token now, Voidbind device/QR later) stays behind it — mirroring how
 * heyarr-mobile keeps a thin `login/` seam that the published `voidbind-client` will
 * one day replace wholesale.
 *
 * Voidbind login (device enrolment + QR approval, `one.rarebit.voidbind:voidbind-client`)
 * is NOT wired yet: resolving that artifact needs a GitHub PAT with `read:packages`,
 * which this environment lacks. So the only live provider is [BearerTokenLogin], and
 * [VoidbindLoginStub] documents the shape the real coordinator will take.
 */
interface LoginProvider {
    /** A human label for the Settings/Login UI. */
    val displayName: String

    /** The credential to present to heyarr, or null when not yet configured. */
    fun credential(): Credential?
}

/**
 * The live provider for v1: the bearer token the Settings screen pastes
 * (`heyarr_<id>_<secret>`, ADR-0011, or a weblogin session token). No round trip — the
 * token is presented directly.
 */
class BearerTokenLogin(private val tokenProvider: () -> String) : LoginProvider {
    override val displayName = "Bearer token"

    override fun credential(): Credential? =
        tokenProvider().trim().takeIf { it.isNotEmpty() }?.let { Credential.Bearer(it) }
}

/**
 * Placeholder for the Voidbind device/QR login. TODO(voidbind): when
 * `voidbind-client:0.7.0` is resolvable (add the GitHub Packages repo + dependency —
 * see settings.gradle.kts and composeApp/build.gradle.kts), replace this with a real
 * coordinator built on the library's `LoginApproval` / `DevicePairing` / `WebLoginClient`:
 *
 *   1. `begin(qr)` → fetch the challenge, render/scan the QR, show the number-match.
 *   2. `approve(...)` → sign the assertion with the device key (a desktop `DeviceKeyStore`
 *      backed by the OS keystore / a software key for now).
 *   3. present the resulting `Device <cert>~<proof>` credential to heyarr.
 *
 * Until then this stub is inert: it never produces a credential and the UI falls back to
 * the pasted bearer token.
 */
class VoidbindLoginStub : LoginProvider {
    override val displayName = "Voidbind device login (stubbed — needs voidbind-client)"
    override fun credential(): Credential? = null
}
