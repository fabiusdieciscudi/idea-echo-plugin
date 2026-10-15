package name.fabius10scudi.ideaecho

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MorphLexiconTest {

    private fun lexiconOf(text: String, charset: java.nio.charset.Charset = Charsets.UTF_8) =
        MorphLexicon.read(text.toByteArray(charset).inputStream())

    @Test
    fun `reads form and lemma, ignoring the features column`() {
        val lexicon = SampleLexicon.lexicon
        assertEquals(10, lexicon.size)
        assertTrue("andrà" in lexicon)
        assertTrue(lexicon.shareLemma("andrà", "andare"))
    }

    @Test
    fun `a form may have several lemmas`() {
        val lexicon = SampleLexicon.lexicon
        assertTrue("both are forms of essere", lexicon.shareLemma("era", "erano"))
        assertTrue("unito is also a participle of unire", lexicon.shareLemma("unito", "univa"))
    }

    @Test
    fun `unknown forms share nothing`() {
        assertFalse(SampleLexicon.lexicon.shareLemma("andare", "camminare"))
    }

    @Test
    fun `lookups ignore case`() {
        assertTrue(SampleLexicon.lexicon.shareLemma("Offriva", "OFFERTO"))
    }

    @Test
    fun `a Latin-9 file is read as well as a UTF-8 one`() {
        val text = "andrà\tandare\tVER:ind+fut+3+s\nandare\tandare\tVER:inf\n"
        val latin9 = lexiconOf(text, java.nio.charset.Charset.forName("ISO-8859-15"))
        assertTrue(latin9.shareLemma("andrà", "andare"))
        assertTrue(lexiconOf(text).shareLemma("andrà", "andare"))
    }

    @Test
    fun `malformed lines are skipped`() {
        val lexicon = lexiconOf("\nsolo-una-colonna\n\tsenza-forma\tX\nbuono\tbuono\tADJ\n")
        assertEquals(1, lexicon.size)
    }
}
