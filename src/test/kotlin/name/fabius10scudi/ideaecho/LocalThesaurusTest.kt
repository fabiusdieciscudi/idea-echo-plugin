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
    fun `several thesauri are merged per headword, the first one first`() {
        val personal = LocalThesaurus.read(
            "UTF-8\ncasa|1\n(personale)|villetta\na lungo|1\n(personale)|per molto\n".toByteArray().inputStream()
        )
        val both = listOf(personal, thesaurus)
        val entries = LocalThesaurus.lookup("casa", null, both)
        assertEquals(listOf("casa"), entries.map { it.headword })
        assertEquals(listOf("personale", "s.f.", "s.f."), entries.single().senses.map { it.category })
        assertEquals(listOf("a lungo"), LocalThesaurus.lookup(" A lungo ", null, both).map { it.headword })
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
        assertTrue(html.contains(">a&lt;b</a>, <a"))
        assertTrue(html.contains(">c&amp;d</a>"))
        assertFalse(html.contains("a<b"))
    }

    @Test
    fun `every synonym is a link that carries the synonym`() {
        val entries = listOf(LocalThesaurus.Entry("stringere", thesaurus.senses("stringere")))
        val html = ThesaurusHtml.entries("strinse", entries, palette, pickScript = "send(w);")
        assertTrue(html.contains("function pick(w){send(w);}"))
        assertTrue(html.contains("data-word=\"tenere in pugno\""))
        assertTrue(html.contains("href=\"pick:tenere+in+pugno\""))
        assertEquals("tenere in pugno", ThesaurusHtml.pickedWord("pick:tenere+in+pugno"))
    }

    @Test
    fun `without a script the links work through their href only`() {
        val entries = listOf(LocalThesaurus.Entry("casa", thesaurus.senses("casa")))
        val html = ThesaurusHtml.entries("casa", entries, palette)
        assertFalse(html.contains("<script>"))
        assertFalse(html.contains("onclick"))
        assertTrue(html.contains("href=\"pick:dimora\""))
    }

    @Test
    fun `other links are not picks`() {
        assertEquals(null, ThesaurusHtml.pickedWord("https://example.org"))
        assertEquals(null, ThesaurusHtml.pickedWord(null))
        assertEquals("perché", ThesaurusHtml.pickedWord("pick:perch%C3%A9"))
    }

    @Test
    fun `the page does not repeat a headword equal to the word`() {
        val entries = listOf(LocalThesaurus.Entry("casa", thesaurus.senses("casa")))
        assertFalse(ThesaurusHtml.entries("casa", entries, palette).contains("←"))
    }
}
