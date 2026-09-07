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
        // DeviceIdentity, DevicePairing …). GitHub Packages requires a token with
        // `read:packages` even for a same-org read, which this build environment does
        // NOT have — so the desktop app keeps a bearer-token login STUB behind an
        // interface (login/VoidbindLogin.kt) and does NOT yet depend on the artifact.
        //
        // To turn Voidbind login on: provide `gpr.user` / `gpr.token` (a PAT with
        // read:packages) in ~/.gradle/gradle.properties or GITHUB_ACTOR / GITHUB_TOKEN,
        // then uncomment this repo AND the dependency line in composeApp/build.gradle.kts.
        //
        // maven {
        //     name = "GitHubPackagesVoidbindKmp"
        //     url = uri("https://maven.pkg.github.com/rarebit-one/voidbind-kmp")
        //     credentials {
        //         username = providers.gradleProperty("gpr.user").orNull
        //             ?: System.getenv("GITHUB_ACTOR")
        //         password = providers.gradleProperty("gpr.token").orNull
        //             ?: System.getenv("GITHUB_TOKEN") // a PAT with read:packages
        //     }
        //     content { includeGroup("one.rarebit.voidbind") }
        // }
    }
}

include(":composeApp")
