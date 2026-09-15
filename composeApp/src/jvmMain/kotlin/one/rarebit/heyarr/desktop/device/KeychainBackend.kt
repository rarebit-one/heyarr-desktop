package one.rarebit.heyarr.desktop.device

/**
 * The narrow native surface a keychain-backed [SecretStore] needs: one generic-password
 * item per account under a fixed service. Concrete implementations wrap macOS Keychain
 * ([MacKeychainBackend]) and freedesktop Secret Service / libsecret ([LibSecretBackend]);
 * a fake backs the unit tests, which is how the store abstraction and platform selection
 * are covered without a real keychain on the CI host.
 *
 * **Contract: every method is total and MUST NOT throw.** A locked, absent or unreachable
 * keychain is signalled by [isAvailable] `false`, [store] `false`, or [retrieve] `null` —
 * so [SecretStores] can fall back to the sealed file rather than crash the enrol flow.
 */
interface KeychainBackend {
    /** A short human name for tier/UX copy, e.g. "macOS Keychain". */
    val label: String

    /** True when the keychain is present AND reachable (unlocked, service running). A cheap probe. */
    fun isAvailable(): Boolean

    /** Store (create or replace) [secret] under [account]. Returns false on any failure. */
    fun store(account: String, secret: ByteArray): Boolean

    /** The stored bytes for [account], or null if absent / unreadable. */
    fun retrieve(account: String): ByteArray?

    /** Remove [account]; a no-op if absent. */
    fun remove(account: String)
}
