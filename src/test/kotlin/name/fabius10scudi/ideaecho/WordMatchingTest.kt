package name.fabius10scudi.ideaecho

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The three grouping rules taken one at a time, and what the analyzer derives from them. */
class WordMatchingTest {

    private fun params(
        minCommonPrefix: Int = 0,
        lexicon: MorphLexicon? = null,
        useStemmer: Boolean = true,
    ) = AnalyzerParams(
        minWordLength = 2,
        windowSize = 160,
        ignoredWords = emptySet(),
        ignoredCommands = emptySet(),
        textArgument = emptyMap(),
        minCommonPrefix = minCommonPrefix,
        lexicon = lexicon,
        useStemmer = useStemmer,
    )

    private fun matches(w1: String, w2: String, params: AnalyzerParams) =
        RepetitionAnalyzer.wordsMatch(w1, w2, params)

    @Test
    fun `the stem alone groups regular inflections`() {
        assertTrue(matches("stessa", "stesse", params()))
        assertFalse(matches("bella", "bellezza", params()))
    }

    @Test
    fun `the stemmer can be turned off`() {
        // Same stem "cos", different words: the stemmer's typical false positive.
        assertTrue(matches("cosa", "così", params()))
        assertFalse(matches("cosa", "così", params(useStemmer = false)))
    }

    @Test
    fun `without the stemmer the other rules still apply`() {
        val p = params(minCommonPrefix = 4, lexicon = SampleLexicon.lexicon, useStemmer = false)
        assertTrue("identical words", matches("casa", "casa", p))
        assertTrue("same lemma", matches("offriva", "offerto", p))
        assertTrue("common beginning", matches("stessa", "stesse", p))
    }

    @Test
    fun `the common prefix rule needs the configured number of letters`() {
        assertTrue(matches("bella", "bellezza", params(minCommonPrefix = 4)))
        assertTrue(matches("fede", "fedele", params(minCommonPrefix = 4)))
        assertFalse(matches("fede", "fedele", params(minCommonPrefix = 5)))
        assertFalse("'poc' is only three letters", matches("poco", "pochi", params(minCommonPrefix = 4)))
    }

    @Test
    fun `a zero prefix turns the rule off`() {
        assertFalse(matches("bella", "bellezza", params(minCommonPrefix = 0)))
    }

    @Test
    fun `the lexicon groups forms that share a lemma`() {
        val lexicon = SampleLexicon.lexicon
        assertTrue(matches("offriva", "offerto", params(lexicon = lexicon)))
        assertTrue(matches("poco", "pochi", params(lexicon = lexicon)))
        assertFalse(matches("offriva", "offerto", params()))
    }

    @Test
    fun `commands only match themselves`() {
        val p = params(minCommonPrefix = 4)
        assertTrue(matches("\\Jacques", "\\Jacques", p))
        assertFalse(matches("\\Jacques", "\\Jacqueline", p))
        assertFalse(matches("\\Jacques", "jacques", p))
    }

    @Test
    fun `variants found together share one group`() {
        val echoes = RepetitionAnalyzer.analyze("La bella casa. Una bellezza rara.", params(minCommonPrefix = 4))
        assertEquals(listOf("bella", "bellezza"), echoes.map { it.word })
        assertEquals(1, echoes.map { it.group }.distinct().size)
    }

    @Test
    fun `a group made of one stem keeps the stem as its key`() {
        val echoes = RepetitionAnalyzer.analyze("Una stella e poi altre stelle.", params())
        assertEquals(setOf(RepetitionAnalyzer.stem("stella")), echoes.map { it.group }.toSet())
    }

    @Test
    fun `a chain of echoes links variants that do not match directly`() {
        // poco -> pochi through the lexicon, pochi -> pochissimi through the prefix "poch".
        val p = params(minCommonPrefix = 4, lexicon = SampleLexicon.lexicon)
        assertFalse(matches("poco", "pochissimi", p))
        val echoes = RepetitionAnalyzer.analyze("Resta poco. Restano pochi. Restano pochissimi.", p)
        val group = echoes.first { it.word == "poco" }.group
        assertEquals(listOf("poco", "pochi", "pochissimi"), echoes.filter { it.group == group }.map { it.word })
    }

    @Test
    fun `the gap is the distance to the nearest twin`() {
        val echoes = RepetitionAnalyzer.analyze("Bella casa, bella vista, bellezza.", params(minCommonPrefix = 4))
        assertEquals(listOf(2, 2, 2), echoes.map { it.minGap })
    }

    @Test
    fun `red marks a twin in the same sentence, whatever the variant`() {
        val p = params(lexicon = SampleLexicon.lexicon)
        val same = RepetitionAnalyzer.analyze("Ne restano pochi, molto poco.", p)
        assertTrue(same.all { it.sameSentence })

        val apart = RepetitionAnalyzer.analyze("Ne restano pochi. Molto poco.", p)
        assertEquals(2, apart.size)
        assertFalse(apart.any { it.sameSentence })
    }
}
