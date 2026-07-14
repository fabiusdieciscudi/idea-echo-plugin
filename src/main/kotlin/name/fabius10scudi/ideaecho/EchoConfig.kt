package name.fabius10scudi.ideaecho

/** Built-in defaults. User overrides live in EchoSettings (per project). */
object EchoConfig {

//    /** Vowels (accented included) for the "except final vowel" match. */
//    const val VOWELS = "aeiouàèéìíòóùú"

    /**
     * A thesaurus REST endpoint.
     *
     * The URL template supports these placeholders (all URL-encoded):
     *  - %1  the word;
     *  - %2  its first letter, lower-cased;
     *  - %3  its first letter, upper-cased.
     * The legacy single-placeholder %s is still accepted and means the word.
     */
    data class ThesaurusServer(val label: String, val urlTemplate: String) {
        override fun toString(): String = label   // shown in the combo box

        /** Builds the URL for [word], filling the placeholders (each URL-encoded). */
        fun urlFor(word: String): String {
            val w = word.trim()
            val initial = w.firstOrNull()?.toString().orEmpty()
            // Single-pass replacement: the already-encoded values may themselves
            // contain "%2x" sequences, so we must not re-scan our own output.
            return PLACEHOLDER.replace(urlTemplate) { m ->
                when (m.value) {
                    "%1", "%s" -> enc(w)
                    "%2" -> enc(initial.lowercase())
                    "%3" -> enc(initial.uppercase())
                    else -> m.value
                }
            }
        }

        private fun enc(value: String): String =
            java.net.URLEncoder.encode(value, Charsets.UTF_8)

        private companion object {
            private val PLACEHOLDER = Regex("%[123s]")
        }
    }

    /** Available thesaurus servers; the first one is the default. */
    val THESAURUS_SERVERS: List<ThesaurusServer> = listOf(
        ThesaurusServer("Corriere (it)", "https://dizionari.corriere.it/dizionario_sinonimi_contrari/%3/%1.shtml"),
        ThesaurusServer("Reverso", "https://synonyms.reverso.net/sinonimi/it/%1"),
        ThesaurusServer("Virgilio (it)", "https://sapere.virgilio.it/parole/sinonimi-e-contrari/%1"),
        ThesaurusServer("Ogma (it)", "https://ogma.lazza.dk/cerca/?q=%1"),
    )

    /**
     * LaTeX commands whose argument (and the command itself) must be fully ignored.
     * Defined in messages/EchoBundle.properties (language specific).
     */
    val DEFAULT_IGNORED_COMMANDS: List<String> by lazy {
        EchoBundle.list("echo.default.ignoredCommands")
    }

    /**
     * Commands whose real text is NOT the first argument.
     * Maps a command name to the 1-based index of the argument holding the prose.
     * Commands not listed here default to the first argument.
     */
    val DEFAULT_TEXT_ARGUMENT: Map<String, Int> = mapOf()

    /**
     * Function words to ignore. Each entry is a regular expression matched against
     * the whole (lower-cased) word, so a plain word like "dopo" works as-is while
     * "quell[aeio]" collapses quella/quelle/quelli/quello into one entry.
     * Defined in messages/EchoBundle.properties (language specific).
     */
    val DEFAULT_IGNORED: List<String> by lazy {
        EchoBundle.list("echo.default.ignoredWords")
    }
}
