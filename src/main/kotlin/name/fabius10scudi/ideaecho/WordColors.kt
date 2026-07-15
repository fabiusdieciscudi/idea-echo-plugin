package name.fabius10scudi.ideaecho

import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.util.zip.CRC32

/**
 * Red is reserved for repetitions within the same sentence. Cross-sentence echoes
 * get a stable palette colour derived from the Snowball stem via CRC32, so all the
 * variants of a group always share a hue.
 *
 * The light/dark variant is chosen from the *actual* background the colour will be
 * painted on, not from the IDE theme nor from the colour scheme: another plugin may
 * repaint the editor background independently, so the caller passes the real colour.
 *
 * Editor backgrounds also fade with distance: an echo one word away from its nearest
 * twin is painted at full strength, one a whole window away at MIN_ALPHA. The fade is
 * pre-composited onto the editor background rather than left as an alpha channel, so
 * the result does not depend on how the editor blends translucent colours.
 */
object WordColors {

    // 12 hues, red band excluded.
    private val HUES = listOf(40, 60, 90, 120, 150, 170, 190, 210, 240, 270, 300, 330)

    // ---- editor: word background -------------------------------------------

    /**
     * Background for one echo occurrence.
     *
     * @param editorBackground the colour the editor really paints (see EchoHighlightState).
     * @param minGap distance in words to the nearest occurrence of the same group.
     * @param windowSize the look-back window, i.e. the distance mapped to [MIN_ALPHA].
     */
    fun colorFor(
        word: String,
        sameSentence: Boolean,
        editorBackground: Color,
        minGap: Int,
        windowSize: Int,
    ): Color {
        val dark = isDark(editorBackground)
        val hue = if (sameSentence) 0 else HUES[stemHash(word).mod(HUES.size)]
        return blend(background(hue, dark), editorBackground, alphaFor(minGap, windowSize))
    }

    // ---- tool window table: word foreground ---------------------------------

    fun textColorFor(word: String, sameSentence: Boolean): Color {
        val dark = isDark(UIUtil.getTableBackground())
        if (sameSentence) return foreground(0, dark)
        return foreground(HUES[stemHash(word).mod(HUES.size)], dark)
    }

    // ---- distance fading -----------------------------------------------------

    /**
     * Linear: 1.0 at a gap of one word, down to [MIN_ALPHA] at a full window.
     * Public so callers can report the value they are getting (tooltip, log).
     */
    fun alphaFor(minGap: Int, windowSize: Int): Float {
        if (windowSize <= 1) return 1f
        val gap = minGap.coerceIn(1, windowSize)
        val t = (gap - 1).toFloat() / (windowSize - 1).toFloat()
        return 1f - (1f - MIN_ALPHA) * t
    }

    /** Paints [fg] over [bg] at [alpha], returning an opaque colour. */
    private fun blend(fg: Color, bg: Color, alpha: Float): Color = Color(
        Math.round(fg.red * alpha + bg.red * (1 - alpha)),
        Math.round(fg.green * alpha + bg.green * (1 - alpha)),
        Math.round(fg.blue * alpha + bg.blue * (1 - alpha)),
    )

    // ---- palettes ------------------------------------------------------------

    /** Pale wash on a light background, deep tint on a dark one. */
    private fun background(hueDeg: Int, dark: Boolean): Color =
        if (dark) Color.getHSBColor(hueDeg / 360f, DARK_SATURATION, DARK_BRIGHTNESS)
        else Color.getHSBColor(hueDeg / 360f, LIGHT_SATURATION, LIGHT_BRIGHTNESS)

    /** Readable as text: dark and saturated on light, bright on dark. */
    private fun foreground(hueDeg: Int, dark: Boolean): Color =
        if (dark) Color.getHSBColor(hueDeg / 360f, 0.65f, 0.90f)
        else Color.getHSBColor(hueDeg / 360f, 0.85f, 0.55f)

    // ---- background probing --------------------------------------------------

    /** Perceptual luminance against mid-grey. */
    private fun isDark(background: Color): Boolean {
        val luminance =
            (0.299 * background.red + 0.587 * background.green + 0.114 * background.blue) / 255.0
        return luminance < 0.5
    }

    private const val MIN_ALPHA = 0.3f

    /**
     * Full-strength echo background. It has to sit well clear of the editor background,
     * because the distance fade blends it towards that colour: the gap between the two
     * is the whole budget the fade can spend. The previous pale wash (saturation 0.30 on
     * white) left alpha 0.98 and 0.60 just 30 levels of red and 2 of blue apart, so the
     * fade was computed correctly but could not be seen. Lower these to tone the
     * highlighting down, at the cost of making the fade harder to read.
     */
    private const val LIGHT_SATURATION = 0.70f
    private const val LIGHT_BRIGHTNESS = 0.98f
    private const val DARK_SATURATION = 0.60f
    private const val DARK_BRIGHTNESS = 0.75f

    private fun stemHash(word: String): Int {
        val stem = RepetitionAnalyzer.stem(word)
        return CRC32().apply { update(stem.toByteArray(Charsets.UTF_8)) }.value.toInt()
    }
}