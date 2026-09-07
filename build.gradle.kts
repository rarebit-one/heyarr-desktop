// Root build. Plugin versions are declared here (apply false) and applied in the
// subproject. Toolchain matches the rest of the org: Kotlin 2.3.20, Gradle 8.9,
// JDK 17. Compose Multiplatform (1.9.3) is NEW to the org — this project establishes it.
plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.3.20" apply false
    // The Compose *compiler* plugin ships WITH Kotlin (versioned with it), so it stays
    // compatible with the Kotlin compiler automatically.
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
    // The Compose Multiplatform Gradle plugin: wires the Compose runtime deps and the
    // `compose.desktop.application { }` / jpackage packaging DSL.
    id("org.jetbrains.compose") version "1.9.3" apply false
}
