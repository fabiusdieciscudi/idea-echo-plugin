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
        ThesaurusServer("Ogma (it)", "https://ogma.lazza.dk/cerca/?q=%s"),
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
alla
alle
allo
altra
altre
altri
altro
anche
ancora
appena
avere
aveva
avevano
avevo
come
comunque
contro
dagli
dall
dalla
dalle
dallo
degli
dell
della
delle
dello
dopo
dove
dovere
doveva
dovevano
dovrebbe
dunque
eppure
erano
essere
finché
forse
fosse
fossero
infatti
invece
loro
mentre
molta
molte
molti
molto
negli
nell
nella
nelle
nello
nessuno
niente
nostra
nostre
nostri
nostro
nulla
ogni
ormai
perché
pero
però
poca
poche
pochi
poco
possono
poteva
potevano
potrebbe
prima
propri
propria
proprie
proprio
qualche
qualcosa
qualcuno
quando
quasi
quel
quella
quelle
quelli
quello
questa
queste
questi
questo
quindi
sarebbe
sarebbero
sempre
senza
sopra
sotto
spesso
stata
state
stati
stato
stessa
stesse
stessi
stesso
sugli
sull
sulla
sulle
sullo
tutta
tuttavia
tutte
tutti
tutto
veniva
venne
vennero
verrebbe
verso
vostra
vostre
vostri
vostro
    """.trimIndent().split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
}
