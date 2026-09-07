# heyarr-desktop

A **Linux desktop client for heyarr**, built with **Compose Multiplatform** (JVM/desktop
target). Targets Linux x64 **and aarch64** (Asahi/Omarchy) — the JVM is the portability
layer, so a single generic JVM target covers both.

This is the org's **first Compose Multiplatform** project. It establishes the
`org.jetbrains.compose` toolchain and proves a v1 vertical slice end-to-end:
Settings → configure heyarr → browse the library over the real `/api/v1` REST API.

## Status — v1 vertical slice

Working:
- **Settings screen** — base URL (default `https://heyarr.br.thesim.family:7777`) + a
  pasted bearer token, persisted to `~/.config/heyarr-desktop/config.json`
  (`$XDG_CONFIG_HOME`-aware). Desktop analog of mobile's `SettingsStore`.
- **JDK HttpTransport** — `net/JdkHttpTransport.kt`, a `java.net.http.HttpClient`
  (JDK 17) actual of the same `HttpTransport` interface shape mobile uses (OkHttp there).
  No Retrofit/Ktor — matches the org stance.
- **Library browse** — `GET /api/v1/works` (paged, follows `next_cursor`) with
  `Authorization: Bearer <token>`, parsed by a hand-rolled `JsonScan`/`WorksJson`
  (copied/adapted from mobile), rendered as a Compose list.
- **JVM unit tests** — JSON parsing, URL building, paging + auth header, run in `check`.

Stubbed / deferred:
- **Voidbind device/QR login** — behind `login/VoidbindLogin.kt` (`LoginProvider`
  interface). Only `BearerTokenLogin` is live; `VoidbindLoginStub` documents the shape.
  The real coordinator needs `one.rarebit.voidbind:voidbind-client:0.7.0`, whose
  resolution requires a **GitHub PAT with `read:packages`** — see below.

## Toolchain

| Piece | Version |
|-------|---------|
| Kotlin | 2.3.20 (matches the org) |
| Gradle | 8.9 |
| JDK | 17 (Temurin) |
| AGP | n/a (no Android target yet) |
| Compose Multiplatform | 1.9.3 |
| Compose compiler | bundled with Kotlin (`org.jetbrains.kotlin.plugin.compose:2.3.20`) |

## Project structure

```
heyarr-desktop/
├── settings.gradle.kts        # :composeApp; GitHub Packages repo for voidbind (commented)
├── build.gradle.kts           # plugin versions (apply false)
├── gradle.properties
└── composeApp/
    ├── build.gradle.kts       # kotlin("multiplatform") + jvm(); compose.desktop.application{}
    └── src/
        ├── jvmMain/kotlin/one/rarebit/heyarr/desktop/
        │   ├── Main.kt                     # entry point (MainKt); wires concretes
        │   ├── net/HttpTransport.kt        # interface + HttpResponse (mobile-shaped)
        │   ├── net/JdkHttpTransport.kt     # java.net.http actual
        │   ├── net/JsonScan.kt             # hand-rolled JSON scanner (from mobile)
        │   ├── net/JsonEscapes.kt
        │   ├── settings/SettingsStore.kt   # file-backed config.json
        │   ├── auth/Credential.kt          # Bearer (Device deferred)
        │   ├── library/{Work,WorksJson,LibraryClient}.kt
        │   ├── login/VoidbindLogin.kt      # LoginProvider seam + bearer stub
        │   └── ui/App.kt                   # Settings + Library screens
        └── jvmTest/kotlin/...              # WorksJsonTest
```

**Why `kotlin("multiplatform")` + a single `jvm()` target** (not plain `kotlin("jvm")`):
this is a genuine KMP module so the later shared-client extraction is a source-set move
(add `commonMain`, `androidTarget()`, `iosX64()`…) rather than a plugin swap. Sources
live in `jvmMain` today; nothing below the UI touches a JVM-only API except
`JdkHttpTransport` and `FileSettingsStore` (the two concretes `Main.kt` injects).

## Build & run

Requires a **JDK 17** on `PATH` / `JAVA_HOME` (this environment had none, so a portable
Temurin 17 aarch64 was fetched to build).

```bash
./gradlew build          # compiles + runs the JVM unit tests
./gradlew :composeApp:run # launches the desktop window
```

`run` needs a display (X11/Wayland); in a headless environment it fails with
`java.awt.HeadlessException: No X11 DISPLAY` — expected, not a code fault.

### Packaging (declared, not run here)

`compose.desktop.application { nativeDistributions { targetFormats(Deb, Rpm) } }` is
wired for Linux via jpackage:

```bash
./gradlew :composeApp:packageDeb        # .deb
./gradlew :composeApp:packageRpm        # .rpm
./gradlew :composeApp:createDistributable  # app-image (wrap for AppImage)
```

AppImage is **not** a jpackage format — it is produced out-of-band by wrapping the
`createDistributable` app-image, which is why only Deb/Rpm are listed.

## Enabling Voidbind login (needs `read:packages`)

`voidbind-client` lives in GitHub Packages (private; a read needs a PAT with
`read:packages`). This build deliberately does **not** depend on it, so it succeeds
without the token. To enable Voidbind device/QR login:

1. Put `gpr.user` / `gpr.token` (a PAT with `read:packages`) in
   `~/.gradle/gradle.properties`, or export `GITHUB_ACTOR` / `GITHUB_TOKEN`.
2. Uncomment the `GitHubPackagesVoidbindKmp` repo in `settings.gradle.kts`.
3. Uncomment `implementation("one.rarebit.voidbind:voidbind-client:0.7.0")` in
   `composeApp/build.gradle.kts`.
4. Replace `VoidbindLoginStub` with a real coordinator over the library's
   `LoginApproval` / `DevicePairing` / `WebLoginClient` and a desktop `DeviceKeyStore`.

## Next steps toward mobile parity

- **Shared KMP module** — extract `net/`, `library/`, `auth/`, `settings/` into a
  `commonMain` shared with heyarr-mobile (the copied code here is the seed).
- **Wants dashboard** — port `acquisition/WantsClient` (`/api/v1/desired`, `PATCH` to
  pause/resume; the `HttpTransport.patch`/`delete` verbs are already here).
- **Playback** — video/audio via **VLCJ** (libvlc) or **GStreamer-java**; stream
  `/api/v1/blobs/{hash}/content` (mobile's `blobHash` handle).
- **Reader** — the EPUB/CBZ reader surface.
- **Personal-state crypto** — the encrypted personal-state sync (`personalstate/`).
- **Secret storage** — move the bearer token out of plaintext config into libsecret /
  KWallet.
```
