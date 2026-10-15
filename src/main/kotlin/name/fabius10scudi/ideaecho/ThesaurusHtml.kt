package name.fabius10scudi.ideaecho

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Builds the pages the thesaurus view shows for the local thesaurus. Free of IDE
 * classes, so the colours and the texts come from the caller.
 *
 * Every synonym is a link that picks it as the replacement of the current echo. A link
 * carries the synonym twice: in `data-word`, read by [pickScript] in the embedded
 * browser, and in an `href` of the form `pick:<url-encoded synonym>`, for the Swing
 * fallback, which reports clicked links through a hyperlink listener ([pickedWord]).
 */
object ThesaurusHtml {

    private const val PICK_SCHEME = "pick:"

    /** Colours as CSS hex strings, e.g. "#1e1f22", taken from the current theme. */
    data class Palette(val background: String, val foreground: String, val secondary: String)

    /**
     * The entries found for [word], one section per headword and one line per sense.
     * [pickScript], when given, is the body of a JavaScript function of one argument `w`,
     * the clicked synonym, that hands it over to the IDE.
     */
    fun entries(
        word: String,
        entries: List<LocalThesaurus.Entry>,
        palette: Palette,
        pickScript: String? = null,
    ): String = page(palette, pickScript) {
        for (entry in entries) {
            append("<h2>").append(escape(entry.headword))
            if (!entry.headword.equals(word, ignoreCase = true)) {
                append(" <span class=\"from\">← ").append(escape(word)).append("</span>")
            }
            append("</h2>")
            for (sense in entry.senses) {
                append("<p><span class=\"category\">").append(escape(sense.category)).append("</span> ")
                append(sense.synonyms.joinToString(", ") { link(it, pickScript != null) })
                append("</p>")
            }
        }
    }

    /** A single message, for "nothing found", "not configured" and similar cases. */
    fun message(text: String, palette: Palette): String = page(palette) {
        append("<p class=\"message\">").append(escape(text)).append("</p>")
    }

    /** The synonym encoded in the `href` of a clicked link, or null for any other link. */
    fun pickedWord(href: String?): String? =
        href?.takeIf { it.startsWith(PICK_SCHEME) }
            ?.let { URLDecoder.decode(it.removePrefix(PICK_SCHEME), Charsets.UTF_8) }

    private fun link(synonym: String, scripted: Boolean): String = buildString {
        append("<a class=\"pick\" href=\"").append(PICK_SCHEME)
        append(escape(URLEncoder.encode(synonym, Charsets.UTF_8))).append('"')
        if (scripted) {
            append(" data-word=\"").append(escape(synonym)).append('"')
            append(" onclick=\"pick(this.dataset.word);return false\"")
        }
        append('>').append(escape(synonym)).append("</a>")
    }

    // Sizes are 1.5 times the original 13px text, so the synonyms read at a glance.
    private fun page(palette: Palette, pickScript: String? = null, body: StringBuilder.() -> Unit): String = buildString {
        append("<html><head><meta charset=\"utf-8\"><style>")
        append("body{font-family:sans-serif;font-size:19.5px;margin:12px;")
        append("background:").append(palette.background).append(";color:").append(palette.foreground).append("}")
        append("h2{font-size:22.5px;margin:18px 0 6px 0}")
        append(".from,.category,.message{color:").append(palette.secondary).append("}")
        append(".from{font-weight:normal;font-size:19.5px}")
        append(".category{font-style:italic}")
        append("p{margin:3px 0 9px 0}")
        append("a.pick{color:inherit;text-decoration:none;border-bottom:1px dotted ").append(palette.secondary).append("}")
        append("a.pick:hover{border-bottom-style:solid}")
        append("</style>")
        if (pickScript != null) append("<script>function pick(w){").append(pickScript).append("}</script>")
        append("</head><body>")
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
