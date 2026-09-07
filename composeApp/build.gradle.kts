import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    // KMP with a single JVM/desktop target for now. This is a genuine Kotlin
    // Multiplatform project (kotlin("multiplatform") + a jvm() target and a
    // `jvmMain` source set) rather than a plain kotlin("jvm") module, so that when the
    // shared client is extracted later, adding `androidTarget()` / `iosX64()` … and a
    // `commonMain` is a source-set move, not a plugin swap. A generic JVM/desktop
    // target covers Linux x64 AND aarch64 (Asahi/Omarchy) — the JVM is the portability layer.
    jvm()

    jvmToolchain(17)

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                // Material's extended icon set is the Compose analog of lucide-react: one
                // dependency, vector icons, no font or CDN.
                implementation(compose.materialIconsExtended)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

                // ── Voidbind login (device/QR) — OPTIONAL, currently STUBBED ──────────
                // Resolving voidbind-client needs a GitHub PAT with read:packages, which
                // this environment does NOT have. So Voidbind login lives behind
                // login/VoidbindLogin.kt with a bearer-token stub, and this dependency
                // stays commented so the build succeeds without the token. To enable:
                // uncomment the GitHub Packages repo in settings.gradle.kts, then:
                //
                // implementation("one.rarebit.voidbind:voidbind-client:0.7.0")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "one.rarebit.heyarr.desktop.MainKt"

        nativeDistributions {
            // Linux packaging via jpackage. Declared (not run) here — `build` does not
            // package; `packageDeb` / `packageReleaseDeb` (and Rpm) would. AppImage is
            // NOT a jpackage format: it is produced out-of-band by wrapping the
            // `createDistributable` app-image (task `:composeApp:createDistributable`),
            // which is why only Deb/Rpm are listed here.
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "heyarr-desktop"
            packageVersion = "1.0.0"
            description = "heyarr desktop client (Linux)"
            vendor = "rarebit.one"

            linux {
                // Populated as the app matures; menu group + a real icon come later.
                menuGroup = "AudioVideo"
                // iconFile.set(project.file("src/jvmMain/resources/icon.png"))
            }
        }
    }
}


// Off-screen screenshots of every screen with fixture data — the "show the running app"
// artefact for a headless container. Renders through Compose's ImageComposeScene (no
// display needed) into build/screenshots/*.png.
tasks.register<JavaExec>("screenshots") {
    group = "verification"
    description = "Render each screen to build/screenshots/*.png without a display."
    dependsOn("jvmMainClasses")
    classpath = files(
        layout.buildDirectory.dir("classes/kotlin/jvm/main"),
        layout.buildDirectory.dir("processedResources/jvm/main"),
        configurations.getByName("jvmRuntimeClasspath"),
    )
    mainClass.set("one.rarebit.heyarr.desktop.preview.ScreenshotsKt")
    systemProperty("java.awt.headless", "true")
    args(layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
}
