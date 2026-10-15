package name.fabius10scudi.ideaecho

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalThesaurusTest {

    private val thesaurus: LocalThesaurus by lazy {
        val stream = LocalThesaurusTest::class.java.getResourceAsStream("thesaurus-sample.dat")
            ?: error("thesaurus-sample.dat not found in the test resources")
        stream.use { LocalThesaurus.read(it) }
    }

    private val palette = ThesaurusHtml.Palette("#ffffff", "#000000", "#808080")

    @Test
    fun `reads headwords and their senses`() {
        assertEquals("an entry without senses is dropped", 5, thesaurus.size)
        val senses = thesaurus.senses("casa")
        assertEquals(2, senses.size)
        assertEquals(LocalThesaurus.Sense("s.f.", listOf("abitazione", "dimora", "residenza")), senses[0])
    }

    @Test
    fun `keeps glosses and expressions of several words`() {
        val sense = thesaurus.senses("stringere").single()
        assertEquals("v. Tenere stretto", sense.category)
        assertTrue("tenere in pugno" in sense.synonyms)
    }

    @Test
    fun `a headword is found directly, without a lexicon`() {
        assertEquals(listOf("casa"), thesaurus.lookup("Casa", null).map { it.headword })
        assertTrue(thesaurus.lookup("bella", null).isEmpty())
    }

    @Test
    fun `an inflected form is found through its lemma`() {
        val lexicon = SampleLexicon.lexicon
        assertEquals(listOf("bello"), thesaurus.lookup("bella", lexicon).map { it.headword })
        assertEquals(listOf("stringere"), thesaurus.lookup("strinse", lexicon).map { it.headword })
    }

    @Test
    fun `a form with several lemmas yields every entry, its own first`() {
        assertEquals(listOf("era", "essere"), thesaurus.lookup("era", SampleLexicon.lexicon).map { it.headword })
    }

    @Test
    fun `the encoding named on the first line is honoured`() {
        val text = "ISO8859-1\nperché|1\n(cong.)|poiché|siccome\n"
        val latin1 = LocalThesaurus.read(text.toByteArray(Charsets.ISO_8859_1).inputStream())
        assertEquals(listOf("poiché", "siccome"), latin1.senses("perché").single().synonyms)
    }

    @Test
    fun `the page names the lemma it came from and escapes the text`() {
        val entries = listOf(LocalThesaurus.Entry("bello", listOf(LocalThesaurus.Sense("agg.", listOf("a<b", "c&d")))))
        val html = ThesaurusHtml.entries("bella", entries, palette)
        assertTrue(html.contains("bello"))
        assertTrue(html.contains("bella"))
        assertTrue(html.contains("a&lt;b, c&amp;d"))
        assertFalse(html.contains("a<b"))
    }

    @Test
    fun `the page does not repeat a headword equal to the word`() {
        val entries = listOf(LocalThesaurus.Entry("casa", thesaurus.senses("casa")))
        assertFalse(ThesaurusHtml.entries("casa", entries, palette).contains("←"))
    }
}
