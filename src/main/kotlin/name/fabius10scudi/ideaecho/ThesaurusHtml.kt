package name.fabius10scudi.ideaecho

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Builds the pages the thesaurus view shows for the local thesaurus. Free of IDE
 * classes, so the colours and the texts come from the caller.
 *
 * Every page starts with a search box, as the online dictionaries have one in theirs,
 * and every synonym is a link that picks it as a replacement. Both reach the IDE as a
 * message: `pick:<url-encoded synonym>` or `search:<url-encoded word>`, read back by
 * [pickedWord] and [searchedWord]. In the embedded browser the page sends them through
 * the function whose body is `sendScript`. In the Swing fallback, which runs no script,
 * a link reports its `href`, which is the same `pick:` message, and the search form is
 * submitted to the `search:` action, read back by [searchedWordFromForm].
 */
object ThesaurusHtml {

    private const val PICK = "pick:"
    private const val SEARCH = "search:"

    /** Colours as CSS hex strings, e.g. "#1e1f22", taken from the current theme. */
    data class Palette(val background: String, val foreground: String, val secondary: String)

    /**
     * The entries found for [word], one section per headword and one line per sense.
     * [sendScript], when given, is the body of a JavaScript function of one argument `m`
     * that hands the message over to the IDE.
     */
    fun entries(
        word: String,
        entries: List<LocalThesaurus.Entry>,
        palette: Palette,
        sendScript: String? = null,
    ): String = page(palette, word, sendScript) {
        for (entry in entries) {
            append("<h2>").append(escape(entry.headword))
            if (!entry.headword.equals(word, ignoreCase = true)) {
                append(" <span class=\"from\">← ").append(escape(word)).append("</span>")
            }
            append("</h2>")
            for (sense in entry.senses) {
                append("<p><span class=\"category\">").append(escape(sense.category)).append("</span> ")
                append(sense.synonyms.joinToString(", ") { link(it, sendScript != null) })
                append("</p>")
            }
        }
    }

    /**
     * A single message, for "nothing found", "not configured" and similar cases, below the
     * search box filled with [word]. A null [text] leaves just the search box.
     */
    fun message(text: String?, palette: Palette, word: String = "", sendScript: String? = null): String =
        page(palette, word, sendScript) {
            if (text != null) append("<p class=\"message\">").append(escape(text)).append("</p>")
        }

    /** The synonym in a `pick:` message or link, or null for anything else. */
    fun pickedWord(message: String?): String? = decode(message, PICK)

    /** The word in a `search:` message, or null for anything else. */
    fun searchedWord(message: String?): String? = decode(message, SEARCH)?.trim()?.takeIf { it.isNotEmpty() }

    /** The word in the data of the submitted search form (`q=<url-encoded word>`). */
    fun searchedWordFromForm(data: String?): String? =
        data?.split('&')?.firstOrNull { it.startsWith("q=") }
            ?.let { URLDecoder.decode(it.removePrefix("q="), Charsets.UTF_8) }
            ?.trim()?.takeIf { it.isNotEmpty() }

    private fun decode(message: String?, prefix: String): String? =
        message?.takeIf { it.startsWith(prefix) }
            ?.let { URLDecoder.decode(it.removePrefix(prefix), Charsets.UTF_8) }

    private fun link(synonym: String, scripted: Boolean): String = buildString {
        append("<a class=\"pick\" href=\"").append(PICK)
        append(escape(URLEncoder.encode(synonym, Charsets.UTF_8))).append('"')
        if (scripted) {
            append(" data-word=\"").append(escape(synonym)).append('"')
            append(" onclick=\"pick(this.dataset.word);return false\"")
        }
        append('>').append(escape(synonym)).append("</a>")
    }

    private fun searchForm(word: String, scripted: Boolean): String = buildString {
        append("<form class=\"search\" method=\"get\" action=\"").append(SEARCH).append('"')
        if (scripted) append(" onsubmit=\"search(this.q.value);return false\"")
        append("><input type=\"search\" name=\"q\" value=\"").append(escape(word)).append("\"")
        if (scripted) append(" autocomplete=\"off\"")
        append("></form>")
    }

    // Sizes are 1.5 times the original 13px text, so the synonyms read at a glance.
    private fun page(
        palette: Palette,
        word: String,
        sendScript: String?,
        body: StringBuilder.() -> Unit,
    ): String = buildString {
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
        append("form.search{margin:0 0 12px 0}")
        append("form.search input{width:100%;box-sizing:border-box;font:inherit;padding:4px 6px;")
        append("background:").append(palette.background).append(";color:").append(palette.foreground)
        append(";border:1px solid ").append(palette.secondary).append(";border-radius:4px}")
        append("</style>")
        if (sendScript != null) {
            append("<script>")
            append("function send(m){").append(sendScript).append("}")
            append("function pick(w){send('").append(PICK).append("'+encodeURIComponent(w))}")
            append("function search(w){send('").append(SEARCH).append("'+encodeURIComponent(w))}")
            append("</script>")
        }
        append("</head><body>")
        append(searchForm(word, sendScript != null))
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
