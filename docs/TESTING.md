# Testing

There are two layers. Everything that can run without a device runs in `./gradlew check`; the rest
needs a device and is run by hand (`connectedDebugAndroidTest`), never by CI.

## Layer A: host tests (JVM, no device)

```
./gradlew testDebugUnitTest      # unit and integration tests only
./gradlew check                  # all of it: tests, detekt, ktlint, Lint, Kover gates
```

The key flows are covered end to end in `app/src/test/java/.../integration/`, with the real
domain and data classes and fakes only at the edges a device would fill:

| Test | Flow |
|---|---|
| `InstallAndLaunchIntegrationTest` | A distro is installed from a `.tar.gz` served by MockWebServer (real downloader, SHA-256 check, transactional extraction, Room), becomes ready and default, and `ProotSessionPlanner` turns it into a proot command line (also for a non-root user created in the guest). A wrong hash, an HTTP error and a distro whose files vanished leave no distro and no launch. |
| `BackupRoundTripIntegrationTest` | Two distros are installed and configured (default distro and user, profiles, layouts, shortcuts, extra keys, appearance, settings); a backup of everything is exported, plain and encrypted, and restored into an empty second device; the files of each distro and every configuration value are compared. A wrong password restores nothing. |
| `ResizeIntegrationTest` | Window size, system bars, keyboard and text margin become the pty grid (`SessionController`); split panes divide the area and each fake pty is told its own size, once per change (`PaneController`). |

What a host test cannot show is in `DECISIONS.md` (the "validated on a device" notes of T02, T03,
T04, T07, T08b and T15), and Layer B covers it.

Coverage gates are unchanged: Kover requires at least 85% in `domain` and `data`, and 100% line and
branch in `data.rootfs.verify` and `data.backup`. The integration tests add no production code.

## Layer B: instrumented tests (a device or emulator)

Sources: `app/src/androidTest/`. They compile in CI-style runs with
`./gradlew assembleDebugAndroidTest`; they are executed only with a device attached:

```
./gradlew connectedDebugAndroidTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```

Read before running on a phone you use:

- Without `leaveApksInstalledAfterRun=true`, Android Gradle Plugin uninstalls the app after the
  run, which deletes the app's data (installed distros, settings). The flag above keeps it.
- The tests use private directories (`files/it-t18-<random>`) and in-memory databases, and delete
  them afterwards. They never open the app's real database nor `files/storage`.
- `MainActivityLaunchTest` launches the real activity (with the real data) and only opens and
  closes screens. It never saves anything: the profile editor is opened and dismissed.

| Test class | Needs network | What it checks |
|---|---|---|
| `ProotGuestTest` | Yes (skipped without) | Installs Alpine with the app's own installer: official index, download, SHA-256 verification, extraction. Starts a real pty with the app's `libproot.so`; checks `uname -a`, `echo ok`, `/etc/os-release`; resizes the pty twice and checks `stty size` in the guest. |
| `BackupDeviceRoundTripTest` | Yes (skipped without) | Exports a real Alpine distro (encrypted) to a file on the device, restores it in an empty storage, compares every file (content hash, links), and starts the restored distro. A wrong password restores nothing. |
| `MainActivityLaunchTest` | No | Back from Settings returns to the terminal; Profiles with its editor sheet and Keyboard shortcuts with its sheet open without crashing. Needs the screen on and unlocked. |

A test that needs the network is skipped (a JUnit Assumption) only when the official index or the
download cannot be reached. A wrong checksum or an HTTP error from the server is a failure, because
that is what the verification exists to catch.

Run one class or one test:

```
./gradlew connectedDebugAndroidTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.qtekfun.ultimateterminal.ProotGuestTest
```

## Test dependencies

Instrumented tests use AndroidX Test (runner, core, ext:junit) and UI Automator, all Apache-2.0, as
`androidTestImplementation` only: they are not in any APK. They are in
`gradle/libs.versions.toml`, `gradle/verification-metadata.xml` and `THIRD_PARTY_NOTICES.md`.
