plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    // The shared Compose-Multiplatform design layer: the design tokens (`Tokens`) and the
    // media → accent theme table (`MediaThemes`). Compose-typed (Color/Dp), so it lives
    // apart from the pure `:core` — but still cross-platform, ready for the android/ios
    // targets that arrive when heyarr-mobile folds in.
    jvm()

    jvmToolchain(17)

    sourceSets {
        val commonMain by getting {
            dependencies {
                // `api` so consumers (the desktop app, later the android app) see the
                // pure MediaType enum the theme table is keyed on without re-declaring it.
                api(project(":core"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
