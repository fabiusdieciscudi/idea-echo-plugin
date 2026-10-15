package name.fabius10scudi.ideaecho

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Main plugin entry point - runs on project open.
 */
class EchoPlugin : ProjectActivity {

    override suspend fun execute(project: Project) {
        // Future initialization (caching, settings, etc.)
        // Starts reading the Morph-it! lexicon, if configured, before the first analysis needs it.
        EchoAppSettings.getInstance().lexiconIfReady()
        println("Idea Echo plugin initialized for project: ${project.name}")
    }
}
