package one.rarebit.heyarr.desktop

import one.rarebit.heyarr.desktop.auth.Credential
import one.rarebit.heyarr.desktop.heyarr.HeyarrApi
import one.rarebit.heyarr.desktop.net.HttpResponse
import one.rarebit.heyarr.desktop.net.HttpTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * playbackUrl asks POST /api/v1/playback/plan and plays what it is told: the
 * server transcodes 4K/HEVC down when the client declares it cannot decode it,
 * and hands the blob over directly otherwise. The client must send a
 * conservative profile, turn the plan's relative URL into an absolute one, and
 * never let a plan failure stop playback.
 */
class PlaybackPlanTest {
    private val base = "https://h.example"

    private fun api(t: HttpTransport) = HeyarrApi(t, base, Credential.Bearer("heyarr_1_secret"))

    private fun transport(status: Int, body: String, seen: (String?) -> Unit = {}) =
        object : HttpTransport {
            override fun get(url: String, headers: Map<String, String>) = HttpResponse(405, "")
            override fun post(url: String, body2: String?, contentType: String?, headers: Map<String, String>): HttpResponse {
                seen(body2)
                return HttpResponse(status, body)
            }
        }

    @Test
    fun aStreamPlanIsPlayedAsAnAbsoluteStreamUrl() {
        var sentBody: String? = null
        val t = transport(200, """{"mode":"stream","url":"/api/v1/playback/stream/tok123","mime":"video/mp4"}""") { sentBody = it }

        val url = api(t).playbackUrl("asset-1", "blake3:abc")

        assertEquals("$base/api/v1/playback/stream/tok123", url)
        // A conservative, transcode-forcing profile: H.264 up to 1080p, this asset.
        assertTrue(sentBody!!.contains("\"asset_id\":\"asset-1\""), "sends the asset id")
        assertTrue(sentBody!!.contains("\"max_height\":1080"), "declares max_height 1080")
        assertTrue(sentBody!!.contains("h264"), "declares only h264 video so hevc transcodes")
    }

    @Test
    fun aDirectPlanIsPlayedAsThePlansUrl() {
        val t = transport(200, """{"mode":"direct","url":"/api/v1/blobs/blake3:abc/content"}""")
        assertEquals("$base/api/v1/blobs/blake3:abc/content", api(t).playbackUrl("asset-1", "blake3:abc"))
    }

    @Test
    fun anAbsolutePlanUrlIsUsedVerbatim() {
        val t = transport(200, """{"mode":"stream","url":"https://peer.example/api/v1/playback/stream/tok"}""")
        assertEquals("https://peer.example/api/v1/playback/stream/tok", api(t).playbackUrl("asset-1", "blake3:abc"))
    }

    @Test
    fun aFailedPlanFallsBackToTheDirectBlob() {
        val t = transport(500, "boom")
        assertEquals(HeyarrApi.blobUrl(base, "blake3:abc"), api(t).playbackUrl("asset-1", "blake3:abc"))
    }

    @Test
    fun aBlankAssetIdFallsBackWithoutCallingThePlan() {
        var called = false
        val t = transport(200, "{}") { called = true }
        assertEquals(HeyarrApi.blobUrl(base, "blake3:abc"), api(t).playbackUrl("", "blake3:abc"))
        assertTrue(!called, "no plan call when there is no asset id")
    }
}
