package name.fabius10scudi.ideaecho

import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic

/** Broadcast when the analyzer parameters change: everything must be re-analyzed. */
fun interface EchoSettingsListener {
    fun settingsChanged()
}

object EchoSettingsNotifier {
    val TOPIC: Topic<EchoSettingsListener> =
        Topic.create("Idea Echo settings", EchoSettingsListener::class.java)

    fun settingsChanged(project: Project) {
        project.messageBus.syncPublisher(TOPIC).settingsChanged()
    }
}
