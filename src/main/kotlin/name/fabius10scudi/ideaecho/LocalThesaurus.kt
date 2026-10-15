package name.fabius10scudi.ideaecho

import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

/**
 * A thesaurus read from a file in the MyThes format used by LibreOffice, such as the
 * Italian `th_it_IT_v2.dat`. Like the Morph-it! lexicon it is downloaded by the user,
 * and it gives the tool window synonyms without going online.
 *
 * The format: the first line names the encoding; then each entry is a header line
 * `headword|count` followed by `count` sense lines `(category)|synonym|synonym|…`.
 * The category is a part of speech, sometimes followed by a short gloss. A synonym may
 * be an expression of several words.
 *
 * Entries are lemmas only: `stringere` is there, `strinse` is not. [lookup] bridges
 * the gap through the lexicon, when there is one.
 */
class LocalThesaurus private constructor(private val sensesByWord: Map<String, List<Sense>>) {

    /** One meaning of a headword: its category, e.g. "s.f." or "v.", and its synonyms. */
    data class Sense(val category: String, val synonyms: List<String>)

    /** The senses found for one headword. */
    data class Entry(val headword: String, val senses: List<Sense>)

    /** Number of headwords. */
    val size: Int get() = sensesByWord.size

    /** The senses of [headword], empty when it is not in the thesaurus. */
    fun senses(headword: String): List<Sense> = sensesByWord[headword.lowercase()].orEmpty()

    /**
     * Entries for [word]: its own entry when it is a headword, then one for each of its
     * lemmas in [lexicon] that is a headword. `bella` yields `bello`, `strinse` yields
     * `stringere`, and `era` yields both `essere` and `era` when the thesaurus has them.
     */
    fun lookup(word: String, lexicon: MorphLexicon?): List<Entry> = lookup(word, lexicon, listOf(this))

    companion object {
        /**
         * Like the instance [lookup], over several thesauri at once: a headword found in more
         * than one gives a single entry, with the senses of the first thesaurus first. This is
         * how a personal thesaurus is shown together with the LibreOffice one.
         */
        fun lookup(word: String, lexicon: MorphLexicon?, thesauri: List<LocalThesaurus>): List<Entry> {
            val key = word.lowercase().trim()
            val candidates = linkedSetOf(key)
            lexicon?.lemmasOf(key)?.let { candidates += it }
            return candidates.mapNotNull { headword ->
                thesauri.flatMap { it.sensesByWord[headword].orEmpty() }
                    .takeIf { it.isNotEmpty() }
                    ?.let { Entry(headword, it) }
            }
        }

        /** Reads a thesaurus file. Throws an exception when it cannot be read. */
        fun load(path: Path): LocalThesaurus = parse(Files.readAllBytes(path))

        /** Reads a thesaurus from a stream, which is not closed. */
        fun read(input: InputStream): LocalThesaurus = parse(input.readBytes())

        private fun parse(bytes: ByteArray): LocalThesaurus {
            // The first line is ASCII in every encoding MyThes uses, so it can be read first.
            val firstLineEnd = bytes.indexOf('\n'.code.toByte()).let { if (it < 0) bytes.size else it }
            val encodingName = String(bytes, 0, firstLineEnd, Charsets.US_ASCII).trim()
            val charset = runCatching { Charset.forName(encodingName) }.getOrDefault(Charsets.UTF_8)
            val text = String(bytes, charset)

            val sensesByWord = LinkedHashMap<String, MutableList<Sense>>()
            var current: MutableList<Sense>? = null
            for (line in text.lineSequence().drop(1)) {
                if (line.isBlank()) continue
                if (line.startsWith('(')) {
                    val fields = line.split('|')
                    val category = fields[0].trim().removePrefix("(").removeSuffix(")").trim()
                    val synonyms = fields.drop(1).map { it.trim() }.filter { it.isNotEmpty() }
                    if (synonyms.isNotEmpty()) current?.add(Sense(category, synonyms))
                } else {
                    val headword = line.substringBefore('|').trim().lowercase()
                    current = if (headword.isEmpty()) null else sensesByWord.getOrPut(headword) { mutableListOf() }
                }
            }
            return LocalThesaurus(sensesByWord.filterValues { it.isNotEmpty() })
        }
    }
}
