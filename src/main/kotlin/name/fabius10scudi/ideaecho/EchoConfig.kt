package name.fabius10scudi.ideaecho

/** Built-in defaults. User overrides live in EchoSettings (per project). */
object EchoConfig {

//    /** Vowels (accented included) for the "except final vowel" match. */
//    const val VOWELS = "aeiouàèéìíòóùú"

    /** A thesaurus REST endpoint. %s = URL-encoded word. */
    data class ThesaurusServer(val label: String, val urlTemplate: String) {
        override fun toString(): String = label   // shown in the combo box
    }

    /** Available thesaurus servers; the first one is the default. */
    val THESAURUS_SERVERS: List<ThesaurusServer> = listOf(
        ThesaurusServer("Reverso", "https://synonyms.reverso.net/sinonimi/it/%s"),
        ThesaurusServer("Virgilio (it)", "https://sapere.virgilio.it/parole/sinonimi-e-contrari/%s"),
        ThesaurusServer("Ogma (it)", "https://ogma.lazza.dk/cerca/?q=%s"),
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
    val DEFAULT_TEXT_ARGUMENT: Map<String, Int> = mapOf(
        "chapterwithsummary" to 3,
    )

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
