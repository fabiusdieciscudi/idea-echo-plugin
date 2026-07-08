package name.fabius10scudi.ideaecho

/** A detected echo: a word repeated within the look-back window. */
data class Echo(
    val word: String,
    val startOffset: Int,
    val endOffset: Int,
    val sameSentence: Boolean, // repeated inside the same sentence -> painted red
)

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

/**
 * Detects "nearby echoes": words repeated within a sliding window of the last
 * N words. Two words match if they are equal, or equal except for the final
 * vowel (same length, both ending in a vowel).
 *
 * LaTeX is pre-processed first (see maskLatex): argument commands are unwrapped
 * to their first argument, ignored commands are dropped with their content, and
 * argument-less commands are kept literally (e.g. \Jacques).
 */
object RepetitionAnalyzer {

    private val SENTENCE_PATTERN = Regex("""[^.!?…]+(?:[.!?…]+|\z)""")

    // A token is either a literal argument-less command (\Foo) or a normal word.
    // (?U) makes \w and \b Unicode-aware so accented Italian letters are matched.
    private val TOKEN_PATTERN = Regex("""(?U)\\[A-Za-z]+|\b\w+(?:-\w+)?\b""")

    private data class Occurrence(val word: String, val sentenceId: Int, val offset: Int)

    fun analyze(text: String): List<Echo> {
        val masked = maskLatex(text) // same length as text, so offsets stay valid

        val window = ArrayDeque<Occurrence>()
        val seen = HashSet<Occurrence>()
        val marksBySentence = HashMap<Int, MutableList<Occurrence>>()

        var sentenceId = 0
        for (sentenceMatch in SENTENCE_PATTERN.findAll(masked)) {
            val sentenceStart = sentenceMatch.range.first
            // Apostrophes -> spaces so "l'aereo" splits into "l" + "aereo".
            val tokenizable = sentenceMatch.value.replace('\'', ' ').replace('\u2019', ' ')

            for (tokenMatch in TOKEN_PATTERN.findAll(tokenizable)) {
                val raw = tokenMatch.value
                val isCommand = raw.startsWith('\\')
                // Commands stay literal (backslash + original case); words are lowercased.
                val word = if (isCommand) raw else raw.lowercase()

                if (!isCommand &&
                    (word.length < EchoConfig.MIN_WORD_LENGTH || word in EchoConfig.IGNORED)
                ) continue

                val current = Occurrence(word, sentenceId, sentenceStart + tokenMatch.range.first)

                for (prev in window) {
                    if (wordsMatch(prev.word, word)) {
                        for (occ in listOf(current, prev)) {
                            if (seen.add(occ)) {
                                marksBySentence.getOrPut(occ.sentenceId) { mutableListOf() }.add(occ)
                            }
                        }
                    }
                }

                window.addLast(current)
                if (window.size > EchoConfig.WINDOW_SIZE) window.removeFirst()
            }
            sentenceId++
        }

        // Red when the same word is marked more than once within its sentence.
        val echoes = ArrayList<Echo>()
        for ((_, marks) in marksBySentence) {
            val countByWord = marks.groupingBy { it.word }.eachCount()
            for (occ in marks) {
                echoes += Echo(
                    word = occ.word,
                    startOffset = occ.offset,
                    endOffset = occ.offset + occ.word.length,
                    sameSentence = (countByWord[occ.word] ?: 0) > 1,
                )
            }
        }
        echoes.sortBy { it.startOffset }
        return echoes
    }

    private fun wordsMatch(w1: String, w2: String): Boolean {
        if (w1 == w2) return true
        // Commands are literal: only an exact match counts.
        if (w1.startsWith('\\') || w2.startsWith('\\')) return false
        if (w1.length != w2.length || w1.isEmpty()) return false
        return w1.last() in EchoConfig.VOWELS &&
                w2.last() in EchoConfig.VOWELS &&
                w1.dropLast(1) == w2.dropLast(1)
    }

    /** Grouping stem: the word without its final vowel (commands are not stripped). */
    fun stem(word: String): String {
        if (word.startsWith('\\')) return word.lowercase()
        val w = word.lowercase()
        return if (w.isNotEmpty() && w.last() in EchoConfig.VOWELS) w.dropLast(1) else w
    }

    /**
     * Returns a same-length copy of [text] with LaTeX commands handled by blanking
     * (never moving) characters, so offsets of the kept text are preserved:
     *  - IGNORED_COMMANDS with an argument: command + argument blanked;
     *  - other commands with an argument: wrapper blanked, first argument kept
     *    (and scanned recursively for nested commands);
     *  - commands without an argument: left literal (e.g. \Jacques).
     */
    private fun maskLatex(text: String): String {
        val out = text.toCharArray()

        fun blank(from: Int, to: Int) {
            for (k in from until to) if (out[k] != '\n') out[k] = ' '
        }

        fun matchBrace(openIdx: Int): Int {
            var depth = 0
            var k = openIdx
            while (k < text.length) {
                when (text[k]) {
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) return k }
                }
                k++
            }
            return -1
        }

        // Matching ']' for the '[' at [openIdx], skipping balanced {..} groups.
        fun matchBracket(openIdx: Int): Int {
            var k = openIdx + 1
            while (k < text.length) {
                val ch = text[k]
                if (ch == '{') {
                    val c = matchBrace(k)
                    if (c < 0) return -1
                    k = c + 1
                } else if (ch == ']') {
                    return k
                } else {
                    k++
                }
            }
            return -1
        }

        fun scan(from: Int, to: Int) {
            var i = from
            while (i < to) {
                if (text[i] == '\\' && i + 1 < to && text[i + 1].isAsciiLetter()) {
                    var j = i + 1
                    while (j < to && text[j].isAsciiLetter()) j++
                    val name = text.substring(i + 1, j)

                    // Collect the trailing option [..] and argument {..} groups.
                    val argSpans = ArrayList<IntArray>()
                    val optionSpans = ArrayList<IntArray>()
                    var k = j
                    loop@ while (k < to) {
                        when (text[k]) {
                            '{' -> {
                                val c = matchBrace(k)
                                if (c !in 0 until to) break@loop
                                argSpans.add(intArrayOf(k, c)); k = c + 1
                            }
                            '[' -> {
                                val c = matchBracket(k)
                                if (c !in 0 until to) break@loop
                                optionSpans.add(intArrayOf(k, c)); k = c + 1
                            }
                            else -> break@loop
                        }
                    }

                    // Literal only when the command has neither [..] nor {..}.
                    if (argSpans.isEmpty() && optionSpans.isEmpty()) {
                        i = j
                        continue
                    }

                    blank(i, j)                                  // blank '\name'
                    for (opt in optionSpans) blank(opt[0], opt[1] + 1)   // drop all [..]

                    val target = EchoConfig.TEXT_ARGUMENT[name] ?: 1
                    val ignored = name in EchoConfig.IGNORED_COMMANDS
                    argSpans.forEachIndexed { idx, sp ->
                        if (!ignored && idx + 1 == target) {
                            blank(sp[0], sp[0] + 1)              // '{'
                            blank(sp[1], sp[1] + 1)              // '}'
                            scan(sp[0] + 1, sp[1])              // keep + recurse into the text arg
                        } else {
                            blank(sp[0], sp[1] + 1)             // drop this argument entirely
                        }
                    }
                    i = k
                    continue
                }
                i++
            }
        }

// Blank LaTeX comments (% ... end-of-line), respecting escaped \% .
        run {
            var i = 0
            while (i < out.size) {
                when (text[i]) {
                    '\\' -> i += 2   // control symbol/command char: '\%' is a literal %, not a comment
                    '%' -> while (i < out.size && out[i] != '\n') { out[i] = ' '; i++ }
                    else -> i++
                }
            }
        }

        scan(0, out.size)
        return String(out)
    }
}