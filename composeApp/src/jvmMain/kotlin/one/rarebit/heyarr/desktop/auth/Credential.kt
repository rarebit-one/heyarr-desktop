package one.rarebit.heyarr.desktop.auth

/**
 * The credential presented to heyarr on every `/api/v1` call.
 *
 * heyarr accepts two credential shapes (mobile-client contract, ADR-0048): a `Device`
 * cert+proof under heyarr's own `Device` scheme (the primary, hardware-backed
 * credential a Voidbind-enrolled client carries) and a `Bearer` token (ADR-0011's
 * opaque `heyarr_<id>_<secret>`, or a short-lived weblogin session token).
 *
 * This desktop v1 slice implements only [Bearer] — the token the Settings screen
 * pastes. [Device] is intentionally absent until the Voidbind login seam
 * (login/VoidbindLogin.kt) is wired to the published `voidbind-client`, which owns the
 * `Device <cert>~<proof>` wire format and the in-enclave possession proof.
 */
sealed interface Credential {

    /** The `Authorization` header value to send. */
    fun headerValue(): String

    /** Convenience: the single-entry header map to merge into a request. */
    fun asHeader(): Map<String, String> = mapOf(HEADER to headerValue())

    /** Opaque bearer token: `heyarr_<id>_<secret>` (ADR-0011) or a weblogin session token. */
    data class Bearer(val token: String) : Credential {
        override fun headerValue() = "Bearer $token"
    }

    companion object {
        const val HEADER = "Authorization"
    }
}
