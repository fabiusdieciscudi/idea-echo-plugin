package name.fabius10scudi.ideaecho

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JScrollPane
import javax.swing.event.HyperlinkEvent

/**
 * Vista HTML del dizionario dei sinonimi/contrari.
 * Usa JCEF (rendering HTML completo) se disponibile, altrimenti un fallback Swing.
 *
 * Pages built locally (see [ThesaurusHtml]) may contain clickable synonyms: a click is
 * reported to [onPick] on the UI thread, from either renderer.
 */
class ThesaurusView(parent: Disposable) : Disposable {

    private val browser: JBCefBrowser? =
        if (JBCefApp.isSupported()) JBCefBrowser() else null

    // Created before the browser loads anything, as JBCefJSQuery requires.
    private val pickQuery: JBCefJSQuery? = browser?.let { b ->
        JBCefJSQuery.create(b as JBCefBrowserBase).also { query ->
            query.addHandler { word ->
                ApplicationManager.getApplication().invokeLater { onPick?.invoke(word) }
                null
            }
        }
    }

    private val fallback: JEditorPane by lazy {
        JEditorPane("text/html", "").apply {
            isEditable = false
            border = JBUI.Borders.empty(8)
            addHyperlinkListener { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    ThesaurusHtml.pickedWord(e.description)?.let { onPick?.invoke(it) }
                }
            }
        }
    }

    val component: JComponent = browser?.component ?: JScrollPane(fallback)

    /** Called with the synonym the user clicked, on the UI thread. */
    var onPick: ((String) -> Unit)? = null

    /**
     * The body of the JavaScript `pick(w)` function for pages shown with [showHtml], or
     * null in the Swing fallback, where links are reported through their `href` instead.
     */
    val pickScript: String? get() = pickQuery?.inject("w")

    init {
        Disposer.register(parent, this)
        pickQuery?.let { Disposer.register(this, it) }
    }

    /** Shows a page built locally, e.g. the entries of the local thesaurus. */
    fun showHtml(html: String) {
        val b = browser
        if (b != null) b.loadHTML(html) else fallback.text = html
    }

    fun showUrl(word: String, url: String) {
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
