package name.fabius10scudi.ideaecho

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBUI
import java.net.URLEncoder
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JScrollPane

/**
 * Vista HTML del dizionario dei sinonimi/contrari.
 * Usa JCEF (rendering HTML completo) se disponibile, altrimenti un fallback Swing.
 */
class ThesaurusView(parent: Disposable) : Disposable {

    private val browser: JBCefBrowser? =
        if (JBCefApp.isSupported()) JBCefBrowser() else null

    private val fallback: JEditorPane by lazy {
        JEditorPane("text/html", "").apply {
            isEditable = false
            border = JBUI.Borders.empty(8)
        }
    }

    val component: JComponent = browser?.component ?: JScrollPane(fallback)

    init {
        Disposer.register(parent, this)
    }

    fun showWord(word: String, urlTemplate: String) {
        val url = urlTemplate.format(URLEncoder.encode(word, Charsets.UTF_8))
        val b = browser
        if (b != null) {
            b.loadURL(url)
        } else {
            fallback.text = """
                <html><body style="font-family:sans-serif;padding:8px">
                <p>JCEF non disponibile in questa IDE.</p>
                <p>Sinonimi/contrari per <b>$word</b>:</p>
                <p><a href="$url">$url</a></p>
                </body></html>
            """.trimIndent()
        }
    }

    override fun dispose() {
        browser?.let { Disposer.dispose(it) }
    }
}
