package one.rarebit.heyarr.desktop.open

/**
 * Picks the temp-file extension so `xdg-open` routes a downloaded blob to the right
 * handler (a browser for `.html`, an e-reader for `.epub`/`.pdf`/`.cbz`, …). The
 * extension of an existing `filename` wins (it is what the server stored); otherwise the
 * MIME is mapped; failing both, `bin`.
 */
object MediaExt {

    private val MIME_TO_EXT = mapOf(
        "application/epub+zip" to "epub",
        "application/pdf" to "pdf",
        "application/vnd.comicbook+zip" to "cbz",
        "application/x-cbz" to "cbz",
        "application/vnd.comicbook-rar" to "cbr",
        "application/x-cbr" to "cbr",
        "text/html" to "html",
        "application/xhtml+xml" to "html",
        "application/zip" to "zip",
        "text/plain" to "txt",
    )

    /** The extension (no dot) for a blob with this [filename] and/or [mime]; never blank. */
    fun forNameAndMime(filename: String?, mime: String?): String {
        extFromName(filename)?.let { return it }
        mime?.let { m ->
            val key = m.substringBefore(';').trim().lowercase()
            MIME_TO_EXT[key]?.let { return it }
        }
        return "bin"
    }

    private fun extFromName(filename: String?): String? {
        val name = filename?.trim().orEmpty()
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return null
        val ext = name.substring(dot + 1).lowercase()
        return if (ext.all { it.isLetterOrDigit() } && ext.length in 1..5) ext else null
    }
}
