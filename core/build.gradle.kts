plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    // The shared, PURE domain module: the hand-rolled JSON codec, the HttpTransport seam,
    // the heyarr MCP/REST DTOs + parsers, library-status / search-grouping derivation and
    // the pure MediaType enum. NO Compose, NO platform SDK — so its tests run as fast
    // `commonTest` without a UI or Android harness.
    //
    // Single JVM target for now (desktop). When heyarr-mobile folds in, `androidTarget()`
    // (and later `iosX64()` …) get added HERE and the code above is already in commonMain,
    // so that is a source-set add, not a rewrite.
    jvm()

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
