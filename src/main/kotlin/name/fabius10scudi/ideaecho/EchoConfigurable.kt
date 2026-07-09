package name.fabius10scudi.ideaecho

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.util.ui.FormBuilder
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

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

    private val settings get() = EchoSettings.getInstance(project)

    override fun getDisplayName(): String = "Idea Echo"

    override fun createComponent(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Minimum word length:", minLengthCombo)
        .addLabeledComponent("Look-back window (words):", windowSpinner)
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun isModified(): Boolean =
        minLengthCombo.selectedItem != settings.minWordLength ||
                windowSpinner.value != settings.windowSize

    override fun apply() {
        settings.minWordLength = minLengthCombo.selectedItem as Int
        settings.windowSize = windowSpinner.value as Int
        EchoSettingsNotifier.settingsChanged(project)
    }

    override fun reset() {
        minLengthCombo.selectedItem = settings.minWordLength
        windowSpinner.value = settings.windowSize
    }
}
