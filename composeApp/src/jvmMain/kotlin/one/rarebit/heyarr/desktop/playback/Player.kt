package one.rarebit.heyarr.desktop.playback

/**
 * The playback seam. heyarr serves a work's file as a range-capable blob stream
 * (`GET /api/v1/blobs/{hash}/content`, ADR-0013); on the desktop we do not decode it
 * in-process — we hand the URL and the bearer credential to an external media player.
 * The interface exists so the UI depends on a seam a test can fake, and so a second
 * backend (a different player) is a new [Player], not a rewrite.
 */
interface Player {

    /**
     * Play the blob [blobHash] from [baseUrl], authenticating with [token] (a bearer
     * token, sent as an `Authorization` header to the player, never logged). Blocking:
     * it launches the player process and returns — the caller runs it off the UI thread.
     */
    fun play(baseUrl: String, blobHash: String, token: String): PlayResult
}

/** The outcome of a [Player.play] — a value the UI renders, never a thrown exception. */
sealed interface PlayResult {
    /** The player process was launched. */
    object Launched : PlayResult

    /** Nothing was launched; [message] is a UI-safe reason (it carries NO token). */
    data class Failed(val message: String) : PlayResult
}

/**
 * The blob-stream URL builder — the desktop twin of heyarr-mobile's
 * `PlaybackClient.blobContentUrl`. Pure and unit-tested: the hash is heyarr's content
 * hash (`blake3:<64 lowercase hex>`) and goes into the path VERBATIM. The server
 * validates that exact shape and answers 400 to a percent-encoded colon
 * (`blake3%3A…`), which is how every live playback used to fail — so anything outside
 * that alphabet is refused here rather than encoded.
 */
object BlobStream {

    private val BLOB_HASH = Regex("^blake3:[0-9a-f]{64}$")

    /** The range-capable content URL for [hash]. Throws [IllegalArgumentException] on a bad hash. */
    fun contentUrl(baseUrl: String, hash: String): String {
        require(BLOB_HASH.matches(hash)) { "not a blob hash: $hash" }
        return baseUrl.trimEnd('/') + "/api/v1/blobs/" + hash + "/content"
    }
}
