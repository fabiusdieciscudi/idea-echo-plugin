package name.fabius10scudi.ideaecho

/**
 * Builds the pages the thesaurus view shows for the local thesaurus. Free of IDE
 * classes, so the colours and the texts come from the caller.
 */
object ThesaurusHtml {

    /** Colours as CSS hex strings, e.g. "#1e1f22", taken from the current theme. */
    data class Palette(val background: String, val foreground: String, val secondary: String)

    /** The entries found for [word], one section per headword and one line per sense. */
    fun entries(word: String, entries: List<LocalThesaurus.Entry>, palette: Palette): String = page(palette) {
        for (entry in entries) {
            append("<h2>").append(escape(entry.headword))
            if (!entry.headword.equals(word, ignoreCase = true)) {
                append(" <span class=\"from\">← ").append(escape(word)).append("</span>")
            }
            append("</h2>")
            for (sense in entry.senses) {
                append("<p><span class=\"category\">").append(escape(sense.category)).append("</span> ")
                append(sense.synonyms.joinToString(", ") { escape(it) })
                append("</p>")
            }
        }
    }

    /** A single message, for "nothing found", "not configured" and similar cases. */
    fun message(text: String, palette: Palette): String = page(palette) {
        append("<p class=\"message\">").append(escape(text)).append("</p>")
    }

    private fun page(palette: Palette, body: StringBuilder.() -> Unit): String = buildString {
        append("<html><head><meta charset=\"utf-8\"><style>")
        append("body{font-family:sans-serif;font-size:13px;margin:8px;")
        append("background:").append(palette.background).append(";color:").append(palette.foreground).append("}")
        append("h2{font-size:15px;margin:12px 0 4px 0}")
        append(".from,.category,.message{color:").append(palette.secondary).append("}")
        append(".from{font-weight:normal;font-size:13px}")
        append(".category{font-style:italic}")
        append("p{margin:2px 0 6px 0}")
        append("</style></head><body>")
        body()
        append("</body></html>")
    }

    private fun escape(text: String): String = buildString(text.length) {
        for (c in text) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(c)
        }
    }
}
