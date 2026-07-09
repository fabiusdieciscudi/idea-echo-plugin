package name.fabius10scudi.ideaecho

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.DefaultListModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.ListSelectionModel
import javax.swing.SpinnerNumberModel
import javax.swing.table.DefaultTableModel

/** Settings page: Settings | Tools | Idea Echo. */
class EchoConfigurable(private val project: Project) : Configurable {

    private val minLengthCombo = JComboBox(
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

    /** Ignored commands: editable list with +/- buttons. */
    private val commandsModel = DefaultListModel<String>()
    private val commandsList = JBList(commandsModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        visibleRowCount = 6
    }

    /** Text-argument map: editable two-column table with +/- buttons. */
    private val argumentModel = object : DefaultTableModel(arrayOf("Command", "Argument"), 0) {
        override fun getColumnClass(columnIndex: Int): Class<*> =
            if (columnIndex == 1) Integer::class.java else String::class.java
    }
    private val argumentTable = JBTable(argumentModel).apply {
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        preferredScrollableViewportSize = Dimension(-1, JBUI.scale(120))
    }

    /** Ignored words: free-form text area, one word per line. */
    private val wordsArea = JBTextArea(10, 40).apply {
        lineWrap = false
        border = JBUI.Borders.empty(4)
    }

    private val settings get() = EchoSettings.getInstance(project)

    override fun getDisplayName(): String = "Idea Echo"

    override fun createComponent(): JComponent {
        // Double click on a list row -> edit it in place.
        val commandsPanel = ToolbarDecorator.createDecorator(commandsList)
            .setAddAction {
                askCommandName("Add ignored command", "")?.let { commandsModel.addElement(it) }
            }
            .setEditAction {
                val i = commandsList.selectedIndex
                if (i >= 0) {
                    askCommandName("Edit ignored command", commandsModel.get(i))
                        ?.let { commandsModel.set(i, it) }
                }
            }
            .setRemoveAction {
                val i = commandsList.selectedIndex
                if (i >= 0) commandsModel.remove(i)
            }
            .createPanel()

        // The table cells are directly editable; +/- add and remove rows.
        val argumentPanel = ToolbarDecorator.createDecorator(argumentTable)
            .setAddAction { argumentModel.addRow(arrayOf<Any>("", 1)) }
            .setRemoveAction {
                val i = argumentTable.selectedRow
                if (i >= 0) argumentModel.removeRow(argumentTable.convertRowIndexToModel(i))
            }
            .createPanel()

        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Minimum word length:", minLengthCombo)
            .addLabeledComponent("Look-back window (words):", windowSpinner)
            .addLabeledComponentFillVertically("Ignored LaTeX commands (with content):", commandsPanel)
            .addLabeledComponentFillVertically("Text argument (command -> 1-based index):", argumentPanel)
            .addLabeledComponentFillVertically("Ignored words \u2014 one regex per line:", JBScrollPane(wordsArea))
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    private fun askCommandName(title: String, initial: String): String? {
        val value = Messages.showInputDialog(
            project, "Command name (no backslash):", title, null, initial, null
        )
        return value?.trim()?.takeIf { it.isNotEmpty() }
    }

    override fun isModified(): Boolean =
        minLengthCombo.selectedItem != settings.minWordLength ||
            windowSpinner.value != settings.windowSize ||
            currentCommands() != settings.ignoredCommands ||
            currentTextArgument() != settings.textArgument ||
            currentWords() != settings.ignoredWords

    override fun apply() {
        // Stop cell editing so the last typed value is committed.
        argumentTable.cellEditor?.stopCellEditing()

        settings.minWordLength = minLengthCombo.selectedItem as Int
        settings.windowSize = windowSpinner.value as Int
        settings.ignoredCommands = currentCommands()
        settings.textArgument = currentTextArgument()
        settings.ignoredWords = currentWords()
        EchoSettingsNotifier.settingsChanged(project)
    }

    override fun reset() {
        minLengthCombo.selectedItem = settings.minWordLength
        windowSpinner.value = settings.windowSize

        commandsModel.clear()
        settings.ignoredCommands.forEach { commandsModel.addElement(it) }

        argumentModel.rowCount = 0
        settings.textArgument.forEach { (cmd, idx) -> argumentModel.addRow(arrayOf<Any>(cmd, idx)) }

        wordsArea.text = settings.ignoredWords.joinToString("\n")
        wordsArea.caretPosition = 0
    }

    private fun currentCommands(): List<String> =
        (0 until commandsModel.size()).map { commandsModel.get(it).trim() }.filter { it.isNotEmpty() }

    private fun currentTextArgument(): Map<String, Int> {
        val result = LinkedHashMap<String, Int>()
        for (row in 0 until argumentModel.rowCount) {
            val name = (argumentModel.getValueAt(row, 0) as? String)?.trim().orEmpty()
            val index = (argumentModel.getValueAt(row, 1) as? Number)?.toInt() ?: 1
            if (name.isNotEmpty() && index >= 1) result[name] = index
        }
        return result
    }

    private fun currentWords(): List<String> =
        wordsArea.text.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
}
