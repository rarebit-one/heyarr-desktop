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
    // Android Gradle Plugin — version matches heyarr-mobile (AGP 8.7.3) so the shared
    // `:core` / `:ui` modules build an android variant identical to what the phone app
    // will consume when it folds in. Declared here (apply false) and applied CONDITIONALLY
    // in :core / :ui only when an Android SDK is present (see each module's build script):
    // a plain `com.android.library` apply would make AGP demand an SDK at CONFIGURATION
    // time, breaking every Gradle invocation on an SDK-less desktop dev box. CI installs
    // the SDK (setup-android) so it builds the android variants; SDK-less desktop builds
    // simply skip the android target and are unaffected.
    id("com.android.library") version "8.7.3" apply false
}
