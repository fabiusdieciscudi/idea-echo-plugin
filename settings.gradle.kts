import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "idea-echo-plugin"

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.17.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}
