package name.fabius10scudi.ideaecho

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/** Persistent, per-project settings. Single source of truth for the analyzer. */
@Service(Service.Level.PROJECT)
@State(name = "EchoSettings", storages = [Storage("ideaEcho.xml")])
class EchoSettings : PersistentStateComponent<EchoSettings.State> {

    data class State(
        var minWordLength: Int = DEFAULT_MIN_WORD_LENGTH,
        var windowSize: Int = DEFAULT_WINDOW_SIZE,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(loaded: State) {
        state = loaded.also { it.clamp() }
    }

    var minWordLength: Int
        get() = state.minWordLength
        set(value) { state.minWordLength = value.coerceIn(MIN_WORD_LENGTH_MIN, MIN_WORD_LENGTH_MAX) }

    var windowSize: Int
        get() = state.windowSize
        set(value) { state.windowSize = value.coerceIn(WINDOW_MIN, WINDOW_MAX) }

    private fun State.clamp() {
        minWordLength = minWordLength.coerceIn(MIN_WORD_LENGTH_MIN, MIN_WORD_LENGTH_MAX)
        windowSize = windowSize.coerceIn(WINDOW_MIN, WINDOW_MAX)
    }

    companion object {
        const val MIN_WORD_LENGTH_MIN = 2
        const val MIN_WORD_LENGTH_MAX = 4
        const val DEFAULT_MIN_WORD_LENGTH = 4

        const val WINDOW_MIN = 20
        const val WINDOW_MAX = 500
        const val WINDOW_STEP = 20

        // NB: 150 is not a multiple of WINDOW_STEP, so the default is 160.
        const val DEFAULT_WINDOW_SIZE = 160

        fun getInstance(project: Project): EchoSettings =
            project.getService(EchoSettings::class.java)
    }
}
