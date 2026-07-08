package name.fabius10scudi.ideaecho

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * Per-project flag telling whether echo highlighting / background search is active.
 * Toggled by the tool window (visible = on), read by the annotator (on a background
 * thread, hence @Volatile). Light service: no plugin.xml registration needed.
 */
@Service(Service.Level.PROJECT)
class EchoHighlightState {
    @Volatile
    private var enabled = false

    fun isEnabled(): Boolean = enabled
    fun setEnabled(value: Boolean) { enabled = value }

    companion object {
        fun getInstance(project: Project): EchoHighlightState =
            project.getService(EchoHighlightState::class.java)
    }
}
