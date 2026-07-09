package name.fabius10scudi.ideaecho

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.util.zip.CRC32

/**
 * Red is reserved for repetitions within the same sentence. Cross-sentence echoes
 * get a stable palette colour derived from the Snowball stem via CRC32, so all the
 * variants of a group always share a hue.
 *
 * The light/dark variant is chosen from the *actual* background the colour will be
 * painted on (editor scheme, or table background), not from the IDE theme: a light
 * colour scheme under a dark IDE theme must still get light highlights.
 */
object WordColors {

    // 12 hues, red band excluded.
    private val HUES = listOf(40, 60, 90, 120, 150, 170, 190, 210, 240, 270, 300, 330)

    // ---- editor: word background -------------------------------------------

    fun colorFor(word: String, sameSentence: Boolean): Color {
        val dark = isDark(editorBackground())
        if (sameSentence) return background(0, dark)
        return background(HUES[stemHash(word).mod(HUES.size)], dark)
    }

    // ---- tool window table: word foreground ---------------------------------

    fun textColorFor(word: String, sameSentence: Boolean): Color {
        val dark = isDark(UIUtil.getTableBackground())
        if (sameSentence) return foreground(0, dark)
        return foreground(HUES[stemHash(word).mod(HUES.size)], dark)
    }

    // ---- palettes ------------------------------------------------------------

    /** Pale wash on a light background, deep tint on a dark one. */
    private fun background(hueDeg: Int, dark: Boolean): Color =
        if (dark) Color.getHSBColor(hueDeg / 360f, 0.55f, 0.42f)
        else Color.getHSBColor(hueDeg / 360f, 0.30f, 0.98f)

    /** Readable as text: dark and saturated on light, bright on dark. */
    private fun foreground(hueDeg: Int, dark: Boolean): Color =
        if (dark) Color.getHSBColor(hueDeg / 360f, 0.65f, 0.90f)
        else Color.getHSBColor(hueDeg / 360f, 0.85f, 0.55f)

    // ---- background probing --------------------------------------------------

    private fun editorBackground(): Color =
        EditorColorsManager.getInstance().globalScheme.defaultBackground

    /** Perceptual luminance against mid-grey. */
    private fun isDark(background: Color): Boolean {
        val luminance =
            (0.299 * background.red + 0.587 * background.green + 0.114 * background.blue) / 255.0
        return luminance < 0.5
    }

    private fun stemHash(word: String): Int {
        val stem = RepetitionAnalyzer.stem(word)
        return CRC32().apply { update(stem.toByteArray(Charsets.UTF_8)) }.value.toInt()
    }
}