package one.rarebit.heyarr.core.discovery

/**
 * Where the desktop client finds a heyarr node to connect to, before any manual typing.
 *
 * The node advertises `_heyarr._tcp` over mDNS/DNS-SD (heyarr-core Phase 2): SRV →
 * `<host>.local:<port>`, TXT = `txtvers=1`, `path=/api/v1`, `tls=0|1`. When that is not
 * reachable (a foreign network, a wired-only VLAN, mDNS filtered), the node is also
 * reachable by a stable split-horizon DNS name. Only if neither answers does the user
 * type an address by hand.
 *
 * The fallback CHAIN and the base-URL derivation live here in pure `:core` so they are
 * unit-tested against a fake resolver with no real network. The platform mDNS browser is
 * a [MdnsResolver] the desktop module provides (jmdns in `:composeApp` jvmMain).
 */

/** One `_heyarr._tcp` advertiser as resolved off the LAN: SRV host+port plus the TXT `tls` flag. */
data class MdnsHit(
    val host: String,
    val port: Int,
    val tls: Boolean,
    /** The advertised API path (TXT `path`), informational — the client already knows `/api/v1`. */
    val path: String? = null,
)

/** Which rung of the fallback chain produced the chosen server. */
enum class DiscoverySource { MDNS, DNS, MANUAL }

/** The base URL the client should default its server field to, and where it came from. */
data class DiscoveredServer(val baseUrl: String, val source: DiscoverySource)

/**
 * Platform seam: browse for the first `_heyarr._tcp` advertiser and return it, or null
 * when none answers within the platform's own timeout. A `fun interface` so a test passes
 * a lambda and the desktop passes the jmdns-backed resolver.
 */
fun interface MdnsResolver {
    fun resolve(): MdnsHit?
}

/** A resolver that never finds anything — the default for previews/tests and headless CI. */
val NoMdnsResolver = MdnsResolver { null }

object Discovery {
    /** The DNS-SD service type the node advertises. */
    const val SERVICE_TYPE = "_heyarr._tcp"

    /** The stable split-horizon DNS name the node also answers on. */
    const val DNS_HOST = "heyarr.thesim.family"

    /** The node's default port (shared with the desktop's hard-coded default URL). */
    const val DEFAULT_PORT = 7777

    /** Derive the base URL from an mDNS hit — scheme from the TXT `tls` flag, no `/api/v1` (the API layer adds it). */
    fun mdnsBaseUrl(hit: MdnsHit): String {
        val scheme = if (hit.tls) "https" else "http"
        return "$scheme://${hit.host}:${hit.port}"
    }

    /** The base URL for the split-horizon DNS name (always TLS; the OS resolves it at connect time). */
    fun dnsBaseUrl(host: String = DNS_HOST, port: Int = DEFAULT_PORT): String = "https://$host:$port"
}

/**
 * The fallback chain (ADR-0094 client section): try mDNS, then the split-horizon DNS
 * name, then the caller's own manual/saved entry. mDNS wins because it names the node
 * actually on THIS network; the DNS name is preferred over asking the user to type;
 * a hand-configured URL is the terminal fallback so it is never lost.
 */
class NodeDiscovery(
    private val mdns: MdnsResolver = NoMdnsResolver,
    private val dnsHost: String = Discovery.DNS_HOST,
    private val dnsPort: Int = Discovery.DEFAULT_PORT,
) {
    /**
     * Resolve the server to default the field to. [manual] is the currently saved/typed URL,
     * offered only when neither mDNS nor a DNS name is available.
     */
    fun discover(manual: String? = null): DiscoveredServer {
        mdns.resolve()?.let { return DiscoveredServer(Discovery.mdnsBaseUrl(it), DiscoverySource.MDNS) }
        if (dnsHost.isNotBlank()) return DiscoveredServer(Discovery.dnsBaseUrl(dnsHost, dnsPort), DiscoverySource.DNS)
        manual?.takeIf { it.isNotBlank() }?.let { return DiscoveredServer(it, DiscoverySource.MANUAL) }
        return DiscoveredServer(Discovery.dnsBaseUrl(), DiscoverySource.DNS)
    }
}
