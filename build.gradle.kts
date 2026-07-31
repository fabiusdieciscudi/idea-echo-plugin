/*
 * © Copyright 2026-present by Fabius Dieciscudi. Licensed under the MIT License, see LICENSE.
 *
 */

import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    // Must be >= the Kotlin used to build everything we compile against: the 2026.1 platform ships metadata 2.3.0.
    // There is no TeXiFy dependency here, so 2.3.0 is the ceiling; 2.4.0 is chosen to match the book-latex sibling.
    // A compiler reads its own metadata version or older, never newer.
    kotlin("jvm") version "2.4.0"
    id("org.jetbrains.intellij.platform") version "2.17.0"
}

group = "name.fabius10scudi.ideaecho"
// The single source of truth for the version is gradle.properties, so a release is one line to change there.
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Path to an already-installed IDE, used instead of downloading the platform. Keep it out of the repository: set it
// in ~/.gradle/gradle.properties as
//     localIdePath=/Applications/IntelliJ IDEA.app
// When unset (CI, fresh checkout) the build falls back to the remote artifact.
val localIdePath: String? = providers.gradleProperty("localIdePath").orNull

dependencies {
    implementation("com.github.rholder:snowball-stemmer:1.3.0.581.1")
    // SnakeYAML ships with the IntelliJ platform: compile against it, do not bundle it.
    compileOnly("org.yaml:snakeyaml:2.2")

    intellijPlatform {
        if (localIdePath != null) {
            local(localIdePath)
        } else {
            // From 2025.3 IDEA ships as a unified distribution: there is no separate `ideaIC` artifact any more,
            // so the `intellijIdea()` helper is used for both editions. This plugin declares no ultimate-only
            // module dependency, so it still loads in Community.
            intellijIdea("2026.1.4")
        }

        // Puts the platform, and the Kotlin stdlib it bundles, on the test classpath. Without it the unit tests have
        // no runtime.
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

// The IntelliJ Platform requires JDK 21 from 2024.2 onwards.
kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        // plugin.xml carries neither <id> nor <name>, so they are declared here.
        id = "name.fabius10scudi.ideaecho"
        name = "Idea Echo"

        ideaVersion {
            sinceBuild = "261"
            // No upper bound: the plugin keeps working across IDE updates.
            untilBuild = provider { null }
        }
    }
}

tasks {
    // Nothing to index yet, and it is slow.
    buildSearchableOptions {
        enabled = false
    }

    test {
        useJUnit()
    }
}
