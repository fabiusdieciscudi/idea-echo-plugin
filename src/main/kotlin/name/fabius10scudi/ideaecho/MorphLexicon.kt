package name.fabius10scudi.ideaecho

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path

/**
 * A full-form lexicon mapping inflected forms to their lemmas, in the Morph-it! format:
 * one entry per line, tab-separated, `form<TAB>lemma<TAB>features`. Only the first two
 * columns are used. A form may carry several lemmas (`era` is both `essere` and `era`).
 *
 * The lexicon is not bundled with the plugin: the user downloads it and points the
 * settings at the file. It lets the analyzer group forms that no stemmer can relate,
 * such as `strinse`/`stringeva` (both `stringere`) or `offerto`/`offriva` (`offrire`).
 *
 * Lemmas are interned as integer ids, so the half million Morph-it forms cost one small
 * array each rather than one string per lemma.
 */
class MorphLexicon private constructor(private val lemmasByForm: Map<String, IntArray>) {

    /** Number of distinct forms. */
    val size: Int get() = lemmasByForm.size

    /** True when [form] is in the lexicon. */
    operator fun contains(form: String): Boolean = form.lowercase() in lemmasByForm

    /** True when both forms are known and have at least one lemma in common. */
    fun shareLemma(form1: String, form2: String): Boolean {
        val l1 = lemmasByForm[form1.lowercase()] ?: return false
        val l2 = lemmasByForm[form2.lowercase()] ?: return false
        return l1.any { it in l2 }
    }

    companion object {
        private val LATIN_9: Charset = Charset.forName("ISO-8859-15")

        /** Reads a lexicon file. Throws [IOException] when it cannot be read. */
        fun load(path: Path): MorphLexicon = parse(Files.readAllBytes(path))

        /** Reads a lexicon from a stream, which is not closed. */
        fun read(input: InputStream): MorphLexicon = parse(input.readBytes())

        /**
         * Morph-it! has been distributed in ISO-8859-15; a converted copy is likely to be
         * UTF-8. Strict UTF-8 is tried first, since Latin-9 text with accented letters is
         * almost never valid UTF-8, and Latin-9 is the fallback.
         */
        private fun parse(bytes: ByteArray): MorphLexicon {
            val text = try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            } catch (_: CharacterCodingException) {
                String(bytes, LATIN_9)
            }

            val lemmaIds = HashMap<String, Int>()
            val lemmasByForm = HashMap<String, IntArray>()
            for (line in text.lineSequence()) {
                val tab1 = line.indexOf('\t')
                if (tab1 <= 0) continue
                val tab2 = line.indexOf('\t', tab1 + 1).let { if (it < 0) line.length else it }
                val form = line.substring(0, tab1).trim().lowercase()
                val lemma = line.substring(tab1 + 1, tab2).trim().lowercase()
                if (form.isEmpty() || lemma.isEmpty()) continue

                val id = lemmaIds.getOrPut(lemma) { lemmaIds.size }
                val known = lemmasByForm[form]
                if (known == null) {
                    lemmasByForm[form] = intArrayOf(id)
                } else if (id !in known) {
                    lemmasByForm[form] = known + id
                }
            }
            return MorphLexicon(lemmasByForm)
        }
    }
}
