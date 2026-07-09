package name.fabius10scudi.ideaecho

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Persistent, per-project settings.
 *
 * Values can live either in the IDE project store (ideaEcho.xml) or in an external
 * YAML file inside the project directory. The public properties always read and
 * write the *effective* configuration, so callers never care which one is active.
 * Turning the external option off restores the untouched internal values.
 */
@Service(Service.Level.PROJECT)
@State(name = "EchoSettings", storages = [Storage("ideaEcho.xml")])
class EchoSettings(private val project: Project) : PersistentStateComponent<EchoSettings.State> {

    data class State(
        var minWordLength: Int = DEFAULT_MIN_WORD_LENGTH,
        var windowSize: Int = DEFAULT_WINDOW_SIZE,
        var thesaurusIndex: Int = 0,
        var ignoredCommands: MutableList<String> = EchoConfig.DEFAULT_IGNORED_COMMANDS.toMutableList(),
        var textArgument: MutableMap<String, Int> = EchoConfig.DEFAULT_TEXT_ARGUMENT.toMutableMap(),
        var ignoredWords: MutableList<String> = EchoConfig.DEFAULT_IGNORED.toMutableList(),
        var useExternalConfig: Boolean = false,
        var externalConfigPath: String = "",
    )

    private var state = State()

    /** Cached parse of the external file, invalidated by its modification time. */
    private var cachedData: EchoData? = null
    private var cachedStamp: Long = -1

    override fun getState(): State = state

    override fun loadState(loaded: State) {
        state = loaded.also { it.clamp() }
        invalidateCache()
    }

    // ---- external file -----------------------------------------------------

    var useExternalConfig: Boolean
        get() = state.useExternalConfig
        set(value) { state.useExternalConfig = value; invalidateCache() }

    var externalConfigPath: String
        get() = state.externalConfigPath
        set(value) { state.externalConfigPath = value.trim(); invalidateCache() }

    fun invalidateCache() { cachedData = null; cachedStamp = -1 }

    /** Absolute path of the external file, or null when unset. */
    fun externalPath(): Path? =
        externalConfigPath.takeIf { it.isNotBlank() }?.let { Paths.get(it) }

    /** True when [path] is inside the project directory (required for external configs). */
    fun isInsideProject(path: Path): Boolean {
        val base = project.basePath?.let { Paths.get(it) } ?: return false
        return path.toAbsolutePath().normalize().startsWith(base.toAbsolutePath().normalize())
    }

    /** Whether the external file is actually usable right now. */
    private fun externalActive(): Boolean {
        val p = externalPath() ?: return false
        return state.useExternalConfig && isInsideProject(p)
    }

    // ---- effective configuration -------------------------------------------

    /** Values currently in force: from the YAML file when enabled, else internal. */
    fun effective(): EchoData {
        if (!externalActive()) return internalData()
        val path = externalPath() ?: return internalData()
        val stamp = runCatching { path.toFile().lastModified() }.getOrDefault(0L)
        cachedData?.let { if (stamp == cachedStamp) return it }
        val loaded = EchoExternalConfig.load(path) ?: return internalData()
        cachedData = loaded
        cachedStamp = stamp
        return loaded
    }

    /** Writes [data] where the effective configuration lives. */
    fun updateEffective(data: EchoData) {
        val clean = data.sanitized()
        if (externalActive()) {
            externalPath()?.let { EchoExternalConfig.save(it, clean) }
            invalidateCache()
        } else {
            applyInternal(clean)
        }
    }

    fun internalData(): EchoData = EchoData(
        minWordLength = state.minWordLength,
        windowSize = state.windowSize,
        ignoredCommands = state.ignoredCommands,
        ignoredWords = state.ignoredWords,
        textArgument = state.textArgument,
    )

    /** Copies [data] into the internal project store (used by the Import button). */
    fun applyInternal(data: EchoData) {
        val clean = data.sanitized()
        state.minWordLength = clean.minWordLength
        state.windowSize = clean.windowSize
        state.ignoredCommands = clean.ignoredCommands.toMutableList()
        state.ignoredWords = clean.ignoredWords.toMutableList()
        state.textArgument = clean.textArgument.toMutableMap()
    }

    private fun EchoData.sanitized() = EchoData(
        minWordLength = minWordLength.coerceIn(MIN_WORD_LENGTH_MIN, MIN_WORD_LENGTH_MAX),
        windowSize = windowSize.coerceIn(WINDOW_MIN, WINDOW_MAX),
        ignoredCommands = ignoredCommands.map { it.trim() }.filter { it.isNotEmpty() },
        ignoredWords = ignoredWords.map { it.trim() }.filter { it.isNotEmpty() },
        textArgument = textArgument.filter { it.key.isNotBlank() && it.value >= 1 },
    )

    // ---- convenience accessors (always effective) ---------------------------

    var minWordLength: Int
        get() = effective().minWordLength
        set(value) { updateEffective(effective().copy(minWordLength = value)) }

    var windowSize: Int
        get() = effective().windowSize
        set(value) { updateEffective(effective().copy(windowSize = value)) }

    var ignoredCommands: List<String>
        get() = effective().ignoredCommands
        set(value) { updateEffective(effective().copy(ignoredCommands = value)) }

    var textArgument: Map<String, Int>
        get() = effective().textArgument
        set(value) { updateEffective(effective().copy(textArgument = value)) }

    var ignoredWords: List<String>
        get() = effective().ignoredWords
        set(value) { updateEffective(effective().copy(ignoredWords = value)) }

    /** The thesaurus server always stays in the IDE project store. */
    var thesaurusIndex: Int
        get() = state.thesaurusIndex
        set(value) { state.thesaurusIndex = value.coerceIn(0, EchoConfig.THESAURUS_SERVERS.lastIndex) }

    /** Snapshot passed to the analyzer, so it never touches the settings service. */
    fun toParams(): AnalyzerParams {
        val data = effective()
        return AnalyzerParams(
            minWordLength = data.minWordLength,
            windowSize = data.windowSize,
            ignoredWords = data.ignoredWords.toSet(),
            ignoredCommands = data.ignoredCommands.toSet(),
            textArgument = data.textArgument,
        )
    }

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

        const val DEFAULT_FILE_NAME = "ideaEcho.yaml"

        fun getInstance(project: Project): EchoSettings =
            project.getService(EchoSettings::class.java)
    }
}
