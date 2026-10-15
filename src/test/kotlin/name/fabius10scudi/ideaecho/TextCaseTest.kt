package name.fabius10scudi.ideaecho

import org.junit.Assert.assertEquals
import org.junit.Test

class TextCaseTest {

    @Test
    fun `lower case stays lower case`() {
        assertEquals("graziosa", TextCase.matchCase("bella", "graziosa"))
    }

    @Test
    fun `a capital initial is kept`() {
        assertEquals("Graziosa", TextCase.matchCase("Bella", "graziosa"))
        assertEquals("Tenere in pugno", TextCase.matchCase("Strinse", "tenere in pugno"))
    }

    @Test
    fun `all capitals are kept`() {
        assertEquals("GRAZIOSA", TextCase.matchCase("BELLA", "graziosa"))
    }

    @Test
    fun `a single capital letter is an initial, not all capitals`() {
        assertEquals("Ecco", TextCase.matchCase("E", "ecco"))
    }

    private fun wordAt(text: String, offset: Int): String? =
        TextCase.wordRangeAt(text, offset)?.let { text.substring(it) }

    @Test
    fun `the word around the caret is found, also with the caret just after it`() {
        val text = "Una casa bella."
        assertEquals("casa", wordAt(text, 4))
        assertEquals("casa", wordAt(text, 6))
        assertEquals("casa", wordAt(text, 8))
        assertEquals("bella", wordAt(text, 14))
    }

    @Test
    fun `no word between spaces or punctuation`() {
        assertEquals(null, wordAt("uno  due", 4))
        assertEquals(null, wordAt("", 0))
    }

    @Test
    fun `an apostrophe separates words and accents belong to them`() {
        assertEquals("aereo", wordAt("l'aereo", 4))
        assertEquals("città", wordAt("la città", 7))
    }

    @Test
    fun `a LaTeX command name is not a word`() {
        assertEquals(null, wordAt("\\emph{bella}", 3))
        assertEquals("bella", wordAt("\\emph{bella}", 8))
    }
}
