package name.fabius10scudi.ideaecho

import org.tartarus.snowball.ext.italianStemmer
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/** A detected echo: a word repeated within the look-back window. */
data class Echo(
    val word: String,
    val startOffset: Int,
    val endOffset: Int,
    val sameSentence: Boolean, // has a twin inside the same sentence -> painted red
    val wordIndex: Int,        // progressive index among *accepted* words (matches the window)
    val sentenceId: Int,       // sentence number, 0-based
    val minGap: Int,           // words to the nearest twin (1..window)
    val group: String,         // key shared by every occurrence of the same word group
    val window: Int,           // look-back window of the pair that gave minGap, for the fade
)

/**
 * Immutable snapshot of the user-configurable analysis parameters.
 *
 * [ignoredWords] entries are regular expressions matched against the whole word.
 * Entries without regex metacharacters are kept in a fast literal set; the rest
 * are compiled once. Invalid patterns are skipped rather than breaking analysis.
 *
 * [useStemmer] groups words with the same Snowball stem. [minCommonPrefix] is the
 * number of leading letters two words must share to be grouped even when their stems
 * differ; 0 turns the rule off. [lexicon], when present, groups forms that have a
 * lemma in common.
 *
 * [wordWindowSize] overrides [windowSize] for single words: each rule is a regular
 * expression matched against the whole word, as in [ignoredWords], with its own
 * window; the first rule that matches wins. See [pairWindow] for how it applies.
 */
data class AnalyzerParams(
    val minWordLength: Int,
    val windowSize: Int,
    val ignoredWords: Set<String>,
    val ignoredCommands: Set<String>,
    val textArgument: Map<String, Int>,
    val minCommonPrefix: Int = 0,
    val lexicon: MorphLexicon? = null,
    val useStemmer: Boolean = true,
    val wordWindowSize: List<Pair<String, Int>> = emptyList(),
) {
    private val windowRules: List<Pair<Regex, Int>> =
        wordWindowSize.mapNotNull { (pattern, size) ->
            runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull()?.let { it to size }
        }

    // A rule is looked up once per word, not once per pair.
    private val overrides = ConcurrentHashMap<String, Int>()

    /** The window of the first rule matching [word], or null when no rule does. */
    fun windowOverride(word: String): Int? =
        overrides.getOrPut(word) { windowRules.firstOrNull { it.first.matches(word) }?.second ?: NO_RULE }
            .takeIf { it != NO_RULE }

    /**
     * The look-back window for two occurrences: the window of the word that has a rule,
     * the larger one when both have, [windowSize] when neither has. An echo is heard as
     * soon as one of the two words asks to be watched from farther away.
     */
    fun pairWindow(w1: String, w2: String): Int =
        listOfNotNull(windowOverride(w1), windowOverride(w2)).maxOrNull() ?: windowSize

    /** How many accepted words the analyzer must remember: the largest window in use. */
    val maxWindow: Int = maxOf(windowSize, windowRules.maxOfOrNull { it.second } ?: 0)

    private val ignoredLiterals: Set<String> =
        ignoredWords.filterNot { it.hasRegexMeta() }.map { it.lowercase() }.toSet()

    private val ignoredPatterns: List<Regex> =
        ignoredWords.filter { it.hasRegexMeta() }.mapNotNull {
            runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull()
        }

    /** True when [word] (already lower-cased) must be excluded from the analysis. */
    fun isIgnored(word: String): Boolean =
        word in ignoredLiterals || ignoredPatterns.any { it.matches(word) }

    private companion object {
        private const val NO_RULE = -1
        private val REGEX_META: Set<Char> = "\\[]{}()*+?.^\$|".toSet()
        private fun String.hasRegexMeta(): Boolean = any { it in REGEX_META }
    }
}

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

/**
 * Canonical composition (NFC): `o` followed by a combining grave accent (U+0300) becomes `ò`.
 * Text written or pasted on macOS may carry decomposed accents, which would never match the
 * precomposed forms of the lexicon, nor the same word typed with a precomposed letter.
 */
internal fun String.composed(): String =
    if (Normalizer.isNormalized(this, Normalizer.Form.NFC)) this else Normalizer.normalize(this, Normalizer.Form.NFC)

/**
 * Detects "nearby echoes": words repeated within a sliding window of the last
 * N words. Two words match when any of these holds (see [RepetitionAnalyzer.wordsMatch]):
 *  - they have the same Snowball Italian stem, unless [AnalyzerParams.useStemmer] is off;
 *  - the lexicon gives them a lemma in common;
 *  - they share at least [AnalyzerParams.minCommonPrefix] leading letters.
 *
 * The relation is not transitive (`parte` ~ `partire` ~ `partita`), so groups are
 * the connected components of the pairs actually found in the text: two variants
 * end up in the same table row when a chain of real echoes links them.
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

    private data class Occurrence(
        val word: String,
        val sentenceId: Int,
        val offset: Int,
        val length: Int,      // in the text, which may differ from word.length (see composed)
        val wordIndex: Int,
    )

    /** What is known about a marked occurrence, from all the pairs it took part in. */
    private class Mark(var minGap: Int, var window: Int, var sameSentence: Boolean)

    // italianStemmer keeps mutable state -> one instance per thread.
    private val stemmer = ThreadLocal.withInitial { italianStemmer() }
    private val stemCache = ConcurrentHashMap<String, String>()

    fun analyze(text: String, params: AnalyzerParams): List<Echo> {
        val masked = maskLatex(text, params) // same length as text, so offsets stay valid

        val window = ArrayDeque<Occurrence>()
        val marks = HashMap<Occurrence, Mark>()
        val groups = WordGroups()
        var sentenceId = 0
        var wordCounter = 0   // counts only accepted words, like the sliding window does

        for (sentenceMatch in SENTENCE_PATTERN.findAll(masked)) {
            val sentenceStart = sentenceMatch.range.first
            // Apostrophes -> spaces so "l'aereo" splits into "l" + "aereo".
            val tokenizable = sentenceMatch.value.replace('\'', ' ').replace('\u2019', ' ')

            for (tokenMatch in TOKEN_PATTERN.findAll(tokenizable)) {
                val raw = tokenMatch.value
                val isCommand = raw.startsWith('\\')
                // Commands stay literal (backslash + original case); words are lowercased and composed.
                val word = if (isCommand) raw else raw.lowercase().composed()

                if (!isCommand &&
                    (word.length < params.minWordLength || params.isIgnored(word))
                ) continue

                val current = Occurrence(
                    word, sentenceId, sentenceStart + tokenMatch.range.first, raw.length, wordCounter
                )
                wordCounter++

                for (prev in window) {
                    val gap = current.wordIndex - prev.wordIndex
                    val limit = params.pairWindow(prev.word, word)
                    if (gap > limit || !wordsMatch(prev.word, word, params)) continue
                    val sameSentence = prev.sentenceId == current.sentenceId
                    for (occ in listOf(prev, current)) {
                        val mark = marks.getOrPut(occ) { Mark(gap, limit, false) }
                        if (gap < mark.minGap) {
                            mark.minGap = gap
                            mark.window = limit
                        }
                        if (sameSentence) mark.sameSentence = true
                    }
                    groups.union(prev.word, word)
                }

                window.addLast(current)
                // Remember as far back as the largest window; each pair applies its own.
                if (window.size > params.maxWindow) window.removeFirst()
            }
            sentenceId++
        }

        return marks.map { (occ, mark) ->
            Echo(
                word = occ.word,
                startOffset = occ.offset,
                endOffset = occ.offset + occ.length,
                sameSentence = mark.sameSentence,
                wordIndex = occ.wordIndex,
                sentenceId = occ.sentenceId,
                minGap = mark.minGap.coerceIn(1, mark.window),
                group = groups.keyOf(occ.word),
                window = mark.window,
            )
        }.sortedBy { it.startOffset }
    }

    /**
     * Union-find over the words that echoed each other. The key of a group is the
     * smallest stem among its members: it does not depend on the order in which the
     * pairs were found, and a group made of one stem keeps that stem as its key, so
     * its colour is the same in every file.
     */
    private class WordGroups {
        private val parent = HashMap<String, String>()

        private fun find(word: String): String {
            var root = word
            while (true) root = parent[root]?.takeIf { it != root } ?: break
            var w = word
            while (w != root) { val next = parent.getValue(w); parent[w] = root; w = next }
            return root
        }

        fun union(w1: String, w2: String) {
            parent.putIfAbsent(w1, w1)
            parent.putIfAbsent(w2, w2)
            val r1 = find(w1)
            val r2 = find(w2)
            if (r1 != r2) parent[r1] = r2
        }

        private val keys: Map<String, String> by lazy {
            val members = parent.keys.groupBy { find(it) }
            buildMap {
                for ((_, words) in members) {
                    val key = words.minOf { stem(it) }
                    for (w in words) put(w, key)
                }
            }
        }

        /** Valid once all the unions are done. */
        fun keyOf(word: String): String = keys[word] ?: stem(word)
    }

// --- Previous rule: equal, or equal except the final vowel (same length) ---
// private fun wordsMatch(w1: String, w2: String): Boolean {
//     if (w1 == w2) return true
//     if (w1.startsWith('\\') || w2.startsWith('\\')) return false
//     if (w1.length != w2.length || w1.isEmpty()) return false
//     return w1.last() in EchoConfig.VOWELS &&
//         w2.last() in EchoConfig.VOWELS &&
//         w1.dropLast(1) == w2.dropLast(1)
// }

    /**
     * True when [w1] and [w2] (lower-cased words, or literal commands) belong together.
     * Commands only ever match themselves. Public so the rule can be tested directly.
     */
    fun wordsMatch(w1: String, w2: String, params: AnalyzerParams): Boolean {
        if (w1 == w2) return true
        if (w1.startsWith('\\') || w2.startsWith('\\')) return false // commands: exact only
        if (params.useStemmer && stem(w1) == stem(w2)) return true
        if (params.lexicon?.shareLemma(w1, w2) == true) return true
        return params.minCommonPrefix > 0 && w1.commonPrefixWith(w2).length >= params.minCommonPrefix
    }

// --- Previous stem: drop the final vowel ---
// fun stem(word: String): String {
//     if (word.startsWith('\\')) return word.lowercase()
//     val w = word.lowercase()
//     return if (w.isNotEmpty() && w.last() in EchoConfig.VOWELS) w.dropLast(1) else w
// }

    /** Snowball Italian stem; commands (\foo) stay literal and match only exactly. */
    fun stem(word: String): String {
        if (word.startsWith('\\')) return word
        return stemCache.computeIfAbsent(word.lowercase()) { key ->
            val s = stemmer.get()
            s.setCurrent(key)
            s.stem()
            s.getCurrent()
        }
    }

    /**
     * Returns a same-length copy of [text] with LaTeX commands handled by blanking
     * (never moving) characters, so offsets of the kept text are preserved:
     *  - IGNORED_COMMANDS with an argument: command + argument blanked;
     *  - other commands with an argument: wrapper blanked, first argument kept
     *    (and scanned recursively for nested commands);
     *  - commands without an argument: left literal (e.g. \Jacques).
     */
    private fun maskLatex(text: String, params: AnalyzerParams): String {
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

                    val target = params.textArgument[name] ?: 1
                    val ignored = name in params.ignoredCommands
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