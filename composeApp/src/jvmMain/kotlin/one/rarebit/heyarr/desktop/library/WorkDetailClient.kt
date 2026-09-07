package one.rarebit.heyarr.desktop.library

import one.rarebit.heyarr.desktop.auth.Credential
import one.rarebit.heyarr.desktop.net.HttpTransport
import java.net.URLEncoder

/**
 * The read behind the desktop **work detail** pane: `GET /api/v1/works/{id}`
 * (heyarr-core ADR-0075). The detail read always inlines the browse embeds — the
 * handler asks for `artwork` and `primary_asset` unconditionally — so this needs no
 * `include=` query and gets back the one playable file with the work in a single hop.
 *
 * Mirrors the desktop's [LibraryClient] shape (blocking [HttpTransport], a [Credential]
 * bearer): a 404 is "no such work" (null), any other non-200 throws so the caller can
 * surface the status.
 */
class WorkDetailClient(
    private val http: HttpTransport,
    private val baseUrl: String,
    private val credential: Credential,
) {
    /** Fetch one work with its inlined `primary_asset`; null on a 404, throws on any other non-200. */
    fun getWorkDetail(id: String): WorkDetail? {
        val resp = http.get(workUrl(baseUrl, id), credential.asHeader())
        if (resp.status == 404) return null
        require(resp.status == 200) { "detail: GET /works/$id failed: HTTP ${resp.status}" }
        return WorkDetailJson.parse(resp.body)
    }

    companion object {
        /** `GET /api/v1/works/{id}` — the detail read (embeds `primary_asset` server-side). */
        fun workUrl(baseUrl: String, id: String): String =
            baseUrl.trimEnd('/') + "/api/v1/works/" + URLEncoder.encode(id, "UTF-8")
    }
}
