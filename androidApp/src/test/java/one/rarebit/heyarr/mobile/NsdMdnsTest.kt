package one.rarebit.heyarr.mobile

import one.rarebit.heyarr.mobile.net.NsdMdns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure half of the Android [one.rarebit.heyarr.mobile.net.NsdMdnsResolver]: turning
 * a resolved advertiser's host/port/TXT into a `:core` `MdnsHit`. The NSD callbacks
 * themselves need a device (they are exercised by the on-device smoke run), but the
 * `tls` parse and the host/port guards are pure and tested here.
 */
class NsdMdnsTest {

    @Test fun tlsFlagPicksTheScheme() {
        val https = NsdMdns.hitFrom("heyarr.local", 7777, tls = "1", path = "/api/v1")!!
        assertTrue(https.tls)
        assertEquals("heyarr.local", https.host)
        assertEquals(7777, https.port)
        assertEquals("/api/v1", https.path)

        assertFalse(NsdMdns.hitFrom("h", 7777, tls = "0", path = null)!!.tls)
        assertFalse(NsdMdns.hitFrom("h", 7777, tls = null, path = null)!!.tls)
        assertTrue(NsdMdns.hitFrom("h", 7777, tls = " 1 ", path = null)!!.tls) // trimmed
    }

    @Test fun unusableSrvDataIsNoHit() {
        assertNull(NsdMdns.hitFrom(null, 7777, "1", null))
        assertNull(NsdMdns.hitFrom("", 7777, "1", null))
        assertNull(NsdMdns.hitFrom("h", 0, "1", null))
        assertNull(NsdMdns.hitFrom("h", -1, "1", null))
    }

    @Test fun txtReadsUtf8OrNull() {
        val attrs = mapOf<String, ByteArray?>(
            "tls" to "1".toByteArray(),
            "path" to "/api/v1".toByteArray(),
            "empty" to ByteArray(0),
            "absent" to null,
        )
        assertEquals("1", NsdMdns.txt(attrs, "tls"))
        assertEquals("/api/v1", NsdMdns.txt(attrs, "path"))
        assertNull(NsdMdns.txt(attrs, "empty"))
        assertNull(NsdMdns.txt(attrs, "absent"))
        assertNull(NsdMdns.txt(attrs, "missing"))
        assertNull(NsdMdns.txt(null, "tls"))
    }
}
