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

    /**
     * Serialized state. Collections must be mutable and of serializable types
     * (List<String>, Map<String, Int>) for the IDE XML serializer.
     * Null/empty on first run -> defaults from EchoConfig are used.
     */
    data class State(
        var minWordLength: Int = DEFAULT_MIN_WORD_LENGTH,
        var windowSize: Int = DEFAULT_WINDOW_SIZE,
        var thesaurusIndex: Int = 0,
        var ignoredCommands: MutableList<String> = EchoConfig.DEFAULT_IGNORED_COMMANDS.toMutableList(),
        var textArgument: MutableMap<String, Int> = EchoConfig.DEFAULT_TEXT_ARGUMENT.toMutableMap(),
        var ignoredWords: MutableList<String> = EchoConfig.DEFAULT_IGNORED.toMutableList(),
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

    var thesaurusIndex: Int
        get() = state.thesaurusIndex
        set(value) { state.thesaurusIndex = value.coerceIn(0, EchoConfig.THESAURUS_SERVERS.lastIndex) }

    /** LaTeX commands dropped together with their content. */
    var ignoredCommands: List<String>
        get() = state.ignoredCommands
        set(value) { state.ignoredCommands = value.map { it.trim() }.filter { it.isNotEmpty() }.toMutableList() }

    /** Command name -> 1-based index of the argument holding the prose. */
    var textArgument: Map<String, Int>
        get() = state.textArgument
        set(value) { state.textArgument = value.toMutableMap() }

    /** Function words excluded from the analysis. */
    var ignoredWords: List<String>
        get() = state.ignoredWords
        set(value) { state.ignoredWords = value.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toMutableList() }

    /** Snapshot passed to the analyzer, so it never touches the settings service. */
    fun toParams(): AnalyzerParams = AnalyzerParams(
        minWordLength = minWordLength,
        windowSize = windowSize,
        ignoredWords = ignoredWords.toSet(),
        ignoredCommands = ignoredCommands.toSet(),
        textArgument = textArgument.toMap(),
    )

    private fun State.clamp() {
        minWordLength = minWordLength.coerceIn(MIN_WORD_LENGTH_MIN, MIN_WORD_LENGTH_MAX)
        windowSize = windowSize.coerceIn(WINDOW_MIN, WINDOW_MAX)
        thesaurusIndex = thesaurusIndex.coerceIn(0, EchoConfig.THESAURUS_SERVERS.lastIndex)
        // A missing/empty section in the XML means "never configured": fall back to defaults.
        if (ignoredCommands.isEmpty()) ignoredCommands = EchoConfig.DEFAULT_IGNORED_COMMANDS.toMutableList()
        if (textArgument.isEmpty()) textArgument = EchoConfig.DEFAULT_TEXT_ARGUMENT.toMutableMap()
        if (ignoredWords.isEmpty()) ignoredWords = EchoConfig.DEFAULT_IGNORED.toMutableList()
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
