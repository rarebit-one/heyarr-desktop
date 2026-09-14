import com.android.build.gradle.LibraryExtension

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

// Register the android target only when an SDK is actually available (CI, or a dev box
// with one). Applying `com.android.library` unconditionally makes AGP demand an SDK at
// CONFIGURATION time, which would break plain JVM/desktop builds on an SDK-less machine.
// Detection: ANDROID_HOME / ANDROID_SDK_ROOT env, or a `sdk.dir` line in local.properties.
val hasAndroidSdk =
    System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    rootProject.file("local.properties").takeIf { it.exists() }?.readText()?.contains("sdk.dir") == true

if (hasAndroidSdk) apply(plugin = "com.android.library")

kotlin {
    // The shared, PURE domain module: the hand-rolled JSON codec, the HttpTransport seam,
    // the heyarr MCP/REST DTOs + parsers, library-status / search-grouping derivation and
    // the pure MediaType enum. NO Compose, NO platform SDK — so its tests run as fast
    // `commonTest` without a UI or Android harness.
    //
    // Targets: JVM (desktop) always; Android when an SDK is present (see `hasAndroidSdk`).
    // The code all lives in commonMain, so adding a target is a source-set add, not a
    // rewrite. `iosX64()` … arrive the same way later.
    jvm()

    if (hasAndroidSdk) androidTarget()

    jvmToolchain(17)

    sourceSets {
        val commonMain by getting {
            // No deps yet: the domain here is pure stdlib. kotlinx-coroutines-core comes
            // back when HeyarrApi / the async layer moves in from :composeApp (Gate A).
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

if (hasAndroidSdk) {
    extensions.configure<LibraryExtension>("android") {
        namespace = "one.rarebit.heyarr.core"
        compileSdk = 35
        defaultConfig {
            minSdk = 33
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}
