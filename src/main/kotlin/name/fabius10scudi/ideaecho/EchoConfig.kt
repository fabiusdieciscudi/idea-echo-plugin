package name.fabius10scudi.ideaecho

/** Tunable parameters. */
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
    )

    /** LaTeX commands whose argument (and the command itself) must be fully ignored. */
    val IGNORED_COMMANDS: Set<String> = setOf("comment", "note", "scene", "beat", "ellipsis", "temporaljump") // command names, no backslash

    /**
     * Commands whose real text is NOT the first argument.
     * Maps a command name to the 1-based index of the argument holding the prose.
     * Commands not listed here default to the first argument.
     */
    val TEXT_ARGUMENT: Map<String, Int> = mapOf(
        "chapterwithsummary" to 3,
    )

    /** Common Italian function words to ignore. */
    val IGNORED: Set<String> = """
        essere avere della delle dello degli nella nelle nello nell negli sulla sulle
        sullo sugli sull dalla dalle dallo dall dagli quella quelle quello quelli questa
        queste questo questi come dove quando mentre perché anche ancora
        aveva avevano avevo erano fosse fossero sarebbe sarebbero stato stata
        stati state veniva vennero venne verrebbe prima dopo senza sotto sopra
        verso contro tutto tutta tutti tutte molto molta molti molte poco poca
        pochi poche sempre spesso ormai forse quasi appena finché quindi
        pero però infatti dunque comunque tuttavia eppure invece proprio propria
        allo alle propri proprie altro altra altri altre ogni qualche stesso stessa
        stessi stesse loro nostro nostra nostri nostre vostro vostra vostri vostre
        poteva potevano potrebbe possono dovere doveva dovevano dovrebbe
        qualcosa qualcuno nessuno niente nulla dell della alla quel

    """.trimIndent().split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
}
