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
}
