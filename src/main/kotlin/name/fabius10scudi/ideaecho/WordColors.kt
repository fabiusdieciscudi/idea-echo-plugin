package name.fabius10scudi.ideaecho

import com.intellij.ui.JBColor
import java.awt.Color
import java.util.zip.CRC32

/**
 * Red is reserved for repetitions within the same sentence. Cross-sentence
 * echoes get a stable palette color derived from the word stem (word minus its
 * final char) via CRC32, so e.g. "aereo" and "aerei" always share a color.
 */
object WordColors {

    val RED: JBColor = hsb(0, 0.40f, 0.97f)

    // 12 hues, red band excluded.
    private val PALETTE: List<JBColor> =
        listOf(40, 60, 90, 120, 150, 170, 190, 210, 240, 270, 300, 330).map { hsb(it) }

    // Saturated, readable variants for use as foreground (text) color.
    private val TEXT_PALETTE: List<JBColor> =
        listOf(40, 60, 90, 120, 150, 170, 190, 210, 240, 270, 300, 330).map { textHsb(it) }

    fun colorFor(word: String, sameSentence: Boolean): JBColor =
        if (sameSentence) RED else PALETTE[stemHash(word).mod(PALETTE.size)]

    val TEXT_RED: JBColor = textHsb(0)

    fun textColorFor(word: String, sameSentence: Boolean): JBColor =
        if (sameSentence) TEXT_RED else TEXT_PALETTE[stemHash(word).mod(TEXT_PALETTE.size)]

    private fun textHsb(hueDeg: Int): JBColor {
        val hue = hueDeg / 360f
        val light = Color.getHSBColor(hue, 0.85f, 0.55f) // dark enough to read on a light background
        val dark = Color.getHSBColor(hue, 0.65f, 0.90f)  // bright enough to read on a dark background
        return JBColor(light, dark)
    }

// Previous: hash the word minus its final char.
// private fun stemHash(word: String): Int {
//     val stem = word.dropLast(1).lowercase()
//     return CRC32().apply { update(stem.toByteArray(Charsets.UTF_8)) }.value.toInt()
// }

    // Base the color on the Snowball stem so all variants of a group share a color.
    private fun stemHash(word: String): Int {
        val stem = RepetitionAnalyzer.stem(word)
        return CRC32().apply { update(stem.toByteArray(Charsets.UTF_8)) }.value.toInt()
    }

    private fun hsb(hueDeg: Int, sat: Float = 0.30f, bri: Float = 0.98f): JBColor {
        val hue = hueDeg / 360f
        val light = Color.getHSBColor(hue, sat, bri)
        val dark = Color.getHSBColor(hue, (sat + 0.25f).coerceAtMost(1f), 0.42f)
        return JBColor(light, dark)
    }
}
