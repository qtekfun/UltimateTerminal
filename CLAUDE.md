# UltimateTerminal — instructions for Claude Code

A modern terminal for Android (successor to Termux) with Linux distros through **proot**, WSL-style. Adaptive on tablets, terminal only, free software (GPLv3), final destination: F-Droid.

Always read `SPEC.md` (what to build) and `PLAN.md` (in what order) before starting. If anything in this file contradicts the spec, stop and ask.

## Project identity
- Name: **UltimateTerminal**
- `applicationId`: `com.qtekfun.ultimateterminal`
- License: **GPL-3.0-or-later** (SPDX header in every source file). Any Termux code that is reused keeps its original license and is credited.
- UI languages: English (default) and Spanish. **No visible string is hardcoded**: everything goes in `strings.xml` (`values/` and `values-es/`).

## Stack (do not change without asking)
- Kotlin, Jetpack Compose, Material 3 (dynamic colors + dark mode), `WindowSizeClass`/adaptive layouts
- `minSdk` 26, **`targetSdk` 28** (mandatory: it allows running binaries from the app's storage). Do not raise it without asking.
- Architecture: MVVM + `ui` / `domain` / `data` layers, unidirectional flow (StateFlow)
- Injection: Hilt · Persistence: Room (metadata) · Background: foreground service + WorkManager for downloads
- Emulator: Termux's `terminal-emulator` (Apache-2.0) wrapped in the `terminal` layer; own view in Compose
- proot and talloc: built from source with the NDK, pinned by hash
- Gradle with the Kotlin DSL and a version catalog (`gradle/libs.versions.toml`)

## Free-software rules (F-Droid) — non-negotiable
- **Forbidden**: Firebase, Google Play Services, Crashlytics, analytics, proprietary SDKs, any non-free dependency. **All telemetry is forbidden.**
- Before adding a dependency: check its license (compatible with GPLv3) and ask the user.
- Do not include third-party prebuilt binaries in the repo or in the APK: proot is built in the build; rootfs images are **downloaded** from official sources with SHA-256 verification.
- **Credits and licenses:** any third-party code, library, font or resource that is added, updated or removed updates `THIRD_PARTY_NOTICES.md` in the same change. Copyright notices and original headers are kept intact. Only code compatible with GPL-3.0-or-later is reused: from `termux-app`, **only** `terminal-emulator` and `terminal-view` (Apache-2.0); the rest is GPL-3.0-only and is not copied. Do not use the name, logo or trademark of Termux, Debian, Ubuntu, Alpine or others in a way that suggests affiliation.
- Release metadata in fastlane format: `fastlane/metadata/android/{en-US,es-ES}/`.
- Reproducible builds: no timestamps or non-deterministic values.

## Commands
- Debug build: `./gradlew assembleDebug`
- Unit tests: `./gradlew testDebugUnitTest`
- Instrumented tests: `./gradlew connectedDebugAndroidTest`
- Lint and style: `./gradlew detekt ktlintCheck lintDebug`
- Coverage: `./gradlew koverVerify koverHtmlReport`
- Everything CI runs: `./gradlew check`

## Quality and tests
- Every task ends with `./gradlew check` green. Do not mark a task as done if it fails.
- Test stack: JUnit5 + MockK, Turbine, MockWebServer (downloads), in-memory Room; instrumented tests for the PTY, proot and resizing.
- **Coverage (Kover):** ≥85 % in `domain` and `data`; **100 %** in rootfs/download verification and in the backup format (encryption and restore). Excluded: generated code, `@Preview`, pure Compose UI.
- **Never write empty or tautological tests** to raise the number.
- Kotlin and Lint warnings are treated as errors.

## Workflow
- **One `PLAN.md` task at a time**, on a `feat/<task>` branch.
- Start in plan mode: propose the approach and wait for confirmation before touching code.
- **Conventional Commits** (`feat:`, `fix:`, `test:`, `chore:`, `docs:`...), small and atomic.
- Do not `git push --force`, do not rewrite shared history, do not touch `master` directly.
- When finishing each task: summarize in 2-3 lines what was done and what is left; mark the task in `PLAN.md`.
- If the spec is ambiguous or information is missing: **ask**, do not invent.

## Code conventions
- One file per relevant public class; packages by feature inside each layer.
- No business logic in composables or in heavy ViewModels: it goes in `domain`.
- Immutability by default. Network/IO errors with sealed types, not loose exceptions towards the UI.
- **All I/O off the main thread** (injectable Dispatchers). Long operations (download, extraction, backup, restore) in a service with a notification and either resumable or transactional: never leave a distro half done.
- **Access to rootfs and files goes through an abstraction** (`DistroRepository`/`FileSystemRepository`); no loose `java.io.File` in `ui` or `domain`.
- PTYs and processes are managed by the service, not the Activity: the UI reconnects to live sessions.
- Secrets (SSH keys, backup passwords) are encrypted with the Android Keystore; never in logs or in plain text. The terminal's content is never logged.
- Accessibility: `contentDescription`, touch targets ≥ 48 dp, large font.

## What NOT to do
- Do not implement anything marked "Out of scope" in `SPEC.md` (above all: graphical environment, own bootstrap/`pkg`, SAF).
- Do not depend on the `com.termux` prefix or on Termux binaries.
- Do not change dependency versions by hand: Dependabot manages them.
- Do not disable or relax detekt, ktlint, Lint or Kover to make CI pass.
