package name.fabius10scudi.ideaecho

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Word pairs that should be reported as echoes of each other when they appear close together.
 *
 * Each case puts the two words in a single short sentence and checks that both occurrences are
 * marked. The filters are neutral (no ignored words, no ignored commands, minimum length 2), so a
 * failure here means the grouping itself misses the pair, not a filter configured by the user.
 * Grouping uses the default common prefix and a small lexicon in the Morph-it! format, which
 * stands in for the real file the user downloads.
 *
 * A single word (no slash) checks that an identical repetition is detected.
 */
@RunWith(Parameterized::class)
class RepetitionAnalyzerTest(private val first: String, private val second: String) {

    companion object {
        private val PAIRS = listOf(
            "acqua/acquatica",
            "altre/altro",
            "andare/andrà",
            "bella/bellezza",
            "delicatamente/delicatezza",
            "divenne/divenire",
            "fede/fedele",
            "gentile/gentilissimo",
            "impercettibili/impercettibilmente",
            "insistette/insistenza",
            "intervistarlo/intervista",
            "limiti/limitato",
            "offriva/offerto",
            "particolare/particolari",
            "pochi/poco",
            "poco/pochi",
            "procediamo/procedettero",
            "qualcosa",
            "quiete/quieto",
            "raggiunsero/raggiungere",
            "sfrecciò/sfreccianti",
            "stessa/stesse",
            "stringeva/strinse",
            "titolo",
            "trascorso/trascorrere",
            "unito/univa",
            "uscisse/uscito",
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}/{1}")
        fun pairs(): List<Array<String>> = PAIRS.map { entry ->
            val words = entry.split('/')
            arrayOf(words.first(), words.last())
        }

        private val PARAMS = AnalyzerParams(
            minWordLength = 2,
            windowSize = 160,
            ignoredWords = emptySet(),
            ignoredCommands = emptySet(),
            textArgument = emptyMap(),
            minCommonPrefix = 4,
            lexicon = SampleLexicon.lexicon,
        )
    }

    @Test
    fun `nearby occurrences are reported as echoes`() {
        val text = "Prima $first e poi $second."
        val firstOffset = text.indexOf(first)
        val secondOffset = text.indexOf(second, firstOffset + first.length)

        val echoes = RepetitionAnalyzer.analyze(text, PARAMS)
        val markedOffsets = echoes.map { it.startOffset }.toSet()

        assertTrue(
            "'$first' and '$second' should be echoes; stems: '${RepetitionAnalyzer.stem(first)}' vs " +
                "'${RepetitionAnalyzer.stem(second)}'; echoes found: ${echoes.map { it.word }}",
            firstOffset in markedOffsets && secondOffset in markedOffsets,
        )
    }
}
