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
import javax.swing.text.html.FormSubmitEvent
import javax.swing.text.html.HTMLEditorKit

/**
 * Vista HTML del dizionario dei sinonimi/contrari.
 * Usa JCEF (rendering HTML completo) se disponibile, altrimenti un fallback Swing.
 *
 * Pages built locally (see [ThesaurusHtml]) have a search box and clickable synonyms: a
 * search is reported to [onSearch], a click to [onPick], on the UI thread, from either
 * renderer.
 */
class ThesaurusView(parent: Disposable) : Disposable {

    private val browser: JBCefBrowser? =
        if (JBCefApp.isSupported()) JBCefBrowser() else null

    // Created before the browser loads anything, as JBCefJSQuery requires.
    private val sendQuery: JBCefJSQuery? = browser?.let { b ->
        JBCefJSQuery.create(b as JBCefBrowserBase).also { query ->
            query.addHandler { message ->
                ApplicationManager.getApplication().invokeLater { dispatch(message) }
                null
            }
        }
    }

    private val fallback: JEditorPane by lazy {
        JEditorPane("text/html", "").apply {
            isEditable = false
            border = JBUI.Borders.empty(8)
            // Report form submissions as events instead of loading the action URL.
            (editorKit as? HTMLEditorKit)?.isAutoFormSubmission = false
            addHyperlinkListener { e ->
                if (e is FormSubmitEvent) {
                    ThesaurusHtml.searchedWordFromForm(e.data)?.let { onSearch?.invoke(it) }
                } else if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    dispatch(e.description)
                }
            }
        }
    }

    val component: JComponent = browser?.component ?: JScrollPane(fallback)

    /** Called with the synonym the user clicked, on the UI thread. */
    var onPick: ((String) -> Unit)? = null

    /** Called with the word searched in the page's search box, on the UI thread. */
    var onSearch: ((String) -> Unit)? = null

    /**
     * The body of the JavaScript `send(m)` function for pages shown with [showHtml], or
     * null in the Swing fallback, where links and the form are reported without scripts.
     */
    val sendScript: String? get() = sendQuery?.inject("m")

    init {
        Disposer.register(parent, this)
        sendQuery?.let { Disposer.register(this, it) }
    }

    private fun dispatch(message: String?) {
        ThesaurusHtml.pickedWord(message)?.let { onPick?.invoke(it); return }
        ThesaurusHtml.searchedWord(message)?.let { onSearch?.invoke(it) }
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
