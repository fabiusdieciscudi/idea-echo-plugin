package name.fabius10scudi.ideaecho

import com.intellij.openapi.diagnostic.thisLogger
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path

/** The full set of user-configurable analysis values, independent of where they are stored. */
data class EchoData(
    val minWordLength: Int,
    val windowSize: Int,
    val ignoredCommands: List<String>,
    val ignoredWords: List<String>,
    val textArgument: Map<String, Int>,
)

/**
 * Reads and writes [EchoData] as YAML, using the SnakeYAML implementation that
 * ships with the IntelliJ platform (declared compileOnly, never bundled).
 */
object EchoExternalConfig {

    private const val KEY_MIN_WORD_LENGTH = "minWordLength"
    private const val KEY_WINDOW_SIZE = "windowSize"
    private const val KEY_IGNORED_COMMANDS = "ignoredCommands"
    private const val KEY_IGNORED_WORDS = "ignoredWords"
    private const val KEY_TEXT_ARGUMENT = "textArgument"

    private fun yaml(): Yaml {
        val options = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = true
            indent = 2
        }
        return Yaml(options)
    }

    /** Returns null when the file is missing, unreadable, or not a YAML mapping. */
    fun load(path: Path): EchoData? {
        if (!Files.isRegularFile(path)) return null
        return try {
            val root = Files.newBufferedReader(path).use { yaml().load<Any?>(it) }
            if (root !is Map<*, *>) {
                thisLogger().warn("Echo config is not a YAML mapping: $path")
                return null
            }
            EchoData(
                minWordLength = (root[KEY_MIN_WORD_LENGTH] as? Number)?.toInt()
                    ?: EchoSettings.DEFAULT_MIN_WORD_LENGTH,
                windowSize = (root[KEY_WINDOW_SIZE] as? Number)?.toInt()
                    ?: EchoSettings.DEFAULT_WINDOW_SIZE,
                ignoredCommands = stringList(root[KEY_IGNORED_COMMANDS], EchoConfig.DEFAULT_IGNORED_COMMANDS),
                ignoredWords = stringList(root[KEY_IGNORED_WORDS], EchoConfig.DEFAULT_IGNORED),
                textArgument = intMap(root[KEY_TEXT_ARGUMENT], EchoConfig.DEFAULT_TEXT_ARGUMENT),
            )
        } catch (t: Throwable) {
            thisLogger().warn("Cannot read Echo config: $path", t)
            null
        }
    }

    /** Writes [data] to [path], creating parent directories. Returns false on failure. */
    fun save(path: Path, data: EchoData): Boolean = try {
        path.parent?.let { Files.createDirectories(it) }
        val root = LinkedHashMap<String, Any>()
        root[KEY_MIN_WORD_LENGTH] = data.minWordLength
        root[KEY_WINDOW_SIZE] = data.windowSize
        root[KEY_IGNORED_COMMANDS] = data.ignoredCommands
        root[KEY_IGNORED_WORDS] = data.ignoredWords
        root[KEY_TEXT_ARGUMENT] = LinkedHashMap(data.textArgument)
        Files.newBufferedWriter(path).use { yaml().dump(root, it) }
        true
    } catch (t: Throwable) {
        thisLogger().warn("Cannot write Echo config: $path", t)
        false
    }

    private fun stringList(value: Any?, fallback: List<String>): List<String> {
        val list = (value as? List<*>)?.mapNotNull { it?.toString()?.trim() }?.filter { it.isNotEmpty() }
        return if (list.isNullOrEmpty()) fallback else list
    }

    private fun intMap(value: Any?, fallback: Map<String, Int>): Map<String, Int> {
        val map = (value as? Map<*, *>)?.mapNotNull { (k, v) ->
            val name = k?.toString()?.trim().orEmpty()
            val index = (v as? Number)?.toInt()
            if (name.isNotEmpty() && index != null && index >= 1) name to index else null
        }?.toMap()
        return if (map.isNullOrEmpty()) fallback else map
    }
}
