package name.fabius10scudi.ideaecho

/** Capitalization helpers for replacing a word with another. */
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
}
