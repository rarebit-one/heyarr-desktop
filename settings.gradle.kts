rootProject.name = "heyarr-desktop"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        // Compose Multiplatform's dev/EAP artifacts (harmless for stable, kept for parity
        // with the wider Compose MP ecosystem). Stable releases resolve from the portal.
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")

        // ── voidbind-kmp's published `voidbind-client` (GitHub Packages, private) ──
        // The shared Voidbind identity/net/flow brain (WebLoginClient, LoginQr,
        // DeviceIdentity, DevicePairing …). Consumed by :androidApp (heyarr-mobile).
        // GitHub Packages requires a token with `read:packages` even for a same-org read:
        //   - CI: `GITHUB_ACTOR` / `GITHUB_TOKEN` (the android workflow's own token, with
        //     `packages: read` — see .github/workflows/android.yml).
        //   - Locally: `gpr.user` / `gpr.token` in ~/.gradle/gradle.properties, or the env
        //     vars, e.g. `GITHUB_ACTOR=<login> GITHUB_TOKEN=$(gh auth token) ./gradlew …`.
        // Scoped to the voidbind group so the desktop modules (which don't depend on it)
        // never probe this repo. The desktop app (:composeApp) still keeps its bearer-token
        // login stub and does NOT depend on the artifact — only :androidApp does (PR #3a).
        maven {
            name = "GitHubPackagesVoidbindKmp"
            url = uri("https://maven.pkg.github.com/rarebit-one/voidbind-kmp")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.token").orNull
                    ?: providers.gradleProperty("gpr.key").orNull // allthing-android's spelling
                    ?: System.getenv("GITHUB_TOKEN")
            }
            content { includeGroup("one.rarebit.voidbind") }
        }
    }
}

include(":core")
include(":ui")
include(":composeApp")

// :androidApp (heyarr-mobile) applies `com.android.application`, which — unlike the
// SDK-gated library target on :core/:ui — demands an Android SDK at CONFIGURATION time.
// Including it unconditionally would break every Gradle invocation on an SDK-less desktop
// dev box (the Asahi laptop, the screenshots flow). So it joins the build ONLY when an SDK
// is present: CI (setup-android) and any dev machine with the SDK get the full project;
// desktop-only boxes silently skip the android app, exactly as they skip the android
// variants of :core/:ui. Keep this guard identical to the one in :core/:ui build scripts.
val hasAndroidSdk =
    System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    file("local.properties").takeIf { it.exists() }?.readText()?.contains("sdk.dir") == true
if (hasAndroidSdk) include(":androidApp")
