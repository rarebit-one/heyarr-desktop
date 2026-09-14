package one.rarebit.heyarr.core.auth

import one.rarebit.voidbind.auth.DeviceCredential

/**
 * The credential a first-party client presents to heyarr on every `/api/v1` call.
 *
 * heyarr accepts two credential shapes (client contract, ADR-0048):
 *
 *  - [Device] — the **primary** credential a first-party client carries once it is an
 *    enrolled device: a user-signed enrolment cert plus a fresh possession proof,
 *    presented under heyarr's own `Device` auth scheme:
 *    `Authorization: Device <cert>~<proof>` (the halves joined by the enrolment
 *    separator `~`). It authenticates **offline** — the server verifies the cert
 *    against a pinned key and checks the possession proof; no token round-trip.
 *    Producing the proof needs the device private key, which lives **non-exportable**
 *    in the platform key store (Android StrongBox / a desktop software key / iOS
 *    Secure Enclave) and signs in-enclave — voidbind-client's [DeviceCredential] owns
 *    the wire format (`~` join, `Device ` scheme, possession proof). Formatting the
 *    header from an already-obtained cert+proof is pure; obtaining the proof is
 *    platform-gated.
 *
 *  - A Bearer token, in two flavours that render the same `Authorization: Bearer <t>`:
 *    [Session] — the **bootstrap** credential from a QR web-login (a short-lived
 *    session token minted by the weblogin broker, how a brand-new install reaches the
 *    library before/without enrolling) — and [Bearer] — an opaque long-lived token
 *    (ADR-0011's `heyarr_<id>_<secret>`, e.g. the one a desktop Settings screen pastes).
 *
 * Unified across the desktop and android clients (heyarr-kmp Gate B): desktop uses
 * [Bearer]; the android client uses [Session] and [Device]; all now share this one type.
 */
sealed interface Credential {

    /** The `Authorization` header value to send. */
    fun headerValue(): String

    /** Convenience: the single-entry header map to merge into a request. */
    fun asHeader(): Map<String, String> = mapOf(HEADER to headerValue())

    /** Bootstrap: a short-lived Bearer session token from a QR web-login. */
    data class Session(val token: String) : Credential {
        override fun headerValue() = "Bearer $token"
    }

    /** Opaque long-lived bearer token: `heyarr_<id>_<secret>` (ADR-0011) or a session token. */
    data class Bearer(val token: String) : Credential {
        override fun headerValue() = "Bearer $token"
    }

    /** Primary: an enrolled device's cert + possession proof under the `Device` scheme. */
    data class Device(val cert: String, val proof: String) : Credential {
        override fun headerValue() = DeviceCredential.headerValue(cert, proof)
    }

    companion object {
        const val HEADER = "Authorization"
    }
}
