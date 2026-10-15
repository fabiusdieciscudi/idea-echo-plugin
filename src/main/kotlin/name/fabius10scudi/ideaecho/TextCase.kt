package name.fabius10scudi.ideaecho

/** Helpers for replacing a word with another: capitalization and word boundaries. */
object TextCase {

    /**
     * [replacement] written with the capitalization of [original]: all upper case when the
     * original is (and has more than one letter), capitalized when the original starts
     * with a capital, unchanged otherwise. `Bella` → `Graziosa`, `BELLA` → `GRAZIOSA`.
     */
    fun matchCase(original: String, replacement: String): String {
        val letters = original.filter { it.isLetter() }
        return when {
            letters.length > 1 && letters.all { it.isUpperCase() } -> replacement.uppercase()
            original.firstOrNull()?.isUpperCase() == true -> replacement.replaceFirstChar { it.uppercaseChar() }
            else -> replacement
        }
    }

    /**
     * The range of the word at [offset] in [text], or null when there is none. The caret
     * may be inside the word or just after it. A word is a run of letters and digits, so
     * an apostrophe separates `l'aereo` into `l` and `aereo`. The name of a LaTeX command
     * (`\emph`) is not a word: replacing it would break the markup.
     */
    fun wordRangeAt(text: CharSequence, offset: Int): IntRange? {
        if (offset < 0 || offset > text.length) return null
        fun isWordChar(i: Int) = i in text.indices && text[i].isLetterOrDigit()
        var start = when {
            isWordChar(offset) -> offset
            isWordChar(offset - 1) -> offset - 1
            else -> return null
        }
        while (isWordChar(start - 1)) start--
        var end = start
        while (isWordChar(end)) end++
        if (start > 0 && text[start - 1] == '\\') return null
        return start until end
    }
}
