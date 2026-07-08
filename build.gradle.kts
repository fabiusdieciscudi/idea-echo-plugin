plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.20"
    id("org.jetbrains.intellij.platform")
}

group = "name.fabius10scudi.ideaecho"
version = "1.0.0"

dependencies {
    implementation("com.github.rholder:snowball-stemmer:1.3.0.581.1")
    intellijPlatform {
        create("IC", "2024.3")          // Community Edition
        // bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "name.fabius10scudi.ideaecho"
        name = "Idea Echo"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "243"
            untilBuild = provider { null }
        }
    }
}

tasks {
    // Disabilita task non necessari per velocizzare
    buildSearchableOptions {
        enabled = false
    }
}