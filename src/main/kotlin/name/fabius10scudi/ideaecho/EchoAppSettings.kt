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
 * Holds the location of the Morph-it! lexicon. The file is downloaded by the user and
 * is not part of a manuscript, so it lives here rather than in the per-project settings
 * or in the external YAML file. Light service: no plugin.xml registration needed.
 */
@Service(Service.Level.APP)
@State(name = "EchoAppSettings", storages = [Storage("ideaEcho.xml")])
class EchoAppSettings : PersistentStateComponent<EchoAppSettings.State> {

    data class State(
        var lexiconPath: String = "",
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

    /** The loaded lexicon and the file version it was read from. */
    private data class Loaded(val path: Path, val stamp: Long, val lexicon: MorphLexicon?)

    @Volatile
    private var loaded: Loaded? = null
    private val loading = AtomicBoolean(false)

    /**
     * The lexicon, when it is already in memory. Otherwise starts reading it on a pooled
     * thread and returns null: reading half a million lines takes a moment, and this is
     * called from the UI thread. When the reading ends every open project is re-analyzed.
     * A file that cannot be read is logged and treated as no lexicon until it changes.
     */
    fun lexiconIfReady(): MorphLexicon? {
        val path = lexiconPath.takeIf { it.isNotBlank() }
            ?.let { runCatching { Paths.get(it) }.getOrNull() } ?: return null
        val stamp = runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)
        loaded?.let { if (it.path == path && it.stamp == stamp) return it.lexicon }

        if (loading.compareAndSet(false, true)) {
            ApplicationManager.getApplication().executeOnPooledThread(Runnable {
                try {
                    val lexicon = try {
                        MorphLexicon.load(path).also {
                            thisLogger().info("Idea Echo: ${it.size} forms read from $path")
                        }
                    } catch (e: Exception) {
                        thisLogger().warn("Idea Echo: cannot read the lexicon $path", e)
                        null
                    }
                    loaded = Loaded(path, stamp, lexicon)
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

    companion object {
        fun getInstance(): EchoAppSettings =
            ApplicationManager.getApplication().getService(EchoAppSettings::class.java)
    }
}
