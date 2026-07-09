package name.fabius10scudi.ideaecho

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.FlowLayout
import java.nio.file.Paths
import javax.swing.DefaultListModel
import javax.swing.JButton
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

    private val externalCheck = JBCheckBox("Store configuration in an external YAML file")
    private val externalPathField = TextFieldWithBrowseButton()
    private val exportButton = JButton("Write file from current values")
    private val importButton = JButton("Import values from file")

    private val commandsModel = DefaultListModel<String>()
    private val commandsList = JBList(commandsModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        visibleRowCount = 6
    }

    private val argumentModel = object : DefaultTableModel(arrayOf("Command", "Argument"), 0) {
        override fun getColumnClass(columnIndex: Int): Class<*> =
            if (columnIndex == 1) Integer::class.java else String::class.java
    }
    private val argumentTable = JBTable(argumentModel).apply {
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        preferredScrollableViewportSize = Dimension(-1, JBUI.scale(120))
    }

    private val wordsArea = JBTextArea(10, 40).apply {
        lineWrap = false
        border = JBUI.Borders.empty(4)
    }

    private val settings get() = EchoSettings.getInstance(project)

    override fun getDisplayName(): String = "Idea Echo"

    override fun createComponent(): JComponent {
        // The chooser is rooted at the project directory, so the user cannot browse outside it.
        val descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().apply {
            title = "Select Idea Echo Configuration File"
            project.guessProjectDir()?.let { setRoots(it) }
        }
        externalPathField.addBrowseFolderListener(null, null, project, descriptor)

        externalCheck.addActionListener { updateEnablement() }

        exportButton.addActionListener { exportToFile() }
        importButton.addActionListener { importFromFile() }

        val externalRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            add(exportButton)
            add(JPanel().apply { preferredSize = Dimension(JBUI.scale(8), 1) })
            add(importButton)
        }

        val commandsPanel = ToolbarDecorator.createDecorator(commandsList)
            .setAddAction { askCommandName("Add ignored command", "")?.let { commandsModel.addElement(it) } }
            .setEditAction {
                val i = commandsList.selectedIndex
                if (i >= 0) askCommandName("Edit ignored command", commandsModel.get(i))
                    ?.let { commandsModel.set(i, it) }
            }
            .setRemoveAction {
                val i = commandsList.selectedIndex
                if (i >= 0) commandsModel.remove(i)
            }
            .createPanel()

        val argumentPanel = ToolbarDecorator.createDecorator(argumentTable)
            .setAddAction { argumentModel.addRow(arrayOf<Any>("", 1)) }
            .setRemoveAction {
                val i = argumentTable.selectedRow
                if (i >= 0) argumentModel.removeRow(argumentTable.convertRowIndexToModel(i))
            }
            .createPanel()

        return FormBuilder.createFormBuilder()
            .addComponent(externalCheck)
            .addLabeledComponent("Configuration file:", externalPathField)
            .addComponent(externalRow)
            .addSeparator()
            .addLabeledComponent("Minimum word length:", minLengthCombo)
            .addLabeledComponent("Look-back window (words):", windowSpinner)
            .addLabeledComponentFillVertically("Ignored LaTeX commands (with content):", commandsPanel)
            .addLabeledComponentFillVertically("Text argument (command -> 1-based index):", argumentPanel)
            .addLabeledComponentFillVertically("Ignored words \u2014 one regex per line:", JBScrollPane(wordsArea))
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    /** Path field follows the checkbox; the two buttons only make sense in internal mode. */
    private fun updateEnablement() {
        val external = externalCheck.isSelected
        externalPathField.isEnabled = external
        exportButton.isEnabled = !external
        importButton.isEnabled = !external
    }

    /** Validated absolute path from the field, or null (with a message) when invalid. */
    private fun validatedPath(showErrors: Boolean = true): java.nio.file.Path? {
        val raw = externalPathField.text.trim()
        if (raw.isEmpty()) {
            if (showErrors) Messages.showErrorDialog(project, "No configuration file selected.", "Idea Echo")
            return null
        }
        val path = Paths.get(raw).toAbsolutePath().normalize()
        if (!settings.isInsideProject(path)) {
            if (showErrors) Messages.showErrorDialog(
                project,
                "The configuration file must be inside the project directory.",
                "Idea Echo",
            )
            return null
        }
        return path
    }

    /** Internal mode only: overwrite the YAML file with the values shown in the form. */
    private fun exportToFile() {
        val path = validatedPath() ?: return
        argumentTable.cellEditor?.stopCellEditing()
        if (EchoExternalConfig.save(path, formData())) {
            Messages.showInfoMessage(project, "Configuration written to $path", "Idea Echo")
        } else {
            Messages.showErrorDialog(project, "Cannot write $path", "Idea Echo")
        }
    }

    /** Internal mode only: load the YAML file into the form (Apply stores it internally). */
    private fun importFromFile() {
        val path = validatedPath() ?: return
        val data = EchoExternalConfig.load(path)
        if (data == null) {
            Messages.showErrorDialog(project, "Cannot read $path", "Idea Echo")
            return
        }
        fillForm(data)
    }

    override fun isModified(): Boolean =
        externalCheck.isSelected != settings.useExternalConfig ||
            externalPathField.text.trim() != settings.externalConfigPath ||
            formData() != settings.effective()

    override fun apply() {
        argumentTable.cellEditor?.stopCellEditing()

        val wasExternal = settings.useExternalConfig
        val nowExternal = externalCheck.isSelected

        if (nowExternal) {
            val path = validatedPath(showErrors = false)
                ?: throw ConfigurationException("The configuration file must be inside the project directory.")
            // Enabling the external file: create it from the current values when missing.
            if (!path.toFile().isFile && !EchoExternalConfig.save(path, formData())) {
                throw ConfigurationException("Cannot create $path")
            }
            settings.externalConfigPath = path.toString()
            settings.useExternalConfig = true
            settings.updateEffective(formData())   // goes to the YAML file
        } else {
            // Leaving external mode restores the untouched internal values;
            // only write the form back when we were already internal.
            settings.useExternalConfig = false
            if (!wasExternal) settings.applyInternal(formData())
        }

        settings.invalidateCache()
        updateEnablement()
        EchoSettingsNotifier.settingsChanged(project)
    }

    override fun reset() {
        externalCheck.isSelected = settings.useExternalConfig
        externalPathField.text = settings.externalConfigPath.ifBlank {
            project.basePath?.let { "$it/${EchoSettings.DEFAULT_FILE_NAME}" } ?: ""
        }
        fillForm(settings.effective())
        updateEnablement()
    }

    private fun fillForm(data: EchoData) {
        minLengthCombo.selectedItem = data.minWordLength
        windowSpinner.value = data.windowSize

        commandsModel.clear()
        data.ignoredCommands.forEach { commandsModel.addElement(it) }

        argumentModel.rowCount = 0
        data.textArgument.forEach { (cmd, idx) -> argumentModel.addRow(arrayOf<Any>(cmd, idx)) }

        wordsArea.text = data.ignoredWords.joinToString("\n")
        wordsArea.caretPosition = 0
    }

    /** The values currently shown in the form. */
    private fun formData() = EchoData(
        minWordLength = minLengthCombo.selectedItem as Int,
        windowSize = windowSpinner.value as Int,
        ignoredCommands = currentCommands(),
        ignoredWords = currentWords(),
        textArgument = currentTextArgument(),
    )

    private fun askCommandName(title: String, initial: String): String? =
        Messages.showInputDialog(project, "Command name (no backslash):", title, null, initial, null)
            ?.trim()?.takeIf { it.isNotEmpty() }

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
