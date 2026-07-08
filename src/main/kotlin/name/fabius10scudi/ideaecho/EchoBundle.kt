package name.fabius10scudi.ideaecho

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

/**
 * Message bundle for internationalization (English by default).
 */
object EchoBundle : DynamicBundle("messages.EchoBundle") {

    fun message(@PropertyKey(resourceBundle = "messages.EchoBundle") key: String, vararg params: Any): String {
        return getMessage(key, *params)
    }
}
