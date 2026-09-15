package one.rarebit.heyarr.desktop.device

/**
 * A named-secret store for the desktop device keyring — the single seam
 * [DesktopDeviceKeyring] persists its signing seed / encryption private key behind, so the
 * keyring is agnostic to WHERE the bytes actually live.
 *
 * Two shapes implement it:
 *  - [DesktopSecretStore] — AES-256-GCM sealed file wrapped by a locally-held key, reported
 *    as [KeyTier.SOFTWARE]. The always-available fallback (and what the tests exercise).
 *  - [KeychainSecretStore] — hands each secret to the OS keychain (macOS Keychain /
 *    freedesktop Secret Service via libsecret), reported as [KeyTier.KEYCHAIN].
 *
 * [SecretStores.forDevice] picks the strongest available one, deferring the availability
 * probe to first use so constructing a keyring stays I/O-free.
 */
interface SecretStore {
    /** The honest protection tier of the secrets this store holds — surfaced as the device tier. */
    val tier: KeyTier

    /** True when a secret named [name] is persisted. */
    fun exists(name: String): Boolean

    /** Persist (create or replace) [secret] under [name]. */
    fun seal(name: String, secret: ByteArray)

    /** The persisted bytes for [name], or null if absent / unreadable. */
    fun unseal(name: String): ByteArray?

    /** Remove the secret named [name]; a no-op if absent. */
    fun delete(name: String)
}
