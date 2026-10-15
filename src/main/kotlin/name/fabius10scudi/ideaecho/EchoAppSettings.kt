package name.fabius10scudi.ideaecho

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.ProjectManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean

/**
 * IDE-wide preferences, shared by every project.
 *
 * Holds the locations of the Morph-it! lexicon and of the local thesauri: the one from
 * LibreOffice and a personal one, with the user's own substitutions. The files are
 * downloaded by the user and are not part of a manuscript, so they live here rather than
 * in the per-project settings or in the external YAML file. Light service: no plugin.xml
 * registration needed.
 */
@Service(Service.Level.APP)
@State(name = "EchoAppSettings", storages = [Storage("ideaEcho.xml")])
class EchoAppSettings : PersistentStateComponent<EchoAppSettings.State> {

    data class State(
        var lexiconPath: String = "",
        var thesaurusPath: String = "",
        var personalThesaurusPath: String = "",
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(loaded: State) {
        state = loaded
    }

    /** Path of the lexicon file; blank when no lexicon is used. */
    var lexiconPath: String
        get() = state.lexiconPath
        set(value) { state.lexiconPath = value.trim() }

    /** Path of the MyThes thesaurus file; blank when there is no local thesaurus. */
    var thesaurusPath: String
        get() = state.thesaurusPath
        set(value) { state.thesaurusPath = value.trim() }

    /** Path of the personal MyThes file; blank when there is none. */
    var personalThesaurusPath: String
        get() = state.personalThesaurusPath
        set(value) { state.personalThesaurusPath = value.trim() }

    private val lexicon = BackgroundFile("lexicon", "forms") { path ->
        MorphLexicon.load(path).let { it to it.size }
    }

    private val thesaurus = BackgroundFile("thesaurus", "headwords") { path ->
        LocalThesaurus.load(path).let { it to it.size }
    }

    private val personalThesaurus = BackgroundFile("personal thesaurus", "headwords") { path ->
        LocalThesaurus.load(path).let { it to it.size }
    }

    /** The lexicon when it is in memory, else null (see [BackgroundFile.ifReady]). */
    fun lexiconIfReady(): MorphLexicon? = lexicon.ifReady(lexiconPath)

    /** True when at least one local thesaurus file is configured. */
    fun hasLocalThesaurus(): Boolean = thesaurusPath.isNotBlank() || personalThesaurusPath.isNotBlank()

    /**
     * The local thesauri in memory, the personal one first, so that its entries come
     * first (see [BackgroundFile.ifReady]); those still loading are left out.
     */
    fun thesauriIfReady(): List<LocalThesaurus> = listOfNotNull(
        personalThesaurus.ifReady(personalThesaurusPath),
        thesaurus.ifReady(thesaurusPath),
    )

    /** True while a configured thesaurus file is not read yet. */
    fun thesaurusLoading(): Boolean =
        personalThesaurus.isLoading(personalThesaurusPath) || thesaurus.isLoading(thesaurusPath)

    /**
     * A user-supplied data file, read on a pooled thread and kept in memory until the
     * path or the file's modification time changes.
     */
    private class BackgroundFile<T : Any>(
        private val what: String,
        private val unit: String,
        private val read: (Path) -> Pair<T, Int>,
    ) {
        private data class Loaded<T>(val path: Path, val stamp: Long, val value: T?)

        @Volatile
        private var loaded: Loaded<T>? = null
        private val loading = AtomicBoolean(false)

        private fun resolve(pathText: String): Pair<Path, Long>? {
            val path = pathText.takeIf { it.isNotBlank() }
                ?.let { runCatching { Paths.get(it) }.getOrNull() } ?: return null
            val stamp = runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)
            return path to stamp
        }

        private fun current(path: Path, stamp: Long): Loaded<T>? =
            loaded?.takeIf { it.path == path && it.stamp == stamp }

        /** True when [pathText] names a file whose current version is not read yet. */
        fun isLoading(pathText: String): Boolean {
            val (path, stamp) = resolve(pathText) ?: return false
            return current(path, stamp) == null
        }

        /**
         * The value, when the file is already in memory. Otherwise starts reading it on a
         * pooled thread and returns null: this is called from the UI thread. When the
         * reading ends every open project is notified, so it is analyzed again. A file
         * that cannot be read is logged and treated as absent until it changes.
         */
        fun ifReady(pathText: String): T? {
            val (path, stamp) = resolve(pathText) ?: return null
            current(path, stamp)?.let { return it.value }

            if (loading.compareAndSet(false, true)) {
                ApplicationManager.getApplication().executeOnPooledThread(Runnable {
                    try {
                        val value = try {
                            read(path).let { (value, size) ->
                                thisLogger().info("Idea Echo: $size $unit read from the $what $path")
                                value
                            }
                        } catch (e: Exception) {
                            thisLogger().warn("Idea Echo: cannot read the $what $path", e)
                            null
                        }
                        loaded = Loaded(path, stamp, value)
                    } finally {
                        loading.set(false)
                    }
                    ApplicationManager.getApplication().invokeLater {
                        for (project in ProjectManager.getInstance().openProjects) {
                            if (!project.isDisposed) EchoSettingsNotifier.settingsChanged(project)
                        }
                    }
                })
            }
            return null
        }
    }

    companion object {
        fun getInstance(): EchoAppSettings =
            ApplicationManager.getApplication().getService(EchoAppSettings::class.java)
    }
}
