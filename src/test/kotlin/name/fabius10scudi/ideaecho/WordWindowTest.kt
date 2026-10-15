package name.fabius10scudi.ideaecho

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Per-word look-back windows, which override the global one. */
class WordWindowTest {

    private fun params(windowSize: Int, vararg rules: Pair<String, Int>) = AnalyzerParams(
        minWordLength = 2,
        windowSize = windowSize,
        ignoredWords = emptySet(),
        ignoredCommands = emptySet(),
        textArgument = emptyMap(),
        wordWindowSize = rules.toList(),
    )

    /** [word], then [gap] - 1 distinct filler words, then [word] again: the two are [gap] words apart. */
    private fun twoOccurrences(word: String, gap: Int, second: String = word): String =
        (listOf(word) + (1 until gap).map { "riempitivo$it" } + second).joinToString(" ") + "."

    private fun echoedWords(text: String, params: AnalyzerParams): List<String> =
        RepetitionAnalyzer.analyze(text, params).map { it.word }

    @Test
    fun `without rules the global window applies`() {
        assertEquals(listOf("sguardo", "sguardo"), echoedWords(twoOccurrences("sguardo", 10), params(10)))
        assertEquals(emptyList<String>(), echoedWords(twoOccurrences("sguardo", 11), params(10)))
    }

    @Test
    fun `a larger window catches echoes beyond the global one`() {
        val p = params(10, "sguard[oi]" to 50)
        assertEquals(2, echoedWords(twoOccurrences("sguardo", 50), p).size)
        assertEquals(0, echoedWords(twoOccurrences("sguardo", 51), p).size)
    }

    @Test
    fun `a smaller window lets the word repeat more often`() {
        val p = params(100, "disse" to 5)
        assertEquals(2, echoedWords(twoOccurrences("disse", 5), p).size)
        assertEquals(0, echoedWords(twoOccurrences("disse", 6), p).size)
    }

    @Test
    fun `the pattern covers the inflections it names`() {
        val p = params(10, "sguard[oi]" to 50)
        assertEquals(2, echoedWords(twoOccurrences("sguardo", 40, second = "sguardi"), p).size)
    }

    @Test
    fun `a pair uses the window of the word that has a rule`() {
        // Same stem "sguard": only "sguardo" has a rule, and it applies to the pair.
        assertEquals(50, params(10, "sguardo" to 50).pairWindow("sguardo", "sguardi"))
        assertEquals(5, params(10, "sguardo" to 5).pairWindow("sguardi", "sguardo"))
    }

    @Test
    fun `when both words have a rule the larger window wins`() {
        val p = params(10, "sguardo" to 30, "sguardi" to 70)
        assertEquals(70, p.pairWindow("sguardo", "sguardi"))
        assertEquals(10, p.pairWindow("casa", "case"))
    }

    @Test
    fun `the first matching rule wins`() {
        val p = params(10, "sguardo" to 30, "sguard.*" to 70)
        assertEquals(30, p.windowOverride("sguardo"))
        assertEquals(70, p.windowOverride("sguardi"))
        assertEquals(null, p.windowOverride("casa"))
    }

    @Test
    fun `rules match the whole word, ignoring case`() {
        val p = params(10, "sguard[oi]" to 50)
        assertEquals(50, p.windowOverride("Sguardo"))
        assertEquals(null, p.windowOverride("sguardone"))
    }

    @Test
    fun `an invalid pattern is skipped`() {
        val p = params(10, "sguard[" to 50, "casa" to 20)
        assertEquals(null, p.windowOverride("sguard["))
        assertEquals(20, p.windowOverride("casa"))
    }

    @Test
    fun `each echo carries the window used for its fade`() {
        val echoes = RepetitionAnalyzer.analyze(twoOccurrences("sguardo", 40), params(10, "sguardo" to 50))
        assertEquals(listOf(40, 40), echoes.map { it.minGap })
        assertEquals(listOf(50, 50), echoes.map { it.window })
        val plain = RepetitionAnalyzer.analyze(twoOccurrences("casa", 3), params(10, "sguardo" to 50))
        assertTrue(plain.all { it.window == 10 })
    }

    @Test
    fun `other words keep the global window when a rule enlarges the memory`() {
        // The analyzer remembers 50 words for "sguardo", but "casa" still stops at 10.
        val p = params(10, "sguardo" to 50)
        assertEquals(0, echoedWords(twoOccurrences("casa", 30), p).size)
    }
}
