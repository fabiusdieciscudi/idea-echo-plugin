package name.fabius10scudi.ideaecho

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.hint.HintManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.Collator
import java.util.Locale
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SpinnerNumberModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

/** Tool window: grouped echo table on top, thesaurus view below. */
class EchoToolWindowPanel(private val project: Project) :
    JPanel(BorderLayout()), Disposable {

    /** One table row = all variants of one word group (e.g. "porta/portare"), see [Echo.group]. */
    private data class EchoRow(
        val group: String,        // the group key, which also picks the colour
        val display: String,      // variants joined alphabetically
        val count: Int,           // total occurrences across variants
        val firstOffset: Int,     // offset of the earliest occurrence
        val searchWord: String,   // first variant, passed to the REST dictionary
        val ranges: List<Pair<Int, Int>>, // (start, end) of every occurrence in the group
        val minWordGap: Int,      // minimum distance in accepted words
        val minSentenceGap: Int,  // minimum distance in sentences (0 = same sentence)
    )

    private val settings get() = EchoSettings.getInstance(project)

    private val tableModel = object : DefaultTableModel(
        arrayOf(
            EchoBundle.message("toolwindow.column.word"),
            EchoBundle.message("toolwindow.column.count"),
            EchoBundle.message("toolwindow.column.wordGap"),
            EchoBundle.message("toolwindow.column.sentenceGap"),
        ),
        0,
    ) {
        override fun isCellEditable(row: Int, column: Int) = false
        override fun getColumnClass(columnIndex: Int): Class<*> =
            if (columnIndex == 0) String::class.java else Integer::class.java
    }

    private val table = JBTable(tableModel).apply {
        setShowGrid(false)
        autoCreateRowSorter = true
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
    }

    private val minLengthCombo = ComboBox(
        (EchoSettings.MIN_WORD_LENGTH_MIN..EchoSettings.MIN_WORD_LENGTH_MAX).toList().toTypedArray()
    )
    private val windowSpinner = JSpinner(
        SpinnerNumberModel(
            EchoSettings.DEFAULT_WINDOW_SIZE,
            EchoSettings.WINDOW_MIN,
            EchoSettings.WINDOW_MAX,
            EchoSettings.WINDOW_STEP,
        )
    )
    private val thesaurusCombo = ComboBox(EchoConfig.THESAURUS_SOURCES.toTypedArray())
    private var syncingControls = false

    /** True while the table selection is being driven by the caret, not by the user. */
    private var syncingSelection = false

    private val thesaurus = ThesaurusView(this)
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val collator = Collator.getInstance(Locale.ITALIAN)

    /**
     * The group whose synonyms the thesaurus view shows: picking a synonym replaces an
     * occurrence of it. Also used to show the page again once the local file is read.
     */
    private var thesaurusGroup: String? = null

    /** The text inserted by the last pick, so that another pick can replace it in turn. */
    private var lastReplacement: RangeMarker? = null

    private var rows: List<EchoRow> = emptyList()
    private var rowByKey: Map<String, EchoRow> = emptyMap()
    private var echoes: List<Echo> = emptyList()

    private var observedDocument: Document? = null
    private var observedEditor: Editor? = null
    private var lastCaretKey: String? = null

    /** On-demand red highlighters for the currently selected/entered word group. */
    private val activeHighlighters = mutableListOf<RangeHighlighter>()

    /** Active only while the tool window is visible. */
    private var active = false

    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) = scheduleRefresh()
    }

    private val caretListener = object : CaretListener {
        override fun caretPositionChanged(event: CaretEvent) = onCaretMoved()
    }

    init {
        border = JBUI.Borders.empty()

        table.columnModel.getColumn(0).cellRenderer = WordCellRenderer()

        val thesaurusPanel = JPanel(BorderLayout()).apply {
            add(thesaurusCombo, BorderLayout.NORTH)
            add(thesaurus.component, BorderLayout.CENTER)
        }
        val splitter = JBSplitter(true, 0.5f).apply {
            firstComponent = JBScrollPane(table)
            secondComponent = thesaurusPanel
        }
        add(buildToolbarPanel(), BorderLayout.NORTH)
        add(splitter, BorderLayout.CENTER)

        // Reload the current word when the thesaurus server changes.
        thesaurusCombo.addActionListener {
            if (syncingControls) return@addActionListener
            settings.thesaurusIndex = thesaurusCombo.selectedIndex
            selectedRow()?.let { showThesaurus(it) }
        }

        // Single click: load synonyms for the first variant + underline occurrences.
        table.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting && !syncingSelection) {
                selectedRow()?.let { row ->
                    showThesaurus(row)
                    highlightOccurrences(row.ranges)
                }
            }
        }
        // Double click: jump to the first occurrence.
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) navigateToSelected()
            }
        })

        val connection = project.messageBus.connect(this)
        connection.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) = refresh()
            },
        )
        // Enable when the tool window is visible, disable when hidden.
        connection.subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
                override fun stateChanged(toolWindowManager: ToolWindowManager) {
                    val tw = toolWindowManager.getToolWindow(TOOL_WINDOW_ID)
                    setActive(tw != null && tw.isVisible)
                }
            },
        )
        // Settings changed from the Settings page: mirror them here and reset.
        connection.subscribe(EchoSettingsNotifier.TOPIC, EchoSettingsListener {
            syncControlsFromSettings()
            resetAll()
            // The local thesaurus may just have been read: replace the "loading" page.
            if (currentThesaurusSource() == EchoConfig.LocalThesaurusSource) {
                thesaurusGroup?.let { rowByKey[it] }?.let { showThesaurus(it) }
            }
        })

        // A click on a synonym of the local thesaurus replaces the current echo.
        thesaurus.onPick = { replaceCurrentEcho(it) }

        // The panel is created when the tool window is first shown -> start active.
        setActive(true)
    }

    /** Turns highlighting + background analysis on/off following tool window visibility. */
    private fun setActive(value: Boolean) {
        if (value == active) return
        active = value
        EchoHighlightState.getInstance(project).setEnabled(value)

        if (value) {
            refresh()
        } else {
            clearHighlights()
            observedDocument?.removeDocumentListener(documentListener)
            observedDocument = null
            observedEditor?.caretModel?.removeCaretListener(caretListener)
            observedEditor = null
            EchoHighlightState.getInstance(project).setEditorBackground(null)
            alarm.cancelAllRequests()
            echoes = emptyList()
            rows = emptyList()
            rowByKey = emptyMap()
            lastCaretKey = null
            tableModel.rowCount = 0
        }
        // Re-run the daemon so the annotator repaints (or clears) editor highlights.
        DaemonCodeAnalyzer.getInstance(project).restart()
    }

    private fun buildToolbarPanel(): JComponent {
        val group = DefaultActionGroup().apply {
            add(object : AnAction(
                EchoBundle.message("toolwindow.action.refresh"),
                null,
                AllIcons.Actions.Refresh,
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    // Full reset: clears the current marking, re-reads the (possibly external)
                    // settings, re-analyzes, and restarts the daemon so the annotator repaints.
                    resetAll()
                }
            })
            add(object : AnAction(
                EchoBundle.message("toolwindow.action.settings"),
                null,
                AllIcons.General.Settings,
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    // Applying the dialog fires EchoSettingsNotifier, which resets this panel.
                    ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, EchoConfigurable::class.java)
                }
            })
        }
        val toolbar = ActionManager.getInstance().createActionToolbar("IdeaEchoToolbar", group, true)
        toolbar.targetComponent = this

        syncControlsFromSettings()
        minLengthCombo.addActionListener { onControlsChanged() }
        windowSpinner.addChangeListener { onControlsChanged() }

        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(toolbar.component)
            add(Box.createHorizontalStrut(JBUI.scale(8)))
            add(JLabel(EchoBundle.message("toolwindow.label.minLength")))
            add(Box.createHorizontalStrut(JBUI.scale(4)))
            add(minLengthCombo)
            add(Box.createHorizontalStrut(JBUI.scale(8)))
            add(JLabel(EchoBundle.message("toolwindow.label.window")))
            add(Box.createHorizontalStrut(JBUI.scale(4)))
            add(windowSpinner)
        }
    }

    private fun syncControlsFromSettings() {
        syncingControls = true
        minLengthCombo.selectedItem = settings.minWordLength
        windowSpinner.value = settings.windowSize
        thesaurusCombo.selectedIndex = settings.thesaurusIndex
        syncingControls = false
    }

    /** Toolbar edit -> persist, then reset everything. */
    private fun onControlsChanged() {
        if (syncingControls) return
        settings.minWordLength = minLengthCombo.selectedItem as Int
        settings.windowSize = windowSpinner.value as Int
        resetAll()
    }

    /** Full reset after a parameter change: drop markings, re-analyze, repaint the editor. */
    private fun resetAll() {
        clearHighlights()
        lastCaretKey = null
        refresh()
        DaemonCodeAnalyzer.getInstance(project).restart()
    }

    private fun currentThesaurusSource(): EchoConfig.ThesaurusSource =
        EchoConfig.THESAURUS_SOURCES[settings.thesaurusIndex]

    private fun showThesaurus(row: EchoRow) {
        thesaurusGroup = row.group
        val word = row.searchWord
        when (val source = currentThesaurusSource()) {
            is EchoConfig.ThesaurusServer -> thesaurus.showUrl(word, source.urlFor(word))
            EchoConfig.LocalThesaurusSource -> thesaurus.showHtml(localThesaurusPage(word))
        }
    }

    /** The page for [word] from the local thesaurus, or a message saying why there is none. */
    private fun localThesaurusPage(word: String): String {
        val palette = ThesaurusHtml.Palette(
            background = "#" + ColorUtil.toHex(UIUtil.getPanelBackground()),
            foreground = "#" + ColorUtil.toHex(UIUtil.getLabelForeground()),
            secondary = "#" + ColorUtil.toHex(JBUI.CurrentTheme.ContextHelp.FOREGROUND),
        )
        val app = EchoAppSettings.getInstance()
        if (app.thesaurusPath.isBlank()) {
            return ThesaurusHtml.message(EchoBundle.message("thesaurus.local.notConfigured"), palette)
        }
        val local = app.thesaurusIfReady()
            ?: return ThesaurusHtml.message(
                EchoBundle.message(
                    if (app.thesaurusLoading()) "thesaurus.local.loading" else "thesaurus.local.unreadable"
                ),
                palette,
            )
        val entries = local.lookup(word, app.lexiconIfReady())
        return if (entries.isEmpty()) {
            ThesaurusHtml.message(EchoBundle.message("thesaurus.local.notFound", word), palette)
        } else {
            ThesaurusHtml.entries(word, entries, palette, thesaurus.pickScript)
        }
    }

    /**
     * Replaces an occurrence of the group shown in the thesaurus view with [synonym],
     * keeping its capitalization. The occurrence is the one under the caret; right after
     * a pick it is the text just inserted, so synonyms can be tried one after the other.
     * When the caret is on neither, nothing is replaced and a hint says why: with the page
     * opened from the table there may be many occurrences, and guessing would surprise.
     */
    private fun replaceCurrentEcho(synonym: String) {
        val editor = observedEditor ?: return
        val document = editor.document
        // Offsets must match the document: run a pending analysis now.
        if (!alarm.isEmpty) {
            alarm.cancelAllRequests()
            refresh()
        }
        val caret = editor.caretModel.offset
        val target = lastReplacement?.takeIf { it.isValid && caret in it.startOffset..it.endOffset }?.textRange
            ?: echoes.firstOrNull { it.group == thesaurusGroup && caret in it.startOffset..it.endOffset }
                ?.let { TextRange(it.startOffset, it.endOffset) }
        if (target == null) {
            HintManager.getInstance().showInformationHint(editor, EchoBundle.message("thesaurus.replace.noTarget"))
            return
        }
        if (!FileDocumentManager.getInstance().requestWriting(document, project)) return

        val original = document.getText(target)
        val replacement = TextCase.matchCase(original, synonym)
        WriteCommandAction.runWriteCommandAction(
            project,
            EchoBundle.message("thesaurus.replace.command", original, replacement),
            null,
            Runnable { document.replaceString(target.startOffset, target.endOffset, replacement) },
        )
        lastReplacement?.dispose()
        lastReplacement = document.createRangeMarker(target.startOffset, target.startOffset + replacement.length)
        editor.caretModel.moveToOffset(target.startOffset)
        IdeFocusManager.getInstance(project).requestFocus(editor.contentComponent, true)
    }

    private fun scheduleRefresh() {
        alarm.cancelAllRequests()
        alarm.addRequest({ refresh() }, 250)
    }

    private fun refresh() {
        if (!active) return

        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val document = editor?.document
        val vFile = document?.let { FileDocumentManager.getInstance().getFile(it) }

        if (document !== observedDocument) {
            observedDocument?.removeDocumentListener(documentListener)
            observedDocument = document
            document?.addDocumentListener(documentListener)
        }
        if (editor !== observedEditor) {
            clearHighlights()   // remove markers from the previous editor
            observedEditor?.caretModel?.removeCaretListener(caretListener)
            observedEditor = editor
            lastReplacement?.dispose()
            lastReplacement = null
            editor?.caretModel?.addCaretListener(caretListener)
            lastCaretKey = null
        }

        // Capture the colour the editor really paints (another plugin may change it,
        // independently of the colour scheme). Read here, on the EDT, for the annotator.
        EchoHighlightState.getInstance(project)
            .setEditorBackground(editor?.contentComponent?.background)

        val isTex = vFile?.name?.endsWith(".tex", ignoreCase = true) == true
        echoes = if (document != null && isTex)
            RepetitionAnalyzer.analyze(document.text, settings.toParams())
        else emptyList()

        // Group as the analyzer did; variants sorted alphabetically.
        val builtRows = ArrayList<EchoRow>()
        val builtMap = HashMap<String, EchoRow>()
        for ((key, list) in echoes.groupBy { it.group }) {
            val variants = list.map { it.word }.distinct().sortedWith { a, b -> collator.compare(a, b) }
            // Minimum gaps: computed independently over adjacent occurrences once sorted.
            val byWordIndex = list.sortedBy { it.wordIndex }
            val minWordGap = byWordIndex.zipWithNext()
                .minOfOrNull { (a, b) -> b.wordIndex - a.wordIndex } ?: 0
            val minSentenceGap = byWordIndex.zipWithNext()
                .minOfOrNull { (a, b) -> b.sentenceId - a.sentenceId } ?: 0
            val row = EchoRow(
                group = key,
                display = variants.joinToString("/"),
                count = list.size,
                firstOffset = list.minOf { it.startOffset },
                searchWord = variants.first(),
                ranges = list.map { it.startOffset to it.endOffset },
                minWordGap = minWordGap,
                minSentenceGap = minSentenceGap,
            )
            builtRows += row
            builtMap[key] = row
        }
        rows = builtRows.sortedWith(compareByDescending<EchoRow> { it.count }.thenBy { it.display })
        rowByKey = builtMap

        tableModel.rowCount = 0
        for (r in rows) tableModel.addRow(arrayOf<Any>(r.display, r.count, r.minWordGap, r.minSentenceGap))
    }

    /** When the caret enters a highlighted word, trigger the same action as a table click. */
    private fun onCaretMoved() {
        if (!active) return
        val editor = observedEditor ?: return
        val offset = editor.caretModel.offset
        val key = echoes.firstOrNull { offset in it.startOffset until it.endOffset }
            ?.group
        if (key == lastCaretKey) return   // still inside the same echo word: don't re-trigger
        lastCaretKey = key
        key?.let { rowByKey[it] }?.let { row ->
            showThesaurus(row)
            highlightOccurrences(row.ranges)
            selectRowInTable(row)
        }
    }

    /** Selects [row] in the table and scrolls it into view, without re-triggering the listener. */
    private fun selectRowInTable(row: EchoRow) {
        val modelIndex = rows.indexOf(row)
        if (modelIndex < 0) return
        val viewIndex = table.convertRowIndexToView(modelIndex)
        if (viewIndex < 0 || viewIndex == table.selectedRow) return
        syncingSelection = true
        try {
            table.setRowSelectionInterval(viewIndex, viewIndex)
            table.scrollRectToVisible(table.getCellRect(viewIndex, 0, true))
        } finally {
            syncingSelection = false
        }
    }

    /** Red underline on every occurrence + red marker on the right-hand error stripe. */
    private fun highlightOccurrences(ranges: List<Pair<Int, Int>>) {
        clearHighlights()
        val editor = observedEditor ?: return
        val markup = editor.markupModel
        val length = editor.document.textLength
        val attrs = TextAttributes().apply {
            fontType = Font.BOLD
            foregroundColor = JBColor.getHSBColor(0f, 0.85f, 0.55f)
            errorStripeColor = JBColor.RED
            effectType = EffectType.BOLD_LINE_UNDERSCORE
            effectColor = JBColor.RED
        }
        for ((start, end) in ranges) {
            if (start < 0 || end > length || start >= end) continue
            val h = markup.addRangeHighlighter(
                start,
                end,
                HighlighterLayer.SELECTION - 1,
                attrs,
                HighlighterTargetArea.EXACT_RANGE,
            )
            h.errorStripeTooltip = EchoBundle.message("editor.stripe.tooltip")
            activeHighlighters += h
        }
    }

    private fun clearHighlights() {
        if (activeHighlighters.isEmpty()) return
        val markup = observedEditor?.markupModel
        for (h in activeHighlighters) {
            try {
                markup?.removeHighlighter(h)
            } catch (_: Exception) {
                // highlighter already gone (e.g. editor closed); ignore
            }
        }
        activeHighlighters.clear()
    }

    private fun selectedRow(): EchoRow? {
        val view = table.selectedRow
        if (view < 0) return null
        return rows.getOrNull(table.convertRowIndexToModel(view))
    }

    private fun navigateToSelected() {
        val row = selectedRow() ?: return
        val document = FileEditorManager.getInstance(project).selectedTextEditor?.document ?: return
        val vFile = FileDocumentManager.getInstance().getFile(document) ?: return
        OpenFileDescriptor(project, vFile, row.firstOffset).navigate(true)
    }

    /** Paints each word in its stable group colour (foreground). */
    private inner class WordCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean,
            hasFocus: Boolean, row: Int, column: Int,
        ): Component {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            if (!isSelected) {
                rows.getOrNull(table.convertRowIndexToModel(row))?.let {
                    foreground = WordColors.textColorFor(it.group, sameSentence = false)
                }
            }
            return this
        }
    }

    override fun dispose() {
        clearHighlights()
        lastReplacement?.dispose()
        EchoHighlightState.getInstance(project).setEnabled(false)
        observedDocument?.removeDocumentListener(documentListener)
        observedEditor?.caretModel?.removeCaretListener(caretListener)
    }

    companion object {
        const val TOOL_WINDOW_ID = "Idea Echo" // must match the id in plugin.xml
    }
}