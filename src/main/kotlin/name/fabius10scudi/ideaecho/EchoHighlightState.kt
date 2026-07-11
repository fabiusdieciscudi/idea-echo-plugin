package name.fabius10scudi.ideaecho

import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import java.awt.Color

/**
 * Per-project state shared between the tool window and the annotator.
 *
 * Holds the highlighting on/off flag and the background colour actually painted by
 * the editor: another plugin may repaint it independently of the colour scheme, so
 * the scheme's declared background is not reliable. The tool window captures the
 * real colour on the EDT; the annotator reads it from a background thread, hence
 * the @Volatile fields. Light service: no plugin.xml registration needed.
 */
@Service(Service.Level.PROJECT)
class EchoHighlightState {
    @Volatile
    private var enabled = false

    @Volatile
    private var editorBackground: Color? = null

    fun isEnabled(): Boolean = enabled
    fun setEnabled(value: Boolean) { enabled = value }

    /** Called on the EDT with editor.contentComponent.background (null when no editor). */
    fun setEditorBackground(value: Color?) { editorBackground = value }

    /** The real painted background, falling back to the colour scheme. */
    fun editorBackground(): Color =
        editorBackground ?: EditorColorsManager.getInstance().globalScheme.defaultBackground

    companion object {
        fun getInstance(project: Project): EchoHighlightState =
            project.getService(EchoHighlightState::class.java)
    }
}
