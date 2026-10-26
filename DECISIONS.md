# UltimateTerminal — Decisions

A record of the decisions taken during development that were not fixed in `SPEC.md`, or that
deviate from it. One entry per decision, the most recent at the end of its section. Format: date,
decision, reason, alternatives, impact. If a decision contradicts `SPEC.md`, it is noted here and the
user decides whether the spec is updated.

## Pending hardware validation

The agents that develop do **not** test on devices: the orchestrator does, and only on the
Pixel 8 (the user authorized it on 2026-10-04). What was verified there is in the section "First
tests on hardware"; **what does not appear there is still unverified** and must not be taken as
working. Pending, among other things:

- That `libproot.so` starts and runs `/bin/sh` of an Alpine rootfs (T02, see D-005).
- That the loader (`libproot-loader.so`, through `PROOT_LOADER`) is injected correctly on arm64,
  armeabi-v7a and x86_64.
- The behavior of seccomp, `ptrace`, `/proc` and `--link2symlink` on modern Android (API 26–37).
- Running binaries from `nativeLibraryDir` with `targetSdk` 28 on Android 15+/16.
- T07, distro installation: downloading, decompressing and moving a real rootfs in private storage,
  the free-space calculation and the management screen (see "T07: what has NOT been validated").
- The data layer (T05): the Hilt wiring, `AndroidSQLiteDriver` and `java.nio` (symbolic links,
  permissions, `ATOMIC_MOVE`) in the app's private storage (see D-T05-9).
- T03, everything that needs a real PTY or a screen: that `libtermux.so` loads and the `fork/exec` of
  `/system/bin/sh` works; that the drawing (colors, cursor, wide text, font) is correct and smooth;
  the on-screen keyboard (IME), the hardware keyboard, the gestures (scroll and selection) and that `stty size`
  matches what is visible after resizing (see D-T03-4 to D-T03-6 and T04).
- T03, release with R8: that the `JNI` native methods survive minification (Android's default
  rules keep the names of `native` methods, but this has not been checked on a real APK).
- T08b, starting a tab in a distro: that `libproot.so` runs from `nativeLibraryDir` with
  `targetSdk` 28, that `uname -a` and `apk update` work inside Alpine, that there is network (DNS), that `su -l <user>`
  works, that the T13 mounts are visible and that the compatibility mode (without seccomp) fixes what fails
  (see D-T08b-8 with the acceptance criterion of the first test on the tablet).
- T14, SSH: the real Keystore, `ssh` inside proot with the temporary key, the file picker and the
  cleanup of the key file (see "T14: what has NOT been validated").
- D-PK, passphrase-protected SSH keys: the dialogs (generate, import, set/change/remove, unlock),
  the time one `bcrypt_pbkdf` run takes on a low-end phone, connecting with a protected key and with
  "keep unlocked", and the fields being cleared on rotation (see "D-PK: what has NOT been validated").
- T08, foreground service: that Android starts it with the `specialUse` type on 12–16, that the
  notification appears and its actions (new session, exit) work, that the shell survives
  closing the activity and turning the screen off, and that the UI reconnects to the live session. Also the
  real wake lock and the effect of the *phantom process killer* (see D-T08-5).
- T09, tabs: the bar's gestures (tap, double tap, long press + drag to
  reorder, scroll of the bar itself) may compete with each other; the drop-down menus, the
  dialogs, the side bar on wide screens (and with a right-to-left language), the 48 dp
  touch targets, TalkBack, and that when switching tab the pty of the one that comes to the foreground receives the
  right size (see D-T09-8).

What is verified without a device: it builds for the three ABIs, the executables are packaged
in the APK with the ELF type and the expected entry points, `./gradlew check` and CI green.
- T13, access to `/sdcard`: that the storage permission is granted with `targetSdk` 28 on Android 11, 12, 13 and later and
  that with it the native processes (proot) can read and write in `/storage/emulated/0`, including Downloads and Documents;
  that `cp /var/log/syslog ~/storage/downloads/` shows up in the Downloads folder (see D-T13-3 and "T13: what has NOT been validated").
- T10, split panes: see D-T10-9. The draggable dividers, focus by touch, the pane
  menu, the size of each pty after splitting, closing or zooming, and the gestures of each pane have been
  tested only with host tests.

## Decisions

### D-001 · 2026-10-04 · proot: use the Termux fork as a git submodule
- **Decision:** the proot sources are the `termux/proot` fork at tag `v5.1.107.96`, as a
  submodule in `third_party/proot`.
- **Reason:** it is proot (GPL-2.0-or-later) with the Android patches that Termux maintains and uses in
  production; the submodule pins the exact commit and F-Droid supports it. It is code, not prebuilt
  binaries and not the `com.termux` prefix, so it meets SPEC §2 ("built from source").
- **Alternatives:** upstream `proot-me/proot` (fewer Android patches); copying the sources into the repo
  (dirties the history and makes updating harder).
- **Impact:** CI and the build need `submodules: true`. It is credited in `THIRD_PARTY_NOTICES.md`.

### D-002 · 2026-10-04 · talloc: vendored with an own `replace.h`
- **Decision:** `talloc.c` and `talloc.h` 2.5.0 unmodified in `third_party/talloc`, with a minimal
  hand-written `replace.h` (also `MIN`/`MAX` and `memset_explicit`, which bionic does not have
  on API 26).
- **Reason:** Samba's `replace.h` is generated by waf (Python, configure with checks); bringing it in
  makes the build much bigger. talloc only needs a few definitions.
- **Alternatives:** building talloc with its waf (fragile when cross-compiling); using Termux's
  `libtalloc` (a binary, tied to the prefix).
- **Impact:** LGPL-3.0-or-later statically linked in a GPL-3.0-or-later project: compatible.
  The tarball's hash is recorded in `third_party/talloc/README.md`. The tarball's GPG signature
  has not been verified (only the SHA-256 when downloading it).

### D-003 · 2026-10-04 · ABIs: arm64-v8a, armeabi-v7a and x86_64
- **Decision:** those three are built. No x86 (32-bit).
- **Reason:** arm64 and armeabi-v7a cover phones/tablets; x86_64 serves emulators and tests.
- **Impact:** the 32-bit loader (`loader-m32`, which proot uses on arm64/x86_64 to run
  32-bit binaries inside a 64-bit distro) **is not built**. 32-bit programs in
  a 64-bit distro will not work. 64-bit rootfs images (Debian, Ubuntu, Alpine arm64) do not
  need it.

### D-004 · 2026-10-04 · proot and the loader packaged as `lib*.so`; NDK pinned
- **Decision:** `add_executable` with `OUTPUT_NAME`/`PREFIX lib`/`SUFFIX .so` (`libproot.so`,
  `libproot-loader.so`), `useLegacyPackaging = true` and `android:extractNativeLibs="true"` so that
  they end up extracted in `nativeLibraryDir`. `ndkVersion` pinned to `28.2.13676358`.
- **Reason:** Android only lets an app with `targetSdk` 28 execute from `nativeLibraryDir`.
  Empirically AGP packages those executables with no further ado (checked in the APK). The NDK is pinned so
  the native build is reproducible on F-Droid.
- **Alternatives:** downloading/extracting the binaries at runtime to private storage (not
  allowed with targetSdk ≥ 29 and against "no third-party binaries").
- **Impact:** the APK is bigger (~0.5 MB per ABI). The loader is located with the
  `PROOT_LOADER` variable, not a compiled path. CI checks that the 6 libraries are in the APK.

### D-005 · 2026-10-04 · T02 without a device test (deviation from the plan)
- **Decision:** T02 is marked as done with the build and packaging verified, but **without
  running proot on a device**. The test with Alpine stays pending.
- **Reason:** the user forbade testing on the Pixel 8. It is not run on the host or on an
  emulator either without their express authorization.
- **Impact:** risk no. 1 of `SPEC.md` (proot on modern Android) remains **open**. T07/T08 must not
  be built assuming it works without validating first.

### D-006 · 2026-10-04 · `libandroid-shmem` is not linked
- **Decision:** `WITH_LIBANDROID_SHMEM` is not defined.
- **Reason:** it is an extra Termux library (another component and license) that only emulates
  SysV shared memory; it is not needed for a shell, apt/apk, ssh, nmap or python.
- **Impact:** programs that use SysV shared memory (e.g. some `postgres`) may fail
  in the distro. It will be reviewed later if needed.

### D-007 · 2026-10-04 · Starting proot: default flags and environment
- **Decision:** `ProotCommandBuilder` uses `--link2symlink`, `--kill-on-exit`, `-0` (simulated root),
  binds of `/dev`, `/proc` and `/sys`, `PROOT_TMP_DIR` in a private directory and a clean guest
  environment (`env -i` with `HOME`, `TERM`, `LANG`, `PATH`). `PROOT_NO_SECCOMP=1` is an option, off
  by default.
- **Reason:** `link2symlink` because the app's storage does not support hard links;
  `PROOT_TMP_DIR` because `/tmp` does not exist on Android; the clean environment because the process's is
  Android's; seccomp is left as a switch in case certain kernels break it.
- **Alternatives:** `--sysvipc`, `-L`, `--root-id` only with a normal user: to be decided when testing.
- **Impact:** the flags are reasonable hypotheses **not validated on a device** (see the section
  on pending items); the builder is covered with host tests.

### D-008 · 2026-10-04 · Coverage: the `data` package now counts
- **Decision:** with `data.proot` the Kover threshold (≥ 85 % in `domain` and `data`) starts being measured
  for real. The critical packages at 100 % (`data.rootfs.verify`, `data.backup`) stay empty
  until T06/T15.
- **Impact:** every new piece of code in `data` needs real tests.

### D-009 · 2026-10-04 · The loader is linked with `--no-gc-sections`
- **Decision:** `libproot-loader.so` is linked with `--no-gc-sections`.
- **Reason:** in release builds CMake adds `--gc-sections`, which removed `pokedata_workaround`
  (arm64): nobody references it, but proot locates its offset at runtime. It was detected because
  `loader-info` failed in release; without that check it would have been a silent failure.
- **Impact:** the generated offset is identical in debug and release (1016 on arm64).

## T03 — Terminal (emulator and view)

### D-T03-1 · 2026-10-04 · `terminal-emulator` vendored as a module, not through JitPack
- **Decision:** the sources of Termux's `terminal-emulator` (tag `v0.118.3`) go **unmodified** in the
  Gradle module `:terminal-emulator` (Java, JNI `termux.c` and its tests). Only its
  `build.gradle.kts` and a `CMakeLists.txt` that replaces `Android.mk` are ours.
- **Reason:** F-Droid builds from source and does not accept a binary already built by third parties (the JitPack
  artifact); besides, the JNI has to be built with the NDK anyway, and this way the exact commit is pinned
  and the build is reproducible.
- **Alternatives:** the `com.termux.termux-app:terminal-emulator` dependency from JitPack (convenient, but
  somebody else's binary and one more repository); writing an emulator of our own (SPEC §8: months of work and risk).
- **Impact:** 3 ABIs (arm64-v8a, armeabi-v7a, x86_64) and the same NDK as `:app`. Updating Termux is
  copying again from another tag. The 145 upstream tests pass on the host JVM
  (`:terminal-emulator:testDebugUnitTest`) with JUnit 4 (test only).

### D-T03-2 · 2026-10-04 · The emulator's license: an open point to confirm
- **What was found:** no file in `terminal-emulator/` carries a license header and the directory
  has no `LICENSE`. The only statement is termux-app's root `LICENSE.md`: the repository is
  GPL-3.0-only **except** the code derived from Android Terminal Emulator (Jack Palevich), in the
  `terminal-view` and `terminal-emulator` libraries, which is Apache-2.0. It does not say so file by file and the
  library has grown since then (e.g. sixel and bitmap support).
- **Decision:** it is used under that statement, unmodified, with the Apache-2.0 text in
  `terminal-emulator/LICENSE`, the credit to Termux and to Jack Palevich, and the MIT notice of
  `jquast/wcwidth` (from which `WcWidth.java` derives) in `terminal-emulator/NOTICE-wcwidth.txt`. Nothing
  else is copied from termux-app.
- **Risk:** if some part were actually GPL-3.0-only, it would still be compatible with our
  GPL-3.0-or-later, but **not** with the "or-later" part nor with the Apache-2.0 label. It is an
  upstream uncertainty, not something we can resolve from here.
- **Proposed action (the user decides):** ask the Termux maintainers to confirm the
  library's license (or to add headers) before publishing on F-Droid; if they do not confirm it,
  replace the emulator with one of our own. This is not legal advice.

### D-T03-3 · 2026-10-04 · Own view in Compose; `terminal-view` is not used
- **Decision:** a `TerminalPainter` was written from scratch (an Android `Canvas` inside a Compose
  `Canvas`) that only reads the emulator. No code from `TerminalRenderer` or `terminal-view` was copied;
  that class was read to understand how styles are interpreted (indexed and 24-bit color, bold
  with bright colors, dimmed to 2/3, inverse).
- **Reason:** SPEC §2 asks for an own view in Compose and avoids depending on the part with a doubtful license
  (D-T03-2). The color rules were isolated in `domain/terminal/CellStyles` to test them on the host.
- **Impact:** drawing groups ASCII cells of the same style in a single call and draws the
  rest (CJK, emoji, combined) separately, shrinking them to their cells if they do not measure what is expected. No blinking
  (`blink`), no sixel or images, no special underlines: outside the prototype.

### D-T03-4 · 2026-10-04 · Keyboard through an invisible `View`, not Compose's text protocol
- **Decision:** the on-screen and hardware keyboards reach a `TerminalInputView` (1 dp, focusable) with
  `onCreateInputConnection`. A visible-password input without suggestions is requested so the keyboard
  sends characters as they are typed; what arrives as "composition" is held back and sent when
  it is confirmed. Backspace is sent as `DEL`, Enter as `\r`.
- **Reason:** it is the pattern Android terminals already use and it does not depend on Compose's experimental APIs
  (`PlatformTextInputModifierNode`). Without a device neither can be validated, so
  the lower-risk one was chosen.
- **Impact:** **not validated**: Gboard and other keyboards remain to be checked (composition, autocorrect,
  backspace on an empty field, dead keys), sticky Ctrl/Alt (T11) and the focus when returning to the app.

### D-T03-5 · 2026-10-04 · Gestures and selection: minimal, not validated
- **Decision:** a tap asks for the keyboard; a vertical drag scrolls the scrollback; a long press and
  drag select by flow and "Copy" appears. There is no paste button yet (the emulator already
  knows how to paste with bracketed paste; the action in the interface is missing).
- **Reason:** to cover what is essential (RF-01, RF-08) for the prototype. The three gesture detectors
  are on the same node and **could compete** (vertical drag against long press): this could not
  be checked without a device.
- **Impact:** pending for T11: selection handles, selection by word, paste, mouse (mouse
  reporting), wheel, pinch zoom and scrolling with inertia.

### D-T03-6 · 2026-10-04 · Performance: not measured
- **Status:** nothing has been measured (there is no device). The design avoids allocating memory per frame
  (a single `Run` and a reused `Paint`) and groups text into spans; this is an expectation, not data.
- **Pending:** measure with massive output (`cat` of a large file, `yes`) and scrolling, with the
  criterion of SPEC §6 (smooth without blocking the UI). If it is not enough, a row cache is evaluated or drawing on
  a `SurfaceView`/own layer.

### D-T03-7 · 2026-10-04 · Lint relaxed only in the vendored module
- **Decision:** in `:terminal-emulator` Lint does not treat warnings as errors (`warningsAsErrors=false`,
  `abortOnError=false`, `checkReleaseBuilds=false`). In `:app` it stays strict.
- **Reason:** it is third-party code that is deliberately not modified; its Lint findings are not
  ours. It is an exception to the `CLAUDE.md` rule "do not relax Lint", limited to that module.
- **Alternatives:** patching the upstream sources (it would break "unmodified" and easy updating).

### D-T03-8 · 2026-10-04 · Prototype: Android shell and logic in `domain/terminal`
- **Decision:** the prototype launches `/system/bin/sh` with a minimal environment (`TERM=xterm-256color`,
  `COLORTERM=truecolor`, private `HOME` and `TMPDIR`, the system's `PATH` and only the
  `ANDROID_*` variables of the process). proot will be connected in T07/T08. Everything that does not touch Android (cell
  styles, grid size, scroll, selection, key encoding, environment) is in
  `domain/terminal` and tested on the host; the Android glue (`TerminalSessionHost`, `TerminalPainter`,
  `TerminalInputView`, the screen) stays out of Kover, like the UI.
- **Impact:** sequences such as those printed by `ls --color` and `top` (colors, cursor
  positioning, erasing, alternate screen, scrollback, double width, resizing) are verified against the
  real emulator with host tests (`EmulatorScreenTest`). Real programs (vim, tmux, htop, `ls`,
  `top`) have not been run: there is no distro or device.
- **Logs:** the library's client discards its log messages: they may contain terminal
  text and SPEC §6 forbids recording it.

## T04

### D-T04-1 · 2026-10-04 · A single function decides the terminal's size
- **Decision:** `terminalLayoutFor(window, EdgeInsets, cell metrics)` (in `domain/terminal`) is the
  only source of the size. The drawn area is the window minus the insets, and the UI applies as padding
  those same numbers; so what is drawn and what the pty is told cannot drift apart. The insets
  are those of the system bars, the screen cutout and the keyboard, combined edge by edge with
  the maximum (`EdgeInsets.union`): the keyboard replaces the navigation bar, it is not added.
- **Change from T03:** the UI no longer uses `safeDrawingPadding()` nor measures the `Canvas`; it measures the whole
  window (the black background also covers under the bars: no bands) and computes the layout with the function.
- **Alternatives:** letting Compose distribute the insets and measuring the `Canvas` (simpler, but the
  semantics of the insets stay outside the domain and without tests).

### D-T04-2 · 2026-10-04 · Resizing with a debounce and without restarting the session
- **Decision:** layouts go through `Flow.settled(120 ms)`: the first is delivered at once (the shell
  starts already with its size) and afterwards only the last arrives, when no other comes in 120 ms; equal ones
  are discarded. This way a keyboard animation or dragging a window's edge does not send a `SIGWINCH`
  per frame. `TerminalSessionHost.resize` already ignored equal sizes and calls
  `TerminalSession.updateSize`, which resizes the emulator and the pty (`TIOCSWINSZ`) without touching the
  process: the content and the shell are kept.
- **Reason for the value:** 120 ms skips the ~8 frames of the keyboard animation without being noticed as a
  delay. It is an unmeasured estimate.
- **Dependencies:** `kotlinx-coroutines-core` 1.11.0, declared explicitly because the code uses it
  directly (before it arrived transitively with `lifecycle`, at 1.9.0), and `kotlinx-coroutines-test` 1.11.0
  (tests only). Both Apache-2.0. Lint (`NewerVersionAvailable`) fails with 1.9.0 and did not accept 1.11.0
  only for tests while production resolved 1.9.0. It is noted in `THIRD_PARTY_NOTICES.md`.

### D-T04-3 · 2026-10-04 · Activity: no recreation and resizable
- **Decision:** `configChanges` adds `navigation|fontScale|layoutDirection|locale` to what was already there
  (orientation, sizes, keyboard, `uiMode`, density), and `resizeableActivity="true"` is explicit (for
  `targetSdk` 28 it is already the default, but this way it does not depend on an implicit value on foldables and
  multi-window). `ManifestWindowConfigTest` fails if someone removes it. The `ViewModel` (and with it the
  shell) already survives a recreation, but recreating would rebuild the view and the input field.
- **Ruled out:** `androidx.window` / `WindowSizeClass` in this prototype: the calculation does not depend on
  size classes. It will be evaluated (Apache-2.0 license) in T10, when the layout of tabs and panes
  depends on the width.

### D-T04-4 · 2026-10-04 · Risk: `adjustResize` with edge-to-edge
- **Risk:** `windowSoftInputMode="adjustResize"` is kept and the app draws edge-to-edge. From API 30
  the keyboard arrives as insets and the window is not resized; on API 26-29 the behavior depends on
  how `enableEdgeToEdge` propagates the insets. If at some API level the system resized the
  window **and** the keyboard were also subtracted as an inset, the terminal would lose the keyboard's height
  twice.
- **Pending:** check it on API 26-29 and 30+ (see below). If it happens, the way out is not to subtract the
  keyboard on the APIs where `adjustResize` already does it.

### T04: what has NOT been validated (without a device; a real tablet and a phone are needed)
Covered on the host (26 tests): the grid calculation for phone, portrait and landscape tablet, split
screen, floating window, side cutout and keyboard; the debounce with virtual time; that the real
emulator adopts the new size and keeps the content and the history; and the manifest attributes.
Not validated:
1. `stty size` and `echo $LINES $COLUMNS` match what is visible after rotating, entering/leaving
   multi-window and split screen, opening/closing the keyboard and folding/unfolding.
2. That the pty really receives `TIOCSWINSZ` and the programs (`vim`, `tmux`, `htop`) redraw.
3. That there are no bands or gaps: the area takes 100 % under the bars on a tablet and a phone, with and without
   a screen cutout, and with the tablet's taskbar.
4. The keyboard's behavior on API 26-29 against 30+ (D-T04-4) and with a hardware keyboard connected.
5. That the activity is not recreated in any of those changes (the test only checks the manifest).
6. That 120 ms of debounce feels right and does not make the content jump.
7. Resizing the floating window by dragging (freeform) on tablets with that mode.

## T05 — Room and repositories

### D-T05-1 · 2026-10-04 · Room 3 as in UltimateDeck, the system SQLite in the app
- **Decision:** `androidx.room3` 3.0.3 (Deck's versions). In the app, `AndroidSQLiteDriver`
  (the system SQLite); in the host tests, the JVM `BundledSQLiteDriver`. The schema is exported to
  `app/schemas`, version 1, with no destructive migration: raising `UltimateTerminalDatabase.VERSION` requires
  adding its migration (checked by `DatabaseSchemaTest`) and a case in `MigrationTest`.
- **Reason:** the same pattern proven in Deck; the system SQLite does not fatten the APK.
- **Alternatives:** SQLite bundled in the APK (more weight); DataStore or hand-made JSON (no schema or
  verifiable migrations).
- **Impact:** `MigrationTest` creates a database with each exported schema and opens it with the current
  code; today only version 1 exists.

### D-T05-2 · 2026-10-04 · Settings in a Room key-value table
- **Decision:** `AppSettings` is stored in the `setting` table (`key`, `value` as text), not in
  DataStore. A missing or unreadable value falls back to its default; the *scrollback* is bounded to
  100..1 000 000 when saving.
- **Reason:** the settings travel in the backup (T15) together with the rest of the metadata and
  stay in a single database; it adds no dependency. A damaged row must not break the app.
- **Alternatives:** DataStore (another dependency and another file to back up).
- **Impact:** **the key names (`SettingKeys`) are part of the backup format**: do not
  rename them without a migration.

### D-T05-3 · 2026-10-04 · Paths always relative and validated (`FsPath`)
- **Decision:** each distro's directory is stored as a path relative to the storage
  root (`FsPath`), never absolute. `FsPath` rejects empty, absolute, empty segments,
  `.`, `..`, `\` and NUL. Rows whose path is not valid (e.g. from a tampered backup) are not
  returned.
- **Reason:** a backup restored on another device (T15) must not depend on where
  `filesDir` is, and a path coming from a backup must not be able to leave the root.
- **Impact:** `ui` and `domain` do not use `java.io.File`; the absolute path only exists as a `String` in
  `FileSystemRepository.absolutePathOf`, to build proot's command line.

### D-T05-4 · 2026-10-04 · `FileSystemRepository` does not follow symbolic links and copies atomically
- **Decision:** if any intermediate component of a path is a symbolic link, the operation is
  rejected (`InvalidPath`); tree walks do not follow links. `copyRecursively` builds
  a `<destination>.partial` copy and renames it (all or nothing; a `.partial` from a previous close is
  replaced). Deletion gives the owner write permission on read-only directories. Special
  files (sockets, pipes, devices) are skipped and hard links end up as independent
  copies.
- **Reason:** a rootfs is full of absolute links (`/var/run -> /run`); following them would allow
  deleting or reading outside the root. It is the most important security property of this layer and it has
  specific tests.
- **Alternatives:** a plain `Files.copy` without a temporary folder (it leaves distros half done, against CLAUDE.md's
  rule).
- **Impact:** T07 (duplicate/delete) and T15 (restore) must use only this interface.

### D-T05-5 · 2026-10-04 · A single default distro, guaranteed by a transaction
- **Decision:** the first registered distro is the default. `setDefault` leaves exactly
  one. When the default one is deleted, the **oldest** of the remaining ones becomes it. It is done in
  DAO transactions, not with a constraint in the database (SQLite through Room does not offer partial
  indexes).
- **Reason:** a new tab must always have somewhere to open; without this rule, deleting the
  default one left the state with no default distro.
- **Impact:** the SPEC does not fix this behavior; if "none" is preferred, it is a one-line
  change in `deleteAndPromote`.

### D-T05-6 · 2026-10-04 · Names unique ignoring case; SSH entries validated
- **Decision:** distros, profiles, layouts and hosts have a name unique ignoring case
  (`COLLATE NOCASE` + a transactional check), of 1 to 64 characters and with no control characters.
  The SSH host must match `[A-Za-z0-9._:%\[\]-]+` and not start with `-`; the user, a pattern with no
  spaces or leading `-`; the port, 1..65535. Profiles: font 6..72 sp, *scrollback* 100..1 000 000.
- **Reason:** a host `-oProxyCommand=...` passed to `ssh` would be interpreted as an option (argument
  injection). It is rejected already when saving, in addition to whatever T14 must do when launching the command.
- **Impact:** T14 must keep passing `--` before the destination even if the data is already validated.

### D-T05-7 · 2026-10-04 · The pane tree is stored as JSON
- **Decision:** `LayoutNode` (`pane` / `split`) is serialized with kotlinx.serialization to a text
  column, with `type` as the discriminator and unknown keys ignored (a layout from a later
  version still opens). The `ratio` of a `Split` must be strictly between 0 and 1; a
  layout with unreadable JSON or an invalid `ratio` is not returned instead of breaking the list.
- **Alternatives:** relational node tables (more complex with no gain: the tree is always
  read and written whole).
- **Impact:** the JSON format of the configuration backup (T15) can reuse this one.

### D-T05-8 · 2026-10-04 · Fakes: only those already needed, verified with the same contract
- **Decision:** `FakeDistroRepository` and `InMemoryFileSystemRepository` (in `src/test`) pass the
  same contract tests as the real implementations (`DistroRepositoryContract`,
  `FileSystemRepositoryContract`), so that a fake does not drift from reality. There are no fakes of
  profiles, layouts, hosts or settings yet; they are added when a task needs them.
- **Impact:** T07 can test distro installation without a database or a disk.

### D-T05-9 · 2026-10-04 · What is NOT validated
- Everything was verified on the host JVM (in-memory Room with bundled SQLite, `java.nio` on
  Linux's file system). **It has not been tested on Android**: the Hilt wiring (`di/`), the
  `AndroidSQLiteDriver`, the creation of `files/storage`, and that `java.nio` (symbolic links,
  POSIX permissions, `ATOMIC_MOVE`) behaves the same in the app's private storage.
- Coverage of `domain` + `data` at the time of this task: 99.4 % of lines and 92.4 % of branches.

## T11

### D-T11-1 · 2026-10-04 · A single router decides what each input means
- **Decision:** `InputRouter` (in `domain/terminal`) receives everything that comes from the keyboard (hardware
  keys, on-screen keyboard text and taps on the extra-keys row) and returns
  `RoutedInput`: an app shortcut, a key for the shell, text, or nothing. `TerminalKeyboard` only
  executes that result. This way sticky Ctrl/Alt apply equally to the three sources (tapping CTRL and
  typing `c` on the on-screen keyboard sends Ctrl+C) and everything is tested on the host.
- **Modifier keys on their own** (Shift, Ctrl, Alt, Meta, Caps Lock...) are ignored and do not spend an
  armed modifier.
- **Alternatives:** leaving the logic in `TerminalInputView` (it cannot be tested on the host).

### D-T11-2 · 2026-10-04 · Sticky Ctrl/Alt: one tap arms, another locks
- **Decision:** each tap cycles off → armed (only the next key) → locked → off.
  "Double tap = locked" is implemented as a second tap while it is still armed, with no time
  window: it is deterministic and does not depend on how long the user takes.
- **Alternative:** a long press to lock (like Termux). Discarded: harder to discover
  and to make accessible.
- **Accessibility:** the state (active for the next key / locked) is announced with
  `stateDescription`; the spoken names of each key are in `strings.xml` (en/es).

### D-T11-3 · 2026-10-04 · Extra-keys and shortcut configuration as text, with no new dependency
- **Decision:** `ExtraKeysConfig` and `ShortcutMap` are pure, immutable models with
  `serialize()`/`parse()` in plain text (one row per line with ids; `ctrl+shift+t=new_tab`).
  `parse` is tolerant: it discards what is unknown and returns it in a list of rejections, and if nothing
  is left it uses the default value. `ExtraKeysStore` is only an interface: persistence
  (settings/Room) is done by another task.
- **Reason:** the JSON format planned for the backup (SPEC §5) would need `kotlinx-serialization`,
  a new dependency that is not needed here. When the configuration backup exists (T15), these
  texts can go as they are as fields of that JSON.
- **Default row:** two rows of seven keys, which fit in 360 dp with the 48 dp touch size.

### D-T11-4 · 2026-10-04 · Default shortcuts and their known clashes
- **Values:** Ctrl+Shift+T new tab, Ctrl+Shift+W close, Ctrl+Tab / Ctrl+Shift+Tab next
  and previous, Alt+1..9 go to tab n, Ctrl+Shift+C / Ctrl+Insert copy, Ctrl+Shift+V /
  Shift+Insert paste, Ctrl+Shift+`+` / Ctrl+Shift+`-` / Ctrl+Shift+0 zoom (plus the numeric keypad's Ctrl+`+` and
  Ctrl+`-`).
- **Clashes:** Alt+digit clashes with readline's numeric arguments (the SPEC asks for it, it is
  reassignable). Plain Ctrl+`-` and Ctrl+0 are **not** used for zoom because the terminal
  needs them (`^_` undo in readline).
- **Rule:** a shortcut must carry Ctrl or Alt (Shift only types capitals), with the only
  exception of Shift+Insert; `bind` and `parse` reject anything else, so a badly
  configured shortcut cannot take normal typing away from the user.
- **No effect yet:** the tab shortcuts come out as events (`appShortcuts`) that nobody
  handles until T09. Copy, paste and zoom do act. With Ctrl+Shift+C and no selection nothing is sent
  to the shell.

### D-T11-5 · 2026-10-04 · Paste: the emulator's is used
- **Decision:** it is not reimplemented. `TerminalEmulator.paste` already strips ESC and the C1s, converts
  line breaks into carriage return and wraps with `ESC[200~ … ESC[201~` if the program turned on the
  bracketed mode. `PasteTest` checks it with the real emulator, including that a pasted text
  containing `ESC[201~` cannot close the bracket early.
- **Pending:** text that arrives through the on-screen keyboard's `commitText` (long-text
  autocomplete) is sent without bracketed paste: it is typing, not pasting. If some keyboard pastes that way,
  it will have to be handled.

### D-T11-6 · 2026-10-04 · Pinch zoom: the exact size apart from the displayed one
- **Decision:** `FontZoom` keeps the exact size (between 8 and 40 sp) and shows it rounded to
  half a point. A slow pinch gives many very small factors; rounding each one would make the
  size never move. Shortcut steps are 1 sp and the reset goes back to 14 sp.
- **Effect:** when the size changes the cell changes, and the layout is recomputed (T04) and reaches the pty
  with the usual debounce. No persistence yet.

### D-T11-7 · 2026-10-04 · The extra-keys row reserves its height in the layout
- **Decision:** the row sits above the keyboard and the bars; its height (rows × 48 dp, or 0 if it is
  hidden) is added to the bottom edge with `EdgeInsets.reserveBottom` before computing the grid,
  so the pty does not count that space. A fixed height of 48 dp per row, the accessibility touch minimum.
- **Hidden row:** the configuration has `visible`; the setting that changes it arrives with T16.

### T11: what has NOT been validated (without a device) and what is missing
Covered on the host (everything in `domain/terminal`): sticky states, catalog and (de)serialization of
extra keys, exact modifier matching and (de)serialization of shortcuts, the router with the
real `KeyEncoder` (Ctrl+C, Alt+x, Ctrl+arrow...), zoom, height reservation and paste with the real
emulator.
Not validated on a device:
1. **Three gestures on the same node** (tap, vertical scroll, long press) plus the new
   pinch. The pinch goes last in the `pointerInput` chain to see the events first and
   only consumes with two or more fingers, but it has not been checked that it does not compete with scrolling
   (a finger that already dragged before the second one comes in) nor with selection.
2. **A real IME:** `commitText`, `setComposingText` and the keys of other keyboards with sticky
   Ctrl/Alt (a keyboard that sends the already composed character does not go through `KeyEvent`).
3. **A real hardware keyboard:** that `onKeyDown` receives Alt+digit, Ctrl+Tab and Ctrl+Shift+letter (the
   system or the keyboard may keep them), `numLock` and non-US keyboards (the
   code of `+` and `=` changes with the layout).
4. **The extra-keys row:** that tapping it does not take the focus from the input view and close the
   keyboard; its look; its real height against the calculated 48 dp.
5. **Zoom performance:** recreating the `TerminalPainter` at every step of the pinch.
6. **The real clipboard** with Ctrl+Shift+C/V.
Still to do:
- **Mouse and wheel** (mouse reporting to the shell, wheel for the history): it is not in this change.
- **Persistence and setting of the row and of the shortcuts:** another task (settings, T16).
- **Effect of the tab shortcuts:** T09.

## T08 — Foreground service and sessions

### D-T08-1 · 2026-10-04 · The sessions' lifecycle is pure logic in `domain/session`

- **Decision:** `Sessions` (an immutable snapshot) and `SessionController` (with `SessionFactory`,
  `SessionHandle` and `ServiceControl` as interfaces) decide which sessions exist, which one is active
  and when the service must run. They use no Android types.
- **Reason:** CLAUDE.md asks for business logic in `domain`, and this way the rules of SPEC RF-07
  ("the service lives while a shell runs and only then") are tested on the host.
- **Rules:** the service runs if there is at least one *running* shell; an ended session stays
  listed, so its output can be read or it can be restarted, but it does not keep the service. When closing the
  active one, a running one is preferred. Ids are not reused. A shell that cannot start ends up
  as an ended session with status `-1`; one that ends before `start` returns is registered
  all the same (the session is published before the shell is started).
- **Impact:** `SessionManager` (Android, `@Singleton`) is only a thin adapter. The Android layer has no
  host tests because it needs a PTY.

### D-T08-2 · 2026-10-04 · Service type `specialUse`, not `dataSync`

- **Decision:** `foregroundServiceType="specialUse"` with the subtype `terminal_sessions`, plus the
  permissions `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE`.
- **Reason:** no standard type describes a terminal. `dataSync` is the one used by habit,
  but it is meant for finite transfers and Android 15 puts a cap of 6 hours per day on it
  when `targetSdk` is ≥ 35; it would cut a long SSH. `specialUse` has no cap. The review that
  Google Play requires for `specialUse` does not apply: it is not published there (SPEC §2).
- **Alternatives:** not declaring a type (what Termux does with `targetSdk` 28), valid today because the
  type is only mandatory with `targetSdk` ≥ 34; it is declared anyway so as not to have to
  touch it the day the `targetSdk` is raised.
- **Impact:** `ServiceCompat.startForeground` passes the type only from API 34. **Not validated on a
  device.** If some Android rejected `specialUse` with `targetSdk` 28, the alternative is to remove
  the type and keep only the permissions.

### D-T08-3 · 2026-10-04 · The service does not own the sessions; `SessionManager` does

- **Decision:** the sessions live in a Hilt singleton with the process's lifetime. The service only
  keeps the process alive, shows the notification, holds the wake lock and stops itself.
  `TerminalViewModel` no longer starts or stops the shell: it reconnects to the active one. Closing the activity
  with "back" does not kill the shell while the service runs.
- **Reason:** the UI reconnects to live sessions after recreating or closing the activity (SPEC RF-07),
  and a *bound* service or a `Binder` would have added one more lifecycle for no gain: service and
  activity share a process.
- **Details:** the ViewModel's frame counter counts each emission of the active host, because
  the counters of two hosts can coincide and the screen would not redraw when switching
  session. "Exit" in the notification closes all the sessions and the activity; after the first
  layout the ViewModel no longer creates a session by itself, so as not to resurrect the shell
  while the screen is closing.
- **Pending:** switching between sessions from the UI (tabs) is T09; here there is only one active at
  a time, created on opening or from the notification.

### D-T08-4 · 2026-10-04 · Permissions requested in context and battery optimization only as a notice

- **Decision:** when a shell is running, once per start and as a dialog with an
  explanation, first the notification permission (API 33+) and then the battery notice are shown. The
  order and conditions are in `nextPrompt`, tested. What is rejected is not asked again in that
  start (persisting it, with the settings, is T16).
- **Battery:** `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` is opened, the system's settings screen.
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is **not** declared, which would show a direct
  exclusion dialog: the app does not exclude itself and a test watches for it. It is the "no forced
  exclusion" the plan asked for.
- **Notifications with `targetSdk` 28:** Android 13+ only requires the runtime permission from
  apps with `targetSdk` ≥ 33, and shows the others its own notice when the channel is created. The app asks
  for it explicitly, with its explanation, before that happens. **Not validated on a device** which
  of the two appears first.
- **Wake lock:** `PARTIAL_WAKE_LOCK` with no maximum time, while there is a shell and the `keepAwake`
  setting (the T05 model) is on; it is released when the last shell ends and in `onDestroy`.
  Lint's `WakelockTimeout` is suppressed at that point, with a reason: a cap would defeat the feature.
  Changing the setting from the UI arrives with T16.

### D-T08-5 · 2026-10-04 · Phantom process killer (Android 12+): documented, with no mitigation in code

- **What it is:** since Android 12 the system limits child ("phantom") processes to 32 per app and kills
  the oldest when exceeded, **even with a foreground service**. Every program that launches a
  shell (and with proot, every program of the distro) counts. A `tmux` with many panes or several
  sessions with SSH, `ssh` and `top` can reach the limit and die without warning.
- **What is needed from the user today:** on Android 14 QPR1 and later, Developer options →
  "Disable child process restrictions". On 12–13 it is only disabled with `adb`
  (`settings put global settings_enable_monitor_phantom_procs false`, or
  `device_config put activity_manager max_phantom_processes 2147483647`).
- **Decision:** the app cannot change that setting (it needs a system permission), so nothing is
  attempted in code. It will be documented in the README and the privacy policy (T21) and is
  a point to explain on first run when the help screen exists.
- **Impact:** the service keeps the process alive, but does not guarantee that all its children survive.
  **Not measured:** it is not known how many phantom processes each shell with proot counts (T07).

### D-T08-7 · 2026-10-04 · The selection moves out of the `ViewModel` into `SelectionController`

- **Decision:** `SelectionController` (in `terminal/`, like `FontSizeController`) holds the selection and
  copies the text of the active host. `TerminalViewModel` exposes it as `selection` and the screen calls
  `viewModel.selection.start/extend/clear/copy`.
- **Reason:** detekt (`TooManyFunctions`, 11/11) failed in `TerminalViewModel` when the session lifecycle was added.
  The rule was not relaxed (CLAUDE.md): the logic was moved to a collaborator, the pattern that zoom and the
  shortcuts already used.
- **Impact:** an internal API change with no visible effect. Not validated on a device, like the rest.

### D-T08-8 · 2026-10-04 · `POST_NOTIFICATIONS` with an `SDK_INT` guard, and a corrected test

- **Decision:** the permission is requested only if `SDK_INT >= 33`, with an explicit check instead of
  silencing `InlinedApi`. The dialog was already only shown on API 33+, but Lint did not know it.
- **Corrected test:** `activatingASessionResizesItToTheCurrentLayout` assumed that the inactive session
  received the size change, but after `newSession()` the active one is the last, so the
  controller's behavior was right and the test, wrong. It now checks that the inactive
  session is only resized when activated and that the active one is in `onLayout`.

### D-T08-6 · 2026-10-04 · What is NOT validated in T08

Everything that needs a device, because of the user's prohibition: the service start and its type
on each Android version, the notification and its actions, the UI's reconnection when changing
activity, the real wake lock, the behavior after "Exit" and the permission dialogs. On the host the
following are tested: the sessions' lifecycle (`SessionsTest`, `SessionControllerTest`), the order of
the notices (`SessionPromptsTest`) and the service manifest (`ManifestServiceTest`).

## T19 — Versioning and releases

### D-T19-1 · 2026-10-04 · Release only with a key: the workflow refuses to publish without a signature
- **Decision:** `release.yml` fails if `UT_KEYSTORE_BASE64` is not defined; it also fails if
  `CHANGELOG.md` has no notes for that version. Without the `UT_*` variables, `assembleRelease` still
  produces an unsigned APK (what F-Droid compares).
- **Reason:** UltimateDeck's workflow would publish an unsigned APK if the secret were missing; a
  user could not update over it afterwards.
- **Impact:** the first tag needs the key created and the 4 secrets (see `RELEASING.md`). No key,
  secret, tag or release was created in this task.

### D-T19-2 · 2026-10-04 · Reproducibility: paths out of the native code
- **Decision:** `-ffile-prefix-map=<project root>=.` in the C builds of `:app` (proot and
  talloc) and of `:terminal-emulator`. There are no timestamps or `git describe` in the build
  scripts (`PROOT_VERSION` is a constant). NDK and CMake stay pinned.
- **Checked:** in a clone outside the repo, no proot or emulator lib contains the checkout's
  path.
- **NOT checked:** that two different machines produce a bit-for-bit identical APK (`diffoscope`).
  It remains as a step before the first submission to F-Droid (`RELEASING.md`).

### D-T19-3 · 2026-10-04 · `fdroid/com.qtekfun.ultimateterminal.yml` with example values
- **Decision:** version `0.1.0`, `submodules: true`, NDK `28.2.13676358` and `AllowedAPKSigningKeys` with a
  placeholder `REPLACE_WITH_THE_SHA256_OF_THE_RELEASE_CERTIFICATE`. Only final versions are
  offered (`UpdateCheckMode: Tags ^v…$`, no `-rc`).
- **Pending:** the real certificate fingerprint, the real tag and a review of the antifeatures. The
  `ndk:` and `submodules:` fields follow fdroiddata's syntax but have not been validated with `fdroid lint`.
- **Out of scope here:** fastlane metadata and screenshots (T20).

## T06 — Rootfs download and verification

### D-T06-1 · 2026-10-04 · Where the URL and the hash come from: each distro's official index
- **Decision:** the catalog (`OfficialRootfsCatalog`) carries no URLs or hashes written in the code.
  It reads, at that moment, the index each project publishes, and the current file comes from there:
  - **Alpine:** `https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/<arch>/latest-releases.yaml`.
    The `alpine-minirootfs` entry carries `file`, `version`, `size` and `sha256`. Architectures: `aarch64`,
    `armv7`, `x86_64`.
  - **Ubuntu:** `https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/SHA256SUMS`, with lines
    `<sha256> *ubuntu-base-<version>-base-<arch>.tar.gz`. There are several point releases in the same
    folder, so the highest one is chosen by comparing numbers (24.04.10 > 24.04.9). The index gives no
    sizes: Ubuntu's size is `null` and only the hash is verified. Architectures: `arm64`, `armhf`,
    `amd64`.
  - **Debian:** the official `slim` rootfs built by [debuerreotype] and published as an OCI image by the
    `debuerreotype/docker-debian-artifacts` repository (branches `dist-arm64v8`, `dist-arm32v7`,
    `dist-amd64`). The `image-manifest.json` gives the `digest` (SHA-256) and the size of the single layer, and the
    file is `<suite>/slim/oci/blobs/rootfs.tar.gz` (a `tar.gz`, about 30 MB), served by
    `raw.githubusercontent.com`.
- **Reason:** mirror URLs expire (each point release replaces the previous one and the old ones are
  withdrawn). Discovering them in the index avoids publishing an app version just to
  update a URL, and the hash always travels together with the file it describes.
- **Alternatives discarded:**
  - *Fixed URLs and hashes in the code:* they break with the next point release.
  - *Debian with `debootstrap` on the device:* it needs network, is slow and complex under proot, and leaves the
    result without a single hash to verify.
  - *Debian cloud images:* they are `qcow2`/`raw` disks, not a rootfs.
  - *`rootfs.tar.xz` from the same repository:* only its `.sha256` exists; the file is not on the branch.
    The one that is published and can be downloaded is the OCI layer's `tar.gz`.
- **Impact:** the constants of `RootfsEndpoints` are used (Alpine `latest-stable`, Ubuntu `24.04` LTS,
  Debian `trixie`); changing major version is an app update, on purpose (see D-T06-4).

[debuerreotype]: https://github.com/debuerreotype/debuerreotype

### D-T06-2 · 2026-10-04 · Hash over HTTPS, no GPG signature check (accepted risk, to improve)
- **Decision:** integrity relies on the **SHA-256 published by the project itself**, obtained over
  HTTPS, and on checking the size when the index gives it. The GPG signatures published by Ubuntu
  (`SHA256SUMS.gpg`), Alpine (`.asc`) or Debian (`InRelease`) are **not** verified.
- **Reason:** verifying signatures requires an OpenPGP library and shipping each project's public
  keys (and keeping them up to date), which adds a dependency and a large surface for mistakes for
  the MVP.
- **Risk (what this does NOT protect against):** whoever controls both the mirror (or the GitHub account, for
  Debian) and its hash, or breaks TLS validation, can serve a tampered rootfs that would pass the
  check. The hash does protect against corrupted or truncated downloads, against mirrors serving a file
  different from the index's, and against mistakes when resuming.
- **Pending improvement (backlog):** check the signature of `SHA256SUMS`/`InRelease` with keys included in
  the app (and review their license and distribution). It is not done without consulting the user.

### D-T06-3 · 2026-10-04 · Debian: manifest and layer are read in two requests over a branch that changes
- **Risk:** `dist-*` is a branch that is rewritten frequently (it is republished when the image is rebuilt).
  Between reading the `image-manifest.json` and downloading the `rootfs.tar.gz` a new version may be published,
  and then the manifest's hash no longer matches the file.
- **Decision:** it is not attempted to avoid it in T06. The downloader treats it as what it is, a `HashMismatch`:
  it deletes the partial and installs nothing. T07 must **resolve the catalog again and retry once**
  before showing the error. (It could be pinned to a commit with the GitHub API, but that adds a request
  with a rate limit; it is discarded for now.)

### D-T06-4 · 2026-10-04 · Chosen versions
- **Decision:** Alpine `latest-stable` (updates itself), Ubuntu **24.04 LTS** (the latest point release of
  that series) and Debian **trixie**. `RootfsEndpoints` groups them.
- **Reason:** a stable, long-support version is safer for a user who maintains
  servers; Alpine is the one that moves the most and that is why it follows `latest-stable`.
- **Impact:** when another LTS or stable is released the constant has to be changed by hand and the app published.

### D-T06-5 · 2026-10-04 · Download protocol: partial, Range, retries and atomic installation
- **Decision (`HttpRootfsDownloader`):**
  - It downloads to `<destination>.part`; the file only appears at `<destination>` after verifying it, with an
    `ATOMIC_MOVE` in the same directory (it exists complete or it does not exist). A previous destination is replaced.
  - **Resuming:** if there is a partial, `Range: bytes=<n>-` is requested. With `206` it is appended to the partial, but
    only if `Content-Range` starts exactly at `<n>`; otherwise the partial is discarded and it is retried.
    With `200` (the server ignored the range) it starts from zero. With `416` the partial is verified: if it is already
    the complete file it is installed, and if not it is deleted and retried.
  - **Retries:** up to 4 attempts with exponential backoff (1 s, 2 s, 4 s) on network errors, `5xx`,
    `408` and `429`; the partial is kept to continue. Any other `4xx` (e.g. `404`) fails without
    retrying. (OkHttp already retries a first `408` by itself.)
  - **Verification:** the size (if known) and the SHA-256 are checked over the complete file. If
    they fail, the **partial is deleted** and `SizeMismatch`/`HashMismatch` is returned: there is no retry, because
    repeating the download of the same thing does not fix a hash that does not match.
  - **Cancellation:** it is checked on every block read; the partial is kept to resume later.
  - Errors with sealed types (`RootfsError`/`RootfsResult`), not exceptions towards the UI.
- **Reason:** these are the guarantees the spec asked for (RF-04): a network failure midway does not leave a corrupted
  distro and nothing is installed without verifying.

### D-T06-6 · 2026-10-04 · HTTPS only, enforced in the code
- **Decision:** the downloader rejects (`InsecureUrl`) any URL that is not `https://`, without making the
  request. A `requireHttps` parameter (`true` by default) exists only for the tests with
  `MockWebServer`.
- **Reason:** with `targetSdk` 28 cleartext traffic is allowed by default on Android, so it
  cannot be delegated to the system.

### D-T06-7 · 2026-10-04 · New dependencies
- **Decision:** OkHttp 5.5.0 (Apache-2.0; it pulls in Okio and AndroidX Startup, also Apache-2.0),
  `kotlinx-serialization-json` 1.11.0 (Apache-2.0) and, for tests only, `mockwebserver3` and
  `mockwebserver3-junit5`. They are the same versions and libraries UltimateDeck uses. Noted in
  `THIRD_PARTY_NOTICES.md`; they pass `licensee` (Apache-2.0 only) and `checkForbiddenDependencies`.
- **Reason for the JSON:** Android's `org.json` is empty in host tests, and a regular expression
  over JSON is fragile. Alpine's YAML and `SHA256SUMS` are flat formats and are read by hand, without
  adding a YAML parser.

### D-T06-8 · 2026-10-04 · Critical coverage of the `data.rootfs.verify` package
- **Decision:** `Sha256Verifier` (size and SHA-256) is at **100 % of lines and branches** with host
  tests that cover: match, uppercase hash, unknown size, wrong size or hash, file larger than
  the buffer, empty file, malformed checksum and unreadable file. The constants
  are at file level because in a private `companion object` Kover counted the synthetic accessor
  as an uncovered line.
- **Impact:** `koverVerifyCritical` now measures real code; before, the package was empty.

### T06: what is NOT validated
- The real indexes were queried with `curl` on 2026-10-04 and the tests use excerpts of those
  responses, but **the app has not downloaded from the real mirrors yet** (no device nor
  authorization to use the network from one). The format of those indexes may change.
- Real network behavior on Android: timeouts (OkHttp's default client cuts after
  10 s without data and it is retried), network changes midway, data saver and the
  system's TLS validation.
- That `ATOMIC_MOVE` works on the app's private storage on all devices (it is the
  same directory, so it should).
- Extraction, the architecture choice with `Build.SUPPORTED_ABIS` (`Architecture.fromAbis`) and
  Debian's retry on `HashMismatch` belong to T07; here there are only the `RootfsCatalog` and
  `RootfsDownloader` interfaces and their implementations.

## T07 — Distro installation and management

### D-T07-1 · 2026-10-04 · An installation is a transaction: prepare on the side and move at the end
- **Decision:** `DistroInstaller` registers the distro as `INSTALLING` **before** downloading (so a repeated
  name fails without spending data), downloads and decompresses in `distros-tmp/<token>/` and only moves the
  finished tree to `distros/<token>` with an atomic rename; afterwards it marks the row `READY`. Each installation
  has its own token (UUID), so two installations, or an installation and the leftovers of a crash, never
  share a directory.
- **Failure or cancellation:** a `finally` under `NonCancellable` deletes the temporary directory, the destination and the
  row. This way, cancelling midway through extraction leaves neither files nor records (there is a test that cancels with
  the extractor stopped and checks both).
- **If the process dies:** `DistroManager.recoverInterrupted()` deletes every row that is not `READY` together with its
  files and the temporary area. It is called once when the screen starts, before allowing installs.
- **Alternatives discarded:** extracting directly into the destination and deleting on failure (a power cut
  would leave a half-done distro that looks valid); not registering the row until the end (it would allow downloading
  100 MB to discover that the name already exists).
- **Impact:** T08 moves the installation to the foreground service; the cleanup logic does not change.

### D-T07-2 · 2026-10-04 · On a hash mismatch, the catalog is resolved again and retried once
- **Decision:** fulfills what D-T06-3 asked for. Only the `HashMismatch` error repeats the full attempt (resolve,
  download, verify, extract); any other failure (network, 404, disk) is shown without repeating. A second
  `HashMismatch` is reported to the user. Each attempt is an entire transaction, so the first one leaves nothing behind.
- **Reason:** Debian's branch is rewritten often and the index's hash may become stale between the two
  requests. It is not repeated on network failures so as not to spend the user's data in a loop.
- **Risk:** a *really* wrong hash (a tampered file) is also retried once; the second attempt
  fails again and nothing is installed, so the only cost is an extra download.

### D-T07-3 · 2026-10-04 · Own extractor on top of Apache Commons Compress, gzip and tar only
- **Decision:** `TarGzExtractor` uses `commons-compress` 1.28.0 (Apache-2.0). Only **gzip and plain tar** are read,
  which is what the three T06 sources publish. xz, bzip2 and zstd are recognized by their header and rejected
  with an error that names the format (`ArchiveFormat`), instead of a generic failure.
- **Dependencies that enter the APK** (checked with `licensee`, all Apache-2.0): `commons-compress`
  1.28.0, `commons-io` 2.20.0, `commons-codec` 1.19.0 and `commons-lang3` 3.18.0. xz, zstd and brotli are
  optional for Commons Compress and are **not** included (`-dontwarn` rules in R8).
- **Alternatives discarded:** the system's `tar` via `ProcessBuilder` (not guaranteed on Android and it cannot
  be tested on the host); writing our own tar reader (it reinvents a format with many quirks, PAX,
  GNU long names…); adding `xz` (another dependency for a format no source uses today).
- **Impact:** if a source starts serving `.tar.xz`, `org.tukaani:xz` has to be added (public domain, it can
  be used) and the rejection removed.

### D-T07-4 · 2026-10-04 · Extraction security rules (the most delicate part of T07)
A downloaded rootfs is treated as hostile input even if its hash matches, because the hash only proves that it is
the file the project published, not that it is harmless. Rules (code in `SafeTreeWriter`, tested with
hostile synthetic tars):
- **Nothing outside the destination.** A name with `..` is rejected (`UnsafeEntry`). Leading slashes are removed
  as GNU tar does.
- **Never write through a symbolic link.** Each parent directory is checked without following links:
  if it is a link, the whole archive is rejected. This closes the classic attack of creating `link -> /path/outside` and
  then writing `link/file`.
- **Symbolic links themselves are stored as they are**, even absolute ones (`/bin/sh -> /bin/busybox`), because
  a normal rootfs is full of them and they are only resolved inside proot, never on the host system.
- **Hard links:** only towards a regular file already extracted inside the destination; towards the outside, towards a symbolic
  link or towards the root they are rejected. If the file system does not support hard links, the content is copied.
- **A directory cannot replace a file nor the other way round**, and a file cannot take the place of a
  parent directory.
- **Permissions:** the nine rwx bits are kept; **setuid, setgid and sticky are dropped**. Directories
  always keep rwx for the owner (the app) so they can be deleted; the real mode is applied at the end so
  that a read-only directory does not block its children. The owner is not restored (everything belongs to the app).
- **Devices, sockets and FIFOs are skipped** (they cannot be created without root) and are counted in `skippedSpecialFiles`.
- **Limits against decompression bombs:** 2,000,000 entries, 16 GiB and 4096 characters per path (values
  far above any real rootfs; configurable). An archive with **no entries at all** is rejected (an empty or
  wrong file is not a distro).
- **A name with a null byte cannot smuggle a path in:** Commons Compress cuts the name at the first `\0`, like
  `tar`, so what is validated is what is written. It was checked with a hand-built tar.
- **Cancellation:** it is checked between entries and every MiB of data, so cancelling responds without waiting for
  a large file to finish.

### D-T07-5 · 2026-10-04 · Free space: checked before downloading
- **Decision:** `max(64 MiB, 5 × the archive's size)` is required (the archive is kept while it is decompressed and
  a rootfs takes about 4 times its compressed size); if the index gives no size (Ubuntu), 512 MiB is assumed.
- **Reason:** rejecting with a clear message and the space needed is better than filling the disk and failing at
  the end. It is an estimate **not measured on a real rootfs**: pending validation.

### D-T07-6 · 2026-10-04 · Duplicating and deleting are safe against interruptions
- **Duplicate:** the copy is built next to its destination and renamed (`copyRecursively` from T05); its row only
  becomes `READY` afterwards. A failure or a cancellation leaves neither copy nor row. The copy is not the default
  distro.
- **Delete:** the row is marked `FAILED` **before** deleting files. If the deletion is interrupted, the distro
  is seen as "damaged" (and can be deleted again) instead of looking intact. A distro that is being installed
  cannot be deleted.
- **Default:** only a `READY` distro can be it, so that a new tab can always open it.
  When the default one is deleted, T05 already passes the mark to the oldest one left.

### D-T07-7 · 2026-10-04 · Management screen: minimal and provisional
- **Decision:** `DistroScreen` (list, install, rename, duplicate, delete, default, progress and
  cancel) with no logic in the composables: the state and the actions are in `DistroViewModel`, with the
  error messages translated in `DistroMessages`. It is opened from a provisional button in `MainActivity`
  that T09 (tabs) will replace. Texts in `values/` and `values-es/`; buttons of at least 48 dp.
- **The installation lives in the `ViewModel`**: it survives rotating the screen but **not closing the app**. T08 moves it
  to the foreground service. Meanwhile, if the process dies midway, `recoverInterrupted()` cleans up
  at startup.
- **`INTERNET` permission:** added to the manifest, only to download the rootfs of the distro the user chooses to
  install. It must be explained in `PRIVACY.md` (T21).

### D-T07-8 · 2026-10-04 · Apache Commons credits: the APK does not keep their notices, they are packaged by hand
- **Finding:** when checking the APK, the Android Gradle Plugin **does not include** these libraries' `META-INF/NOTICE.txt` nor
  `LICENSE.txt` (the only `NOTICE` present is Jakarta Injection's). The Apache-2.0 requires keeping the notice
  when redistributing in binary form.
- **Decision:** the texts of the four libraries are copied **unmodified** to
  `app/src/main/res/raw/third_party_notices_apache.txt`, with a `keep.xml` so the *resource shrinker* does not
  remove them. It was checked with `aapt2` on the release APK that the resource exists and is byte-for-byte identical to the source.
- **Open in T07, closed in T23 (D-T23-2):** the same may affect **OkHttp and Okio** (T06) and the rest of the dependencies that
  were already in `master`; it has to be checked and, if so, their notices packaged too and a licenses screen
  shown. It is left to decide; T07's scope was not widened without asking.

### T07: what has NOT been validated (without a device)
- **That the installation works end to end on a device:** downloading from the real mirrors,
  decompressing a real rootfs (tens of thousands of files) and that `ATOMIC_MOVE` and symbolic links
  work on private storage (already noted in D-T05-9).
- **The real time and space** of installing Debian, Ubuntu and Alpine, and whether the free-space calculation
  (D-T07-5) is right.
- **That a rootfs extracted this way boots with proot** (T02 is not validated either): permissions, absolute
  links and the absence of devices may need adjustments that are only seen when running it.
- **The screen:** the look and the real use on phone and tablet, rotation during an installation, the
  dialogs with the keyboard, and TalkBack. Only the `ViewModel` and the logic have been tested, not the composables.
- **Extraction performance** with real storage, and that cancelling responds in time on a very
  large file.

## T09 — Tabs

### D-T09-1 · 2026-10-04 · A tab is a session; the model lives in `Sessions`
**Decision:** there is no separate tab model. `SessionInfo` gains `title` (what the user types,
null = default name) and `distroId`, and `Sessions` gains pure reducers: `renamed`,
`moved`, `switched(TabSwitch)` and `closeAction`. The order of the tabs is the order of `items`.
**Reason:** a tab has no life of its own without its session; a second model would have to be kept
in sync (what happens if the shell ends?). This way all the logic is immutable and tested on the host.
**Alternatives:** a separate `Tab` with a reference to `SessionId` (more pieces, same behavior).
**Impact:** `SessionInfo` changes with default values, so T08's code and tests
still compile.

### D-T09-2 · 2026-10-04 · Pure changes go through `SessionEditor.edit`
**Decision:** `SessionController` implements `SessionEditor` (`state`, `newSession(distroId)`,
`close`, `edit { ... }`). Renaming, reordering and switching tab are an `edit` that does not touch the
shells; only if the active tab changes is the pty that comes to the foreground resized to the current
size (the same as `activate` already did, which is now an `edit`).
**Reason:** adding one function per operation to the controller exceeded detekt's `TooManyFunctions` limit
(11), which is not relaxed, and mixed the processes' lifecycle with edits that do not change
it. The interface makes it possible to test `TabsController` with the real controller and fakes.
**Alternatives:** one function per operation (breaks detekt); `TabsController` depending on
`SessionManager` (Android, no host tests).

### D-T09-3 · 2026-10-04 · Closing: confirmation only if the shell is still alive
**Decision:** closing a tab whose shell already ended closes it without asking; if it is still running,
`TabsController` keeps the tab in `closeConfirmation` and the UI shows a dialog; only on
confirming is the shell stopped. A question about a tab that disappears in the meantime (the
shell was closed elsewhere) is dropped by itself. Closing the last tab closes the app, the same as
"Exit" in the notification (T08's behavior: with no sessions, the screen closes).
**Reason:** SPEC RF-02 ("closing a session with running processes asks for confirmation"); not
losing work to an accidental tap.
**Alternative:** opening a new tab when closing the last one. More convenient, but it contradicts "Exit" and
leaves the foreground service running without the user asking for it.

### D-T09-4 · 2026-10-04 · Each tab's distro is registered, but not yet used to start
**Decision:** a new tab opens in the default distro if it is `READY` (`defaultDistroId`)
and, if there is none, in the Android shell. A long press on "+" offers choosing between the shell
and the `READY` distros (`distroOptions`). The `distroId` is stored in the session, and "restart" on an
ended session opens it in the same distro. `AndroidSessionFactory` keeps starting the Android shell
whatever the distro: linking `ProotCommandBuilder` comes with T07, which is the one that installs
distros. The notification's "New session" always opens the Android shell.
**Reason:** SPEC RF-02/RF-04; leaving the selector and the model ready without inventing a start that cannot
be tested yet (there are no installed distros).
**Impact:** when T07 links the start, it will be enough for the factory to read the `distroId`.

### D-T09-5 · 2026-10-04 · Top or side bar depending on the width, no new dependency
**Decision:** `tabBarPlacement(widthDp)`: side (a 192 dp column) from 600 dp, top (48 dp)
below. It is the threshold of Material's guidelines "medium" class, computed by hand. The bar's space
is subtracted from the pty layout with `reserveForTabBar`, like the extra-keys row in T04, so
`stty size` should still match what is visible.
**Reason:** `WindowSizeClass` is another dependency (Apache-2.0) for a single comparison.
**Alternatives:** `material3-window-size-class`.
**Pending:** the side bar always sits on the physical left edge, also with a right-to-left
language (the terminal is left to right); not validated on a device.

### D-T09-6 · 2026-10-04 · Tab shortcuts with real effect
**Decision:** `ShortcutHandler` receives a `TabCommands` and resolves all the shortcuts (the "unhandled
shortcuts" flow no longer exists). `Alt+n` selects tab n counting from 1 and does nothing if it does not
exist (it does not jump to the last one as in browsers); `Ctrl+Tab` and `Ctrl+Shift+Tab` go forward and
back, wrapping around.
**Warning:** `Alt+digit` clashes with readline's numeric arguments (see T11); it is kept because
the SPEC asks for it and it can be reassigned.

### D-T09-7 · 2026-10-04 · `TerminalViewModel` becomes `@HiltViewModel`
**Decision:** it receives `SessionManager` and `DistroRepository` by injection, instead of reading the manager
from the `Application` class. Compose's `viewModel()` uses the activity's Hilt factory
(`@AndroidEntryPoint`), so `hilt-navigation-compose` is not needed. When the active session changes
it goes back to the end of the history and the selection is cleared.
**Limitation:** the scroll position belongs to the ViewModel, not to each tab: when returning to a tab
it starts at the live screen, not where it was left.
**Alternative:** storing the position per session (more state; it was not asked for).

### D-T09-8 · 2026-10-04 · What is NOT validated (without a device)
Everything was tested with host tests (380 in total, 47 new for tabs): reducers, drag reordering
(`dropIndex`), the bar's state, the controller with the real `SessionController` and the
shortcuts. The bar has not been seen on any screen. Pending on a real tablet and phone:
1. That each tab's gestures (tap, double tap, long press + drag) do not step on each other
   nor on the bar's scroll, and that the double tap to rename is recognized without delaying the
   single tap.
2. The dropdown menus and the dialogs (rename, confirm close) and that the keyboard does not cover them.
3. The side bar: width, scrolling with many tabs and split screen (600 dp threshold).
4. That when switching tab the pty receives the right size (`stty size` and `SIGWINCH`).
5. Accessibility: TalkBack descriptions ("Shell 2, tab 2 of 3, running"), 48 dp touch
   sizes and the tab role.
6. The effect of having many tabs with Android 12+'s *phantom process killer* (SPEC §8).

## T10 — Split panes

### D-T10-1 · 2026-10-04 · The pane tree and the focus live in `Sessions`
**Decision:** a tab with panes is still a session (the one that opened it); its extra panes
are other sessions in `items` that do not appear in the bar. `Sessions` gains `panes: Map<SessionId,
PaneTab>` (tree, pane with the keyboard and zoom) and `tabs` (what the bar shows). `activeId` becomes
the pane that has the keyboard. The new operations (`split`, `focused`, `ratioSet`,
`swappedPanes`, `zoomToggled`, `closedPane`) are extension functions; T09's reducers
(`closed`, `activated`, `switched`, `moved`, `closeAction`) now count tabs, not sessions.
**Reason:** a single immutable source of truth tested on the host; the service, the notification and the
pty size still see all the sessions unchanged. The extensions avoid exceeding detekt's
`TooManyFunctions` limit (10 functions in `Sessions`), which is not relaxed.
**Alternatives:** a separate `Workspace` with `Map<tab, tree>` (two models to sync when a
shell ends or is closed); moving `Sessions` to "tab = tree" (rewrites all of T09).
**Impact:** `nextId` goes from private to public (needed to create the new pane's id). With
unsplit panes everything behaves as in T09 (T09's 380 tests still pass).

### D-T10-2 · 2026-10-04 · Two models: the runtime one and the stored one
**Decision:** `PaneNode` (leaves = live sessions) is the runtime tree and `LayoutNode` (T05) is still
the stored one. `toLayoutNode()` drops the sessions and keeps shape and proportions;
`paneNodeOf(layout) { session }` assigns new sessions in reading order. The Room schema and
`LayoutCodec` do not change (there is a JSON round-trip test).
**Reason:** the stored schema must not depend on session ids that do not survive the process.
Saving and restoring named layouts, profiles and commands per pane is T12b.
**Alternatives:** storing the `PaneNode` (ephemeral ids on disk).

### D-T10-3 · 2026-10-04 · Orientation: it names the line that divides
**Decision:** `HORIZONTAL` is a horizontal line (the first pane on top, the second below) and
`VERTICAL` a vertical line (left and right), as in Terminator. "Split right" uses
`VERTICAL`; "Split down", `HORIZONTAL`. The new pane always goes in the second half.
**Reason:** `Layout.kt` (T05) did not define what they meant; fixing it now avoids ambiguous
saved layouts. **Impact:** documented in `PaneNode`; if the other convention is preferred, it is changed in a
single place before saved layouts exist.

### D-T10-4 · 2026-10-04 · Pure geometry: the UI draws what the domain computes
**Decision:** `paneScene` splits the area into rectangles in whole pixels (the divider between the two
halves, the exact split, no gaps) and `paneLayouts` computes each pty's grid with T04's same
`terminalLayoutFor`. The UI only places boxes in those rectangles. Dragging is measured against the
divider's "span" (`ratioForPointer`) with a minimum size and snapping to 50 % (margin of 0.03).
**Reason:** what is seen and the size the pty receives come from the same function, so `stty size`
must match what is visible (to be validated on a tablet). It is tested with phone and tablet sizes.
**Alternatives:** measuring each pane in Compose and notifying the pty from `onSizeChanged` (two sources of
truth and harder to test).

### D-T10-5 · 2026-10-04 · Minimum size and a notice instead of blocking
**Decision:** a pane does not go below 20 columns × 4 rows (`canSplit`, `ratioForPointer`). Splitting where
it does not fit does nothing and shows a toast ("Not enough room to split here"); on a
phone in portrait it can be split up to that limit. `WindowSizeClass` is not used: panes can be
created at any width and the tab bar already changes at 600 dp (D-T09-5).
**Reason:** SPEC RF-02 and the user's request: "on phone allow splits but with a minimum size and
notices". **Alternatives:** preventing splits on narrow screens.

### D-T10-6 · 2026-10-04 · pty sizes with debounce, also when dragging
**Decision:** the controller sends each pane's size with T04's same `settled(120 ms)`
(now generic) and only if it changed (`PtySizes`). When dragging a divider the drawing moves on every
frame, but each shell receives a single `SIGWINCH` when the drag stops. An unsplit tab
is still sized with the whole area (`onLayout`); a zoomed pane takes the whole
area. **Reason:** one `SIGWINCH` per frame makes `vim`/`htop` redraw nonstop.
**Impact:** when splitting, the new pane starts with the full size and receives its own ~120 ms
later.

### D-T10-7 · 2026-10-04 · Focus, zoom, swap and close
**Decision:** the pane with the keyboard carries a border (only if the tab is split). Tapping another
pane gives it the keyboard; T11's shortcuts move the focus by geometry (the nearest neighbor on that
side, and among those the one that overlaps the most). Zoom: only the focused pane is drawn, at the whole
area, without closing the others. Swapping exchanges the places of two panes, not the sessions. Closing a pane
with a live shell asks for confirmation; its sibling takes the space and, if it was the tab's "own" pane,
the next one in reading order becomes the tab keeping its place and its name.
Closing a tab closes all its panes (the others first, the own one last) and the confirmation
counts all the panes.
**New shortcuts** (configurable, T11): Ctrl+Shift+O split down, Ctrl+Shift+E split right,
Ctrl+Shift+Q close pane (Ctrl+Shift+W still closes the tab), Ctrl+Shift+X zoom and Ctrl+Alt+arrow
for the focus, so that Alt+arrow keeps moving by words in readline. Without Ctrl/Alt they are not accepted.

### D-T10-8 · 2026-10-04 · What was NOT done (T12b's scope)
Per-pane profiles, named saved layouts with an initial command, shortcuts editable in the interface and
broadcasting to several panes are T12b. "Restart" on an ended pane closes that pane and opens a
new tab (it does not restart it in place). Scrolling and text selection only exist
in the pane with the keyboard; the others are drawn live and a tap gives them the focus. Reordering
panes by dragging them was not done either: they are swapped with the pane's menu or by geometry.

### D-T10-9 · 2026-10-04 · What is NOT validated (without a device)
Everything was tested with host tests (new tests for the tree, geometry, `Sessions`, controllers and
shortcuts): the tree, the pixel split, focus by geometry, the reducers and that each pty receives its
pane's size. No screen with panes has been seen. Pending on a real tablet and phone:
1. That `stty size` matches what is visible in each pane after splitting, closing, dragging and zooming.
2. Dragging the divider (48 dp touch zone) and that it does not compete with the pane's scroll.
3. The focused pane's gestures (scroll, selection, pinch) and the tap to give focus.
4. The "⋮" menu and the close dialog, and that the on-screen keyboard does not cover them.
5. Performance when drawing several panes at once (the shared painter draws one after another).
6. TalkBack: the pane and separator descriptions.
7. The effect of having many shells with Android 12+'s *phantom process killer*.

## T12 — Themes, OLED mode and fonts

### D-T12-1 · 2026-10-04 · Font: JetBrains Mono 2.304, bundled unmodified
- **Decision:** the terminal uses JetBrains Mono (Regular, Bold, Italic, Bold Italic) as a bundled font, in `res/font`.
- **Reason:** SIL OFL-1.1 license verified in the official repository (`Copyright 2020 The JetBrains Mono Project Authors`), compatible with distributing the font inside a GPL-3.0-or-later app as long as it is not sold on its own and the license text is included. No *Reserved Font Name*.
- **Verified:** the four `.ttf` files are byte-for-byte identical (SHA-256) to those of the official `v2.304` release. The OFL text and the list of authors are in the APK (`assets/licenses/`).
- **Alternatives:** Fira Code, Hack or a Nerd Font (Nerd Fonts are patches of other fonts with mixed licenses: more work to verify). **Impact:** about 1.1 MB more in the APK. **Pending:** Nerd Fonts / powerline icons, in case the user wants them (not MVP).

### D-T12-2 · 2026-10-04 · Bundled schemes and why Tango is NOT there
- **Decision:** Dracula (default), Solarized Dark, Solarized Light, Gruvbox Dark, Nord and an own OLED scheme come bundled. They are credited in `THIRD_PARTY_NOTICES.md` and with the full MIT text in `assets/licenses/ColorSchemes-MIT.txt`.
- **Verified in the official repositories:** Solarized (© 2011 Ethan Schoonover, MIT), Dracula (© 2023 Dracula Theme, MIT), Nord (© 2016-present Sven Greb, MIT) and Gruvbox (© Pavel Pertsev; MIT/X11 according to its README and `package.json`, although the repository has no `LICENSE` file).
- **Tango, removed:** the first draft included it as the default saying "public domain". **I could not verify that claim**: Tango's guidelines are published under CC BY-SA 2.5, which is not compatible with GPLv3, and I found no clear statement for the palette. Since it could not be checked and to avoid claims, it was removed. If a clear license source is verified later, it can be added.
- **Changes to the palettes:** minimal legibility tweaks, documented next to each scheme in `BuiltInSchemes.kt` and required by the contrast test: Solarized Light (green, yellow and cyan 1–2 % darker: the official ones give 2.9–3.0:1) and Gruvbox Dark (lighter normal red: the official `CC241D` gives 2.7:1). The MIT schemes allow modification with attribution.
- **Impact:** the default scheme changes to Dracula. A user who imports a scheme is responsible for its origin.

### D-T12-3 · 2026-10-04 · OLED mode = a variant of the dark theme
- **Decision:** `resolveTheme` only activates OLED when the resulting theme is dark (`oled = oledBlack && dark`). In OLED, a dark scheme gets a pure `#000000` background (`forOled()`); a light one is not touched.
- **Reason:** switching pixels off only makes sense in dark; a black background with a light scheme would leave illegible text.
- **Impact:** the Material interface and the terminal view use the same decision.

### D-T12-4 · 2026-10-04 · The scheme is applied to all sessions, also to future ones
- **Decision:** `SessionManager.applyScheme` delegates to `AndroidSessionFactory`, which remembers the scheme, applies it to all running hosts and to each new host before starting its emulator.
- **Reason:** with several tabs (T09) a per-host scheme would leave tabs with old colors. Termux's library reads its initial colors from a shared static palette; it is written there and the active emulator's colors are reset (those a program set with OSC 4/10/11 are discarded: the new scheme wins).
- **Impact:** the integration with T08/T09's changes was redone on top of `master`; H's design, which assumed a single host, was not valid as it was.

### D-T12-5 · 2026-10-04 · Nothing is drawn until the settings are there
- **Decision:** `MainActivity` waits for the saved settings to arrive (`produceState`) before composing the screen.
- **Reason:** it avoids a first frame with the default scheme and font size that then jumps to the saved ones.
- **Alternative:** a loading screen; discarded because it is an instant. **Risk:** if the repository were slow, the empty window would be seen; not measured.

### D-T12-6 · 2026-10-04 · Persistence and font size
- **Decision:** theme, OLED mode, dynamic colors, chosen scheme, imported schemes and font size live in T05's settings repository (key-value). T11's zoom size is restored once and only the user's changes are saved, with a 500 ms *debounce* (a pinch generates many values).
- **Import/export:** versioned JSON (`version: 1`) with `kotlinx.serialization`, tested with a round trip. A file of an unknown version is rejected. A test caught that, with `encodeDefaults` off, `kotlinx.serialization` omitted the `version` field on export (because it has a default value): files carried no version. `encodeDefaults` was turned on.

### D-T12-7 · 2026-10-04 · Minimum contrast checked in tests
- **Decision:** each bundled scheme must meet: text on background ≥ 4.5:1 (WCAG AA), cursor ≥ 3:1, text on selection ≥ 3:1, each ANSI color on the background ≥ 3:1 and "bright black" ≥ 1.5:1; and the same in its OLED variant.
- **Reason:** a palette with illegible colors is an accessibility failure; the test catches it before publishing.
- **Note:** the 1.5:1 threshold for bright black is deliberately low: in many schemes it is the "dimmed" text color and raising it distorts it.

### D-T12-8 · 2026-10-04 · The system bars follow the scheme
- **Decision:** the status and navigation bar icons are chosen according to the **scheme's** background (`SystemBarStyle.auto`), not the system theme, because the terminal is drawn under the bars.

### Pending hardware validation (T12)
None of this has been seen on screen; there are only host tests.
1. That the colors really look good: real contrast at low brightness and in the sun, and that OLED turns the pixels off.
2. That the hot scheme change repaints all the tabs without restarting them, and the colors a program set with OSC.
3. JetBrains Mono rendering: bold/italic with the four variants, correct cell width, and that it does not change the grid size (`stty size`) compared with the system font.
4. System bar icons with light and dark schemes.
5. The settings screen to choose scheme/theme does not exist yet (T16): today they are only changed through the settings repository.

## T14 — SSH hosts and keys

Scope: list of saved hosts with create/edit/delete, one-tap connection in a new tab, and key
management (generate, import, export, delete) encrypted at rest. Everything has been tested
only on the host JVM; see "T14: what is NOT validated" at the end.

### D-T14-1 · 2026-10-04 · `ssh` is launched as an argument vector, without a shell
**Decision:** `SshCommand.build` returns a list of arguments (`ssh -o ServerAliveInterval=30 …
-p <port> [-i <key> -o IdentitiesOnly=yes] -- user@host`) that proot runs directly. There is
no shell string, so nothing needs escaping: the interpreter that would read it does not exist. Even so,
host, user and port are validated (`Validation`: a host that starts with `-` would be read as an
`ssh` option; spaces and metacharacters are not accepted), `--` ends the options
before the destination and the key's path can only be `/tmp/.ut-ssh-<hex>`.
**Reason:** a command injection through the name of a saved host (or one imported from a
backup) would be the most obvious vulnerability of this feature. Removing the shell is more
robust than escaping correctly.
**Alternatives:** `sh -c "ssh …"` with quote escaping (a single escaping mistake is a
command execution); `ProxyCommand`/`LocalCommand` stay out: no `-o` option that executes
programs is emitted.
**Impact:** there are tests with hostile inputs (`;`, `$(…)`, quotes, line breaks, `-oProxyCommand=…`,
paths with `..`). The saved host's name is only a label and never reaches the command line.

### D-T14-2 · 2026-10-04 · Key format: own OpenSSH, no new libraries
**Decision:** the `openssh-key-v1` format (private key without passphrase) and the `authorized_keys` line
are written and read in `OpenSshKeyFormat`, for Ed25519 and RSA. Import also accepts PKCS#8 and
PKCS#1 (RSA only, which are the usual old keys) in `PrivateKeyText`. A key with a passphrase
is rejected with a message that explains how to remove it (`ssh-keygen -p`; **superseded by D-PK-1**: an OpenSSH key with a passphrase is now read, only the old PEM/PKCS#8 encryption is still refused). ECDSA, DSA and
certificates are not supported, nor Ed25519 in PKCS#8.
**Reason:** `ssh` needs that format and the platform's cryptography API does not produce it. It is
little code (about 200 lines) and is tested against real `ssh-keygen` keys; BouncyCastle would add
~2 MB and one more license for something we do not need.
**Alternatives:** BouncyCastle (discarded for weight); generating the keys inside the distro with
`ssh-keygen` (the key would be born in plain text in the rootfs, exactly what we want to avoid).
**Impact:** Ed25519 in PKCS#8 cannot be imported because the public key would have to be derived from the
seed and the JDK does not expose that. Whoever has such a key converts it with `ssh-keygen -p -m RFC4716`.

### D-T14-3 · 2026-10-04 · Generation with the platform's cryptography; Ed25519 only from Android 13
**Decision:** `JcaSshKeyGenerator` uses `KeyPairGenerator` (3072-bit RSA on all versions;
Ed25519 where the platform has it, Android 13 or later). The screen only offers the types that
`supportedTypes` says the device supports. RSA generation runs off the main thread.
**Reason:** `minSdk` 26 does not have Ed25519 in `java.security`. RSA 3072 takes a few seconds on a phone;
4096 could take half a minute.
**Alternatives:** BouncyCastle for Ed25519 on API 26–32 (see D-T14-2); offering only RSA.
**Impact:** on Android 8–12 the only type to generate is RSA. Importing Ed25519 works on all.

### D-T14-4 · 2026-10-04 · Encryption at rest: AES-256-GCM with an Android Keystore key
**Decision:** the private key (OpenSSH text) is sealed with `AesGcmSecretBox`: AES-256-GCM, random 12-byte
IV, format `version | IV length | IV | ciphertext + tag`. The key's alias
goes as authenticated data, so a blob cannot be moved to another key. The AES key is in
the Android Keystore (`KeystoreSecretKey`, not exportable). Each key is two files in
`files/storage/ssh-keys/`: `<alias>.key` (sealed, 0600) and `<alias>.meta` (JSON with name, type, public
key and fingerprint; the alias is the file name). The `.meta` is written last: a key whose
write was interrupted does not appear in the list.
**Reason:** the Keystore prevents taking the key off the device, so a dump of the storage (or
a backup) does not reveal the private keys. The format and the integrity checks
are tested on the JVM with an in-memory key; only the real Keystore needs a device.
**Alternatives:** `EncryptedFile`/Jetpack Security (deprecated); requiring biometric authentication for
each use (`setUserAuthenticationRequired`): better security but it breaks one-tap connection.
**Impact:** since the Keystore key does not leave the device, SSH keys **cannot be
restored as they are on another phone** with the backup (T15). They will have to be exported (decrypted,
with a warning) or encrypted with a user password, as Deck already does with the sessions.

### D-T14-5 · 2026-10-04 · How the key reaches `ssh`: a temporary 0600 file in the rootfs
**Decision:** when connecting, `SshConnector` decrypts the key and writes it to
`<rootfs>/tmp/.ut-ssh-<16 random hex>` (readable only by the app, atomic write), passes
`-i /tmp/.ut-ssh-…` to `ssh` and deletes the file when the session ends or is closed. Files
left by an abrupt close are deleted when the next connection starts, except those of live sessions. The
paths go through `FileTrees.resolveInside`, which rejects a symbolic link on the way: a
foreign rootfs's `/tmp` cannot point outside the storage.
**Reason:** `ssh -i` needs a file. It is the simplest thing that works with any distro.
**Alternatives:** `ssh-agent` inside the distro with `ssh-add -` reading the key from standard
input (the key would not reach a file, but the agent and its socket have to be orchestrated in each
tab); a FIFO (`ssh` opens the key more than once); the environment variable (visible in
`/proc/<pid>/environ`). The agent is the natural improvement if the threat model asks for it.
**Impact:** the key is in the clear in private storage while the session lasts (see D-T14-6).

### D-T14-6 · 2026-10-04 · Threat model
**Protects against:** a storage dump or a system backup with the app
closed (the keys are sealed with a key that does not leave the Keystore); a key ending up in
a log (the key types do not print their fields, and the interface only receives public parts); a host
with shell characters or that reads as an `ssh` option (D-T14-1); a `/tmp` linked outside the
storage; deleting a key that a host still uses.
**Does not protect against:** another app with the same UID (none) or with root on the device, which can read
the key's temporary file while the session is open or the process memory; an unlocked device in the
hands of another person (no authentication is asked to use the key, see D-T14-4);
programs inside the distro, which can read `/tmp/.ut-ssh-…` while the session lasts; the
user saving the exported private key in an insecure place (the screen warns before).
**Impact:** it is a level similar to `~/.ssh/id_*` without passphrase on any Linux, with the
advantage that at rest it is encrypted. **Updated by D-PK-6:** a passphrase now covers the "unlocked
device in the hands of another person" case for protected keys.

### D-T14-7 · 2026-10-04 · Gap found: nothing connected proot with the sessions
**Decision:** T09 and T07 left the `distroId` saved in each tab, but `AndroidSessionFactory`
only knew how to start the Android shell; `ProotCommandBuilder` (T02) was not in the injection. T14
needs it to run `ssh` in a distro, so it adds the minimum: `SessionLaunch` (command, environment
and cleanup), `LaunchingSessionFactory` (a factory that also knows how to run a command),
`SessionController.newSession(distroId, launch)` and `DistroLaunchFactory`, which assembles the proot line.
`ProotCommandBuilder` stays injected in `SshModule`.
**Reason:** without this the "Connect" button could not open anything.
**Alternatives:** changing `SessionFactory`'s signature (it broke T08 and T09's tests).
**Impact:** **a distro's normal tabs still open the Android shell**: launching proot
in a tab with no command is the same piece but it has not been connected to the bar's "+" button
(`TabsController.newTabIn`) so as not to touch T09. It remains as a pending task and is the first thing
to do so that distros are good for more than `ssh`. There is also no "restart" for an `ssh`
tab: `restartActive` opens an Android shell.

### D-T14-8 · 2026-10-04 · DNS inside proot
**Decision:** before connecting, `SshConnector` writes the rootfs's `/etc/resolv.conf` with
`1.1.1.1` and `9.9.9.9` if it is missing or empty. It does not touch one the user has configured.
**Reason:** Android has no `resolv.conf` to share and the one in a freshly installed rootfs does not
work (Ubuntu's is a link to a file that does not exist), so `ssh server.example`
would fail to resolve the name even though the network works.
**Alternatives:** reading the system's DNS (an app cannot, since Android 8); mounting one of our own with
`-b`; not doing it (only IPs would work).
**Impact:** there is a privacy decision: names are resolved with those public resolvers,
not with the user's network's. It is only for the distro. **It should be configurable** (T16).
**Superseded by D-T08b-6 (2026-10-04):** there is now a single mechanism for all tabs. The active network's
DNS is used and `1.1.1.1`/`9.9.9.9` only as a last resort. `SshConnector` no longer writes anything in the
rootfs. Note on the alternative discarded here: an app can indeed read the system's DNS with
`ConnectivityManager.getLinkProperties` (what it cannot do since Android 8 is read `net.dns1` with `getprop`).

### D-T14-9 · 2026-10-04 · Screens, ViewModels and entry point
**Decision:** two screens (hosts and keys) with one ViewModel each (`SshViewModel`,
`SshKeysViewModel`); the key list on the hosts screen is read-only. Exporting the
private key and importing from a file use the system picker (`CreateDocument` and
`OpenDocument`), with a warning before saving the private one. The public key is copied to the clipboard.
A key in use by some host cannot be deleted (`KeyInUse`). A provisional "SSH" button next to the
"Distros" one in `MainActivity`; it must go to the "new tab" menu that T09 owns (as D-T07-7).
**Reason:** detekt's limit of functions per class, and that each screen has a single job.
**Alternatives:** a single ViewModel (exceeded the function limit).
**Impact:** hosts without a key open a normal `ssh` and ask for the password in the terminal.

### D-T14-10 · 2026-10-04 · `known_hosts` and server key verification
**Decision:** it is not touched. `ssh` asks about the fingerprint the first time and saves `known_hosts` in
the rootfs's `~/.ssh`, as on any Linux. `StrictHostKeyChecking=no` is not passed.
**Reason:** disabling it would nullify the protection against an impersonated server.
**Impact:** the history lives in the rootfs, so it travels with the distro's backup.

### D-T14-11 · 2026-10-04 · Verified against real OpenSSH, on the host
**Decision:** besides the unit tests, the key format was checked against OpenSSH 10.2's `ssh-keygen` and
`sshd` on the development machine (not on a device): keys generated by `ssh-keygen` were read
(OpenSSH Ed25519 and RSA, PKCS#1, PKCS#8) and the fingerprints matched
`ssh-keygen -l`; `ssh-keygen -y` accepted the copies written by `OpenSshKeyFormat` and derived the same
public key; the Ed25519 and RSA 3072 keys generated by `JcaSshKeyGenerator` were accepted by
`ssh-keygen`, and a user `sshd` on loopback port 2222 admitted them with the same command shape
that `SshCommand` builds (`-p … -i … -o IdentitiesOnly=yes -- user@host`) and rejected a
foreign key. An ECDSA key and the two with a passphrase (PEM and OpenSSH) were rejected with the expected
error.
**Reason:** a format "looking" correct is not enough: only a real `ssh` says whether it accepts it.
**Impact:** test keys are not committed (a secrets scanner would flag them); the check was
done with disposable keys and is not an automatic test. The unit tests cover the round trip,
corruption and the hostile cases; if the format changes this check must be repeated by hand.

### T14: what has NOT been validated (without a device)
- **Real Keystore:** that `KeystoreSecretKey` creates the AES-256 key and that `AesGcmSecretBox` seals and
  opens with it on Android 8 to 16 (the format was tested with an in-memory key, not with the Keystore).
- **Generation on the phone:** the real time of RSA 3072 on a low-end phone, and that Ed25519 is
  available on Android 13 or later (`supportedTypes` decides it at run time).
- **`ssh` inside proot:** that the distro has `openssh-client` (it is not installed; the user must do
  `apt install openssh-client` or `apk add openssh`; the screen does not warn about it yet), that proot runs
  `ssh` with `DistroLaunchFactory`'s line, that `-i` accepts the 0600 file when proot simulates root
  (owner and permissions seen from inside) and that network and DNS work (D-T14-8).
- **Key cleanup:** that the temporary file disappears when closing the tab and when the process dies,
  and that the leftovers are deleted on the next connection.
- **System file picker** (import and export) and the clipboard; rotation with the
  dialogs open (`HostDraft` is saved with `listSaver`); TalkBack and the 48 dp.
- **A distro's normal tabs**: they still open the Android shell (D-T14-7).

## T13 — Access to the device's files

### D-T13-1 · 2026-10-04 · What is mounted and where
- **Decision:** with the feature on, each proot session receives `~/storage/shared` (all the shared storage) and shortcuts to the usual folders that exist on the device: `downloads` (Download), `dcim`, `documents`, `pictures`, `music` and `movies`. The source is `Environment.getExternalStorageDirectory()` (normally `/storage/emulated/0`), not `/sdcard`, which is a symbolic link.
- **Reason:** `~/storage/downloads` is what the SPEC asks for (RF-05) and `shared` covers any other folder without having to list them all. Only the folders that exist are mounted so as not to show broken shortcuts.
- **Alternatives:** mounting only Downloads (not very useful); mounting `/sdcard` directly at `~/storage` (leaves no room for the shortcuts).
- **Impact:** the computation lives in `domain/storage` (`SharedStoragePlanner`) and is applied with `ProotSession.withSharedStorage`.

### D-T13-2 · 2026-10-04 · Global setting, not per distro
- **Decision:** a single switch (`AppSettings.sharedStorage`, key `shared_storage`, off by default) for all distros.
- **Reason:** the SPEC says "per distro/setting"; Android's permission belongs to the whole app, so a switch per distro would only add a screen without giving more real control.
- **Alternatives:** a column in the distros table (a Room migration and a per-distro screen). It can be added later without breaking anything.
- **Impact:** the key goes into the settings backup (T15) with the rest.

### D-T13-3 · 2026-10-04 · Permission: READ/WRITE_EXTERNAL_STORAGE, without MANAGE_EXTERNAL_STORAGE (NOT verified on hardware)
- **Decision:** when the switch is turned on, `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` are requested, and only then. `MANAGE_EXTERNAL_STORAGE` is neither declared nor requested. If the user already granted "access to all files" in the system settings, that is also accepted (`Environment.isExternalStorageManager()`).
- **Reason (hypothesis, not checked on a device):** the app targets API 28 on purpose (SPEC §2). At that version Android treats it as an app with "classic" storage and lets a process read and write by path with the usual permission; it is Termux's model, which also targets 28. Requesting `MANAGE_EXTERNAL_STORAGE` would force sending the user to a settings screen, it is a very broad permission and Google Play restricts it; it is not needed if the above works.
- **Known risks:** (1) on Android 13 and later the storage permissions were split by media type and it may be that, for an app that targets an earlier version, the system only grants photos, video and audio and not Downloads or Documents; (2) since Android 11 `Android/data` and `Android/obb` are out of reach; (3) the shared storage's file system does not support symbolic links, execute bits or `chmod`, so `ln -s`, `chmod +x` and executables inside `~/storage` will fail or have no effect (just like in Termux), and `--link2symlink` cannot emulate hard links there.
- **Plan if it fails:** offer "access to all files" with `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION`. The code already recognizes it as a granted permission; the permission would only have to be declared and the button added. It remains to be decided after testing on a real tablet.
- **For PRIVACY.md (T21):** the permission is only requested when the user turns the feature on; it is there so that distros read and write their shared folders; no data leaves the device because of it. Note: the permission is **declared** in the manifest even if the feature is off, and that is why it appears in the app's permission list.

### D-T13-4 · 2026-10-04 · Degrade, don't fail
- **Decision:** if the permission is not granted, the storage is not mounted or the mount points cannot be created, the plan stays as `Degraded(reason)`, the session starts anyway and without `~/storage`. With the feature off the permission is not even queried.
- **Reason:** a terminal that does not open because of a revoked permission is worse than one without `~/storage`.
- **Impact:** the notice to the user today is the text under the switch ("permission not granted"). The notice when opening a session will come when proot's start is connected (see next point).
- **Important, not connected:** there is still nowhere that launches proot with a distro (T07 installs and T08 manages sessions with the Android shell). `SharedStorageMounts.prepare(...)` and `withSharedStorage(...)` are ready and tested, but **nobody calls them yet**. Whoever connects proot to the sessions must use them; until then the switch is saved but has no effect on a session.

### D-T13-5 · 2026-10-04 · Mount points are created through the file repository
- **Decision:** the empty directories where proot mounts (`<distro>/rootfs/root/storage/...`) are created with `FileSystemRepository.createDirectories` and validated `FsPath` paths, on each start (it is idempotent). It is done so that `ls ~/storage` shows them: proot does not invent entries in a directory.
- **Impact:** there is no `java.io.File` in `domain` or `ui`. `DistroInstaller.UNPACKED_NAME` (`rootfs`) was made public so as not to duplicate the constant (a minimal change in T07's code).

### D-T13-6 · 2026-10-04 · The Android code goes in `platform`, outside coverage
- **Decision:** `AndroidSharedStorageAccess` (permission, path, `File.isDirectory`) is in `com.qtekfun.ultimateterminal.platform`, with no logic of its own. Kover only measures `domain` and `data`, so it does not count; the logic that decides is all in `domain.storage` with tests.
- **Reason:** it cannot be run on the host JVM and a test that simulates it would only prove the simulation.
- **Note:** `Environment.getExternalStorageDirectory()` is deprecated (API 29) and is used with `@Suppress("DEPRECATION")` because giving a path to a native process is exactly what `targetSdk` 28 allows.

### T13: what has NOT been validated (without a device)
1. That the permission dialog appears and that, once granted, `/storage/emulated/0` can be read and written from proot (see D-T13-3 and its risks), on the tablet (API 31) and on Android 13 or later.
2. The SPEC's criterion: `cp /var/log/syslog ~/storage/downloads/` appears in Downloads.
3. That proot mounts (`-b`) those paths with `--link2symlink` and `--kill-on-exit` without error, and that `ls ~/storage` shows the mount points.
4. The screen: the switch, changing the permission in the system settings while the app is in the background and the "Open app settings" button.
5. That turning off the switch leaves no hanging mounts in an already open session (it changes when the next one is opened).

## T08b — Connecting proot to the sessions

Context: after T07 (installing distros), T09 (tabs) and T13 (mounts) nobody launched proot: the session factory
always started `/system/bin/sh`. T08b joins the pieces. **None of this has been run on a device**: all the
decisions about proot's real behavior are hypotheses until the first test on the tablet (D-T08b-8).

### D-T08b-1 · 2026-10-04 · A pure planner decides; the factory only executes
- **Decision:** `ProotSessionPlanner` (`data/proot`) receives the tab's `distroId` (null = Android shell) and returns a `LaunchPlan`: `AndroidShell`, `FallbackToAndroid(notice)`, `InDistro(proot, notice?)` or `Failed(problem)`. It does not touch processes. `AndroidSessionFactory` executes it. It is only used when whoever opens the tab does **not** bring its own `SessionLaunch` (see D-T08b-9).
- **Reason:** all the rules (which distro, which user, which mounts, which errors) are tested on the JVM. The only thing that needs a device is the `fork/exec`.
- **Alternatives:** deciding inside `TerminalSessionHost` (impossible to test without a PTY); a `runBlocking` on the main thread (it would block the UI when querying Room and the disk).
- **Impact:** `data/proot` now also covers the start policy (Kover ≥85 % in `data`). The planner is injected through the constructor; the device ports (`ProotRuntime`, `ResolvConfSource`) have their Android implementation in `platform/`, with no logic of their own.

### D-T08b-2 · 2026-10-04 · Starting is asynchronous: the host waits for the plan and the size
- **Decision:** `SessionFactory.start` does not change signature and returns the handle at once. The host remembers the size the screen asks for; the factory launches a coroutine (main thread, which is where the emulator library delivers its callbacks) that obtains the plan and calls `host.launch(...)`. The PTY is created when **both things** are known: what to run and what size it has.
- **Reason:** the plan needs Room and the disk (suspend). It avoids touching the `SessionFactory` interface and its test fakes. The tab's distro is read from the state the controller already publishes before calling the factory, and there is a test that pins it. The factory implements `LaunchingSessionFactory` (from T14), so the signature the controller sees does not change.
- **Alternatives:** adding the `ShellRequest` to `SessionFactory.start` (breaks the fakes and does not solve the suspend part).
- **Impact:** `TerminalSessionHost.stop()` forgets what was to be run; "restart" opens a new session (it already was so). A `Handle.stop()` cancels the coroutine, so a tab closed while starting leaves nothing behind.

### D-T08b-3 · 2026-10-04 · What is done when the distro cannot be opened
- **Decision:** no distro requested → Android shell with no notice (it is the explicit "Android shell" choice). Distro requested that is not ready or no longer exists → **Android shell with a dismissible notice** and access to the distros screen. Distro ready but without its files, proot missing, invalid user or no temporary folder → **explained error** (`LaunchProblem`) and the session ends as failed; never an exception.
- **A tab with its own `SessionLaunch` (ssh) does not go through here**, so it never falls back to the Android shell: if it cannot start, it fails with its own message.
- **Reason:** the SPEC asks for "with no ready distro, fall back to the Android shell" and "sealed errors, never a crash". "There is no distro yet" (normal on a first start) is separated from "something is broken".
- **Impact:** the notice is drawn over the first rows (it does not change the PTY's size) and the texts are in English and Spanish.

### D-T08b-4 · 2026-10-04 · Default tab: the default distro if it is ready
- **Decision:** when the app starts and from the notification, `SessionManager.newDefaultSession()` queries the default distro and opens in it only if it is `READY`; if not, Android shell. The "+" button already did the same.
- **Reason:** before, startup always passed `null` (Android shell) even if there was a default distro.
- **Alternatives:** always opening the Android shell (ignores the default distro).

### D-T08b-5 · 2026-10-04 · Distro user: `su -l`, with the name validated
- **Decision:** `root` runs the shell directly (`-0`). Another user goes through `su -l <user>`. The name is validated with `[a-z_][a-z0-9_-]{0,31}`: a name that starts with `-` would be read as an option of `su`. A command (`ProotSession.command`, used by the SSH connection) is an **argument list**, never a shell line, and runs with proot's identity (root), not the user's.
- **Reason:** the name comes from the database; validating it in the planner protects even if an old row was not validated. There are tests with hostile names.
- **Unvalidated hypothesis:** that `su -l` works with proot and `-0` on Debian, Ubuntu and Alpine (busybox `su`), and that the user exists in the distro; if it does not exist, the shell ends with an error from `su`.
- **Limit:** `/bin/sh -l` is the default login shell on the three distros (dash on Debian/Ubuntu, ash on Alpine); `bash` is not chosen even if it exists.

### D-T08b-6 · 2026-10-04 · `/etc/resolv.conf` from the device's DNS, with the `ACCESS_NETWORK_STATE` permission
- **Decision:** the app writes `filesDir/resolv.conf` with the active network's DNS (`ConnectivityManager`) and mounts it with `-b` over the distro's `/etc/resolv.conf`. Only IPv4/IPv6 literals are accepted (no zone, names or line breaks) and at most three (glibc's limit). If the device reports none, `1.1.1.1` and `9.9.9.9` are used. If the file cannot be written, the session starts anyway with a notice.
- **Reason:** Android has no `/etc/resolv.conf` and a rootfs's points to a nonexistent resolver. It does not depend on Termux paths. Mounting a file avoids modifying the rootfs.
- **A single mechanism (unified with T14):** normal tabs (`ProotSessionPlanner`) and the SSH connection (`DistroLaunchFactory`) use it equally, through `ResolvConfSource` and `resolvConfBinds`. `SshConnector.ensureResolver` and its decision D-T14-8 (writing `1.1.1.1`/`9.9.9.9` into the rootfs) disappear. The device's DNS goes first; the public ones are a **last resort**, constant `ResolvConf.FALLBACK_SERVERS`, so that T16 makes them configurable.
- **Privacy (for `PRIVACY.md`, T21):** the fallback DNS are third-party public resolvers (Cloudflare and Quad9); they are only used when the device gives none. `ACCESS_NETWORK_STATE` is a normal permission (not asked of the user) and only serves to read those servers.
- **Alternatives:** always writing `8.8.8.8` (sends everything to a third party even if the network has its own DNS, and breaks internal DNS); copying the system's `/etc/resolv.conf` (it does not exist since Android 8).
- **Not validated:** that `getLinkProperties(activeNetwork)` returns servers on all networks (VPN, mobile data) and that binding a file over `/etc/resolv.conf` works with proot.

### D-T08b-7 · 2026-10-04 · Compatibility mode (proot without seccomp) as a visible setting
- **Decision:** `AppSettings.prootCompatibilityMode` (key `proot_compatibility_mode`, off) sets `PROOT_NO_SECCOMP=1`. There is a switch on the distros screen and it applies to tabs opened afterwards.
- **Reason:** on some kernels the seccomp filter makes proot fail or hang; it is the first thing to try if a distro does not start, and without a setting the app would have to be recompiled to try it on the tablet. It goes into T15's settings backup with the rest.
- **Cost:** without seccomp, proot intercepts all system calls with `ptrace`: it is noticeably slower.

### D-T08b-8 · 2026-10-04 · Unvalidated hypotheses and acceptance criterion for the first real test
- **Running from `nativeLibraryDir`:** the manifest already declares `android:extractNativeLibs="true"` and Gradle `useLegacyPackaging = true`, so the binaries are real files. With `targetSdk` 28 the app can run them; it has not been checked on Android 10–16 (`untrusted_app_27/28` SELinux).
- **`PROOT_TMP_DIR`:** `cacheDir/proot-tmp`; proot has no `/tmp` on Android.
- **Binds:** `/dev`, `/proc`, `/sys`, `/etc/resolv.conf` and T13's. Android 8+ does not let apps read `/proc/stat` or `/proc/loadavg`: `top`, `uptime` or `free` may fail. Termux solves it with fake files; it is not done here yet.
- **Android 12+'s *phantom process killer*** may kill proot's child processes even if the service is in the foreground (D-T08-5).
- **Acceptance criterion (first test on the tablet, only when the user authorizes it):** (1) install Alpine from the distros screen; (2) open a new tab and check that the prompt is Alpine's, not Android's; (3) `uname -a` and `cat /etc/os-release` show Alpine; (4) `apk update` downloads indexes (network and DNS); (5) with shared storage on, `ls ~/storage/downloads`; (6) close and reopen the app with the session alive; (7) if (2) or (3) fail, repeat with compatibility mode on and note the result here. Until then, none of these statements is true.

### D-T08b-9 · 2026-10-04 · A single "what to run" mechanism per tab: `SessionLaunch`
- **Context:** T14 (already in `master`) added `SessionLaunch(command, environment, onClosed)` so that the hosts screen opens a tab with `ssh`, and I had added an `initialCommand` per tab with the same purpose. They were two paths for the same thing.
- **Decision:** `SessionLaunch` wins, because it carries the `onClosed` cleanup (the SSH key file is deleted whatever happens to the session) and the hosts screen already uses it. My `initialCommand`, `ShellRequest`, the `InvalidCommand`/`CommandNeedsDistro` problems and the quoted `su -c` (`ShellQuote`) are removed: without commands in the planner they had no use.
- **Rules:** (1) with `SessionLaunch` it is run as is, with proot already built by whoever creates it (`DistroLaunchFactory`); (2) without it, `ProotSessionPlanner` decides according to the tab's distro. Never both.
- **"+" and the other shortcuts** open through (2): default distro if ready, Android shell if not, with a notice.
- **Restarting an SSH tab that ended** (`restartActive`) opens a normal shell **of the same distro** (through the planner), not the Android one; the key file was already deleted when it ended, so the connection cannot be repeated: you have to reconnect from the hosts screen. If the distro no longer works, the "Android shell" notice or the error is seen; it does not happen silently.
- **Menu entries:** the provisional "Distros" (T07) and "SSH" (T14) buttons of `MainActivity` move to the "new tab" menu (long press on "+").

### D-T08b-10 · 2026-10-04 · Fix for a T13 bug: the distro's directory **is** the rootfs
- **Finding:** `DistroInstaller.publish` moves what it extracted (`staging/rootfs`) to `distro.directory`; therefore the distro's root is `distro.directory` and there is no `rootfs` folder inside. T14 uses it correctly, but `SharedStorageMounts` (T13, already merged) created the mount points in `distro.directory/rootfs/…` (it did not exist, so the `~/storage` mount would not have appeared) and my first version of the planner used the same path (it would have reported every distro as "damaged").
- **Decision:** `SharedStorageMounts` and the planner use `distro.directory`. `DistroInstaller.UNPACKED_NAME` becomes private (it is only the name of the staging folder) and its comment, which said the opposite, is corrected. T13's tests used the same wrong path and are corrected; there is a new test that pins `-r` at `distros/<token>`.
- **Why the tests did not catch it:** the file system fake and T13's tests built the path with the same wrong assumption as the code. Lesson: a test that shares the assumption with the code does not check it; an end-to-end test with a real rootfs is needed in the first test on the tablet.

## T08c — Fixes found on hardware

They come from the first real tests on a Pixel 8 (Android 17, API 37, app with `targetSdk` 28), done
by the orchestrator when the user authorized them; this fork did not touch any device. **What
worked** (reported by the orchestrator): the shell with a real PTY in a tab, input, the terminal area
shrinking above the keyboard and the extra-keys row (`stty size` = 18×49, what was
visible), the foreground service (`specialUse`) and proot with an Alpine rootfs launched by hand
from `nativeLibraryDir` (network, `apk update`, `ssh`, `nmap` and `python3` installed inside). **What
failed or looked wrong** is what this section fixes. Everything below is tested on the host only.

### D-T08c-1 · 2026-10-04 · Battery permission: the system dialog (replaces D-T08-4 on this point)

- **What was seen:** the "Keep sessions active" dialog (notifications) and, chained to it, the battery notice
  led to the general *Battery usage of apps* list in Settings. The user
  wants the real allow screen.
- **Decision:** the battery notice now taps "Allow" and launches
  `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `package:<the app>`, which is the system dialog
  "Allow the app to always run in the background?". If the device does not have it
  (`ActivityNotFoundException`) or rejects it (`SecurityException`), the general list is opened
  (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`). The order and the data are in `BatteryExemption`,
  tested; opening the screens is Android and remains to be checked. It is not requested if the app is already
  exempt (`nextPrompt` does not change).
- **Permission:** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is declared (a normal permission, no install
  prompt). **It only opens that dialog: it changes nothing unless the user accepts there.** F-Droid
  admits it; Google Play restricts it, and this app is not published there (`targetSdk` 28, SPEC §2). Lint
  warns with `BatteryLife`: it is suppressed **only on that manifest line**, with the reason written
  next to it, not in the global configuration.
- **Why D-T08-4 is replaced:** the previous one was "no forced exclusion", and a system dialog
  that the user accepts or rejects forces nothing. The rest of D-T08-4 (order of the notices, once
  per start, wake lock) stays the same.
- **For `PRIVACY.md` (T21):** explain that the permission exists so that an SSH connection or a
  long task is not paused with the screen off, that it collects no data and that it only opens the dialog.
- **Not validated on a device:** that the system dialog appears on Android 17 with `targetSdk` 28.

### D-T08c-2 · 2026-10-04 · Storage switch: it could not be reproduced; two real bugs fixed

- **What was seen:** on a clean install, the "Device storage at `~/storage`" card
  showed the switch **on**, although T13 (D-T13-2) documents it as off by default.
- **What was checked:** no code writes `shared_storage = true` by itself; `AppSettings`'s default
  value is `false` and a test already watches it (`SharedStorageSettingTest`). So
  **the exact cause could not be determined without the device**.
- **Latent bug 1 (probable cause):** the permission callback used `results.values.all { it }`, and
  `all` over an empty map is `true`: an empty result (an interrupted request) turned the
  feature on **with no permission at all**. Now `StorageToggle.allGranted` requires the result not to be empty.
- **Latent bug 2:** the UI showed `enabled` without checking whether the permission was granted, so a saved
  "on" with no permission looked like it worked. Now the switch is shown
  on only if the setting is on **and** the permission granted (`StorageToggle.isShownOn`).
- **Pending:** reinstall clean and check the switch. If it still appears on, the
  cause is another and the real state of `setting` on the device has to be looked at.

### D-T08c-3 · 2026-10-04 · Distros screen: the header on two rows

The "Distributions" title and the "Install a distro" button shared a row and overlapped on a
~360 dp phone. Now the title and "Close" share a row (the title with `weight(1f)`) and the install
button takes its own, at full width. This way it does not overlap at any width or font scale.
Not validated on a device (the overlap was seen; the fix is a design one).

### D-T08c-4 · 2026-10-04 · Fake `/proc` inside proot (approximate on purpose)

- **What was seen:** inside proot, `/proc/stat`, `uptime`, `loadavg`, `version`, `vmstat` and others give
  `Permission denied` (SELinux does not let an app read them), so `top`, `uptime`, `free`, `vmstat`
  and `htop` do not work.
- **Decision:** as proot-distro does, each launch writes five fake files to private storage
  (`files/fake-proc/`) and mounts them with `-b <file>:/proc/<name>` **after**
  `-b /proc` (binds are applied in order). It applies to normal tabs and to SSH ones
  (`ProotSessionPlanner` and `DistroLaunchFactory`). If they cannot be written, the session starts without
  them (`FakeProcSource.None`), with no error.
- **What it contains (`FakeProc`, pure and tested):** the **format** is exact so that `procps` and
  `busybox` read it; the **values are made up**: `stat` splits CPU time in fixed proportions
  of the uptime (3 % user, 4 % system, 1 % iowait, 1 % softirq, the rest idle) with one line per
  real CPU and counters that grow with the uptime; `uptime` is the device's real one (with the idle
  of all CPUs); `loadavg` is a constant (`0.12 0.07 0.02`); `version` carries the real kernel
  version (sanitized: no line breaks or control characters, so that an odd value does not
  inject lines); `vmstat` has the counters `procps` asks for, with plausible values.
- **Consequence:** `top` and `htop` will show a **fake** CPU usage that is constant over time. It serves
  to make them start, not to measure anything. `/proc/meminfo` and the per-process folders are **not**
  faked: they are assumed readable (not checked on the device).
- **Not faked:** `cpuinfo` (partial in the listing seen), `/proc/sys/kernel/cap_last_cap`.
- **Not validated on a device:** that proot admits binding a file over one in `/proc` on
  Android 17 (proot-distro does it in Termux) and that `top`, `uptime` and `free` end up working.

### D-T08c-5 · 2026-10-04 · The dark circle was the panes menu; text margin

- **The circle:** it was not a handle or an artifact: it is the "⋮" button of T10's panes menu
  (`PaneMenu`), always drawn in the top-left corner of the focused pane, with a round
  dark background. It covered the start of the first line even if there was no split.
- **Decision:** the button only appears when the tab is split (then it serves for zoom,
  close and swap) and moves to the **top-right** corner. Splitting a pane that is alone is
  done from the "+" menu of the tab bar (long press), next to SSH and Distros, with
  "Split right" and "Split down"; T11's shortcuts stay the same. The links of that
  menu are grouped in `TabBarLinks` because of detekt's parameter limit.
- **Margin:** 6 dp around the text (today it touched the screen's edge). It is not grid: it is
  subtracted from the area (`EdgeInsets.withTextMargin`, tested) and the screen pads the same
  (`padding` before measuring the panes' area), so the PTY still knows exactly the
  size that is drawn. 6 dp is an eyeballed choice, without seeing the result.
- **Out of scope, for T12c:** own fonts, scheme editor and tab style.
- **Not validated on a device:** that the margin looks good on phone and tablet, that the grid size
  still matches what is visible (`stty size`) and that splitting from the "+" menu works.

## T12c — Customizable appearance

It comes from the first real test on a Pixel 8: the user said the terminal view was ugly and
asked whether there could be own fonts and design (and, understood this way, that it should **not** look like Termux).
Before T12c the schemes and JetBrains Mono existed, but **there was no screen at all** to choose them.

### D-T12c-1 · 2026-10-04 · An "Appearance" screen with a live preview, linked from the "+" menu

- **Decision:** `AppearanceScreen` (light/dark/system theme, OLED black, dynamic colors, scheme,
  font, size, line spacing, letter spacing, margin, corners, cursor shape and blink, style
  of the bars) with a preview that draws a sample terminal with those values. It is opened from
  the "+" menu of the tab bar ("Appearance…"). T16 will link it from the general settings.
- **Reason:** changing something without seeing the result forces going back and forth to the terminal. The preview uses
  Compose's `Text` with the same font (`FontFamily(Typeface)`), the same scheme and the same spacing.
- **Alternatives:** flat settings without a preview (faster, worse for deciding); previewing with the
  real emulator (more faithful, but it drags a whole session into a settings screen).
- **Impact:** the preview is an approximation: Compose's line spacing and letter spacing are not
  identical to `TerminalPainter`'s. What is definitive is what is seen in the terminal.

### D-T12c-2 · 2026-10-04 · Own fonts: validated before accepting and not redistributed

- **Decision:** import with `ACTION_OPEN_DOCUMENT`, read at most **8 MB**, and accept only if: the
  header is TrueType (`0x00010000` or `true`) or OpenType (`OTTO`); `Typeface.Builder` loads it; it has the
  basic glyphs (`a-z`, digits, commonly used signs) and it is **monospaced** (15 glyphs of very
  different widths, `iIl1.:|WMm@#0OQ`, measure the same within a 1 % tolerance). If any step fails the
  **file is deleted** and nothing is left. It is stored in `files/fonts/<id>.ttf|otf` with its own id
  (`font-<name>-<random suffix>`), never with the original file's name.
- **Reason:** a proportional font breaks the grid (every cell measures the same); an icon font
  or one without letters makes the terminal illegible. The file name is untrusted data: it is discarded.
- **Alternatives:** accepting `.ttc` collections (`ttcf`, discarded: a collection brings several faces and one
  would have to be chosen); not validating and warning on use (worse: the user sees a broken terminal without knowing why).
- **Impact:** imported fonts stay on the device and are **not uploaded nor redistributed**: the
  screen warns that their license is the user's responsibility. Bold and italic of an
  imported font are synthesized by Android (`Typeface.create(family, BOLD)`) if the family does not carry them.
- **Not validated:** that `Typeface.Builder(file)` loads real fonts of different formats on Android 8–17,
  and the width measurement with the real text engine (the tests use a fake probe).

### D-T12c-3 · 2026-10-04 · Letter spacing widens the cell; line spacing lengthens it and centers the text

- **Decision:** `Paint.letterSpacing` is applied before measuring `cellWidth` ("X"), so the whole grid
  uses the widened cell and `terminalLayoutFor` computes the columns with it. Line spacing multiplies
  the cell height and the text is centered in it (the extra space is split above and below).
- **Ranges:** line spacing 0.8 to 1.6; spacing −0.05 to 0.3 em; margin 0 to 24 dp; corners 0 to 16 dp. Every
  saved or imported value is **clamped** to its range (`sanitized()`), and a non-finite one goes back to the default.
- **Impact:** a line spacing below 1 may clip the strokes of tall letters: it is the price of
  allowing it (minimum range 0.8).
- **Not validated:** the real look with each font, and that wide glyphs (emoji, CJK) stay aligned with
  a spacing other than 0 (T03's existing correction squeezes them into their cell).

### D-T12c-4 · 2026-10-04 · The cursor shape is chosen by the user and takes precedence over what the program asks for

- **Decision:** the cursor (block, underline, bar) and its blinking come from the appearance; the
  `TerminalPainter` no longer looks at `emulator.cursorStyle`.
- **Reason:** predictable. With the program's style, a `vim` or a `fish` that changes the shape per mode
  leaves the user without control.
- **Alternative:** respecting the shape the program asks for (DECSCUSR) and using the chosen one only as a default.
  It is closer to what a desktop terminal does. **Revisable**: a "respect the program"
  setting can be added.
- **Not validated:** the blinking (a 530 ms timer that invalidates the canvas) and its battery cost.

### D-T12c-5 · 2026-10-04 · The bars take the scheme's colors, and are always readable

- **Decision:** `ChromeColorsFor.scheme` derives from the scheme's palette the colors of the tab bar,
  the active tab, the extra keys, the pane border and the accent, mixing background and text
  (7 % / 18 % / 30 %). If that mix leaves the bar's text below **4.5:1** (Solarized does so
  by design), `ColorMath.readableOn` pushes it towards white or black the minimum necessary. `ChromeStyle`
  allows going back to the system theme's colors.
- **Reason:** with dynamic colors on, the Pixel showed the bars in the wallpaper's blue,
  alien to the scheme. With this the whole window looks like one piece.
- **Finding:** the legibility test of all the built-in schemes caught Solarized Dark before
  it reached the screen.
- **By default:** `SCHEME`. Whoever wants the previous behavior chooses "The system theme's".

### D-T12c-6 · 2026-10-04 · No "compact" bars: they break the 48 dp touch targets

- **Decision:** there is no compact bar option. Instead the **color** and the
  **corner radius** are customized.
- **Reason:** the SPEC (§6, accessibility) requires touch targets of at least 48 dp, and the tab bar
  and the keys row already measure that. A 36 dp bar would be an accessibility regression.
- **Alternative:** allowing it with a warning. Discarded: better not to offer what should not be used.
- **Detail:** each key's color is drawn with a 2 dp gap, but **the whole cell** is the touch
  target.

### D-T12c-7 · 2026-10-04 · The floating `⋮` button was already resolved in T08c

- The dark circle over the prompt was T10's panes menu. T08c already shows it only when there are
  split panes (splitting a single pane lives in the "+" menu). T12c only gives it the colors of
  the bars' palette.

### D-T12c-8 · 2026-10-04 · Scheme editor without a color library: hexadecimal and three sliders

- **Decision:** a full-screen editor with the name and the **20 colors** (16 ANSI, text, background,
  cursor, selection). Each color is changed with a hexadecimal text box (`#rrggbb`, also `#rgb`) and
  three synchronized sliders (red, green, blue). Duplicate, create, edit, delete, import and
  export (T12's versioned JSON). A scheme cannot have the same name as another, built-in or own.
- **Contrast warning:** lists up to five colors that are hard to read on the background (text 4.5:1; cursor and
  ANSI 3:1; black and bright black are excepted, which blend into the background on purpose). **It never blocks.**
- **Reason for not using a color picker:** one more dependency for a control that this does just as well.
- **Alternative:** an own HSV color wheel. More convenient; more code to validate without a device.

### D-T12c-9 · 2026-10-04 · For the backup (T15): keys and font files

- **Settings repository keys that T15 must serialize** (they are part of the backup format: do not
  rename): `appearance_font_id`, `appearance_line_spacing`, `appearance_letter_spacing`,
  `appearance_margin_dp`, `appearance_cursor_shape`, `appearance_cursor_blink`, `appearance_chrome_style`,
  `appearance_corner_dp`, `custom_fonts` (JSON: `id`, `name`, `fileName`) and the already existing
  `terminal_scheme`, `terminal_font_size_sp`, `custom_schemes`, `theme_mode`, `oled_black`, `dynamic_color`.
- **The font files are NOT in those keys:** they live in `files/fonts/`. **T15 must include that
  folder** in the configuration backup; if it only carries the JSON, a restored font goes back to the bundled one
  (`FontCatalog.idOrBundled`), without breaking anything.
- **Safe restoration:** `FontCatalog.decodeList` discards entries whose `fileName` is a path (`/`, `\`,
  `..`, `.`) or comes empty, and `AndroidFontStore` only touches names that are a simple file name
  inside `files/fonts`. A tampered backup cannot write outside that folder.

### D-T12c-10 · 2026-10-04 · The sliders save on release

- Each `Slider` keeps its value while it is dragged and writes to the settings when the finger is lifted, so
  there is one write per gesture and not hundreds. In exchange, the preview updates on release.
- This screen's font size and the pinch zoom share the same saved value
  (`terminal_font_size_sp`): a new value from the store is reapplied to the live terminal.

### T12c: what has NOT been validated (without a device)

There are only host tests. Still to check on a phone and on the tablet:

- The full "Appearance" screen (layout, scrolling, keyboard, rotation, TalkBack and 48 dp targets).
- The document picker to import a font and to import or export a scheme.
- That `Typeface.Builder` really loads real .ttf and .otf, and that the monospace check
  accepts the good fonts and rejects proportional ones with the real text engine.
- The terminal's look with line spacing and letter spacing other than 1 and 0, and with foreign fonts.
- The cursor shape and its blinking, the margin, and the bars' colors with each scheme (also
  with dynamic colors on and with OLED mode).
- That changing the font or the spacing with open sessions repaints and readjusts the pty without cutting anything.

## T15 — Backups and restore

### D-T15-1 · 2026-10-04 · Format: a plain tar, optional encryption on top

- **Decision:** a `.utbackup` file is a plain `tar` (readable with any `tar`) with `manifest.json` first, `config.json` and one `distros/N.tar.gz` part per distro. Encryption, if requested, wraps the whole tar. The manifest carries the format version, the type, and each part's SHA-256 and size.
- **Reason:** restoring can validate the whole file before touching anything, and a backup without a password is still recoverable by hand.
- **Alternatives:** an own binary container (more compact, but opaque and impossible to rescue without the app); a `.zip` (forces reading the central directory at the end: bad for streams).
- **Detail:** the container uses `BIGNUMBER_STAR` and not `BIGNUMBER_POSIX`: the latter prepends a PAX header to each entry and the file no longer starts with `manifest.json`, which is how a plain backup is recognized.

### D-T15-2 · 2026-10-04 · Deviation from the SPEC: gzip instead of zstd

- **Decision:** distros are compressed with **gzip** (`java.util.zip`), not with zstd as the SPEC asks (`.tar.zst`).
- **Reason:** zstd in Java needs a library. `zstd-jni` packages precompiled `.so` files (forbidden by CLAUDE.md for F-Droid). `aircompressor` is Apache-2.0 and pure Java, but its v3 relies on recent JDK APIs and on `Unsafe`, and I cannot validate it on Android without a device. gzip adds no dependencies and is already what the installer knows how to read.
- **Impact:** files somewhat larger than with zstd. If a library is validated later, only the name and the compressor of the distro parts change: `format` goes up to 2.

### D-T15-3 · 2026-10-04 · Encryption: AES-256-GCM in chunks with a PBKDF2 key

- **Decision:** a 256-bit key derived from the password with PBKDF2-HMAC-SHA256 (600,000 iterations, random 16-byte salt). Data is encrypted in 64 KiB chunks, each with its own GCM tag. Each chunk's nonce is a random prefix of the file, the chunk number and a "last" mark; the whole header is authenticated with each chunk.
- **Reason:** the rootfs does not fit in RAM, and with this construction a changed, repeated or reordered chunk or a truncated file (even right between two chunks) fails authentication just like a wrong password. A header with absurd iterations or chunk size is rejected before spending CPU.
- **Impact:** a wrong password and a tampered file are indistinguishable (`WrongPasswordOrCorrupt`): nothing is written in either case.

### D-T15-4 · 2026-10-04 · SSH keys: only inside an encrypted backup

- **Decision:** the SSH private keys are encrypted with the device's Keystore, and that key cannot leave. That is why a backup carries them **in the clear inside the container encrypted with the user's password**, and the export **refuses** (`KeysNeedPassword`) to include them without a password. The user can leave them out.
- **Reason:** the alternative, writing them unprotected in a file that travels between devices, is exactly what T14's threat model avoids.
- **Impact:** when restoring without the keys, a host that pointed to a key loses that alias and is left without a key, not broken.

### D-T15-5 · 2026-10-04 · Restoring only adds; settings are replaced

- **Decision:** restoring never deletes or overwrites what is there: profiles, layouts, hosts and keys whose name (or alias) already exists are kept and counted as "skipped". Settings are replaced, because the goal is to leave the app the same. References between elements go by name or position, never by database id, because those change between devices.
- **Device-specific values:** the shared storage switch is **not copied** (the permission belongs to the device and is requested again). Out-of-range values are clamped to the limit or go back to the default value.
- **Not included yet:** extra keys and shortcuts are not persisted in the app, so there is nothing to copy. `config.json` ignores unknown fields and carries a version, so they can be added.

### D-T15-6 · 2026-10-04 · Two passes and transactional distros

- **Decision:** the first pass reads the whole file and checks encryption tags, manifest, hash and size of each part and the configuration, **without writing anything**. The second restores and checks each part again on the fly (the file may have changed between passes, or come from a source that changes).
- **Each distro** is all or nothing: it is registered, unpacked into a temporary directory with T07's safe extractor (no path traversal, no writing through links, with size limits), the hash is checked and only then is it moved to its place and marked `READY`. A failure or a cancellation at any point removes it.
- **Between distros there is no transaction:** if the second fails, the first, already complete and verified, stays. The configuration is applied at the end, after the distros it may refer to.
- **If the name already exists**, the restored distro is called `Name (restored)`, then `(restored 2)`, etc.
- **Impact on disk:** exporting archives each distro first in a private working directory (to know its hash before writing the manifest), so room is needed for a temporary compressed copy. Restoring needs no copy: it reads from the stream.

### D-T15-7 · 2026-10-04 · What is kept and what is not of the rootfs

- The nine permission bits, symbolic links as links, dates, and directories are kept. **Sockets, pipes and devices are skipped**, as T07's extractor does.
- **Hard links are archived as independent files** (the size grows if there are many).
- **Owners:** all entries are written as `root`. On the device all files belong to the app's user and proot presents ownership by itself, so there is no real owner to keep.
- A rootfs file without read permission for the owner makes the export fail with a clear error and without leaving a half-done file (forcing the permission was not attempted). It is a hypothesis: if it appears in real distros, it will have to be changed.

### D-T15-8 · 2026-10-04 · Coverage

- `data.backup` is measured by Kover's critical threshold: **100 % of line and branch (749/749 and 362/362)**. To get there, unreachable defensive branches were removed, and the `@Serializable` classes and the `$$serializer`s were added to Kover's exclusion filter: they are pure data with generated code whose branches no test can reach, just as Hilt and Room were already excluded.
- `TarGzExtractor` gained the overload that extracts from a stream (to read a distro's part without copying it); its behavior on files does not change and its tests stay green.

### D-T15-9 · 2026-10-04 · What is NOT validated (without a device)

1. The system file picker (`ACTION_CREATE_DOCUMENT` and `ACTION_OPEN_DOCUMENT`) with real providers (Drive, SD card), and that `openOutputStream(uri, "wt")` truncates in all of them.
2. An export and restore of a **real** distro (hundreds of MB) on a phone: times, memory use, and that PBKDF2 with 600,000 iterations does not take too long on a low-end phone.
3. That the restored rootfs boots with proot (it depends on T02/T08b's validation).
4. Whether a long export survives with the app in the background: it runs in the ViewModel's scope, **not** in the foreground service, so Android may kill it.
5. The interface (dialogs, password, progress) and its accessibility with TalkBack.
6. `mkfifo` is needed on the host for one of the tests (there is `mkfifo` on Linux and macOS).

## Requirements added after the first tests on hardware (2026-10-04)

The user tried the app on a Pixel 8 and asked for three things. It is noted here which requirement was touched and why.

### D-REQ-1 · 2026-10-04 · The extra-keys row follows the on-screen keyboard
- **Decision:** `ExtraKeysConfig.onlyWithKeyboard` (default `true`): the row is drawn only while the keyboard is visible (`WindowInsets.ime`), and its height is no longer reserved in the grid computation when it is not seen. SPEC RF-08 updated.
- **Reason:** with the keyboard closed the row (two rows of 48 dp) ate terminal without being useful for typing.
- **Alternatives:** always hiding it and showing it with a button (more taps); tying it to "there is a physical keyboard" (not reliable on Android). The option to always see it is left.
- **Impact:** the pty resize when opening and closing the keyboard was already debounced (D-T04-2); the row enters and leaves with the keyboard. The saved text format adds the `onlyWithKeyboard=` line; an old text without it counts as `true`.
- **Not validated on the device:** that there is no grid flicker while the keyboard animates.

### D-REQ-2 · 2026-10-04 · Starting in the default distro: already fulfilled, with an explicit requirement (RF-13)
- **Checked on a Pixel 8:** with Alpine installed, closing the app completely and reopening it leaves `localhost:~#` (proot) in the first tab.
- **Decision:** it becomes requirement RF-13 and T09b is added for what is missing: naming the tabs with their distro and repainting new tabs.

### D-REQ-3 · 2026-10-04 · The Settings screen becomes an access requirement, not just a content one
- **Finding:** there was no way to reach a settings screen (T12, T13, T14 and T15 each left their card on the distros screen). T16 was a task at the end of the plan.
- **Decision:** RF-11 requires a permanent ⚙ icon and defined sections; T16 is extended with them and goes up in priority.
- **Reason:** without visible settings, T12c (appearance) and all the options of T11–T15 cannot be used.

## T22a — iOS-style design system: base components

### D-T22a-1 · 2026-10-04 · Own components on top of `foundation`, without Material 3 or a widget library
- **Decision:** the components (`ui/ios`) are written on top of `compose.foundation` and `compose.ui`, without using Material 3's components. The Material theme of the current screens is not touched until T22b and T22c.
- **Reason:** Material brings its own ripple, shapes, states and typography; to look like iOS almost all of them would have to be disabled. Accessibility (roles, states, 48 dp, font scale) is done by hand and covered in each component.
- **Alternatives:** *compose-cupertino* (Apache-2.0, 1,659 stars): its last change is from October 2025 and it is built on Compose Multiplatform 1.6.1, much older than our BOM; it would add Multiplatform dependencies to an Android-only project. *Themed Material 3*: see above.
- **Impact:** no new dependencies or changes in `verification-metadata.xml`.

### D-T22a-2 · 2026-10-04 · Typography: Inter (SIL OFL-1.1), four weights unmodified
- **Decision:** Inter 4.1 (Regular, Medium, SemiBold, Bold), copied unchanged from the `extras/ttf` of the official release (`Inter-4.1.zip`, SHA-256 `9883fdd4a49d4fb66bd8177ba6625ef9a64aa45899767dde3d36aa425756b11e`). License and credits in `THIRD_PARTY_NOTICES.md` and `assets/licenses/Inter-OFL-1.1.txt`.
- **Reason:** SF Pro cannot be used. Inter is the closest free alternative in shape and has the weights the iOS scale asks for (Large Title 34 to Caption 12).
- **Alternatives:** Inter's variable font (fewer files, but a more delicate variations API and more weight per use); the system font (Roboto, which does not look like iOS).
- **Impact:** the APK grows about 1.6 MB. The terminal's font is still JetBrains Mono (T12).

### D-T22a-2b · 2026-10-04 · Icons: Lucide (ISC), 16 vectors with the paths unchanged
- **Decision:** 16 icons from Lucide 1.52.0 (chevrons, check, plus, x, search, ellipsis, settings, trash, folder, download, terminal, info, copy, key) converted to `res/drawable/ic_ios_*.xml`; an `IosGlyph` enumeration names them.
- **Reason:** SF Symbols cannot be used. Lucide is ISC (compatible with GPL-3.0) and its rounded 2 px stroke looks like iOS's. Some icons inherit from Feather (MIT): both are credited.
- **How it was converted:** each SVG element (`path`, `line`, `circle`, `rect`, `polyline`, `ellipse`) became a vector path with the same stroke; the color is set when drawing (`ColorFilter.tint`).
- **Note:** `trash-2` does not exist in 1.52.0; `trash` is used.

### D-T22a-3 · 2026-10-04 · Translucent bars: real background blur with `GraphicsLayer`, no library
- **Decision:** the scrolling content is recorded once into a layer (`GraphicsLayer`); the bar draws a blurred copy of the part behind it (`RenderEffect`, from Android 12) with a tint on top. Before Android 12 the bar is almost solid (97 %). The policy is pure logic (`BarStyle.forSdk`, with tests).
- **Reason:** `Modifier.blur` only blurs its own content, not what is behind it; the iOS effect needs to blur what is underneath.
- **Alternative:** *Haze* (Apache-2.0, active): does the same with more polish. It stays as plan B if ours gives problems, in exchange for a new dependency.
- **Risk (not validated):** the cost of blurring again on every frame of a scroll, especially on low-end devices. It has to be measured on the device.

### D-T22a-4 · 2026-10-04 · Palette: iOS colors with the text raised to 4.5:1, and the tint comes from T12's scheme
- **Decision:** `iosPalette(decision, scheme)` gives backgrounds, cells, labels, separator, tint and destructive red in light, dark and OLED. The tint starts from the scheme's ANSI blue (index 4); the tint, the red and the secondary label approach black or white just enough to reach 4.5:1 on the cell and on the page (`ColorAdjust.ensureContrast`).
- **Reason:** iOS's system blue (≈4.0:1) and its secondary label (≈3.3:1) do not reach AA in light. RF-14 requires 4.5:1.
- **OLED:** background and page in pure black; cells in `#111113` (not `#000000`) so they can be told apart, and separator `#2A2A2D`.
- **Test:** a test goes through the 3 themes × (no scheme + all the bundled ones) and requires 7:1 for the label and 4.5:1 for the rest.

### D-T22a-5 · 2026-10-04 · Bottom sheet: a `Dialog` draggable by the handle, with detents by velocity projection
- **Decision:** the sheet is a full-screen `Dialog` with its own background; it is dragged by the handle (48 dp tall) and, on release, `SheetDetents.snap` projects the velocity 0.15 s and chooses the nearest detent (medium 0.5, large 0.94) or closes it if it ends up below half of the lowest one. A tap on the background, the back button and an accessibility action also close it.
- **Reason:** a `Dialog` gives modal mode and the back button for free.
- **Known limit:** the sheet's content scrolls on its own; it does not drag the sheet (at most, the handle). It is simpler and does not fight with the scroll.

### D-T22a-6 · 2026-10-04 · Alerts and action sheets: `Dialog` with its own window
- **Decision:** `IosAlert` (270 dp, two actions side by side) and `IosActionSheet` (stuck to the bottom, cancel on a separate card) are `Dialog`s. Each action closes the dialog after running; all measure at least 48 dp.

### D-T22a-7 · 2026-10-04 · What the catalog shows and where it lives
- **Decision:** `IosCatalog` and its activity are in the `debug` *source set* (with their strings in English and Spanish), so they do not enter the production APK. It is opened with `adb shell am start -n com.qtekfun.ultimateterminal/.ui.ios.IosCatalogActivity`. A segmented control switches between light, dark and OLED, and a row goes through the schemes.

### D-T22a-8 · 2026-10-04 · What Lint and detekt forced to change (without relaxing anything)
- `Modifier.offset` with state values must use the lambda overload (the switch and the segmented control).
- An unused string resource (it is used as the title of the catalog's theme section).
- Constructors with more than 7 parameters (`IosColors`, `IosTypography`): they now receive the palette or a map of text roles.
- Files that do not match their single declaration (`IosGlyph`, `IosAccessory`, `IosButtonStyle`, `BackdropState`): renamed or split.

### D-T22a-9 · 2026-10-04 · What is NOT validated (without a device)
- How each component looks on the device and on the tablet, and that the catalog opens.
- **The bars' blur**: that it looks good and that scrolling stays smooth (D-T22a-3).
- Haptic feedback (the `SegmentTick`, `ToggleOn`/`ToggleOff`, `Confirm` types depend on the Android version and on whether the user disabled it).
- TalkBack with roles and states (switch, segments, sheet with custom actions).
- The bottom sheet: the feel of the drag, the projection velocity and that the `Dialog` without window dimming looks good with the keyboard.
- The search field with the keyboard (focus, IME, clear button).
- Performance and memory consumption of the blur layers.

## T20-T21 — F-Droid metadata and documentation

Text work: nothing was compiled, no code or screen was touched and no device was used. What
is claimed about what is verified on a device comes from what the orchestrator noted in
`DECISIONS.md` (section T08c and following) and in `PLAN.md`; it has not been checked again.

### D-T20-1 · 2026-10-04 · The fastlane texts are rewritten; the changelog is `10001`'s

- **What there was:** a draft from a previous agent, from when only T03 existed ("today the app shows a
  terminal with the Android shell; distros and tabs are future"). It is no longer true.
- **Decision:** `title`, `short_description`, `full_description` and the changelog were rewritten, in
  `en-US` and `es-ES`, with what exists in `master` and saying expressly what is tested only on a
  computer. Limits checked with a script: title 16/30, summary 64 and 65/80, description 2442 and
  2570/4000, changelog 320 and 358/500.
- **Changelog `10001`:** `0.1.0-rc.1` gives `(0*10000 + 1*100 + 0)*100 + 1 = 10001`, which is the file. `10099`
  (the final `0.1.0`) is **not** created because that version does not exist yet: `RELEASING.md` asks for it when cutting the release.
- **Why say it in the listing:** an F-Droid listing that promised tablets, panes or backups
  "working" would be false today: it has only been tested on a phone. It says "early preliminary version".

### D-T20-2 · 2026-10-04 · Icon

- `images/icon.png` (512×512, RGB) left by the previous agent is reused: the green `>_` prompt on the
  launcher icon's dark background. It was looked at by hand and matches `ic_launcher_foreground.xml`/the background
  color; it was not regenerated. It is the same image in both locales.
- **Pending:** if the launcher icon is redesigned (T22b/c), regenerate this one.

### D-T20-3 · 2026-10-04 · Screenshots: pending, not invented

- There is no `phoneScreenshots/` or `sevenInchScreenshots/`: a screenshot has to be real and of the current build, and
  it cannot be taken without a device. `fastlane/README.md` lists the ones that are advisable.
- **Pending (orchestrator, on a device):** taking them, above all one on a tablet, which is the case for which
  the project exists.

### D-T20-4 · 2026-10-04 · Recipe `fdroid/com.qtekfun.ultimateterminal.yml`: what fits and what does not

- **Consistent with the repo:** `submodules: true` (proot comes from the submodule), `ndk: 28.2.13676358` (same as
  `app`'s and `terminal-emulator`'s `ndkVersion`), `subdir: app`, JDK 21, `UpdateCheckMode: Tags` with only
  final versions, `Binaries` for the reproducible build, `License: GPL-3.0-or-later`.
- **Risks that cannot be checked without running `fdroid build`:**
  1. **CMake 3.31.6** is pinned in `app/build.gradle.kts` (`externalNativeBuild.cmake.version`) and the recipe does not
     install it. If F-Droid's server does not have it, Gradle tries to download it with `sdkmanager` and may fail. It
     has to be tried and, if needed, a `prebuild`/`sudo` that installs it added.
  2. `AllowedAPKSigningKeys` is a placeholder: it is filled with the release certificate's fingerprint (see `RELEASING.md`).
  3. `versionName`/`versionCode`/`commit` (`0.1.0`, `10099`, `v0.1.0`) are those of the first final version, not those of the
     tree (`0.1.0-rc.1`, `10001`); the `-rc.N` are not offered on F-Droid on purpose.
  4. `targetSdk 28`: how F-Droid's inclusion policy treats it has not been checked (it does not forbid it, but it may
     warn). Verify it when submitting the recipe.
- **The recipe was not changed:** each change would be an assumption about F-Droid's server that I cannot check.

### D-T20-5 · 2026-10-04 · Anti-features: none for sure, one to ask about

- **Do not apply:** `Ads`, `Tracking`, `NonFreeAdd`, `NonFreeAssets` (the font and the icons are free: OFL/ISC),
  `NonFreeDep` (no Google services or blobs; `checkForbiddenDependencies` and `licensee` watch it),
  `UpstreamNonFree`, `KnownVuln`, `NoSourceSince`.
- **To ask F-Droid about, without asserting:** `NonFreeNet`. To install **Debian** the app downloads the manifest and
  the `rootfs.tar.gz` from `raw.githubusercontent.com/debuerreotype/docker-debian-artifacts` (D-T06-3). GitHub is a
  proprietary hosted service, although what is downloaded is free software; I do not know whether F-Droid counts it as
  "depends on a non-free network service" for an optional download the user initiates. Ubuntu
  (`cdimage.ubuntu.com`) and Alpine (`dl-cdn.alpinelinux.org`) come from the projects' own mirrors.
- **Downloading and running third-party software** (the distros) has no anti-feature of its own that I know of;
  it is mentioned in the description and in `PRIVACY.md`. Confirm it when submitting the recipe.

### D-T20-6 · 2026-10-04 · Declared license and a point that remains open

- **Declared:** GPL-3.0-or-later. Compatible with what it carries: Apache-2.0 (Termux emulator, AndroidX, Kotlin…),
  OFL-1.1 (JetBrains Mono, Inter), ISC (Lucide), MIT (schemes), GPL-2.0-or-later (PRoot), LGPL-3.0-or-later
  (talloc); everything is in `THIRD_PARTY_NOTICES.md`.
- **Open, not resolved here (D-T03-2):** the files of Termux's `terminal-emulator` carry no license header and
  Apache-2.0 only appears in upstream's root `LICENSE.md`. It has to be **confirmed with the Termux
  maintainers before submitting the app to F-Droid**. This is not legal advice.

### D-T21-1 · 2026-10-04 · The README distinguishes "verified on a device" from "only on a computer"

- **Decision:** a table with two columns (device / host only). Verified on a Pixel 8: startup, shell with
  PTY, input and resizing with the keyboard (`stty size`), foreground service, installing Alpine from the app,
  opening it with proot in a tab, `apk add` with network, `ssh` and `python3` in that tab, `nmap` installed, and that on
  startup the default distro opens. Everything else, host only.
- **Reason:** the project has been made almost without a device and an optimistic README is worse than one that says what is missing.
  What was fixed and has not been looked at again (the row that hides with the keyboard, the text that reaches the shell
  while typing, the direct battery dialog, the fake `/proc`) is listed apart as "in the code, not looked at".
- **Outdated fact that was not touched:** the "Pending hardware validation" section, at the start of this file, begins
  by saying that "the user has forbidden testing on the Pixel 8". It was true when written, but the user
  authorized it later for the orchestrator. It is not edited here so as not to clash with the other agents who add to this
  file; it should be updated when the validation on a device is closed.

### D-T21-2 · 2026-10-04 · `PRIVACY.md`: each permission with its reason, in English and Spanish

- **Content:** no telemetry or accounts; what is stored and where (private storage, `allowBackup=false`, keys
  encrypted with the Keystore); the **three domains** it connects to in order to download distros (`dl-cdn.alpinelinux.org`,
  `cdimage.ubuntu.com`, `raw.githubusercontent.com`) and that only HTTPS is used; the public fallback DNS
  (`1.1.1.1`/`9.9.9.9`) as a **last resort** and its effect on privacy; a table with the nine manifest
  permissions (`INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE(_SPECIAL_USE)`, `POST_NOTIFICATIONS`,
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `WAKE_LOCK`, `READ/WRITE_EXTERNAL_STORAGE`) and the ones it does **not** ask for; the
  backups' password (it is not stored, it is not recoverable); and why `targetSdk 28` and not Google Play.
- **Nuance that was meant to be avoided:** "does not write content to logs" was worded as "by design", because it is a rule
  of the code (`CLAUDE.md`) and not something this work has audited.
- **The storage permission** is described as it is: always declared, requested only when turning on `~/storage`,
  and with the caveat that on Android 13 or later it may be granted for media only (D-T13-3, unverified).
- **It must be reviewed when it changes:** the manifest (any new permission), T16 (DNS will become a setting),
  and if any other domain the app connects to is added.

### D-T21-3 · 2026-10-04 · `CONTRIBUTING.md` collects what was learned in this stage

- Main branch `master`; Conventional Commits; PR with **all** checks, including GitGuardian (which may
  flag false positives: it is read and explained, not ignored); **do not merge `master` into the branch** (GitGuardian
  re-read T15's code in other people's PRs) but rebase; `lintDebug` locally before opening the PR (Lint with warnings as
  errors); deterministic tests (the clock in the tar fixtures left critical coverage failing 2 out of 6
  runs); coverage 85 % and 100 % critical; `targetSdk` 28 untouchable; credits in `THIRD_PARTY_NOTICES.md`.
- **It does not claim** anything that has not been done: it does not promise GitHub's private vulnerability reporting (I do not know if it is
  enabled), only "contact the maintainer privately".

### Pending from this work

- Real screenshots (D-T20-3). `AllowedAPKSigningKeys` fingerprint, check CMake on F-Droid's server and ask about
  `NonFreeNet`, `targetSdk` 28 and the Termux license point (D-T20-4 to D-T20-6) when submitting the recipe.
- `fdroid lint` and `fdroid readmeta` were not run: they are not on this machine. They have to be run before sending the recipe.

## T09b — Tabs: repainting the new ones and distro name

### D-T09b-1 · 2026-10-04 · Root cause of the blank tab: `activeHost` was computed before the host existed
- **Finding (Pixel 8):** a new tab came out blank until switching tab and coming back, and with the keyboard visible what was typed was not repainted until hiding it. It was reproduced on cold start and in tabs created with "+".
- **Cause:** `SessionManager.activeHost` was `state.map { factory.host(it.activeId) }.distinctUntilChanged()`. The state publishes the new tab **before** starting its session (the planner reads the distro from the already published state), and the host is registered in the factory afterwards. At that moment `factory.host(...)` returned `null`, `distinctUntilChanged` prevented repeating the query and the ViewModel rebroadcast `flowOf(0)`: no change notification from the emulator reached the view. Anything that changed the state (switching tab, the resize when hiding the keyboard) repeated the query and "fixed" the screen, which explains the symptoms.
- **Decision:** `HostRegistry<T>` (domain, generic) keeps the hosts and exposes an observable counter; `follow(activeId)` repeats the query each time a host is registered or removed. `AndroidSessionFactory` uses it instead of a `mutableMapOf`, `SessionManager.activeHost` is `registry.follow(...)`, and the inactive panes (`InactivePane`) re-read their host when the registry changes (`hostChanges`), because they had the same defect.
- **Alternatives:** forcing a periodic repaint (hides the bug and spends battery); registering the host before publishing the state (changes T08's startup order, more risk).
- **Test:** `HostRegistryTest` reproduces the order of the failure (state first, host after) and checks that the host is followed, that there are no repeated emissions and that a closed host stops being followed.
- **Not validated on a device:** that the new tab is drawn instantly and that what is typed is repainted without switching tab.

### D-T09b-2 · 2026-10-04 · Tab name: the distro, numbered if repeated (RF-13)
- **Decision:** `tabNames` (domain, pure) gives `Custom` if the user renamed it, `InDistro(distro, ordinal)` if not ("Alpine", "Alpine 2"...), and `Plain(n)` ("Shell n") for the Android shell. A renamed tab does not consume a number of its distro.
- **Reason:** with several tabs in a distro, "Shell 1/2/3" says nothing; the user's name always wins.

### D-T09b-3 · 2026-10-04 · The dark circle with "⋮" over the prompt is not drawn by the app (unconfirmed)
- **Finding:** in the Pixel 8 screenshots there is a dark circle with three dots in the top-left corner of the terminal. No part of the code draws it there: the panes menu (`PaneMenu`) is at the top right and only appears with split panes. It is anchored to the origin of the input view (`TerminalInputView`, 1 dp), so it looks like a system feature for focused editors.
- **Mitigation applied, unconfirmed:** `importantForAutofill = NO`, `setAutoHandwritingEnabled(false)` from Android 14 and `IME_FLAG_NO_PERSONALIZED_LEARNING`. T08c had already attributed it to the panes menu without looking on the device and the circle stayed there; I do not consider this point resolved until it is seen on a device.

## T22b — iOS-style terminal chrome

### D-T22b-1 · 2026-10-04 · The chrome uses `ui/ios` (T22a) with the terminal scheme's colors
- **Decision:** `TerminalScreen` wraps the content in `IosTheme(decision, scheme)`, with `dark` and `oled` taken from the scheme (not from the system theme): the screen is drawn in the scheme's colors and its chrome has to match them. The bars keep using `ChromePalette` (T12c) for surfaces and text.

### D-T22b-2 · 2026-10-04 · Tab bar: capsules, and the active tab's menu
- **Decision:** each tab is a capsule (`capsule`, drawn with a 6 dp vertical margin inside a 48 dp touch target, so the capsule is shorter than what is touched). The active one carries the `selected` fill, the text in semibold and a `⋯` button (menu with rename and close, `IosContextMenu`); the others, text only. A thin (0.5 dp) line between the bar and the terminal. All tabs offer rename and close as **accessibility actions**, so no function depends on seeing the button.
- **Gestures:** they are kept (tap selects, double tap renames, long press and drag reorders).
- **Alternatives:** a context menu by long press on all of them (it clashes with the drag to reorder).

### D-T22b-3 · 2026-10-04 · No blur on the tab bar
- **Decision:** the bar uses the scheme's `surface` color, without `backdropBar`. T22a's blur needs content that passes *under* the bar, and here the bar does not overlap the terminal (the grid computation excludes it). Overlapping them would change the pty size computation and it cannot be validated without a device.
- **Pending:** decide in T22c / once tested on a device whether the terminal is drawn under the bar.

### D-T22b-4 · 2026-10-04 · Extra keys as keyboard keys
- **Decision:** each key is a rounded key with a bottom line (a key's edge), on a tray of the `surface` color. The colors come from the domain (`ChromeColorsFor`: `key`, `keyPressed`, `onKey`): the key is **lighter than the tray** in light and dark schemes, and darker when pressed; the text meets contrast ≥ 4.5:1 in all the bundled schemes (test). Armed and locked Ctrl/Alt keep the accent. `onlyWithKeyboard` is kept.
- **Haptics:** `IosPressable`'s on each key, like a keyboard's click.

### D-T22b-5 · 2026-10-04 · Menus and notices with the iOS components
- **"+" and panes:** `IosContextMenu` with Lucide icons on the right (D-T22a). The entries are built as a list so that the last one carries no separator. **Rename:** an iOS alert with a text field (`NamePrompt`: title, field, note and two buttons side by side), which takes the keyboard by itself; **close:** `IosAlert` with the destructive button in red. **Launch notice:** a rounded card with a shadow over the first rows; the icon is red if it is a problem.
- **Panes button:** a 48 dp target with a 30 dp capsule inside (covers less text) and only with split panes.
- **Not touched (T22c):** the `SessionPrompts` dialogs (permissions), Distros, SSH, Appearance and Settings.

### D-T22b-6 · 2026-10-04 · Refactor forced by detekt
- `NamePrompt` receives its texts grouped (`NamePromptTexts`), `TabChip` is split into `TabChip`, `TabLabel` and `tabSemantics` with two context classes, the tab notices and alerts live in `TabPrompts.kt`, and `TerminalScreen` groups what it passes to `TerminalContent`. No rule was relaxed.

### D-T22b-7 · 2026-10-04 · What is NOT validated (without a device)
- The real look of the capsules, the keys and the menus on a phone and a tablet, in light, dark and OLED.
- The rename alert with the keyboard (that the field is seen and not covered), and the field's automatic focus.
- That the context menu appears in the expected place from the screen's edge (it is anchored to its button's top-right corner).
- The tabs' gestures with the capsule, and TalkBack with the custom actions.
- That the circle with "⋮" disappears (D-T09b-3).

### D-T22b-8 · 2026-10-04 · Lesson: deterministic tests
- This branch adds no tests that depend on the clock, on chance or on compressed file sizes (critical coverage was intermittent because of that; see D-T15-*/the deterministic fixtures PR).

## First tests on hardware (Pixel 8, 2026-10-04)

Manual tests by the orchestrator on a **Pixel 8** (Android 17, API 37, arm64, debug app with `targetSdk` 28), with `adb` and screenshots. The development agents do not touch devices.

### Verified on the device
- **Startup and PTY (T03):** the app starts in ~540 ms; the first tab is a shell with a real PTY (`uname -a` returns the real kernel, aarch64).
- **Resizing (T04):** with the keyboard open, `stty size` gives 18×49 and matches what is visible (the view shrinks above the keyboard).
- **Foreground service (T08):** it runs with `targetSdk` 28 on Android 17 (`isForeground=true`, type `specialUse`, channel `sessions` with 2 actions).
- **Tabs (T09):** creating and switching tabs work.
- **Distros (T06/T07):** installing Alpine 3.24.2 from the app (download, SHA-256, extraction) takes ~3 s and leaves it ready and default.
- **proot (T02/T08b):** a new tab opens `localhost:~#`, an Alpine root shell under proot; when closing the app completely and reopening it, the first tab is the distro again (6 out of 6 starts).
- **Tools inside the distro:** `apk update` (28,553 packages) and `apk add` of 41 packages with the device's network and DNS; OpenSSH 10.3, Python 3.14.8 and nmap 7.99 work. `free -m` and `uptime` work.

### Failures found (and their status)
- **Crash when installing a distro:** `SecurityException: getFileStore` (Android forbids apps from reading `/proc/mounts`). Fixed with `StatFs` (PR #19).
- **What was typed did not appear until hiding the keyboard:** `setComposingText` did not send the text until `finishComposingText`. Fixed by sending the composition immediately (`ComposingText`, PR #23). **Pending verification on the device.**
- **New tab blank until switching tab and coming back:** root cause in `SessionManager.activeHost` (T09b). Fixed in T22b; pending verification.
- **Battery dialog** opened a general Settings list: changed to the system dialog (T08c).
- **Distros title overlapping the install button:** T08c and T22c.
- **The keys row stayed visible with the keyboard hidden:** fixed (`onlyWithKeyboard`, PR #22); pending verification.
- **Partial `/proc`:** `stat`, `loadavg` and others give `Permission denied` inside proot (SELinux), as in Termux; `top` and `htop` may fail without fake files mounted over them (T08c).

### Not tested yet
Split panes (T10), colors and fonts (T12/T12c), `~/storage` mount (T13), SSH hosts and keys (T14), backups (T15), gestures and physical keyboard, and **everything on the tablet** (resizing, multi-window, wide panes).

### Second round (Pixel 8, 2026-10-04, `master` APK with T22b and T09b)

Verified on the device with the `master` APK at `b6e3475`:

- **Input with the keyboard open (fix #23):** what is typed appears instantly, before pressing Enter (`echo primero`, `echo segundo` and its output, and `echo tercero` while typing). The keyboard no longer has to be hidden.
- **The keys row follows the keyboard (RF-08):** with the keyboard open it is seen (rounded keys in a gray derived from the scheme) and when hiding it it disappears and the terminal takes the whole screen.
- **New tab (T09b):** pressing "+" creates "Alpine Linux 2" and draws its prompt the first time, without switching tab. Tabs are named with their distro (RF-13).
- **Split panes (T10):** "Split right" creates a second pane with its own Alpine shell (3 proot processes in total); the purple border marks the focus, input goes to the focused pane and long lines wrap.
- **Appearance (T12 and T12c):** the screen is opened from the "+" menu; it shows a preview with ANSI colors, the theme (system/light/dark), the OLED Black switch, the dynamic colors and the list of schemes. Choosing Solarized Dark repaints all the panes instantly and the tab bar takes the scheme's accent; going back to Dracula restores the `#282A36` background.
- **"+" menu (T22b):** a dark context menu with rounded corners and line icons: Android shell, the distro, split right and down, SSH, Manage distros and Appearance.

Defects seen (not fixed yet):
- **The focused pane's `⋯` button covers the end of the first line** when the text reaches the right edge.
- **The "Keep sessions active" dialog and the Appearance screen still have the Material look;** T22c and T16 redo them.
- **Not checked yet:** scroll performance with the translucent bar, the TalkBack pass, rotation and everything on the tablet.

### D-REQ-4 · 2026-10-04 · Fedora moves from the backlog to a task (T24)
- **Decision:** the user said their favorite distro is Fedora and asked why it was not there. In the initial interview Arch and Fedora were left in the backlog (SPEC §2); now Fedora enters the plan as T24. Arch stays in the backlog.
- **Why it was not trivial:** Fedora's images come as tar.xz and T07's extractor only reads gzip (D-T07-3), so T24 includes xz decompression with the same security rules.
- **Impact:** SPEC §2 and PLAN updated. `org.tukaani:xz` will be credited in `THIRD_PARTY_NOTICES.md`.

## T12b — Profiles, layouts, broadcast and shortcuts (domain only, no interface)

Everything lives in `domain/profile`, `domain/broadcast` and `domain/terminal/ShortcutConflicts.kt`, without touching any screen,
`SessionController` or `TabsController`. 51 new deterministic host tests (no clock or chance). Not validated on a
device: there is nothing to run yet, the interface is missing.

### D-T12b-1 · 2026-10-04 · In a profile, the saved default values mean "use the global setting"
- **Decision:** `Profile` keeps `colorSchemeId = "default"`, `fontFamily = "monospace"` and `fontSizeSp = 14` as "not
  customized". `PaneSpecResolver` translates them to `null` in `PaneLook` and only a different value replaces the global one.
  This way changing the global scheme or font (T12/T12c) still reaches the panes that did not customize them.
- **Reason:** the profile was saved in T5, before the global appearance existed; changing its Room schema to
  admit `null` forced a migration. The cost is that a profile cannot ask for "exactly 14 sp" when the
  global one is another: if needed, the field will have to be migrated to nullable (the heart of the problem is in `Profile.kt`).
- **A scheme or font that no longer exists** (an imported one that was deleted) does not break the pane: the global one is used and a
  `PaneNotice.SchemeMissing`/`FontMissing` is emitted. A number out of range is brought to its range with `ValueAdjusted`.

### D-T12b-2 · 2026-10-04 · The startup command is typed into the shell, not run in its place
- **Decision:** `PaneSpec.startupInput` is the command followed by Enter (`\r`), which the session types when the shell is
  already running. `sh -c <command>` is not launched.
- **Reason:** the shell stays interactive (if the command ends or fails a prompt remains), there is nothing to
  quote, and the input does not go through proot's command line. The cost: the command is seen on screen and
  depends on the prompt being ready (the UI must type it after the first prompt or with a short margin).
- **What is accepted** (`StartupCommand`): a single line, no line breaks (several commands would run from a field
  meant for one), no control characters (a Tab would ask for completion; an Escape would drive the shell) and up to
  1000 characters. Blank means "none"; the command of a layout's pane replaces the profile's, and a blank
  one cancels it.

### D-T12b-3 · 2026-10-04 · A saved layout always opens: what fails is degraded and reported
- **Decision:** `PaneSpecResolver.resolve` rejects with a sealed `ProfileProblem` (nonexistent distro, distro not ready,
  invalid user, invalid command), never with an exception. `resolveOrDegrade`, which restoration uses, opens that
  pane as the default profile and **without a startup command**, with `PaneNotice.Degraded(reason)`; a deleted profile
  gives the default profile, keeps the layout's own command and reports it with `ProfileMissing`.
- **Reason:** a layout comes from disk, from a backup or from another device, and must not be lost entirely because a
  distro is no longer there. A command written for a distro that does not exist could do harm in another, so it is not
  kept when degrading.
- **Alternative discarded:** refusing to open the layout. The structure is lost for a single broken pane.

### D-T12b-4 · 2026-10-04 · A layout is untrusted data: bounded size and sanitized proportions
- **Decision:** `LayoutRestorePlanner` rejects (`LayoutRefusal`) a tree of more than 16 panes (`TOO_MANY_PANES`) or more
  than 8 levels of splitting (`TOO_DEEP`; the check stops at the limit, so its recursion is bounded
  even if the JSON is malicious). A split whose part is smaller than 10 % is brought to 10 % with a
  `RatioAdjusted` notice; one close to 50 % (±3 %) is set to half without notice, as when dragging the separator.
- **Compatibility:** the round trip and reading an old JSON were tested with `LayoutCodec` (T05), with panes without
  profile or command and with a key a future version might add.
- **Numbers:** 16 panes and 8 levels are safety limits, not design ones; they can be raised without migrating anything.

### D-T12b-5 · 2026-10-04 · Broadcasting to several panes: in memory only and with brakes
- **Decision:** `BroadcastState` (`Off`, `AllPanes`, `Group(name)`) is not saved: a broadcast that survived a
  restart would write to servers by surprise. `targets(active, panes, kind)` returns to which panes what is typed goes,
  in the tab's order and never empty. Brakes:
  - with a single pane there is nobody to broadcast to;
  - with `textOnly` (default) control keys (Ctrl/Alt, Esc, arrows) only go to the active pane, so that a
    Ctrl+C or an arrow meant for one server does not reach the others; pasted or typed text is broadcast;
  - a pane outside the group does not sneak into it, and a pane that is not in the tab only receives itself.
- `pruned(alive)` forgets closed panes (a closed pane's number must not coincide with a new one) and turns off a
  broadcast to a group that was left without panes. `isEmitting(panes)` lets the interface show clearly that
  typing will reach others: it is the notice that must not be missing.
- **Alternative discarded:** an "all panes of all tabs" mode: too dangerous for the first
  cut; it can be added as another `BroadcastMode`.

### D-T12b-6 · 2026-10-04 · Shortcuts: three new actions and conflicts are warnings
- **New actions** in `AppShortcut` (`ShortcutHandler`'s `when`s have `else`, so they have no effect until
  the interface connects them): `ToggleBroadcast` (`Ctrl+Shift+B`), `SaveLayout` (`Ctrl+Shift+S`) and `OpenLayouts`
  (`Ctrl+Shift+L`). Terminator uses Alt+letter, but Alt+letter is readline's Meta (Alt+b and Alt+f move by words),
  so they go with Ctrl+Shift, like the rest of the pane ones (D-T11-4).
- **Detection** (`ShortcutConflicts.kt`): `terminalConflict()` flags Ctrl+letter without other modifiers
  (`StealsControlKey`: Ctrl+C interrupts, Ctrl+D closes the input) and Alt+letter or Alt+digit without Ctrl
  (`StealsReadlineMeta`). They are warnings, not rejections: the SPEC asks for Alt+digit to choose a tab (D-T11-4), and the only
  default shortcuts that clash are those nine. `bindChecked` returns the new map, what it replaced and the cost, or
  `Refused` if the combination would steal normal keyboard input (before it threw an exception). `ShortcutText.analyze` detects a
  combination that the saved text gives to two different actions (the last line wins, as in `parse`).
- **One combination for two actions** cannot happen within `ShortcutMap` (it is a map by combination); several
  combinations for one action can (Copy has two by default).

### D-T12b-7 · 2026-10-04 · What was NOT done: persisting the shortcuts and putting them in the backup
- **Finding:** shortcuts are not saved anywhere today (`InputRouter` starts with `ShortcutMap.defaults()`), and
  `ConfigSnapshot` (T15) carries no shortcuts, nor the extra keys, nor T12c's appearance. Profiles and layouts **are** already
  in the backup: layouts refer to profiles by position, not by id, so they travel well.
- **Decision:** not touching `data.backup`. Its critical coverage requires 100 % of branches and there is no shortcut store
  yet, so adding it there without a store would be dead or untested code. The `ShortcutStore` interface is defined
  (like `ExtraKeysStore`) and the already existing text format (`ShortcutMap.serialize/parse`).
- **Update (2026-10-05):** done, see D-T12b-8. The "What the later interface must do" list below is
  done, except what D-T12b-9 and D-T12b-16 leave out.
- **To complete it:** a key in the settings repository with the text of `ShortcutMap.serialize()`; adding it to
  `SettingsDto` or to a new field of `ConfigSnapshot` (reading is lenient: a missing field does not break an old
  backup); `ConfigCollector` reads it and `ConfigApplier` applies it with `ShortcutText.analyze` so as not to lose the good
  lines; a round-trip test, and keeping `data.backup` at 100 %.

### What the later interface must do
1. **Open a pane with a profile:** call `PaneSpecResolver.resolve(profile)` with the known distros, schemes and
   fonts; show a `Rejected` with a message for each `ProfileProblem`, and the `PaneNotice`s as a non-blocking
   notice. Build the session with the launch that already exists from `PaneSpec.target`, apply `look` to
   that pane (today the scheme and the font are global: the painter must admit a `PaneLook` per pane) and type
   `startupInput` after the first prompt.
2. **Profile screens** (list, create, edit, delete with confirmation; `Profile` already validates name and ranges) and
   **layouts** (list, save with `LayoutSaving.build` from the tab's tree and each pane's `PaneDescription`,
   rename, delete, open).
3. **Open a layout:** `LayoutRestorePlanner.plan` with the profiles by id; if it returns `Refused`, say so; if not, create
   a tab with `paneNodeOf(...)`/the sessions in reading order (`LayoutRestorePlan.panes`) and show the
   `LayoutNotice`s, which indicate the pane by its position.
4. **Broadcast:** one `BroadcastState` per tab in the ViewModel; route what is typed through `targets(...)` (`TEXT` for
   text and paste, `CONTROL` for the rest); an indicator always visible while `isEmitting` is true; assign
   panes to groups from the pane menu; call `pruned` when closing a pane and when switching tab.
5. **Shortcuts:** connect `ToggleBroadcast`, `SaveLayout` and `OpenLayouts` in `ShortcutHandler`; a shortcuts screen that uses
   `bindChecked` and shows the `ShortcutConflict`s; persist them and carry them to the backup (D-T12b-7).
6. **Not validated:** typing the startup command after the first prompt in a real shell, broadcast performance with
   several panes and restoring a 16-pane layout with proot on a device.

### D-T12b-8 · 2026-10-05 · Shortcuts: persisted as one more setting and in the backup with a versioned field
- **Decision:** `AppSettings.shortcuts` (a `ShortcutMap`, now with value equality) is saved in the `setting` table with the key
  `shortcuts` and the text of `ShortcutMap.serialize()`; without a key, the default shortcuts; when reading, an unreadable line is
  skipped and the rest is kept. `TerminalViewModel` applies the map to the `InputRouter` as soon as it changes.
- **Backup:** `SettingsDto.shortcuts: ShortcutsDto?` (`version = 1`, `bindings` in the same text). It is optional and
  `ConfigCodec.VERSION` does not change, as D-T16-6 did. When restoring (`ConfigApplier`) the **device's shortcuts are kept**
  if the backup does not carry them, if its `version` is not the one this app knows how to read (a format from a later
  version is not guessed) or if no line is usable; if it carries some valid ones, `ShortcutText.analyze` discards the
  bad ones and the good ones are applied. All those branches have a test, and `data.backup` stays at 100 % of line and branch.
- **`ShortcutStore`** (the interface D-T12b-7 left) is removed: the shortcuts go through `SettingsRepository`, like the extra keys.

### D-T12b-9 · 2026-10-05 · A profile's scheme, font and size are saved but not applied per pane
- **Finding:** an emulator's initial colors are read from `TerminalColors.COLOR_SCHEME`, an object **shared by the whole
  library** (`TerminalSessionHost.applyScheme` writes there), and the painter (`TerminalPainter`) is single for all the panes
  and takes the global font and size. A scheme per pane requires changes in Termux's emulator and a painter per pane.
- **Decision:** the profile form edits name, distro, user, history and initial command, which do apply
  per pane. The scheme, font and size stay in the model, are kept when editing and travel in the backup, but are neither
  edited nor applied; the form says so in a note. When the painter admits a `PaneLook`, it will be enough to add the fields.
- **Why not fake it:** a list of settings that do nothing would be misleading (same reason as D-T16-7).

### D-T12b-10 · 2026-10-05 · Each pane remembers what it was opened with; the command is typed 0.8 s after launching the shell
- **Decision:** `SessionController` keeps a `PaneOpening` (the `PaneSpec` and the command its layout gave it) per session, until it is
  closed. `PaneEditor` gains `openTab(PlannedNode)` (a tab with all its panes, `Sessions.openedTab`),
  `splitActive(orientation, opening)` and `openingOf(id)`. `AndroidSessionFactory` reads it at the start: the pane's history,
  the user (`ProotSessionPlanner.plan(distro, user)`, which revalidates the name) and the command.
- **Initial command:** `AndroidSessionFactory` writes `startupInput` 800 ms after launching the shell, only if there was a launch
  (not on a failure). The pty keeps what was written until the shell reads it, so a slow proot does not lose it; the pause avoids it falling
  in the middle of the startup output. **Not validated with a real shell.**
- **Saving:** a pane is described with its profile and its own command, not the profile's (`PlannedNode.Pane.command`), so that
  saving a restored layout again leaves it the same. A pane without a profile is saved without a distro (`LayoutNode.Pane` only has
  profile and command): when reopened it uses the default distro, even if it had been opened in another. It is noted as a limit.
- **Splitting with a profile** opens the new pane in the profile's distro, not in the split pane's; without a profile it is still the origin's.

### D-T12b-11 · 2026-10-05 · Opening a layout always opens a new tab and reports what changed
- **Decision:** `PaneOpener.restore` plans with `LayoutRestorePlanner` and opens **a new tab** (it never replaces the current one or its
  panes); the layouts screen closes by itself if there were no changes and, if there were (deleted profile, distro not ready, split
  corrected), lists them in an alert before closing (`LayoutNotice`, pane and split counted from 1). A rejected layout
  (`TOO_MANY_PANES`, `TOO_DEEP`) opens nothing and says so.
- **Profiles:** opening a profile (in a tab, or in a split to the right or below) uses `PaneSpecResolver.resolve`; if it rejects it
  (deleted or not-ready distro, invalid user or command) it is explained and nothing is opened.

### D-T12b-12 · 2026-10-05 · Saving a layout: unique name, replacement with confirmation, always the whole tree
- **Decision:** `LayoutSaver.save(name, replace)` saves the active tab's tree (whole, even if a pane is zoomed) with
  `LayoutSaving.build`. A name in use (case-insensitive) fails with `NameTaken` and the sheet asks "Replace?"; only
  then is it called with `replace = true`. From the list, "Replace with this tab's panes" overwrites without a sheet.
  `asLayoutSaveProblem` translates the error to the message (name, in use, no tab, command, limit of 100, other).

### D-T12b-13 · 2026-10-05 · Broadcast: switch in the pane menu, red indicator and which keys are broadcast
- **Decision:** `BroadcastController` (one per process, state per tab, in memory only) decides to which panes what is typed goes.
  `TerminalOutput` gains `write(text, InputKind)` and `writeCodePoint(…, InputKind)` (with a default implementation that ignores the
  kind); `ActiveSessionOutput` distributes to `targets(kind)` and paste goes through `pasteFromClipboard`.
- **Which keys:** `KeyInput.inputKind()`: Ctrl or Alt, Esc, arrows, Home, End, PgUp/PgDn, Insert, Delete and F1 to F12 are `CONTROL`
  (only to the active pane); letters, Enter, Tab, Backspace and text are `TEXT` (to all). This way Enter, Tab and Backspace, which are needed to
  type a command on several servers, do arrive, and Ctrl+C does not.
- **Added brakes:** with a single pane it cannot be activated (`toggle` does nothing) and a tab that is left with one pane forgets its
  broadcast, so that splitting again later does not type into the new pane without the user asking for it.
- **Indicator (mandatory):** a red capsule at the top left, "What you type reaches N panes", while `isEmitting`; tapping it
  stops the broadcast (48 dp, with a TalkBack description). The panes that receive carry a red border.
- **Groups:** three fixed names (Group A, B, C) from "Group of this pane…" and "Type in this pane's group"; `textOnly` is not
  exposed (it stays `true`). The pane menu only appears with the tab split (T10), which is when broadcasting makes sense;
  the `Ctrl+Shift+B` shortcut always works (no effect with one pane).

### D-T12b-14 · 2026-10-05 · Editable shortcuts: one sheet per action, with a preview of what it costs
- **Decision:** Settings > Keyboard > Shortcuts lists all the actions (also the ones left without a combination); tapping one opens a
  sheet with its combinations (tapping one asks to remove it) and a field where the keys are **pressed** on a physical keyboard
  (`onPreviewKeyEvent`, only with Ctrl or Alt) or **typed** (`ctrl+shift+t`). Before adding, `ShortcutEditing.preview` says whether it is
  unreadable, whether Ctrl or Alt is missing, whether the action already has it, which other action loses it and whether it steals a control key or
  readline's Meta (warnings, not rejections, D-T12b-6). "Restore default shortcuts" asks for confirmation.
- **The tab numbers** (Alt+1 to 9) remain a read-only row; making them configurable requires a "prefix +
  1 to 9" model that is not there. `ShortcutDisplay.allRows` lists all the rows; `rows` only those with a combination.
- **Not validated:** capture with a real physical keyboard and with the IME (a combination the system keeps does not reach the app).

### D-T12b-15 · 2026-10-05 · Where each screen is reached from
- **Profiles and layouts:** rows at the root of Settings, entries in the "+" menu of the tab bar (with "Save layout…") and in the
  pane menu; `Ctrl+Shift+S` asks for the name and `Ctrl+Shift+L` opens the layouts (`TerminalRequest`, which the `TerminalViewModel`
  emits and the screen converts into opening an activity sheet). `ScreenLinks` groups these three links in `ProfileLinks`, and
  `TabBarLinks` carries the whole `ScreenLinks`, because of detekt's parameter limit.
- **Screens** with the `ui/ios` components (T22a/c) and strings in `values/` and `values-es/`; the alerts and sheets follow the
  convention that the action sheet closes by itself after the action (`closeActions` looks at the current state).

### D-T12b-16 · 2026-10-05 · What remains to be validated on a device
- The initial command typed after starting the shell (including with a slow proot); broadcast with several proot panes and performance;
  restoring a 16-pane layout; the look of the sheets (profile, layouts, shortcuts) and of the red capsule, and that it does not cover text;
  key capture on a physical keyboard; and TalkBack on the new screens.
- **Coverage:** the new logic is in `domain` (`PaneOpener`, `LayoutSaver`, `ProfileForm`, `BroadcastController`, `ShortcutEditing`,
  `Sessions.openedTab`) with host tests; the ViewModels and Compose screens do not enter coverage, like the rest of the UI.

## T23 — Pending cleanup

### D-T23-1 · 2026-10-04 · Tests with a real database: `runDatabaseTest` with a 5-minute margin
- **Problem:** `MigrationTest` and `LayoutAndSettingsRepositoryTest` (and six other test files from T05, T08b, T12 and T12c) sometimes failed with `UncompletedCoroutinesError: After waiting for 1m` when the machine was loaded (load of 90 on 14 cores; they passed on their own in 47 s). Room opens SQLite on a real thread and loading its native library and touching the disk can take more than 1 minute, which is what `runTest` waits by default.
- **Decision:** `runDatabaseTest` in `data/local/TestDatabase.kt`, a `runTest(timeout = 5.minutes)`, used in the **eight** files and in the `DistroRepositoryContract` contract that open a real database. The dispatcher is not changed: the wait is still wall-clock, because what is tested is the real I/O.
- **Alternatives discarded:** a test dispatcher that does not wait (the tests would stop exercising Room for real); raising the timeout of all `runTest`s (it would hide a real hang in tests that do not use a database and do not have that problem).
- **Impact:** a really hung test still ends at 5 minutes with the same error. No test depends on the clock.

### D-T23-2 · 2026-10-04 · Apache license notices: measured, not assumed
- **Measured on the release APK and on the jars of the runtime classpath (245 dependencies):** only **five** publish their own `NOTICE`/`LICENSE` in `META-INF`: Commons Compress, IO, Codec and Lang, and Jakarta Inject. The rest (OkHttp, Okio, kotlinx, AndroidX, Hilt, Room, Compose…) carry no `NOTICE` of their own; only AndroidX leaves a `LICENSE.txt` with the standard Apache-2.0, which does reach the APK.
- **State in the APK:** of those five, the packaging only keeps Jakarta's `NOTICE.md` by chance; the four Commons ones do not arrive. T07 (D-T07-8) already packaged them by hand in `res/raw`.
- **Decision:** the same file (renamed to `res/raw/third_party_notices_apache`) now also carries Jakarta Inject's `NOTICE` and license, copied unmodified from its jar. With this the open point of D-T07-8 about OkHttp/Okio is **closed**: they publish no notice, so there is nothing to keep from them beyond their mention in `THIRD_PARTY_NOTICES.md`.
- **API for the "About" screen (T16):** `LicenseTexts` in `domain/license` (injectable with Hilt, there is already a `LicenseModule`). `entries` lists the notices (`id`, title and SPDX license), `read(id)` returns the text and `missing()` those that are not there. T16 only has to show `entries` and, on tapping one, open `read(id)`. Host tests: they read the real files of the source tree and fail if a text disappears or a resource is renamed.
- **Not validated on a device:** that `AndroidLicenseSource` finds the resources in the release APK with the *shrinker* (it was checked with `aapt2` that `raw/third_party_notices_apache` exists in the APK; reading at run time was not).
- **It is not legal advice:** what the licenses ask for is met (keeping the notice when redistributing in binary form); it is advisable for someone to review it before publishing.

### D-T23-3 · 2026-10-04 · GitGuardian: test phrase renamed and `.gitguardian.yaml` documented
- **Finding:** two "Generic Password" false positives (the `"s3cret"` literal in `BackupExportTest` and the `passwordFor` field of `BackupViewModel`) blocked several PRs. They are not credentials.
- **Decision:** the literal becomes a constant with a comment (`TEST_PHRASE = "open sesame"`), without changing the test's behavior, and a `.gitguardian.yaml` is added that excludes **only** the tests under `data/backup/**`, with the reason written in the file itself. `passwordFor` is left as is: it is a field name, not a value.
- **Limit:** the exclusion does not cover production code or other tests; a real credential anywhere would still be flagged. If GitGuardian does not read that file on this plan, the incidents are dismissed in its dashboard (incidents 37863678 and 37863679).

## T22d — Extra keys style

The user tried the app on a Pixel 8 and said the special keys (Esc, Tab, Ctrl, arrows…) had "a very ugly finish". They were capsules in Material's `surfaceVariant` color with a 1 dp bottom shadow line: a bevel effect that contradicts iOS's flat style, and a gray that belongs neither to the scheme nor to the navy-blue Gboard keyboard right below. They asked to **choose the style in Settings, with the flat ones by default**.

### D-T22d-1 · 2026-10-04 · Three styles, `FLAT` by default
- **Decision:** `ExtraKeyStyle { FLAT, CAPSULE, CLASSIC }`, with `FLAT` by default; what is not recognized or missing is read as `FLAT`.
  - **FLAT:** no background per key; only the symbol (medium weight), thin separators (0.5 dp, 20 % opacity) between keys and rows, and an oval tinted with the accent (18 %) when pressed. Armed Ctrl/Alt are a capsule filled with the accent; **locked**, the same plus an underline under the label, so that the difference does not depend only on color.
  - **CAPSULE:** each key is a capsule in the text color at 10 % opacity, without a shadow, with 6 dp of separation between keys; armed, the accent at 28 %.
  - **CLASSIC:** the previous look (a capsule with a bottom edge), but with the **scheme's** colors: key = background mixed with the foreground at 12 %, edge = background darkened by 40 %. It no longer comes from `surfaceVariant`.
- **Reason:** so that the user chooses, and that no variant is an alien gray.
- **Impact:** `TerminalAppearance.extraKeyStyle`; it is saved in `appearance_extra_key_style` (same pattern as the rest of T12c's appearance).

### D-T22d-2 · 2026-10-04 · The color logic is pure and tested with all the schemes
- **Decision:** `ExtraKeyPaletteFor.of(KeyChromeInputs, style)` in `domain/appearance` returns opaque ARGB colors (precomposed mixes, not transparencies), and `ExtraKeyPalette.look(latch, pressed)` says how a key looks according to its state. The inputs are the scheme's background and foreground and the bars' colors, taken from the scheme or from the system theme (`ChromeStyle.SYSTEM`), so that the same style works with both.
- **Tests:** WCAG contrast ≥ 4.5:1 of the label on its background at rest and pressed, of an armed key's label and a locked one's, for **all the bundled schemes and their OLED variant, for each of the three styles**. Solarized's text, of low contrast by design, is pushed towards black or white just as much as needed (`readableOn`).
- **Change:** the `key`, `keyPressed` and `onKey` colors of `ChromeColors` and `ChromePalette` disappear (only the row used them and they would have been left dead); their three tests are replaced by the new ones.

### D-T22d-3 · 2026-10-04 · Deviation: the tray is neither translucent nor blurred
- **Request:** a translucent bar with blur from API 31 and a solid color on 26–30, reusing T22a and T22b.
- **What happens:** T22a's blur (`BackdropState`) blurs the content that passes **under** the bar. This row goes below the terminal, without overlapping it, so there is nothing to blur.
- **Decision:** the `FLAT` tray is the terminal's background mixed 4 % with the foreground (the other styles, 7 %), with the usual thin top line. This way it reads as part of the terminal and not as a gray slab. If some day the row overlaps the content, it can be moved to `BackdropState`.

### D-T22d-4 · 2026-10-04 · Reusable picker: `ExtraKeyStylePicker`
- **API:** `ExtraKeyStylePicker(settings: AppSettings, onStyle: (ExtraKeyStyle) -> Unit, modifier)` in `ui/ExtraKeyStylePicker.kt`. It shows an `IosSegmentedControl` with the three options and below **a real preview** (Esc, Tab, armed Ctrl and two arrows) in the current scheme's colors; the preview is the `ExtraKeysRow` itself over the terminal's background.
- **It saves nothing:** it reads `settings` and notifies with `onStyle`. The Appearance screen shows it in the "Special keys" section (`KeysSection`); **Z can link it from Settings > Keyboard** by calling it the same way, with the same `update { it.copy(appearance = it.appearance.copy(extraKeyStyle = style)) }`.
- **Accessibility:** the options measure 48 dp; the preview carries a description and does not expose its keys, which do nothing.

### D-T22d-5 · 2026-10-04 · Backup
- `SettingsDto.extraKeyStyle` (text, default `FLAT`). An earlier backup, without the field, is read as `FLAT`; an unknown value too. It is applied when restoring. With a tested round trip.
- **What this task does not solve:** T15's backup **does not save the rest of T12c's appearance either** (font, margins, cursor, bar style), despite what D-T12c says. I only added my field; the rest is separate work.

### D-T22d-6 · 2026-10-04 · Geometry
- Each row measures 48 dp, the usual touch target; the separation between keys goes inside the cell. `FLAT` leaves 4 dp of air per side, the others 3 dp (6 dp between two capsules). Haptics and the accessibility actions (key name and armed/locked state) are T22b's.

### D-T22d-7 · 2026-10-04 · What is NOT validated (without a device)
- How each style really looks (above all `FLAT` against the system keyboard), the thickness of the separators on screens of different density, the press oval, the underline of a locked key, the preview on the Appearance screen and TalkBack. The orchestrator tests it on the Pixel 8.

## T16 / T22c — Settings by sections and iOS-style screens

Done in a separate clone, without devices. What only the device can say is in the list at the end.

### D-T16-1 · 2026-10-04 · The Settings screen is a stack of pages, with navigation as pure data
- **Decision:** `SettingsNavigation` (`domain/settings`) keeps the stack of `SettingsPage`s with the root always at the bottom; "back" pops a page and from the root it closes the screen. It is saved with a `Saver` (page names), so it survives rotating the phone. Each page knows its parent (`parent`) to label the back button.
- **Reason:** the logic of moving between pages is tested on the host (the screen does not have to be seen), and the UI only draws it.
- **Alternative discarded:** Navigation Compose: a new dependency for 11 pages without arguments.
- **Appearance and Distributions** are not Settings pages: they open their own screens on top (T12c and T22c), and when closing them it goes back to Settings.

### D-T16-2 · 2026-10-04 · Where the access is: a permanent ⚙ next to the "+" and the first entry of its menu
- **Decision:** `TabBar` puts a 48 dp ⚙ button, with `contentDescription`, in the bar's last slot, always in view (in the top bar and in the side one). The "+" menu carries "Settings" as the first entry that is neither a tab nor a split; "Appearance…" and "Manage distros…" are still there and are also opened from inside Settings.
- **Reason:** the user did not see how to get into Settings; the access was only behind a long press on the "+". RF-11 requires it to be permanent.
- **Scope:** this touches `ScreenLinks`, `TabBarLinks`, `TabBar` and `NewTabMenu` (a few lines each). It was first done additively so as not to step on T22b; when T22b was merged, the icon was placed directly in its bar.

### D-T16-3 · 2026-10-04 · The new settings live in the same key-value table and the extra keys start being saved
- **Finding:** `ExtraKeysStore` was an interface without an implementation, so the key row's configuration was never saved (`TerminalViewModel` always started with the default one) and there was no way to change it.
- **Decision:** `AppSettings` gains `extraKeys` and `dnsFallbackServers`; they are saved in the `setting` table (`extra_keys` in its `visible=`/`onlyWithKeyboard=`/rows text, and `dns_fallback` with the addresses separated by commas). An unreadable value gives the default. `TerminalViewModel` reads the keys from the settings repository (they change live); `ExtraKeysStore` is removed.
- **Editing limits** (`ExtraKeysEditing`): up to 3 rows of 8 keys, each key of the catalog only once. Removing the last key of a row removes the row.

### D-T16-4 · 2026-10-04 · The history (scrollback) was a setting that did nothing; now it is applied
- **Finding:** `AppSettings.defaultScrollbackLines` existed, but `TerminalSessionHost` used a constant of 10,000.
- **Decision:** the host has `transcriptRows` and `AndroidSessionFactory` sets it before launching the shell by reading the setting (`SessionManager` injects it). It applies to tabs opened afterwards. Options: 1,000, 2,000, 5,000, 10,000, 20,000 and 50,000, which is the maximum the emulator admits; a saved value out of range is brought to the nearest one when displayed and clamped (100–50,000) when used.
- **Not validated:** the effect on a real session and the memory consumption at 50,000 lines.

### D-T16-5 · 2026-10-04 · Editable fallback DNS, with strict validation
- **Decision:** `ResolvConf.render(servers, fallback)` uses the user's fallback when the device gives none, and the usual ones (1.1.1.1 and 9.9.9.9) if theirs has nothing usable. What is typed in the sheet is read with `DnsServers`: IP addresses separated by commas, spaces or `;`, at most three; a hand-written zone (`fe80::1%wlan0`) is rejected, not silently discarded. Empty means the usual ones.
- **Notice on the screen:** the usual ones are third-party public resolvers (Cloudflare and Quad9), as D-T08b-6 already recorded.

### D-T16-6 · 2026-10-04 · The backup now carries all the configuration it was missing
- **Finding:** T15 did not include T12c's appearance, proot's compatibility mode, the DNS or the extra keys, which RF-06 requires.
- **Decision:** `SettingsDto` gains four **optional** fields (`prootCompatibilityMode`, `dnsFallbackServers`, `extraKeys`, `appearance`). The format's version does not change: the reader already ignores unknown fields and these have a default value. An earlier backup that does not carry them **leaves what the device already has**, it does not reset it. What is restored is validated: DNS by `DnsServers`, keys by `ExtraKeysConfig.parse` (without useful keys, the default ones) and the appearance's numbers and names by `sanitized()`.
- **Imported fonts:** their files do not travel (they are third-party binaries, whose license is the user's responsibility); if the backup's font does not exist on this device, the bundled one is used.

### D-T16-7 · 2026-10-04 · Shortcuts: a reference list, not editable yet
- **Decision:** the page shows the default shortcuts in fixed order, with the keys with a readable name (`Ctrl+Shift+T`) and the tab numbers on a single line (`Alt+1–9`), and warns that they cannot be changed.
- **Reason:** changing them requires capturing combinations from a physical keyboard and handling clashes between shortcuts; it cannot be validated without a device and it is T12b's scope. Showing a list is truthful; faking an edit would not be.

### D-T16-8 · 2026-10-04 · "About" reads `THIRD_PARTY_NOTICES.md` copied to the APK by Gradle
- **Decision:** a `copyNotices` task copies the root file to each variant's assets on every build (`addGeneratedSourceDirectory`), and `Notices` converts it into sections (`##` headings, tables on one line, no Markdown marks). A test reads the real file, so a change to its format that breaks the screen is seen in CI.
- **Reason:** a single source to maintain; a copy in the repository would go out of sync and the APK would carry old credits.

### D-T16-9 · 2026-10-04 · Distros and backups in iOS style, and the error of chaining dialogs
- **Decision (T22c):** the Distros screen is a list with a large title; tapping a row opens an action sheet (default, rename, duplicate, delete), install and rename are sheets with fields and delete is an alert. The storage, backups and compatibility mode cards leave there and move to Settings. The export and password dialogs of the backups are sheets.
- **Error avoided:** `IosActionSheet` runs the action and **afterwards** calls `onDismiss`. If the action opens another dialog (rename), that closing undid it. The sheet's closing only closes if what is current is still the sheet, reading the state at that moment, not the captured one.
- **New components in `ui/ios`:** `IosTextField` (form row), `IosSheetHeader` (Cancel, title and confirm, with the button inert while it is not valid) and `IosProgress`.
- **Not migrated:** the SSH (T14) and appearance (T12c) dialogs are still in Material.

### D-T16-10 · 2026-10-04 · What is NOT validated (without a device)
- The look and feel of all the new screens: grouped lists, sheets, alerts, the ⚙ in the top bar and in the side one.
- The sheets with a keyboard: `imePadding` inside a `Dialog`, that the field is seen with the keyboard open and the behavior of password fields.
- The system flows: notification permission, battery exemption, the app's screen, the backups' file picker and opening the source code link.
- That the system's "back" stacks properly when Appearance or Distributions are opened on top of Settings.
- TalkBack: reading order, role and state of the switches, and the key rows.
- The real effect of the history, the extra keys and the DNS on a session.

## T17 — Accessibility and performance

### D-T17-1 · 2026-10-05 · Method: code audit and pure logic, without Compose tests on the JVM
- **Context:** the repository has no `ui-test` or Robolectric (and adding dependencies requires a license and `THIRD_PARTY_NOTICES.md`). Layout tests with `fontScale` 2.0 cannot be done on the host without them.
- **Decision:** all the composables were audited by hand (terminal chrome, tabs, extra keys, panes menu, Settings, Appearance, Distros, backups, SSH, `ui/ios`) looking for roles, states, headings, merged semantics, custom actions, targets < 48 dp and text with `maxLines = 1` or fixed rows that get cut with a large font; what could be expressed as logic goes to `domain` with a test. Contrast was already covered (`IosPaletteTest`, `ChromeColorsTest`, `BuiltInSchemesTest` in all themes and schemes).
- **Pending on a device:** real TalkBack and font 2.0 (Settings > Accessibility > Font size) on each screen; see D-T17-8.

### D-T17-2 · 2026-10-05 · `ui/ios` components: semantics and large font
- `IosSection`: the header is a heading (`heading`). `IosListRow` without an action merges title, subtitle and value into a single element. The navigation bar's small title is hidden from TalkBack (the large one is already the heading and it was read twice).
- `IosBarButton` and `IosBarIconButton`: minimum 48 × 48 dp (before only 48 tall: "OK" measured less in width).
- `IosAlert`: title as a heading and scrollable body. `IosButton`, `ActionButton` and `IosMenuItem` stop forcing `maxLines = 1`: with font 2.0 the text wraps to another line instead of being cut with an ellipsis.
- `IosSegmentedControl`: minimum height of 48 dp that grows if a label wraps; the drawn track is still 32 dp. `IosSheetHeader`: with font scale > 1.3 the buttons take the width they need and the title wraps in what is left (the fixed 1:2:1 split cut "Cancel").

### D-T17-3 · 2026-10-05 · Tabs: every gesture has an accessibility action
- Tapping a tab was a pointer gesture without a click action: `onClick` ("Select") is added. Reordering by dragging was the only path: the "Move before" and "Move after" actions are added (only if there is room). The "+" and "⋯" buttons declare `Role.Button`.

### D-T17-4 · 2026-10-05 · Terminal and panes
- **Terminal view:** `contentDescription` "Terminal" and click action "Show keyboard". **Decision: the screen's text is not exposed** (SPEC: "the terminal itself exposes text" remains as the content the user can copy with the selection): reading every redraw aloud is noise, a node with hundreds of characters that changes with every byte is expensive, and the terminal's content must not reach any log. If reading is needed, the correct design is a live region with the new lines, and it is separate work.
- The unfocused pane has the same click action. The pane separator, which could only be moved by dragging, offers "Move the separator back/forward" (48 dp steps). The panes menu button declares role and click.
- Checked by searching: no `Log.*` or `println` touches the terminal's content.

### D-T17-5 · 2026-10-05 · Extra keys row: the font is capped at 1.2
- The row measures 48 dp per row and each key is a fraction of the width: the label cannot grow without limit. `ExtraKeyFit.MAX_FONT_SCALE` = 1.2: the drawn labels follow the system font only up to there (each key's spoken name does not change). `ExtraKeyFitTest` checks, with a conservative width estimate (0.65 em per letter), that all the default keys fit in 360 dp with the three styles at scale 2.0.
- **Alternative discarded:** letting the rows grow with the font. It breaks the pty height computation (T12/T22b's D: row × 48 dp) and would eat the terminal.

### D-T17-6 · 2026-10-05 · Material screens (SSH, Appearance): rows that wrap and headings
- The SSH button rows (header, host and key cards) and the schemes' ones become `FlowRow`: with a large font the buttons drop to another line instead of going off the screen. Screen titles and `SectionTitle` are headings. `LabeledSlider` announces its value (`stateDescription`) as well as the label. Small changes on purpose: T22c and T12b edit those screens.

### D-T17-7 · 2026-10-05 · Performance: a guard on the host and a procedure on the device
- `FeedThroughputTest` (`terminal-emulator` module) feeds the emulator with the output of `seq 1 200000`, with 20,000 colored lines that wrap and with wide/combining Unicode, in 4 KiB chunks like the pty reader. It checks the result (last line, bounded history) and a limit of **30 s**, two orders of magnitude above what a laptop takes (milliseconds): it only fails on a complexity regression, not because of a slow machine or the clock. It measures neither drawing nor the device.
- `docs/PERFORMANCE.md`: a reproducible procedure for cold start to the prompt (the `PS1` itself writes the first prompt's time; `am start -W` gives the first frame) and for `seq 1 200000` (`time`, `dumpsys gfxinfo`, live interface during the output, panes and large font). SPEC target: < 1.5 s.

### D-T17-8 · 2026-10-05 · What is NOT validated (without a device)
1. TalkBack: reading order and announcements of the tabs (Select/Rename/Close/Move actions), the terminal, the pane separator, the headings and the merged rows of Settings.
2. Font 2.0 in: the tab bar (48 dp fixed on top: a tab with a long name is clipped to one line), the keys row, the form sheet (`IosSheetHeader`), alerts, `NamePrompt` (not touched: it is T22c's), Appearance's segmented control and the SSH screens.
3. Performance: no figure from the device; the SPEC's targets remain unchecked until `docs/PERFORMANCE.md` is run.

## T24 — Fedora as a distro

A request from the user (Fedora is their usual distro). Done without a device: tested on the host with synthetic samples and with Fedora's real responses (listings and `CHECKSUM`) copied into the tests. **Pending the real test on the Pixel 8.**

### D-T24-1 · 2026-10-04 · Source: the official release's `Base` container image, with its signed `CHECKSUM`
- **Decision:** `https://dl.fedoraproject.org/pub/fedora/linux/releases/<version>/Container/<arch>/images/Fedora-Container-Base-Generic-<version>-<compose>.<arch>.oci.tar.xz` is used (63 MB on Fedora 44, aarch64) and the SHA-256 hash from `Fedora-Container-<version>-<compose>-<arch>-CHECKSUM`, which Fedora signs with PGP and which also carries the size.
- **Why that image and not another:** the layer carries `dnf5` (`usr/bin/dnf -> dnf5`, seen in its listing), which is what the user is going to use. The `Minimal` one (49 MB) carries one more word in the name and is not chosen.
- **Alternative discarded:** `fedora-cloud/docker-brew-fedora` (branch `44`, `aarch64/fedora-<date>.tar`). It is simpler —its "`.tar`" is actually a gzip (it starts with `1f8b`), the current extractor would read it—, but the repository **publishes no SHA-256** (only git's blob SHA-1), so there would be no verification on par with the other distros. Besides, its date changes with each update.
- **Impact:** the hash is verified by `Sha256Verifier` as for the others (T06), over the whole `.oci.tar.xz`.

### D-T24-2 · 2026-10-04 · Which version: the most recent one that has an image, no fixed URLs
- **Decision:** the catalog reads the `releases/` listing, sorts the numbers from highest to lowest and tries the first three until finding one with `Container/<arch>/images/` and its `CHECKSUM`. Today (2026-10-04) 45 appears listed but has no containers directory (404), so **Fedora 44** is chosen.
- **Reason:** like D-T06-1: no URL or hash in the code, and a new version is picked up without updating the app. Trying only the latest would fail exactly when a new branch is opened.
- **Accepted risk:** the **`CHECKSUM`'s PGP signature is not checked**: the clear text is read, which arrives over HTTPS from the project's server. It is the same risk as D-T06-2. Possible improvement: carrying Fedora's publication keys and verifying the signature (an OpenPGP library would be needed, not justified today).
- **Impact:** the release images are frozen at their date (Fedora 44's is from 2026-04-22); the distro is updated afterwards with `dnf upgrade`.

### D-T24-3 · 2026-10-04 · ABIs: arm64 and x86_64; Fedora does not publish 32-bit ARM
- **Checked:** on Fedora 44 the `armhfp`/`armv7hl` directory is empty. The catalog returns `CatalogUnavailable("Fedora has no image for armv7")` **without touching the network**.
- **Pending:** the install dialog still lists Fedora on an armv7 device and fails when trying with that message. Hiding the option by architecture is left for the new Distros screen (T22c).

### D-T24-4 · 2026-10-04 · Two levels of archive: xz, OCI tar and gzip layer; the layer is found by its content
- **Fact checked by reading the real image as a stream:** the `.oci.tar.xz` contains, in this order, `blobs/`, `blobs/sha256/`, a JSON blob (config), **the 66.8 MB layer** (gzip), another JSON blob (manifest), `index.json` and `oci-layout`. **The manifest comes after the layer**, so the layer cannot be located by reading it.
- **Decision:** `OciArchive` recognizes an OCI image by its first entry and takes as the layer the `blobs/sha256/<64 hex>` blob whose content is a compressed or plain tar (by the header: gzip, xz, zstd, bzip2 or `ustar`), not JSON. It extracts it as a stream with the same security rules of T07 (`TarPass` and `SafeTreeWriter` do not change).
- **Layer integrity:** OCI blobs are named after their own SHA-256. The one of the layer's bytes is computed as they are read (including those the decompressor does not ask for) and compared with its name at the end; if it does not match, `Corrupt("the OCI layer does not match its digest")` and the installer discards the temporary directory (D-T07-1). It serves as a second check on top of the whole archive's hash.
- **A single layer**, like Debian (D-T06-3): after the layer the rest of the archive is walked and another layer would give `UnsupportedFormat("an OCI image with several layers")` instead of installing half a system.
- **Technical detail:** the layer's stream does not admit `mark`/`reset` and redoes `skip` with `read`, because either would bypass the hash computation.

### D-T24-5 · 2026-10-04 · xz library: `org.tukaani:xz` 1.12 (0BSD), with a memory limit
- **License:** 0BSD, checked in the Maven Central POM and in `COPYING` of the official repository (`tukaani-project/xz-java`); compatible with GPL-3.0-or-later. 0BSD does not require keeping any notice; it is credited anyway in `THIRD_PARTY_NOTICES.md`. `licensee` now allows `0BSD`.
- **Notice in the APK:** the xz-java jar carries no `NOTICE` or `LICENSE` (only `META-INF/MANIFEST.MF` and the module), and 0BSD does not oblige keeping any notice, so, with D-T23-2's criterion (the texts of the dependencies that publish their own are packaged), nothing is added to `res/raw` or `assets/licenses`. It is credited in `THIRD_PARTY_NOTICES.md`.
- **Dependency verification:** the `jar` and the `pom` (Maven Central does not publish `.module`) were downloaded and their SHA-256 match the `.sha256` that Maven Central publishes; they were inserted into `verification-metadata.xml` without reformatting the file.
- **Memory:** `XZInputStream` is opened with a limit of **64 MiB** (the dictionary of an `xz -9`; Fedora's asks for about 1 MiB). xz-java reserves the dictionary in the Java heap, and a stream that asks for more is rejected (`TooLarge`) instead of risking an `OutOfMemoryError`. The decompressor's other errors are classified as `Corrupt`.
- **Bombs:** xz compresses much more than gzip, so the defense is not the compressed size: the limits that already existed on what is **written** (16 GiB and 2 million entries) count, and a cap of 8 GiB is added for a layer's declared size.

### D-T24-6 · 2026-10-04 · The format is always decided by the first bytes, never by the extension
- `ArchiveFormat.open` already looked at the magic header; now it also reads xz. The installer saves the downloaded file with a fixed name (`archive`) and the tests check it with a gzip named `.tar.xz` and an xz named `.tar.gz`. zstd and bzip2 are still unsupported and are named in the error.

### D-T24-7 · 2026-10-04 · Space: T07's rule already covers Fedora
- `InstallSpace` requires 5 times the archive's size: about 330 MB for Fedora's 66 MB (the archive plus the decompressed, which is estimated at about 200 MB). **The real decompressed size has not been measured**: it will be measured on the device.

### D-T24-8 · 2026-10-04 · What Fedora needs from the proot launch: nothing new, with three things to watch
- **`/etc/resolv.conf`:** the layer does not carry it and T08b solves it with a proot bind, not by writing into the rootfs: it works the same.
- **`PATH`:** the generic one (`/usr/local/sbin:…:/bin`) is fine; `/bin` and `/sbin` are **relative** links to `usr/…` and proot resolves them inside the distro. There is no check of `bin/sh` on the host side (the file repository does not follow links and would take it as nonexistent).
- **Relative links** (`etc/os-release -> ../usr/lib/os-release`, `usr/sbin -> bin`, `usr/local/sbin -> bin`): the extractor stores them as they are, and the rule of not writing through a link is still in force.
- **To watch on the device:** `dnf5` under proot (seccomp, `ptrace`, partial `/proc`, the `rpm` database) and the time of the first download.

### D-T24-9 · 2026-10-04 · What was tested and what was not
- **Host tests (no real network):** valid, truncated, damaged xz, with only its magic, bomb and memory excess (editing the block header's dictionary and recomputing its CRC); Fedora OCI image (single layer, gzip or plain wrapper, names with `./`, plain tar layer, digest that does not match, no layer, several layers, hostile layer with `..` and with writing through a link, bomb inside the layer); the layer's stream (read, `skip`, `mark`/`reset`, close); Fedora parsers and catalog with the real responses (the new branch without an image, armv7, three versions without an image, `CHECKSUM` without an entry); and the installer registers `DistroType.FEDORA`. The tars' dates are fixed and no test depends on the clock or on compressed sizes.
- **Not covered by tests:** the 8 GiB cap of a layer (a hand-written tar header with an absurd size is needed).
- **Not validated on hardware, and criteria of the real test on the Pixel 8:**
  1. Installing Fedora from the app: download of ~63 MB with progress, hash check, extraction without error and the distro "ready"; note the time and the decompressed size.
  2. Opening a tab: `cat /etc/fedora-release` (it must say Fedora Linux 44) and `echo $0`.
  3. `dnf --version` and `dnf install -y nano` (network, device DNS, writing in `/var/lib` and `/var/cache`); `nano --version`.
  4. If `dnf` fails: trying the compatibility mode (proot without seccomp) and noting the exact error.

### Third round (Pixel 8, 2026-10-04, `master` APK with T16, T22d and T24)

**Fedora (T24), verified end to end on the device.**
- **Installation from the app:** "Fedora" appears in the "Install a distro" sheet, next to Debian, Ubuntu and Alpine. It downloads with a progress bar and a cancel button, verifies and extracts the tar.xz: Fedora 44 ends up "ready", with **195 MB** decompressed.
- **Tab:** the "+" menu offers "Fedora" and opens its own bash (`[root@localhost ~]#`) under proot.
- **`dnf` under proot (T24's biggest risk): it works.** `dnf --version` gives dnf5 5.4.1 with its plugins; `dnf install -y nano` loads Fedora 44's repositories (65.4 MiB and 11.6 MiB of metadata), downloads 724 KiB and the `rpm` transaction ends in "Complete!". Afterwards `nano --version` gives 8.7.1 and `rpm -q nano bash` answers `nano-8.7.1-2.fc44.aarch64` and `bash-5.3.9-3.fc44.aarch64`, so the `rpm` database is valid.
- `clear` does not exist in Fedora's minimal image: it is not an app failure.

**Settings (T16) and Distros (T22c).**
- The ⚙ is always seen next to the "+" and opens the Settings screen (large title, grouped lists with chevrons, "Done"), with the nine sections. The Network page shows the fallback DNS with its explanation; the Distributions one links "Manage distributions" and the compatibility mode.
- The redone Distributions screen (grouped list, "+" to install, modal sheet with a handle) no longer clashes the title with the button.
- **Not tested yet:** the Terminal, Keyboard, Sessions, Storage, Backups and About pages; the background dialog, which still has the Material look.

**Special keys (T22d):** the three variants work and are chosen in Appearance with a live preview. Flat (default): symbols with thin separators, no capsules; Capsules: faint capsules with armed Ctrl in accent; Classic: keys with a bottom edge in grays derived from the scheme.

**Bug found and fixed: the "back" button/gesture did nothing on any screen** (neither in Settings, nor in Appearance, nor from the terminal). Cause: `MainActivity` did not declare `android:enableOnBackInvokedCallback` and, with `targetSdk` 28 on Android 17, Compose's `BackHandler`s did not receive the event. One line in the manifest fixes it (PR #44) and it is verified on the device: back goes from a Settings page to the root, closes Settings and goes back from Appearance. A manifest test protects it. Observation from the previous version: pressing "back" several times inside the terminal ended up showing bash's "Display all 395 possibilities?", which suggests that the key reached the shell as a tab; **it has to be checked with the new build that "back" in the terminal no longer writes anything to the shell.**

**Withdrawn: the supposed bug of "OLED Black on and no scheme selected".** It was not the app's: the selected scheme was "OLED Black" (the sixth in the list, off-screen in my capture) and OLED Black was on, consistent with each other. I almost certainly changed it myself with test taps while scrolling the Appearance screen. I restored Dracula with OLED off.


**Checked on the Pixel 8 with the `master` build (2026-10-04): "back" in the terminal writes nothing to the shell.** With the keyboard visible and `echo prueba` unsent, one press of "back" hides the keyboard (and with it the special keys row) and the line stays intact, without "Display all … possibilities?". One more press, without a keyboard, leaves the app in the background, as expected. The suspicion from the previous version is closed. The "Keep sessions active" dialog still has the Material look (pending).

### Fourth round (2026-10-05, `master` build on the Pixel 8 and on the Huawei MRO-W09 tablet)

**User correction:** the original requirement was indeed the tablet (Termux's resizing); my sentence saying it was not was a misreading.

**Huawei MRO-W09 tablet (Android 12, 2800×1840, arm64): first real validation of T04/T10.**
- With no distro installed, the app opens a "Shell 1" tab with the system shell and the tab side bar (wide screen) with "+" and ⚙ at the bottom.
- `stty size` matches what is visible: landscape 41 rows × 123 columns (about 125 × 42 with margins); portrait (rotation 0) 64 × 72 without a keyboard and 58 × 72 with the extra keys row, which appears and disappears with the keyboard (RF-08 here too).
- Multi-window (floating window forced with `--windowingMode 5` and `am task resize`): small window 23 × 45 and large window 49 × 60; each size change updates the PTY and matches what is visible.
- Not tested: split screen (`--windowingMode 3/4` fails on this EMUI with a system exception), split panes, an installed distro, physical keyboard.
- This tablet's capture does not include the on-screen keyboard (`mInputShown=true` confirms it is open).
- System settings I touched to test (fixed rotation, `enable_freeform_support`, `force_resizable_activities`) restored when finished.

**Pixel 8.**
- "Back" in the terminal hides the keyboard and writes nothing to the shell (verified, previous section).
- Settings pages now tested: Terminal (history, 10,000 lines marked), Keyboard, Sessions (battery unrestricted: "Allowed"; notifications: "Not allowed"), Storage, Backups, About (0.1.0-rc.1, GPL-3.0-or-later) and Credits and licenses.
- **Bug 1:** the on-screen keyboard stays open over Settings and covers content when coming from the terminal.
- **Bug 2:** "Credits and licenses" shows the text with the `.md`'s hard line breaks as separate rows and the table as loose rows, in English. Readable but ugly; no credit is lost.
- Both bugs are handed to an agent (branch `fix/ime-and-credits`).

## Fixes after tests on the Pixel 8

### D-FIX-1 · 2026-10-05 · The keyboard is hidden while another screen covers the terminal

- **Finding:** the terminal is still composed under Settings, Appearance, Distros and SSH (they are drawn on top), so its `TerminalInputView` kept the focus and the keyboard stayed open over those screens.
- **Decision:** `MainActivity` publishes `LocalTerminalCovered` (true if any of those screens is open). `TerminalOverlays` calls `TerminalInputView.hideKeyboard()` (removes the focus and hides the IME) when it becomes covered, and the automatic keyboard of the first composition does not open if it already starts covered (e.g. when rotating with Settings open). On return, the keyboard is not forced: a tap on the terminal opens it.
- **No unit test:** it is view and IME glue with no pure logic; it has to be checked on the device.

- **Back on the Huawei tablet (Android 12):** the "back" button/gesture did nothing in Settings' subpages. Probable cause: the input view, still with focus under Settings, consumed **all** keys (also `KEYCODE_BACK`) in `onKeyDown`, so Compose's `BackHandler` did not run (and it matches the old "back reaches bash as Tab"). Two layers: `SystemKeys.isSystemKey` (Back, Home, Recents, Menu, power and volume) makes the view's `onKeyDown` and `sendKeyEvent` neither consume nor encode those keys (with a test), and the view drops the focus (`clearFocus`) when another screen covers the terminal (D-FIX-1). **Not verified on the tablet.**

### D-FIX-2 · 2026-10-05 · "Credits and licenses": paragraphs joined and one row per component

- **Finding:** `Notices.parse` treated each line of the `.md` (cut by hand) as a row and the tables came out as "Component · License · Notes".
- **Decision:** consecutive lines are joined into a paragraph (blank lines and bullets separate); each table row is a `NoticeItem` with the component as the title and the remaining cells (license, notes) as secondary text. Only the tables' header row (the column names) and the paragraph "This file must be updated…", which is an instruction to the maintainers, are omitted. No credit, notice or license text is lost (the license texts go separately, in `LicenseTexts`).
- **Pending:** the content of `THIRD_PARTY_NOTICES.md` is still in English (source legal text); the interface around it is localized.

## T22c (rest) — Session, SSH and Appearance dialogs in iOS style

Done in a separate clone, without touching any device. Everything tested on the host only.

### D-T22c-1 · 2026-10-05 · "Keep sessions active": `IosAlert`, and "Allow" already requested the system dialog
- **Decision:** the two notices of `SessionPrompts` (notifications and battery) are a two-button `IosAlert`: "Not now" (cancel, in bold) and "Allow". The behavior does not change.
- **"Allow" path (checked in the code):** the battery notice calls `requestBatteryExemption`, which tries in order `BatteryExemption.intentsFor` (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `package:<app>` and, only if missing or rejected, the general list). It does not open the list except as a fallback, so there was nothing to fix; the general list is only opened from Settings when the app is already exempt (to be able to revoke it). That stays as in D-T08c-1.
- **Theme:** these notices and the SSH and Appearance screens were outside `IosTheme` in `MainActivity`; now they are inside (without that `IosAlert` used the default light palette).
- **Not validated on a device:** that the system dialog appears after "Allow" on Android 17 with `targetSdk` 28 (already pending in D-T08c-1).

### D-T22c-2 · 2026-10-05 · SSH (T14): host and key lists as grouped lists, forms in sheets
- **Decision:** `SshScreen` and `SshKeysScreen` are `IosLargeTitleScreen`s. A host or a key is tapped and opens an `IosActionSheet` (connect/edit/delete; copy public/save private/delete). Adding a host is the bar's "+"; adding a key opens an action sheet (generate/import). The forms (host, generate, import) are `IosBottomSheet`s with `IosSheetHeader` and `IosTextField`; the host's key and distro are lists with a mark (`IosAccessory.Check`). Deleting and the warning when saving the private key are `IosAlert`s (the warning is not in red). Messages and "Working…" are rows, as in Distros.
- **New components in `ui/ios`:** `IosTextArea` (multi-line text, for pasting a key), the `detail` slot of `IosListRow` and `IosAccessory.Swatch` (a color box with its code).
- **Strings:** `ssh_key_add` (EN/ES); `ssh_connect_to` is removed (no longer used).

### D-T22c-3 · 2026-10-05 · Appearance (T12c): large-title screen, iOS controls and `IosSlider`
- **Decision:** the screen, the scheme editor and the color picker leave Material. Theme, cursor and bar style are `IosSegmentedControl`s; the switches, rows with `IosAccessory.Toggle`; fonts and schemes, lists with a mark and an `IosActionSheet` per element (use, edit, duplicate, export, delete; deleting a scheme asks for confirmation with `IosAlert`). Own fonts are removed from their sheet. The editor and the picker are large `IosBottomSheet`s (Cancel/Save and Cancel/OK).
- **`IosSlider`:** it did not exist and Material was not an option. It is from `foundation` (track, fill and thumb with tap and drag gestures, 48 dp tall, `setProgress` for TalkBack). The arithmetic (finger position, value, TalkBack steps) is pure and tested (`SliderMath`, `SliderMathTest`). It saves on release, as before.
- **Strings:** new `appearance_scheme_use` and `appearance_font_use`; `appearance_scheme_menu`, `appearance_font_delete` and `appearance_color_row` are removed (no longer used; Lint fails with unused resources). TalkBack now reads title + hex code of each color row through the grouped row.
- **Does not change:** the terminal preview still uses Material's `Text`, because it is a text sample with its own font, not a dialog; `ChromePalette` still reads `MaterialTheme` (D-T12c). The "Dynamic colors" text speaks of menus and dialogs: it now mostly affects bars and keys in "system" mode (to review with the user).

### D-T22c-4 · 2026-10-05 · The panes' "⋯" button goes in a strip of its own, not over the text
- **What was seen:** with the tab split, the button (in the focused pane's top-right corner) covered the end of the first line.
- **Decision:** in a split tab **each** pane reserves a 40 dp strip at the top (`PaneHeaderHeight`), focused or not, so that moving the focus does not change the size of any pty. Each pane's PTY is sized with the rectangle minus that strip (`paneLayouts(..., headerPx)` and `belowHeader`, pure and tested), the text is drawn below, and the button is in the strip. Its touch zone is still 48 dp: it protrudes 8 dp below the strip, only at the right corner, where there is no visible text to cover. `canSplit` and the minimum when dragging a divider count the strip (each half needs the strip plus 4 rows). A single-pane tab does not change (no strip, no button). The selection's "Copy" button moves down below the strip.
- **Alternative discarded:** shrinking only the first row of the focused pane: it changes the pty's size when moving the focus and causes a `SIGWINCH` on every tap.
- **Cost:** 40 dp less height per pane in a split. Not validated on a device: that 40 dp looks good, that the 48 dp zone does not get in the way and the tablet.

### D-T22c-5 · 2026-10-05 · The Material buttons that remained in the terminal
- "Copy" (selection) and "Session ended, tap to restart" were Material `TextButton`/`Button`s over the terminal; now they are `IosButton`s (tinted and filled). No `AlertDialog`, `ModalBottomSheet` or `DropdownMenu` remain outside `ui/ios`, nor unused imports of them in `PaneViews`.

### D-T22c-6 · 2026-10-05 · What is NOT validated (without a device)
- The real look of the SSH and Appearance screens and of the form sheets (keyboard, scrolling inside the sheet with the slider), dragging the `IosSlider` inside a sheet that also scrolls vertically, TalkBack on color rows and sliders, and the panes' strip on phone and tablet (D-T22c-4).

## Non-root user without `su` (fix/non-root-user)

Evidence (Huawei tablet): the minimal Fedora 44 does not carry `su` and the chosen user did not exist in `/etc/passwd`; `su -l <user>` ended with code 127. It replaces D-T08b-5. Done without a device.

### D-USER-1 · 2026-10-05 · Identity with proot's `-i uid:gid`, without `su`
- **Decision:** a user that is not root runs their login shell directly (`<shell> -l`) with `-i uid:gid` instead of `-0`, and `env -i` with `HOME`, `USER`, `LOGNAME`, `SHELL`, `TERM`, `LANG`, `PATH`. The ids, the home and the shell come from the rootfs's `/etc/passwd` (`GuestAccounts.find`, a pure function; it skips comments and broken lines; with duplicate names the first one wins). A `nologin`/`false`/empty/relative shell is replaced with `/bin/sh`.
- **Checked in proot's code (`third_party/proot`, Termux's fork):** `-i, --change-id=uid:gid` makes the current user and group appear as uid:gid and the real user's files appear with that owner; `-0` is `-i 0:0`. In the rootfs all files belong to the app's uid, so inside they appear as the user's: no `chown` is needed.
- **Root and commands:** root does not change (`-0`, `/bin/sh -l`). A command (SSH) still runs with `-0`, ignoring the user.
- **Not validated on a device:** that `-i` works with seccomp on Android 17 and that the prompt shows the user (Pixel 8).

### D-USER-2 · 2026-10-05 · If the user does not exist, it is created (mandatory by the user's decision)
- **Decision:** `GuestAccountResolver` creates the user if missing: a free uid from 1000 (up to 59999), a group with their name (reused if it exists, with its gid) and gid equal to the uid if free, a line `name:x:uid:gid:name:/home/name:shell` in `/etc/passwd` (without a hash: there is no password or `/etc/shadow`, because `su` is not used), and `/home/<name>` through `FileSystemRepository`. Shell: `/bin/bash` if `/etc/shells` lists it, otherwise `/bin/sh`. An existing entry is never overwritten nor is `root` created; it works with files without a final line break.
- **Transactional:** `FileSystemRepository` gains `readText` and `writeText` (it writes next to the destination and renames with `ATOMIC_MOVE`; it rejects final symbolic links and files larger than 1 MiB). `/etc/group` is written first and `/etc/passwd` last: a group without a user is harmless, so a failure in between does not change anyone's login.
- **Home ownership and mode:** the directory is created by the app with the repository's default mode; through `-i` it appears as the user's. 0700 is not forced (the abstraction does not expose modes); it remains pending if wanted.
- **Failure:** if `/etc/passwd` cannot be read, cannot be created or there is a failure when writing, a doomed session is not launched: `LaunchProblem.UserUnavailable` with a localized notice (EN/ES) that suggests using root. A home outside `/home` is not created; the session starts at `/`.

### D-USER-3 · 2026-10-05 · Codes 127 and 126
- The "Session ended" card shows a different text for 127 ("a program was not found") and 126 ("could not be executed"), through `ExitHint` (pure, tested). Other codes as before.

### Fifth round (Pixel 8, 2026-10-05, `master` build at `ec4bb2c`: T17, T12b, T22c, user without `su`)

**Performance (SPEC §6; debug build, Fedora 44 under proot, `docs/PERFORMANCE.md`).**
- **Cold start to the prompt:** six starts with `am start -W` and a `PROMPT_COMMAND` that writes the time (device clock): 1,471, 1,653, 1,478, 1,585, 1,650 and 1,628 ms; median of the last five **1,628 ms** (range 1,478–1,653). First frame (`TotalTime`): 467–508 ms. **The 1.5 s target is exceeded by about 130 ms on a debug build**; a release build still has to be measured.
- **`seq 1 200000`:** `time` gives `real 1.073 s` (`sys 0.352 s`); `gfxinfo` in three passes: 98/126/127 frames, 0–2.04 % janky, p50 5 ms, p90 5–6 ms, p99 9–150 ms (the 150 ms comes from the first pass). The interface stays smooth.
- The first batch of measurements (same day) is not valid: the Pixel was locked with the screen off and the shell did not start; it was repeated with the screen on. A measurement with the Alpine distro actually pointed to Fedora (the default one); the hook in Fedora's `.profile` works because `/bin/sh -l` is bash in POSIX mode and does not read `.bash_profile`.

**Non-root user (D-USER-1..3).** On Alpine, installing a distro with default user `ops` (which did not exist) creates the user: `id` gives `uid=1000(ops) gid=1000(ops) groups=3003,9997,20399,50399` and `pwd` is `/home/ops`. Groups 3003, 9997 and the application ones come from Android (proot inherits them); they are not a bug, but a "non-root" user keeps them. Pending to repeat on Fedora (the cause of code 127 was the lack of `su`, which is no longer used).

**Bugs found.**
1. **App crash (T12b):** "Profiles" > "+" and Settings > Keyboard > Shortcuts > a row close the app with `IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints` (nested vertical scroll in a sheet). The Profiles and Layouts lists open fine. In an agent's hands (`fix/profile-form-crash`).
2. **Already installed distros:** the action sheet only offers Rename, Duplicate and Delete. There is no way to change the default user or to choose the default distro (SPEC §5 and RF-13); the user is only set at install time.
3. **Tab bar:** with three tabs the active tab's "⋯" button is clipped by the "+" edge.
4. **Pending to test:** saving and opening a layout (the "Save the panes" sheet), the profile form and broadcast; they depend on fix 1.

**Good on the device:** the "Keep sessions active" dialog is now an iOS alert; "+" opens a tab of the default distro and a long press shows the menu (distros, split, profiles, layouts, settings, SSH, manage distros, appearance); Settings has "Profiles" and "Layouts".

## Failure when opening the profile sheet (fix/profile-form-crash)

Evidence (Pixel 8, Android 17): Settings > Profiles > "+" closed the app with `IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`. Done without a device.

### D-FIX-1 · 2026-10-05 · `IosBottomSheet`'s content scrolls only once, and a test watches it
- **Cause:** `IosBottomSheet` already wraps its content in a `Column` with `verticalScroll` (height bounded by the detent). `ProfileEditorSheet` and `ShortcutSheet` (T12b) added another `verticalScroll` inside, which receives infinite height and throws the exception.
- **Decision:** the inner `verticalScroll` is removed from both sheets; scrolling and the height limit are the sheet's responsibility. All the other sheets (SSH, Appearance: scheme editor and color picker, distros, backups, DNS, save layout) were reviewed: none nests a scroll or a `LazyColumn`.
- **Protection:** `SheetScrollNestingTest` (no new dependencies) reads the sources and fails if a file that opens an `IosBottomSheet` uses `verticalScroll`, `LazyColumn` or lazy vertical grids, and checks that the sheet scrolls only once. Rule: sheet content = a simple column; if a long list is needed, build it with `Column` or give the sheet an explicit bounded height.
- **Not validated on a device:** that "+" and editing a profile, and editing a shortcut, open the sheet and scroll with the keyboard open (Pixel 8).

## Distros: user and default from the list (feat/distro-user-default)

Evidence (Pixel 8): the action sheet of an installed distro did not allow changing its default user or choosing another default distro. Done without a device.

### D-DISTRO-1 · 2026-10-05 · Changing a distro's user
- **Decision:** a new "Change user…" action (only `READY` distros) that opens a sheet with a text field (`UserSheet`, same pattern as `NameSheet`, without its own scrolling). `DistroManager.setDefaultUser` trims the name and validates it with `GuestUser.isValid` (root allowed) before calling the repository, which already had `setDefaultUser`; the repository's `Validation.user` is laxer and is kept as a second net. An invalid name gives `InvalidValue("user")` and the sheet's OK button stays disabled while it is not valid.
- **Nonexistent user:** the sheet warns that it is created when the next session starts (D-USER-2) and that root avoids creating anything. Nothing is created when saving.

### D-DISTRO-2 · 2026-10-05 · Default distro
- **Decision:** the action already existed in the code (`setDefault`, only if `READY` and not already the default; the repository makes it exclusive in a transaction). It is renamed to "Set as default". On the Pixel the row showed "Default" and that is why the action was hidden; with a single distro there is no other to switch to.
- **Backup:** no format changes. The manifest already stores `defaultUser` and `isDefault` per distro and the configuration `DistroRefDto(name, isDefault)`; restoring applies them as before.

### D-DISTRO-3 · 2026-10-05 · The active tab is always seen whole
- **Decision:** the "⋯" only exists on the active tab, which grows when activated and could end up under the "+" at the end of the bar. Each tab carries a `BringIntoViewRequester` and, when it becomes active (or the number of tabs changes), it waits a frame (already with the new size) and scrolls into view. No computation of its own, so there is no pure logic to test.
- **To validate on a device:** with 3 or more tabs on a phone, the active one's "⋯" is complete and tappable.

### Sixth round (Pixel 8, 2026-10-05, `master` build at `e2f115a`: fix for the T12b sheets crash)

**Verified on the device.**
- **T12b's crash is fixed (#58):** "Profiles" > "+", editing a profile and a shortcut's sheet (Settings > Keyboard > Shortcuts) open without closing the app and are saved.
- **Profile with a nonexistent user on Fedora:** profile "dev" (default distro = Fedora, user `ops`, command `echo hola`) → "Open in a new tab": the prompt is `[ops@localhost ~]$` and the command runs. The cause of the tablet's 127 (missing `su`, nonexistent user) is resolved on Fedora too.
- **Split and broadcast:** "Split right" works; the `⋯` button is now in a strip above the pane and does not cover the text. "Type in all panes" shows the red notice "What you type reaches 2 panes", with a red frame on both panes, and the text reaches both shells (one as `ops`, the other as `root`).
- **Layouts:** saving the panes ("two · 2 panes"), listing it and opening it reproduces the two panes with their users.

**Usability bugs found (they do not close the app).**
1. **Settings stays open after opening a profile or a layout:** when choosing "Open in a new tab" or "Open" the user stays at Settings' root and has to press "Done" to see the tab.
2. **The tab name ignores the profile's:** the tab opened from profile "dev" is called "Fedora 2".
3. **The forms' text fields only take focus when tapping their right part:** tapping the label ("Name", "User") does nothing. Besides, when typing in the first one, the red help text disappears and the form scrolls, so a later second tap lands on another field.
4. **The red broadcast notice covers the start of the pane's first line.**
5. **The initial command is seen twice:** it is typed 800 ms after the shell starts, before it draws the prompt, and the terminal's echo repeats it (once without a prompt and once with it). It is only cosmetic.
6. The new pane of a split does not carry the original pane's profile (it uses the default distro).

**Pending to verify with the next build:** "Change user…" (#59) and the `⋯` clipping with three tabs, which are already in `master` but not in the tested build.


---

## T12b usability fixes (sixth round, branch `fix/t12b-usability`)

Six usability bugs found on the Pixel 8 (none was a crash). Numbering continued from D-FIX-2.

### D-FIX-3 · 2026-10-05 · Opening a profile or a layout closes the whole of Settings
- **Decision:** `ProfilesScreen` and `LayoutsScreen` gain `onOpened` (by default equal to `onClose`). `MainActivity` connects it to a closing
  of Profiles, Layouts and Settings at once; "Close" (and Back) still close only their own screen. The new tab or pane already
  took focus when opened (`openTab`/`splitActive`), so it is seen immediately. The three indicators of profile screens are a
  single state (`ProfileOverlay`, with its `listSaver`) so that `Screens` does not grow further.

### D-FIX-4 · 2026-10-05 · A profile's tab is named after the profile
- **Decision:** `PaneSpec.profileName` (only if the profile is saved) goes to `SessionInfo.profileName` and to `TabItem.profileName`;
  `tabNames` gives `TabName.InProfile(name, n)`. Order: name typed by the user, profile name, distro name (RF-13),
  "Shell N". Several tabs of the same profile are numbered ("dev", "dev 2"), like those of a distro.

### D-FIX-5 · 2026-10-05 · A text field's whole row focuses it; notices do not move the form
- **Decision:** `IosTextField` and `IosTextArea` wrap the row in a `clickable` without a ripple that requests focus (`FocusRequester`) and
  shows the keyboard, so tapping the label works and it applies to all forms. In the profile form the footers of
  "Name" and "History" reserve a blank line (`RESERVED_FOOTER`) when there is no error, so that the notice appearing or
  disappearing does not move the rows below. A two-line notice would make it grow; it is rare and accepted.

### D-FIX-6 · 2026-10-05 · The red broadcast capsule goes in the first pane's header strip
- **Decision:** the panes' header strip goes from 40 to 48 dp (the capsule's touch target height, which used to cover
  8 dp of text). The capsule is placed in the first pane's strip, to the left of the "⋯" button, with the pane's width minus
  48 dp (the text is clipped with an ellipsis if it does not fit). Always visible while broadcasting; tapping it stops the broadcast.
  Cost: 8 dp less text per pane in split tabs.
- **Not validated** on a device with 4 panes or more (clipped text).

### D-FIX-7 · 2026-10-05 · The initial command is typed when the shell has drawn its first prompt
- **Decision:** `StartupInputGate` (pure, with injected times): ready when the shell has written something and has been silent for 300 ms
  (the prompt is drawn and waiting), or at 5 s from startup at most (a mute or very noisy shell does not lose the command).
  `TerminalSessionHost.outputCount` counts the outputs (`onTextChanged`); `AndroidSessionFactory` queries it every 50 ms. It replaces
  D-T12b-10's fixed 800 ms delay, which typed before the prompt and made the echo be seen twice.
- **Limit:** a shell whose startup output has pauses of more than 300 ms (slow proot, a `motd`) may receive the command between
  two bursts; it is not lost, but the echo may repeat. **Not validated with a real shell.**

### D-FIX-8 · 2026-10-05 · Splitting a pane with a profile inherits its distro and user
- **Decision:** `SessionController.splitActive` without an explicit profile uses the active pane's `PaneOpening.forSplit()`: same target
  (distro, user), same look and same profile, but **without** the initial command (it would run again). A pane without a profile is still
  split in the origin's distro, as before.
- **Effect on saving:** the inherited pane is saved with the profile (`LayoutNode.Pane(profileId)`), so when reopening that layout
  the profile's command will run in it too. It keeps the distro (D-T12b-10 lost it) in exchange for that.

## T18 · Integration and device tests (feat/t18-integration-tests)

Done without a device: the host tests run in `./gradlew check`; the instrumented ones are only compiled (`assembleDebugAndroidTest`) and the orchestrator runs them on a Pixel 8. Usage guide in `docs/TESTING.md`.

### D-T18-1 · Host integration tests: real pieces, only the edges simulated
- **Decision:** `app/src/test/.../integration/` joins real `domain` and `data` classes with the only edges that require a device as fakes (official catalog, process, shared storage, native library, pty). `InstallAndLaunchIntegrationTest` installs from a `.tar.gz` served by MockWebServer (real downloader with SHA-256, real transactional extraction in a real directory, real Room) and plans the session with `ProotSessionPlanner`; `BackupRoundTripIntegrationTest` exports (in the clear and encrypted) and restores on a second empty device and compares files, distro and default user, profiles, layouts, shortcuts, extra keys, appearance and settings; `ResizeIntegrationTest` follows window, bars, keyboard and margin down to the fake pty's calls, with and without split panes.
- **No new production code:** Kover's thresholds (85 % in `domain`/`data`, 100 % in `data.rootfs.verify` and `data.backup`) do not change and stay green. The tests reuse the existing `Device`, `TarBuilder` and fakes; they do not repeat what the per-piece tests cover, but the join between them (e.g. that the installed default user ends up as `-i uid:gid` in proot's line, or that a fake hash leaves neither distro nor files).
- **Limit:** the official catalog is a `FakeCatalog` (its parsers already have their own tests, T06); the real process and pty are only tested on a device.

### D-T18-2 · Instrumented tests: no pinned rootfs, the app's installer with its SHA-256
- **Decision:** `ProotGuestTest` and `BackupDeviceRoundTripTest` obtain Alpine with `DistroInstaller` + `OfficialRootfsCatalog` + `HttpRootfsDownloader`: the official index gives URL and hash and the app verifies the file. There is no pinned download or binary in the repository. Without a network (index or download unreachable) the test is skipped with `Assume`; a wrong hash or HTTP **fail**, because that is what the verification must detect.
- **Real pty and proot:** `TerminalSessionHost` is used with the `ShellStart.proot` that the planner produces, i.e. the same path as the app; it is written through the pty and the emulator's transcript is read. The checks avoid false positives with the command's echo (the typed `echo ok` line is not equal to `ok`). Resizing is checked with `stty size` in the guest (24 80, 30 100, 12 60).
- **Backup on a device:** it exports a real encrypted distro to a file on the device's storage, restores onto an empty storage, compares each file (content hash and links' targets) and boots the restored distro. SSH keys are not included (the store uses the Keystore): the round with keys is covered on the host.

### D-T18-3 · Isolation: the user's distros are never touched
- **Decision:** `TestStorage` creates `files/it-t18-<random>/` with its own `NioFileSystemRepository` and an **in-memory** Room database, and deletes it on `close()`. It does not open `ultimateterminal.db` or `files/storage`. `MainActivityLaunchTest` is the mandatory exception: it launches the real activity with the real data, but only opens and closes screens (the profile editor is discarded without saving).
- **Operational warning:** by default the Android plugin uninstalls the app after `connectedDebugAndroidTest`, which would delete the user's data; that is why `docs/TESTING.md` says to pass `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`.

### D-T18-4 · UI Automator instead of Compose Test
- **Decision:** the activity test uses UI Automator (Apache-2.0) and the `strings.xml` strings (`R.string.*`), so it works in English and Spanish. Compose Test would require `ui-test-manifest` as `debugImplementation`, which would enter the debug APK; UI Automator is only `androidTestImplementation`.
- **What it checks:** Back from Settings returns to the terminal; Profiles with its edit sheet, and Keyboard shortcuts with its sheet, open without an unexpected close. Taps by text/description are the most fragile part and could not be run here: if a selector fails after an interface change, it has to be adjusted in `MainActivityLaunchTest`.

### D-T18-5 · Test dependencies
- **Added** (all Apache-2.0, only `androidTestImplementation`, they do not enter any APK): `androidx.test:runner` 1.7.0, `androidx.test:core` 1.7.0, `androidx.test.ext:junit` 1.3.0 and `androidx.test.uiautomator:uiautomator` 2.4.0, with the `androidx.test.runner.AndroidJUnitRunner` runner.
- **Dependency verification:** `verification-metadata.xml` regenerated with a clean `GRADLE_USER_HOME` and `--write-verification-metadata sha256`; only the missing components were added (115 lines, none removed or reformatted). Credited in `THIRD_PARTY_NOTICES.md` in the same change.
- **To validate on a device:** that the three classes pass on the Pixel 8 with a network (`ProotGuestTest`, `BackupDeviceRoundTripTest`, `MainActivityLaunchTest`), especially the interface selectors and that `stty` exists in Alpine's rootfs.

### Seventh round (Pixel 8, 2026-10-05, `master` at `f372372`): T18 instrumented and manual checks

- **T18 instrumented tests (`connectedDebugAndroidTest`, only on the Pixel 8 with `ANDROID_SERIAL`): 7 out of 7 pass** after fixing a failure in the test itself. `ProotGuestTest` (real proot on Alpine with the app's installer: `uname -a`, `echo ok`, `/etc/os-release`, and `stty size` after two PTY resizes) and `BackupDeviceRoundTripTest` (an encrypted backup of a real distro restored onto an empty storage, with identical content and links, and a wrong password restoring nothing) passed the first time. `MainActivityLaunchTest` (Back from Settings, Profiles with its sheet, Shortcuts with its sheet) failed with "the terminal screen did not appear": the "Keep sessions active" notice appears as a modal alert at startup and hides what is below from UI Automator; now the test presses "Not now" if it appears (`prompt_not_now`), without changing any setting.
- **Manual checks of #59 and #61, all good:** opening a profile or a layout closes Settings and focuses the tab; the tab is named after the profile ("dev"); the initial command comes out only once; tapping a field's label focuses the field and the form does not move; the broadcast notice does not cover text (it is clipped to "What you typ…" in a narrow pane; improvable); with four tabs the active one's `⋯` is seen whole; a pane split from a profile inherits distro and user without repeating the command; "Change user…" and "Set as default" appear in the menu of each distro that is not the default (`dev2` on Alpine: `uid=1000(dev2)`).
- **Correction to the fifth round:** the gap "the default cannot be chosen" was my mistake when looking: the option is hidden precisely on the distro that already is it.

## T21 (second pass) — Documentation review (docs/t21-review)

Text only, without touching code or devices. Everything that claims to be verified comes from the test rounds of this file.

### D-T21-4 · 2026-10-05 · Conventions of the user-facing documentation
- **README table:** each row says on which device it was verified (Pixel 8 or Huawei tablet) and, if the verification is partial, says so in the row itself. A fix that is in `master` but has not been seen again on a device does not go up to the table: it goes in the paragraph "Fixed in the code and not looked at on a device yet". Performance numbers are quoted with their context (debug build, Pixel 8, Fedora) and without rounding in our favor.
- **Instrumented tests:** they are described as "written and compiled, no recorded run" while no round notes that they were run (D-T18-1 to 5).
- **`CHANGELOG.md`:** a single `[Unreleased]` section is kept, grouped in Added/Changed/Fixed/Known limitations; each fix found on a device says whether it was verified again there.
- **`PRIVACY.md`:** it was reviewed against the manifest (no changes in the nine permissions) and against `OfficialRootfsCatalog` (`dl-cdn.alpinelinux.org`, `cdimage.ubuntu.com`, `raw.githubusercontent.com` and `dl.fedoraproject.org`). Changes: Fedora's domain, and that the fallback DNS are now edited in Settings > Network (T16).
- **`fastlane`:** `changelogs/10002.txt` (en/es) is added for the next `versionCode` (`0.1.0-rc.2` would give `(0*10000 + 1*100 + 0)*100 + 2 = 10002`), following `10001.txt`'s name. `gradle.properties` is still at `0.1.0-rc.1`: raising the version is a release decision (`RELEASING.md`) that was not taken here; meanwhile the 10002 file is not used. Limits checked with a script: title 16/30, summary 72/80 (en and es), description 3112 and 3331/4000, changelogs 371 and 426/500.
- **`CONTRIBUTING.md`:** it collects the submodule initialization, the instrumented tests (`ANDROID_SERIAL`, `leaveApksInstalledAfterRun`), the regeneration of `verification-metadata.xml` and the convention of appending at the end of `DECISIONS.md` (and how conflicts are resolved when rebasing).

### D-T21-5 · 2026-10-05 · What could not be checked
- **Fedora redirects:** `PRIVACY.md` names only `dl.fedoraproject.org`, the host of the catalog's URLs. It was not checked whether that server redirects to other mirrors when downloading; if it does, those hosts would also see the IP.
- **The notifications notice:** it is described as "a notice of its own before the system's" (`SessionPrompts`) without asserting when it appears.
- **Back on the Android 12 tablet:** the README and the changelog leave it as not verified (D-FIX-1 of "Fixes after tests").
- **Outdated fact that is still untouched:** the "Pending hardware validation" section, at the start of this file, still says that the agents do not test on devices and does not collect the six rounds; it is not edited so as not to clash with other branches (see D-T21-1).

### D-T20-7 · 2026-10-05 · F-Droid screenshots
- **Decision:** six phone screenshots per language (`fastlane/metadata/android/{en-US,es-ES}/images/phoneScreenshots/1..6.png`), taken on the Pixel 8 with the app in each language (`cmd locale set-app-locales`) and the status bar fixed (the system's demo mode, restored when finished): terminal with Fedora, split pane, Settings, Appearance, Distributions and Profiles. The test distro "AlpineOps" and the tests' profile "dev" appear; they can be retaken without them later.
- **Pending:** a tablet screenshot (`tenInchScreenshots`/`sevenInchScreenshots`) when the tablet is available; the `AllowedAPKSigningKeys` fingerprint and `fdroid lint` (D-T20-1..6).

### D-REL-1 · 2026-10-05 · First published version: 0.1.0-rc.2
- **Decision:** the user asked to go up to rc.2 (0.1.0-rc.1 was never published: it is just the version that was in the tree). `appVersion=0.1.0-rc.2` (versionCode 10002, already with its fastlane changelog), notes in `## [0.1.0-rc.2]`. F-Droid does not offer the `-rc.N` (`UpdateCheckMode: Tags` only final ones), so for now it is only released through GitHub Releases.
- **Signing key:** the user prefers to do the steps by hand (without a script): `keytool` in `~/keys/ultimateterminal-release.jks` and `gh secret set` of the four `UT_*` (RELEASING.md, "Signing"). A key of its own per app, as in the other apps.
- **Termux license:** the user decides not to ask the Termux maintainers for confirmation (D-T03-2 stays open and accepted). **GitGuardian:** the user dismisses the two incidents in their dashboard.

### D-REL-2 · 2026-10-05 · Signing key created
- The signing key (`~/keys/ultimateterminal-release.jks`, alias `ultimateterminal`, RSA 4096) was created by the user by hand and they uploaded the four `UT_*` secrets. SHA-256 fingerprint of the certificate: `19:73:00:45:28:24:46:33:3B:45:F8:72:B0:39:5C:D5:31:B4:A8:81:68:71:BE:6E:09:B7:8D:64:19:CD:21:2B`, put in the F-Droid recipe's `AllowedAPKSigningKeys` (in lowercase and without colons). The fingerprint is public; the password and the `.jks` are not in the repository.
- Pending on F-Droid: `fdroid lint`, and that the recipe uses the first final version (the `-rc.N` are not offered there).

### Eighth round (Pixel 8, 2026-10-05): signed release APK (`0.1.0-rc.2`)
- **Signature:** the APK published on GitHub Releases verifies with `apksigner` and its certificate has the SHA-256 fingerprint of D-REL-2.
- **Release performance (Alpine, distro installed with the app itself):** cold start to the prompt, six starts measured with successive screenshots (the prompt is detected when text appears on the first line; the measurement carries about ±250 ms from the capture itself): 1,406, 1,245, 1,174, 1,201, 1,172 and 1,217 ms; median of the last five **1,200 ms**. First frame (`am start -W`) 124–229 ms. `seq 1 200000`: `real 1.09 s`; `gfxinfo` in three passes: 124/143/141 frames, 0–0.81 % janky, p90 5 ms, p99 5–8 ms. **It meets the 1.5 s target.** Fedora was not measured on release (it is heavier: 1.63 s on debug).
- **Method:** the release build is not debuggable, so `run-as` or the `PROMPT_COMMAND` hook could not be used; to measure it the debug one had to be uninstalled (the Pixel's test distros were lost, nobody else's) and Alpine reinstalled from the app itself.
- **User's request during the test:** if there is no distro, the app must directly show an initial setup wizard to install one (consistency of experience). Today a clean install opens an Android shell without a distro. It becomes task T25 (in progress).

## T25 · First start (feat/t25-first-run-setup)

### D-T25-1 · 2026-10-05 · The initial setup replaces the tab, it is not overlaid
- **Decision:** `MainActivity` does not compose `TerminalScreen` until the gate (`SetupGate`) closes. It is necessary: the first layout of `TerminalViewModel` opens the first tab (`newDefaultSession`, RF-13); with the terminal composed underneath an Android shell would already have started. When the gate closes (distro ready or skip) the terminal is composed and that same path opens the default distro. While the distros have not been read (`UNDECIDED`) only the background is painted.
- **Rule (`domain/setup/FirstRunSetup`):** it is shown if the distros were already read, there is no `READY` one, there are no open tabs and it was not skipped. An installation in progress does not hide the screen: its progress is part of it (the request's "none being installed" rule is met because whoever installs from Distributions already has tabs or skipped). Once in `SHOWING` it only closes with a `READY` distro or on skipping, even if a tab appears (e.g. the notification's "new session" action); once `CLOSED` it does not come back in that process, so deleting the last distro with open tabs does not reopen it over them.

### D-T25-2 · The installer and the restore are reused
- `SetupScreen` uses the `DistroViewModel` (install, cancel, dismiss message) and the `BackupViewModel` (SAF picker, password, progress) of the Settings screens, which `viewModel()` shares per Activity; `InstallProgress`, `MessageRow` and `Working` were made `internal` to paint progress and errors with the same components. `SetupStep`/`setupStepOf` derives the step (choose, installing) from the installer's state: a failure is not a final state, it is "choose" with the last error, so the form is still there to retry (the button becomes "Retry") or to choose another distro.
- Preselected distro: Alpine (the smallest download, ~4 MB; the list puts the catalog's first with Alpine recommended). The suggested name follows the chosen distro until the user types one (like the install sheet), and the default user is `root`.

### D-T25-3 · Sizes: fixed notes, not from the catalog
- The catalog (`RootfsCatalog`) only resolves the source with a network at install time and Debian/Ubuntu do not publish a reliable size, so there is no size to show before choosing. Notes are put with the figures measured in D-T24 (Alpine ~4 MB, Fedora ~63 MB) and a general note that a network is needed, that the SHA-256 is verified and that decompressed it takes more. No figures are made up for Debian and Ubuntu. The real free-space check is still the installer's (`InsufficientSpace`).

### D-T25-4 · The new distro is the default before the tab is opened
- The first `add` already marks the first distro as default, but a `FAILED` leftover with the flag would leave it out and the first tab would fall to the Android shell. `SetupViewModel` checks, with the gate at `SHOWING`, that there is a default `READY` one (`distroToMakeDefault`) and if not it sets it with `DistroManager.setDefault` before closing the gate; if that fails, it closes anyway so as not to leave anyone trapped.

### D-T25-5 · Skip only lasts the session; sessions-active notice
- "Skip" is a `MutableStateFlow` in the ViewModel: it is not saved in settings or in `rememberSaveable`, so when reopening the app (a new process) without a distro it comes up again. It is a flat button at the end, disabled while installing or restoring.
- `SessionPrompts` already required a running session (`hasRunningSession`); since the setup creates none, the "Keep sessions active" notice does not appear over it and comes out after the first tab (or after skipping). No behavior changes in the other cases.

### D-T25-6 · Tests
- Host: `FirstRunSetupTest` (gate, recommended distro, default distro, form validity) and `SetupViewModelTest` (with fakes: show/skip/relaunch, app with tabs or with a distro, setting the default, failure when setting it, flow steps). Kover coverage intact.
- Instrumented (they only compile here): `MainActivityLaunchTest` is skipped with an assumption if the setup is shown (a device without distros) and `FirstRunSetupScreenTest` checks the screen and is skipped if the device already has distros. Verify on the Pixel 8 without distros (or in a clean profile/install); see T25 in `PLAN.md`.

### Ninth round (Pixel 8, 2026-10-05, `master` at `49842fd`): first-run wizard (T25)
- **Clean install (uninstalling and installing the debug build):** "Welcome" is shown with Alpine preselected and recommended (about 4 MB), Debian, Ubuntu and Fedora (about 63 MB), name, default user `root`, "Install", "Restore from a backup" and "Skip, use the Android shell for now". There is no longer an Android shell without a distro nor a sessions notice on top.
- **Installing Alpine:** the wizard closes, the first tab is called "Alpine Linux" and with its prompt, and only then does "Keep sessions active" appear.
- **Without a network (private DNS broken on purpose):** "Could not reach the official server. Check the connection and try again." with "Dismiss"; the form stays and the button becomes "Retry". With the network restored, "Retry" installs and opens the tab.
- **Skip:** opens a "Shell 1" tab of the Android shell; after closing completely and relaunching without a distro, the wizard is shown again.
- **Improvable:** on a phone "Install" sits at the bottom edge and "Restore"/"Skip" have to be scrolled to be seen; a fixed install button at the bottom would fix it. Not tested: restoring a backup from the wizard, rotating the screen midway through an installation, TalkBack.
- **Method lesson:** to simulate "no network" **the Wi-Fi is not turned off** (the Pixel goes through wireless debugging and the adb connection is lost; besides, the adb port changes on reconnecting). Only the private DNS was broken (`private_dns_mode=hostname` with a nonexistent host) and deleted afterwards.

### D-REL-3 · 2026-10-05 · 0.1.0-rc.3
- At the user's request another pre-release (`0.1.0-rc.3`, versionCode 10003) is published with the first-run wizard (T25) and the performance notes; the final `0.1.0` is **not** made yet. GitGuardian: the last 15 PRs have the check in SUCCESS; the dashboard's incidents were handled by the user.

### Tenth round (Huawei MRO-W09 tablet, 2026-10-05, `master` at `cc39941`): split panes
- The `master` debug build was installed on top of the previous one (same debug signature, without losing data). With the user's own distro (Fedora), in landscape: side bar with "+" and ⚙; "Split right" (long press on "+") opens a second pane with its prompt and its focus frame; the left pane measures 38 rows × 61 columns according to `stty size` and matches the visible width. Only `stty size` was typed; nothing of the user's distros was touched.
- **Checked afterwards:** the right pane also gives 38 × 61 (symmetric split) and, when rotating to portrait with both panes, each PTY follows its pane (right 61 × 36, left about 56 × 36; the extra keys row appears with the keyboard). The panes and their focus are kept when rotating.
- **Not checked:** the system's split screen (`--windowingMode 3/4` fails on this EMUI) and the tablet screenshot for F-Droid (the screen shows the user's real user name and "MyFedora"; publishing it as is is avoided).
- System settings that were touched (fixed rotation, screen on) restored.

### D-T20-8 · 2026-10-05 · `fdroid lint` passes
- With `fdroidserver` 2.4.5 (isolated environment, development tool only) and `fdroiddata`'s categories configuration, `fdroid lint com.qtekfun.ultimateterminal` ends without warnings after removing the trailing spaces of the `Binaries:` line and applying the canonical order of `fdroid rewritemeta` (sorted categories). The signing fingerprint is already in `AllowedAPKSigningKeys` (D-REL-2).
- **Pending to send it to fdroiddata:** that the recipe points to the first final version (`0.1.0`; F-Droid does not offer the `-rc.N`) and checking that CMake 3.31.6 and the pinned NDK are available on its build server (D-T20-1..6).

### Eleventh round (Huawei MRO-W09 tablet, 2026-10-05): system split screen
- The user set the split screen by hand (EMUI 14, Android 12; dragging from the side bar with `input` does not achieve it): browser on the left (`mBounds` 0–1392 px) and the terminal on the right (1409–2800 px, `split-screen-secondary`), 1391 px wide. The terminal is seen with its tab side bar and a prompt; there is no visible failure. **Not checked** because of interference: `stty size` at that width and when moving the divider.
- **Side effects of my tests (to be reviewed by the user):** the user's launcher desktop now shows the "Browser" and "Calculator" icons (dragged accidentally when dropping the dock icon at the edge); I did not remove them so as not to touch their launcher. The Calculator was left with text typed in ("size"). `am force-stop` gave internal EMUI exceptions on this tablet after forcing `--windowingMode 3`.
- **Lesson:** on this tablet do not automate system gestures through `input` again while the user tests their launcher; for split screen, let the user set it.

### Twelfth round (Huawei MRO-W09 tablet, 2026-10-05): split screen verified
- After restarting the tablet (the split could no longer be created after my attempts with `--windowingMode`, round 11), the user set the split screen by hand: terminal on the left (0–1391 px) and browser on the right. Only inside the terminal was touched (`stty size`): **43 rows × 49 columns**, consistent with the window's width (1391 px minus the tab side bar, about 50 columns with margins) and the screen's height. The app keeps the side bar in that window and there were no failures.
- **Seen, not investigated:** on the terminal's first line a small gray bar is seen over "host" of `localhost` (painting leftovers after resizing or an old cursor); the prompt is legible. A candidate to review with a larger capture after a new resize.
- **Pending:** moving the divider and checking that the PTY follows (the user has to move it; dragging through `input` was ruled out).

### D-REL-4 · 2026-10-05 · First final version: 0.1.0
- At the user's request ("pues venga, dale") the final `0.1.0` (versionCode 10099) is cut after the three pre-releases: `appVersion=0.1.0`, notes in the changelog's `## [0.1.0]` (summary and known limitations), fastlane changelogs `10099.txt` (en/es). The F-Droid recipe (`fdroid/com.qtekfun.ultimateterminal.yml`) already pointed to `0.1.0`/10099 with the signing fingerprint and passes `fdroid lint` (D-T20-8): the next step is sending it to fdroiddata, which is a public action and is decided by the user.

### D-T26-1 · 2026-10-05 · Dynamic side bar: two states and a setting (RF-16)
- A user request for 0.1.1: that the side bar in landscape shrinks when tapping on the terminal. States: **expanded** (192 dp, as before) and **collapsed** (a 56 dp strip = 48 dp touch + 4 dp padding on each side). It only changes in the side bar (window width >= 600 dp); the top bar is not touched, so the Pixel 8 in portrait stays the same (in landscape it measures more than 600 dp and does use the dynamic side bar).
- The bar starts **expanded** when opening the app (it is what the user already knows) and collapses only with a tap on a pane (the focused pane's and an inactive one's, which also gives it focus). There is no collapsing by time or when typing: collapsing without the user touching anything would be surprising. The state is saved with `rememberSaveable` (it survives rotating the screen) and is not persisted between openings.
- The events that change the state (`SidebarEvent`: `TERMINAL_USED`, `EXPAND`, `COLLAPSE`, `TAB_ACTIONS_NEEDED`) and the mode (`SidebarMode`: `AUTO_COLLAPSE` by default, `ALWAYS_EXPANDED`) are in `domain/session/Sidebar.kt` as pure functions with tests; the UI only sends them.

### D-T26-2 · 2026-10-05 · The setting and the configuration backup
- `AppSettings.sidebarMode`, saved in the `setting` table with the key `sidebar_mode` (no Room migration: it is key-value; an unreadable value goes back to the default). In Settings > Terminal a single switch, "Collapse the side bar when using the terminal": on is `AUTO_COLLAPSE` and off is the "always expanded" option (with the switch off no control to collapse is shown).
- Configuration backup: `SettingsDto.sidebar: SidebarDto(version = 1, mode)`, null in earlier backups (the device keeps its mode). An **unknown version** is not interpreted (the device's mode is kept, as with shortcuts); an unknown mode name in version 1 goes to the default. `data.backup` stays at 100 % of line and branch (four new restore tests and the full export and restore path).

### D-T26-3 · 2026-10-05 · The PTY is resized once per change, not per frame
- Choice: the terminal area is **laid out only once with the target width** (`padding(start = final width)`), and during the animation only the **drawing is slid** with `graphicsLayer { translationX = current_width - final_width }` (`slideOffsetPx`); the bar is drawn on top with the animated width (200 ms). This way the area's `onSizeChanged`, and with it the panes controller and `LayoutEffect`, see a single size per change: neither a `SIGWINCH` per frame nor shell flicker. No new debounce is needed; the existing pipeline (`settled`, T04/T10) still dampens the rest (keyboard, window dragging).
- Discarded: measuring the area while animating and delaying the notice (a debounce makes the first change late and, besides, `onSizeChanged` arrives a frame late with respect to the animated value, so any subtraction of "current width - final width" would oscillate).
- Visible cost: during the 200 ms of the animation the part of the terminal that has not yet reached its place is cut by the window's edge when collapsing, or with a gap in the scheme's color when expanding; the content is already at the final size. The geometry tests (`SidebarTest`) check the offset and that `terminalLayoutFor` gives a single grid per target width (more columns collapsed than expanded).
- Without losing the scrollback: `onArea` only changes the PTY's size (the same path as rotating or opening the keyboard). **The focus and the keyboard** are not touched: the bar only uses touch gestures, does not ask for focus, and the focused pane (`TerminalInputView`) is neither recomposed nor recreated; not checked on a device.
- With "Remove animations" (`Settings.Global.ANIMATOR_DURATION_SCALE` at 0) the width changes at once (`snap`).

### D-T26-4 · 2026-10-05 · The strip and its tabs
- The strip shows each tab's **initial** (first letter or digit in uppercase, `?` if there is none; `tabInitial`), with the oval in the selection color on the active one, and below "+" and ⚙ as always, with a chevron on top to expand. When expanded a chevron is added at the bottom to collapse. The strip's view is switched when the animated width passes the midpoint between the two widths (no fade: simpler and without clipped-text flicker).
- Tapping the strip where there are no buttons expands it (to be reachable with a wide finger); a long press on a strip tab also does (that is where the rename and close menu and the drag to reorder are, which are not duplicated in the strip). The "+" menu (long press) opens in the strip itself without expanding it: it does not need more width.
- Accessibility: each strip tab uses the same texts and actions as the full one (name, "tab n of m", state, selected, rename, close, move before or after) and adds the custom action "Expand the side bar" or "Collapse the side bar" (the second only if the setting allows it); the chevrons are 48 dp buttons with a label. Texts in `values/` and `values-es/`.
- **Not done:** the swipe gesture from the left edge (optional in the request) nor a keyboard shortcut to toggle the bar (adding an `AppShortcut` touches the model, the editor, the display and the configuration backup's shortcuts; it is left for a separate task). With a physical keyboard it is reached with the mouse or with TalkBack.

### D-T26-5 · 2026-10-05 · Structure
- `ui/Sidebar.kt` (state, animated width, `SidebarControl`), `ui/TabRail.kt` (strip tab, chevron button, `tabSpeech`) and the branch in `ui/TabBar.kt`; `TerminalScreen` places the bar next to the terminal (`SideLayout`) and receives `sidebarMode` from `MainActivity`. detekt forced distributing functions into files and grouping parameters (`GridReserve`); no rule was relaxed.

### D-T26-6 · 2026-10-05 · What remains to be checked on a device
- Everything in RF-16 is tested on the host only (state, geometry, setting and backup). Still to see on the Pixel 8 (landscape; in portrait it must stay the same) and on the tablet (landscape and split screen): that collapsing on tap gains columns (`stty size`), that expanding returns them without damaging the scrollback, that the keyboard does not close during the animation, the strip's look, TalkBack and "Remove animations".

### D-T26-7 · 2026-10-05 · The side bar does not keep the keyboard (corrects D-T26-3)
- **Bug (Pixel 8, landscape):** after tapping the terminal the bar collapsed and the keyboard opened, but what was typed did not reach the terminal, and a string with a space expanded the bar again and closed the keyboard. **D-T26-3 was wrong** in assuming that the bar does not take focus: the buttons (`clickable`, `combinedClickable`) of the bar and the strip are Compose focus targets, and, when entering keyboard mode (`adb input` keys or a physical keyboard take the window out of touch mode), the focus passed from the `TerminalInputView` to a chevron or to a tab, which received the keys: Space/Enter pressed it (expand) and the input view stopped being the editor, so the keyboard closed. The exact cause on the device is unconfirmed; the fix covers the two plausible paths.
- **Fix, 1:** the whole bar carries `focusProperties { canFocus = false }` (`TabStrip`), and the chevron (`SidebarButton`) stops using `clickable`: a tap gesture plus the semantic action `onClick(label)`, with the same `contentDescription`, role and 48 dp, so TalkBack keeps its actions. **2:** `TerminalInputView` remembers what the user asked for (`keyboardWanted`: `showKeyboard` yes, `hideKeyboard` no) and `restoreKeyboard()` gives back the focus and the keyboard if they were wanted and lost; `TerminalScreen` calls it when `sidebar.open` changes and again when the animation has finished. It never hides anything, and it only acts with the window focused (a dialog with a text field does not lose its keys).
- The decision is pure logic (`KeyboardIntent`, with tests) and there is a source-code test (`SidebarFocusGuardTest`) that prevents putting a `clickable` on the chevron again, removing the `canFocus = false`, making the bar hide the keyboard or another screen other than the covering ones calling `hideKeyboard`. No keyboard closed by the bar: collapsing and expanding never hide it.
- **Missing on a device:** Pixel 8 in landscape and the tablet: tapping the terminal, typing letters, space, Enter, Backspace, Tab, arrows and Ctrl/Alt (with `adb input` or a physical keyboard) without the bar changing or the keyboard closing; TalkBack on the chevron and the tabs.

### D-FIX-9 · 2026-10-05 · The keyboard no longer covers the focused field (sheets and screens)
- Cause: `IosBottomSheet` is a dialog with `decorFitsSystemWindows = false` and a fixed height (a fraction of the window) stuck to the bottom edge. The keyboard was drawn over the sheet and the `imePadding()` that each sheet put **inside** the scroll only added room at the end of the content: the field was still below the keyboard. In the initial setup screen the `LazyColumn` did not use the keyboard's insets and nobody asked to bring the field into view.
- Fix in a single place (`ui/ios`): (1) the sheet is lifted above the keyboard (`padding(bottom = ime)`) and its height comes from `SheetDetents.heightAboveKeyboard`, a pure function with tests: without a keyboard it keeps its detent; with a keyboard it is clipped to 94 % of the space left (in landscape and on a tablet, with a small keyboard, nothing changes). The navigation bar's bottom padding excludes the keyboard (`navigationBars.exclude(ime)`) so as not to count it twice. (2) `Modifier.bringIntoViewOnFocus()` (`ImeFocus.kt`), put on `IosTextField` and `IosTextArea`, asks to bring the field into view when focused and again every time the keyboard's height changes (it comes in after the focus). It uses the sheet's single scroll: no other is nested (`SheetScrollNestingTest` still passes). (3) `IosLargeTitleScreen` (setup, profiles, SSH, Appearance, etc.) applies `imePadding()` to its list, so the field and the main button stay accessible; the manifest already has `adjustResize` and `enableEdgeToEdge()`.
- The `Column(Modifier.imePadding())` were removed from all the sheets (now the sheet does it once; keeping them would have added a duplicated gap).
- The centered notices (`NamePrompt`, `IosAlert`) do not change: they are small dialogs with the window resizable by default.
- Not checked on a device: the Pixel 8 with the keyboard open in the install distro sheet, the initial setup screen, rename, the profile form, the save layout sheet and the shortcuts one.

## D-KBD · On-screen keyboard type (fix/avoid-secure-keyboard)

### D-KBD-1 · Cause and fix
- `TerminalInputView.onCreateInputConnection` declared `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`. The keyboard frameworks of several manufacturers (EMUI/HarmonyOS, MIUI/HyperOS, ColorOS and others) treat any password field as secure input and show their secure keyboard instead of the normal one. It is the cause according to the code; **the hypothesis has not been checked on a phone of those brands**.
- A "Keyboard type" setting is added (`KeyboardType`): **Normal** (default: `TYPE_CLASS_TEXT or NO_SUGGESTIONS`, no variation; `AUTO_COMPLETE` is not turned on), **Compatible** (the previous behavior, with the visible-password variation) and **Raw** (`TYPE_NULL`, like Termux's `input-type` option). The `imeOptions` (`NO_FULLSCREEN`, `NO_EXTRACT_UI`, `NO_PERSONALIZED_LEARNING`) are the same in the three.
- The default value is Normal for all devices: **the manufacturer is not detected** (fragile and inexact). Whoever notices their keyboard autocorrects in the terminal, chooses Compatible. Cost: on the Pixel 8 with Gboard it has to be checked whether Normal is enough for it not to autocorrect or compose too much (that is why the setting exists).

### D-KBD-2 · Structure and persistence
- `domain/terminal/KeyboardType.kt`: the enum, `parse` (an unknown name is the default value) and `KeyboardFlags.of`, which gives `inputType` and `imeOptions` with local constants; a test compares them with those of `InputType`/`EditorInfo` and checks each mode, so it is tested without Android.
- `AppSettings.keyboardType`, key `keyboard_type` in Room (`RoomSettingsRepository`), `SettingsViewModel.setKeyboardType` and a three-row section with a mark in Settings > Keyboard, with a footer line per option (it includes that some manufacturers' secure keyboard may appear with Compatible). Texts in `values/` and `values-es/`.
- Configuration backup: `SettingsDto.keyboard: KeyboardDto?` with `version` (1) and `type`, like `SidebarDto`: null (earlier backup) or a version this app does not know leaves the device's value; an unknown name is the default value. Tests in `ConfigRestoreSettingsTest` and `ConfigRestoreTest`; `data.backup` stays at 100 %.
- The view receives it through `LocalKeyboardType` (`MainActivity` provides it from the settings, like `LocalTerminalCovered`) and `TerminalOverlays` assigns it to `TerminalInputView.keyboardType`; the setter calls `InputMethodManager.restartInput(view)` only if the value changes, because `onCreateInputConnection` is read when the keyboard connects.

### D-KBD-3 · Text flow with the normal keyboard
- `ComposingText` does not change: it sends the minimal edit (delete what changed, write the new) for `setComposingText`/`commitText`, and `deleteSurroundingText` passes through as Backspace. With the normal field and `NO_SUGGESTIONS` the keyboard should not suggest or autocorrect, but if it composes, the result on the line is the same. New tests in `ComposingTextTest` with a simulated line: composition replaced by another, `commit` different from what was composed, deleting after confirming, deleting inside the composition and continuing, the automatic space after punctuation (delete 1 and write ". "), swipe typing and replacing the word, `\n` as text (the keyboard converts it into `\r`) and end of composition. `newCursorPosition` is still ignored (the terminal has no editing cursor). Enter and Backspace as key events, sticky Ctrl/Alt and the physical keyboard are not touched.
- In **Raw** the connection still exists and serves `sendKeyEvent`; without a real `TYPE_NULL` it cannot be known which keyboard sends key events and which text: check on a device.

### D-KBD-4 · What remains to be checked on a device
- Pixel 8 with Gboard in the three modes: that typing reaches the shell without waiting for the keyboard to be hidden, that Normal does not autocorrect, Backspace and Enter, and that switching mode with the keyboard open restarts it. A Huawei/Honor, Xiaomi or OPPO phone: that with Normal the normal keyboard comes out and not the secure one, and what happens with Compatible.

### D-BAK-1 · 2026-10-05 · The distros stay in private storage (not on the sdcard)
- Report: exporting a backup failed with "cannot read /data/user/0/.../files/storage/distros/<id>/..." and it was suggested to move the distros to a folder on the sdcard "so they can be read".
- **It is not done.** Shared storage (FUSE/sdcardfs, FAT-like) has no symbolic links, permissions or POSIX owners or execute bit and is mounted `noexec`: proot and the distros would stop working. Besides, any app would read it (security) and Android 11 or later's scoped storage prevents it. What the user really wants (reaching their backups and their files) is covered by: (a) fixing the export (D-BAK-2), (b) a visible location for backups (the system picker, D-BAK-5) and (c) `~/storage`, which already mounts the shared storage inside the distro (D-T13-1). The reason and the steps are in the README's FAQ and on the Settings > Backups page.

### D-BAK-2 · 2026-10-05 · Root cause and fix: the app opens for itself what it owns
- **Cause:** the extractor keeps the archive's modes (D-T07-4) and the files belong to the app. A distro like Fedora carries files that only root reads (`/etc/shadow`, `shadow-`, `gshadow`, `gshadow-` in mode 000, `usr/bin/sudo` and `sudoreplay` execute-only). On a real Linux the kernel lets root read; under proot the guest is root but the **host** (the app) is not, and `Files.newInputStream` fails. D-T15-7's hypothesis ("if it appears in real distros, it will have to be changed") was confirmed: the exporter walked the tree and opened those files as they were.
- **Fix (`OwnerAccess`, `TreeWalker`):** the app is the owner, so before reading it adds the owner's read bit (`u+r`; on a directory, `u+rx`) with a per-path `chmod` (`File.setReadable(true, true)`: it does not open the file, and `Files.setPosixFilePermissions` does open it and fails with a 000 file) and **leaves it as it was** when finished. The tar header carries the original mode, read before touching anything (`unix:mode`, falling back to the nine bits). A symbolic link is never touched or followed (`NOFOLLOW_LINKS`, and `addOwner` rejects a link); absolute or broken links are stored as links. Hard links are archived as independent files (D-T15-7): content and mode are identical, the link is not.
- `Files.walkFileTree` opens the directory **before** notifying the visitor, too late to fix its mode; that is why there is a walker of our own (`TreeWalker`) that opens the directory, lists it **sorted by name** (the tar is the same every time) and restores it. It is used by export, duplication (`FileTrees.copy`, which also keeps the copy's 000 mode and sets the time before the mode), deletion (opens `rwx`) and size (`FileTrees.size`, which also skips what cannot be listed: it is an estimate).
- A file that cannot be read even so (it disappeared, an I/O error) **makes the export fail atomically** with `BackupError.UnreadableFile(path, denied)`: the path is relative to the distro, the message is localized (en/es) and not alarmist, nothing is written and the working directory is deleted (CLAUDE.md's transactional rules). Failing was preferred to skipping silently: an incomplete backup that looks good is worse.

### D-BAK-3 · 2026-10-05 · What is done when installing/restoring with the modes (with the code's evidence)
- Chosen: **the disk keeps the archive's mode** (a 000 file stays 000) and the host-side readers go through `OwnerAccess`. Evidence: proot does not read file modes from a side archive or from xattr, only from the disk; the kernel decides with the real mode, so changing the mode on disk would change what `ls -l` sees in the guest and would break the exact copy of modes that is asked for. Directories already carried the owner's `rwx` added (D-T07-4: it is needed to move and delete them), so **in the backup a directory comes out with that `u+rwx`** (e.g. 000 -> 700); it is the only known mode difference.
- **setuid, setgid and sticky are still dropped** when installing and restoring (a security decision of D-T07-4, unchanged: a hostile archive must not leave special bits). The exporter does read all 12 bits if the file system gives them. Therefore an original 4111 `sudo` comes back as 0111. Under proot it is not needed (the guest is already fake root); **if 4111 were seen on the host on the device it would have to be found out who put it there** (it stays in D-BAK-6's list).
- Known limitation, not new: under proot a 000 file is not read by the guest's root either (the kernel rules); a possible future change is giving `u+rw` to those files on disk, at the cost that `ls -l` would stop matching. Without evidence from a device it is not touched.

### D-BAK-4 · 2026-10-05 · Restoring in the initial setup must not close with the first distro
- Found when reviewing the full path: a restore installs the distros one by one and applies the configuration (and with it the default distro and its user) at the end. `FirstRunSetup` closed the wizard at the first ready distro and `SetupViewModel` forced the one with the highest id as default, so the first tab could open with a wrong distro while the restore continued.
- Now `RestoreActivity` (a singleton, put by `BackupViewModel` around the restore) feeds `SetupInputs.restoring`: with a restore in progress the wizard stays (except "skip") and no default is forced; when it ends, the configuration has already set its own and the wizard closes, and the first tab opens the default distro (RF-13) with no manual steps.

### D-BAK-5 · 2026-10-05 · Where the backup goes and what it is called
- The system picker (SAF, `CreateDocument`) chooses the location: Downloads, a USB drive, the cloud. The suggested name is `UltimateTerminal-<date>.utbackup` (everything), `UltimateTerminal-settings-<date>` or `UltimateTerminal-<distro>-<date>` (`BackupFileName`, pure and with tests; the distro's name is reduced to letters, digits and `_`). Settings > Backups explains it and the final message gives the file's name, its size and the recommendation to copy it to a safe place. FAQ in the README.
- The backup is made through streams: each distro is archived to a private working file and from there it goes to the final file with 64 KiB buffers, without loading it into memory; restoring makes two passes through the stream (verify and restore). A failure or cancellation deletes the destination file (`discard`) and the work.

### D-BAK-6 · 2026-10-05 · Checklist on a real device (Pixel 8): export everything -> uninstall -> install -> wizard -> restore
With tests on the host (JVM): modes, links (absolute, relative, broken), 000 and execute-only files, a closed directory, a file that changes mode during the export, a different base path, a new directory name, 2 MiB incompressible, with and without a password, wrong password, truncated file, future format, atomicity, suggested name, a wizard that waits for the restore. **It cannot be checked without a device:**
1. With Fedora installed (and Alpine), export "everything" with a password, saving in Downloads and on a USB drive: suggested name, progress, cancelling midway (no file is left), message with name and size. Fedora (~195 MB) without memory errors and without "cannot read".
2. Copy the file to a PC and back (or use the one on the USB drive); uninstall the app; install it; the wizard: "Restore from a backup", choose the file, ask for the password, wait. Afterwards: the wizard closes by itself and the first tab is the default distro with its user, without manual steps.
3. `ls -l /etc/shadow /usr/bin/sudo /etc/gshadow /etc/resolv.conf` equal before and after (except the setuid of D-BAK-3: note what appears at the source and at the destination); `sudo` and `passwd` work; the links (`ls -l /etc/alternatives`) equal; `sha256sum` of some large files equal.
4. Profiles, layouts, shortcuts, extra keys, appearance, DNS, side bar, SSH hosts and **SSH keys** (only in an encrypted backup: they are exported in OpenSSH text inside the encrypted file and saved again with the new device's Keystore) present; try an SSH connection with a restored key. **Documented gap:** imported fonts do not travel (D-T16): when restoring the bundled one is used.
5. Settings: `~/storage` has to be turned on again (the permission belongs to the device). `/etc/resolv.conf` and proot's paths are rebuilt at launch (no absolute paths are saved).
6. Errors on screen, localized and leaving nothing behind: wrong password, a half-copied file (cut it), a file of a newer format version.
7. Device: check with `find <rootfs> ! -type l ! -readable` (like the report) that after exporting **no** mode is left changed (the list must be the same as before).

### D-REL-5 · 2026-10-05 · 0.1.1
- At the user's request ("tan pronto tengas todo saca una release y vemos de probarla en el móvil real") `0.1.1` (versionCode 10199) is published with the dynamic side bar (T26) and its fixes, the keyboard type, the keyboard that does not cover fields and backups with files that only root reads. Several fixes are tested on the computer and **not yet verified on a real phone**; the log says which. The F-Droid recipe is updated to `0.1.1`/10199 (the fingerprint does not change).

### Thirteenth round (the user's real phone, 2026-10-05, `0.1.1`): export verified
- The user confirms that **the backup export now works** on their phone with `0.1.1` (before it failed with "cannot read /data/user/0/…" because of files only root reads: D-BAK-1..3). No details about the distro or the size.
- **Pending verification (D-BAK-6):** copying the file to a PC or USB, installing on a new phone (or uninstalling/installing), restoring from the initial wizard and checking that distros, user, profiles, layouts, shortcuts and settings come back and that the default distro opens by itself.

### D-FONT-1 · 2026-10-05 · Font import rejected all of them
- Real phone (Pixel 8, Android 17): importing JetBrainsMono Nerd Font Mono gave "letters are missing" (`MissingGlyphs`). Cause: `Paint.hasGlyph(String)` only returns true for a multi-character string if all of it forms ONE glyph (a ligature or grapheme cluster), not if each character has a glyph; `REQUIRED_GLYPHS` has 29 characters, so any font failed. The tests with a fake probe did not detect it.
- Decision: `FontChecker` asks for each code point separately, and `AndroidFontProbe.hasGlyphs` also iterates code points (surrogate pairs together). The `FontProbe` contract documents that it is asked one by one. Tests with a probe that imitates Android (long strings not accepted) to pin the contract; a font that lacks a glyph still gives `MissingGlyphs`.
- Monospace: `advance` uses `measureText` per sample character and a 1 % tolerance; unchanged (Nerd Font Mono measures everything in one cell; a proportional one still gives `NotMonospaced`).
- Finding: `TerminalPainter` draws with `canvas.drawText` and the imported `Typeface`; Android only falls back to the system font for the glyphs the font does not have, so the Private Use Area glyphs (U+E0B0, U+F015) of a Nerd Font are drawn with it. They are not required in the checker. **Not verified on a device.**

### D-SSH-1 · 2026-10-05 · Importing a key from a file proposes the file's name
- At the user's request: when choosing the file in "Add key > Import", the name field is filled with the file's name (`OpenableColumns.DISPLAY_NAME`, read in `KeyFiles` of the `ssh` layer, not in `domain`). The pure logic is `DefaultKeyName.from(displayName, existingNames)` (with tests): it trims spaces and control characters, removes a single final `.pem`/`.key`/`.txt` extension (case-insensitive; a file called just `.pem` keeps the name), cuts to `Validation.MAX_NAME_LENGTH` and, if the name already exists (case-insensitive, as when saving), adds ` 2`, ` 3`... without exceeding the limit. With no usable name it returns `""`: the field stays empty, as before.
- It is only filled if the field is empty or still has the previous suggestion: what the user typed is never overwritten; a second file choice does replace the suggestion. `.pub` is neither removed nor accepted as a private key: the content validation (`PrivateKeyText`) does not change. Pasting text has no file name (unchanged) and "Generate" does not change. **Not verified on a device** (Pixel 8: importing `id_ed25519_homelab` from Downloads and seeing the name).

### D-TAB-1 · 2026-10-05 · A long press on a tab asks before closing
- **Request:** "if I hold down a tab in the bar, let the close confirmation come out". The existing close alert (`CloseConfirm`, `TabsController.closeConfirmation` state) is reused with a new entry point, `askToClose(id)`, which **always** asks, also if the process ended (`requestClose` still closes ended tabs without asking: the "⋯" menu, the shortcut and the TalkBack action use it). The alert names the tab ("Close %1$s?", the same text on both paths) and adds the notice that the program will be stopped only if it is still alive.

### D-TAB-2 · 2026-10-05 · Long press: confirm without movement, drag with movement
- Reordering was already "long press and drag" (`detectDragGesturesAfterLongPress`), so the long press could not simply open the alert without stepping on it. Rule (`resolveLongPress`, pure tested logic): on release, if the finger did not move away more than the `touchSlop` from the start of the long press (the maximum in any direction, not the net) a close confirmation is asked; if it moved more, it is a drag and only reorders, without asking. The alert comes out when lifting the finger, not when the time is met. Tap and double tap do not change.
- TalkBack: the custom actions are kept (rename, close, move before/after, side bar); "Close" already existed and is not changed (it closes without asking if the process ended, as before).

### D-TAB-3 · 2026-10-05 · The strip asks too (changes D-T26-4)
- In the collapsed strip, a long press on a tab no longer expands the bar: it opens the same close alert (consistency requested by the user). Expanding is still possible with the chevron on top, tapping the strip's background and with the TalkBack action. The strip has no drag, so there is no conflict. `SidebarFocusGuardTest` watches it.
- **Pending verification on a device:** Pixel 8 (portrait and landscape) and tablet: that the gesture distinguishes long press and drag well, the alert (name and buttons), the strip, TalkBack.

### D-NEWTAB-1 · 2026-10-05 · The "+" menu has an explicit button
- Problem: the new tab menu (distros, split, profiles, layouts, settings...) only opened with a long press on "+", which nobody discovered (not even the owner).
- Decision: a "More options" button (`ELLIPSIS` glyph, 48 dp) next to the "+" in the top and side bars, and stacked below the "+" in the rail (56 dp). A tap on "+" still opens a tab of the default distro; the long press on "+" is kept as a shortcut to the same menu. A single `menuFrom` state decides which control the menu hangs from.
- Accessibility: the button has `contentDescription` "More options"/"Más opciones", `stateDescription` (menu open/closed) and the semantic `onClick` action; it uses `detectTapGestures`, not `clickable`, and the bar keeps `canFocus = false` (D-T26-7). The source guard test (`SidebarFocusGuardTest`) pins it.
- Position: `IosContextMenu` now uses `MenuPositionProvider` (pure function `menuPlacement`, with tests): top-right corner over the anchor's; if it does not fit to the left (rail stuck to the edge) it opens next to the anchor; always inside the window. It also affects the tab menu. **Not verified on a device** (Pixel 8 portrait/landscape, tablet with bar and rail).

### D-REL-6 · 2026-10-05 · 0.1.2
- At the user's request `0.1.2` (versionCode 10299) is published with the more-options button of the "+", the long press on a tab to confirm closing, the file name as the SSH keys' name and the fix for font import. A backup of the Pixel was made beforehand (148 MB, unencrypted, one distro) to update on top of the signed `0.1.1` without losing data.

### Fourteenth round (the user's Pixel 8, 2026-10-05, `0.1.2` installed on top of `0.1.1`)
- **Update:** the signed `0.1.2` installs on top of `0.1.1` keeping data (Fedora, hosts, prompt). Beforehand a backup was exported (148 MB, unencrypted, one distro): the file is read as a tar with `manifest.json`, `config.json` and `distros/0.tar.gz`, and was downloaded to the PC.
- **Fedora tailored to the user (the user's request):** a distro installed from the welcome wizard with user `qtekfun`, which is created by itself (`uid=1000`). `~/.ssh/config` was copied (without keys or `IdentityAgent`), the aliases (`k`, `upg` without `flatpak`, `sshrouter` with `ssh router`, without the password file) and `dnf.conf`; `openssh-clients` and `starship` 1.26.0 were installed (it is not in Fedora's repos: the release's official `aarch64-unknown-linux-musl` binary, with its SHA-256 checked), plus the user's `~/.config/starship.toml`. The terminal starts as `/bin/sh -l` (bash in POSIX mode, which does not read `~/.bashrc`): `~/.profile` does `exec bash -l` and `~/.bash_profile` loads `~/.bashrc`. Everything was typed into the terminal in chunks and verified by checksum; the root script was run with the user temporarily set to `root`.
- **SSH hosts:** the 8 of `~/.ssh/config` created in the app (ts-* with the Tailscale name, which only resolves with Tailscale active) and associated with their keys, which the user imported (the private files in Downloads were deleted afterwards). `router` goes without a key. The key's default name comes from the file (D-SSH-1): it worked.
- **Nerd Font:** D-FONT-1's fix works on the device: `JetBrainsMonoNerdFontMono-Regular.ttf` is imported and, once selected, starship's prompt draws its icons (Powerline, OS, clock, arrow). Before the fix the app rejected it.
- **Improvable:** when a font is imported, it is not selected by itself (it has to be tapped and "Use this font" chosen); and the SSH host list does not show which key each one has.
- **Tests pending for `0.1.2`:** the long press on a tab to close, and that the three-dot button opens the menu in landscape and on the tablet.

### D-T20-9 · 2026-10-05 · Renewed screenshots (0.1.2)
- The F-Droid screenshots are repeated with `0.1.2` on the Pixel 8 (English and Spanish, status bar fixed with the system's demo mode, restored afterwards): terminal with Fedora and starship's prompt with Nerd Font, split pane, the three-dot button's menu, Settings, Appearance and Distributions. No SSH hosts or IPs appear; the prompt's user name `qtekfun` does (the same as the project's GitHub). The README shows three (`docs/images/`, copies of the English `fastlane` ones).
- Pending: a tablet screenshot and the welcome wizard's (it needs a clean install).

### D-NOTIF-1 · 2026-10-05 · "Exit" from the notification and never zero tabs
- **Symptom (Pixel 8, 0.1.2):** when closing the session from the notification, the app reopens without sessions and has to be tapped twice.
- **Cause (read in the code, without a device):** the "Exit" action is `PendingIntent.getService` -> `SessionService.onStartCommand(ACTION_EXIT)` -> `SessionManager.closeAll()` (closes all sessions, the service stops). The activity, if it was still in recents (stopped), did not receive the change: `collectAsStateWithLifecycle` does not collect below STARTED. On coming back, the state went from "with sessions" to empty and `FinishWithSessions` did `finishAndRemoveTask()`: the app was seen opening and closing, and the second tap was the cold start. Besides, with the activity alive and without sessions, `TerminalViewModel.started` was already true and nothing opened a first tab.
- **Decision:** (a) `SessionController.shutdownAll()` (exposed as `SessionManager.shutdownAll()`, used by the service) closes all sessions and asks to stop the service even if this process did not think it was running; it does not launch the activity. The intent is interpreted with `ServiceCommand.of(action)` (pure, tested). (b) A pure `StartupTabRule` rule: on resuming with zero sessions a default tab is opened; the app only closes if the last tab is closed with the screen resumed (the usual behavior with the X). What happens with the screen stopped closes nothing. `SessionManager.newDefaultSession(onlyIfEmpty)()` checks that there are still no sessions after reading the distro, so that the first layout and the resume do not open two tabs. (c) Tapping an old notification only opens the activity (SINGLE_TOP) or starts the service, which promotes itself and stops by itself; no changes or failures.
- **Pending verification on a device:** Pixel 8: closing from the notification and opening from the icon and from recents: one tab, one tap.

### D-NOTIF-2 · 2026-10-05 · The notification stayed with "0 active sessions"
- **Symptom (Pixel 8, 0.1.2):** with one session, "Exit" left the notification, now with "0 active sessions": the service did not end completely.
- **Second cause (read in the code):** `SessionService.observe()` launched a collector on `Dispatchers.Default` that does `notify(ID, build(runningCount))` on every state change. `ACTION_EXIT` closed the sessions and then did `stopForeground(REMOVE)` + `stopSelf()` on the main thread, but the collector, still active, received the zero-sessions state an instant later and published the notification again as a normal ongoing (non-dismissable) one. It was a race: depending on the thread, sometimes `stopForeground` won (what the orchestrator saw) and sometimes `notify`.
- **Decision:** a pure `ForegroundPolicy` rule: `notificationCount(sessions)` is null with zero running sessions (there is no notification) and `step(sessions)` gives `STOP_AND_REMOVE` when there is no shell. The service handles any command by promoting itself first (Android requires it within a few seconds after `startForegroundService`, also if it is going to stop) and afterwards applies `step`. The collector runs on the main thread (`Dispatchers.Main.immediate`, like the commands) and, on seeing zero sessions, calls `shutDown()`: it cancels the collector first, releases the wake lock, `stopForeground(REMOVE)`, `cancel(ID)` and `stopSelf()`; `onDestroy` also cancels the notification. This way nothing can publish it again. "New session" with zero sessions: the session is opened asynchronously, so the service promotes itself, stops and `SessionController` starts it again when the session is published (previous behavior, unchanged).
- **Pending verification on a device:** "Exit" with the app in the foreground and in the background: the notification disappears and does not come back.

### D-NOTIF-3 · 2026-10-05 · Without sessions, the app closes (replaces D-NOTIF-1's "open a tab on resume" rule)
- **The user's decision:** "si no hay sesiones activas quiero que se cierre, y ya". When `Sessions.items` ends up empty by any path, `SessionController.publish` stops the service and calls `onEmpty`, which in `SessionManager` is `AppTaskFinisher.finishAll()`: `ActivityManager.appTasks.forEach { finishAndRemoveTask() }`, which closes the task even if the activity is stopped or in recents (it does not depend on a stopped activity collecting a flow, which was the original bug). `shutdownAll()` is still the only path of "Exit" and also removes the task.
- **Process:** `exitProcess` is not called: with the service stopped and no activities Android recovers it; killing it by hand would skip the normal lifecycle. If something were left alive on the device, that would be the last resort.
- `StartupTabRule` keeps only what is necessary: a screen that saw sessions and comes back to (or sees) zero closes by itself; a cold start without sessions does not (it opens the first tab because of RF-13). The "open a tab when resuming with zero sessions" rule is removed. Restarting an ended tab opens the new one before closing the old one, so as not to pass through zero sessions. An ended session is still listed, so it does not count as zero.
- **Pending verification on a device:** Pixel 8, with the app in the foreground, in the background and in recents: "Exit" -> the notification disappears, the task leaves recents; opening from the icon -> one tab, one tap; closing the last tab with the X -> the app closes; restarting an ended tab does not close the app.

### Fifteenth round (OPPO PGEM10, Android 16, 2026-10-05, `master` debug build with #101): closing from the notification
- **Before (0.1.2, on the Pixel 8 and on the user's phone):** "Exit" closed the sessions and stopped the service, but the user saw the notification stay with "0 active sessions" and the app reopen empty; the process was still alive (D-NOTIF-1..3).
- **With #101, on the OPPO PGEM10:** with one session, pressing "Exit" in the notification (app in the foreground) removes the notification, stops the service (no service record or notification), takes the task out of recents and returns to the home screen. With the app in the background, the same. Reopening with the icon: one Alpine tab and a single tap, with the service and the notification back.
- **Not checked yet:** "Exit" with the screen off, closing the last tab with its X (the app must close), entering from recents with the task alive, and the menu's "Exit" entry (#102, pending rebuild on top of #101).
- **Device:** the user's OPPO PGEM10 (earlier confused with the CPH2841, which is not touched). Fixed screen restored when finished.

### D-EXIT-1 · 2026-10-05 · "Exit" entry in the "⋯"/"+" menu (RF-16)
- **Request:** "an Exit entry in the menu to close the app completely". It is the last one in the menu (destructive tint, Lucide's `POWER` glyph, the same ISC set), so it comes out the same in the top bar, the side bar and the rail (they share `NewTabMenu`).
- **Ask or not (`exitStep`, pure tested logic):** with any session running, a normal `IosAlert` (TalkBack reads it as an alert) "Exit UltimateTerminal?" with the number ("N sessions will be closed…", plural) and Cancel/Exit; sessions that already ended do not count. With none running it exits without asking.

### D-EXIT-2 · 2026-10-05 · How it exits: stop the service and `finishAndRemoveTask`, without killing the process
- Exit calls `SessionManager.shutdownAll()`, the only shutdown path (D-NOTIF-1): it closes all sessions, stops the service (notification and wake lock) and, through the `onEmpty` hook (`AppTaskFinisher.finishAll()`), removes the task even if the activity is stopped. Afterwards `MainActivity.exitApp()` also calls `finishAndRemoveTask()`: that hook does not fire if there was no session to close, and in that case it is needed. `FinishWithSessions`/`StartupTabRule` do not clash: they only act on reaching zero sessions and end the same task.
- `Process.killProcess` is **not** used: it is the last resort (it skips lifecycles and does not let Hilt/Room close). Android may leave the process empty in the cache for a while, but with no service, session threads or wake lock it runs nothing; opening the app afterwards creates a new activity and, since there are no sessions, `TerminalViewModel` opens the default tab, like a cold start.
- **Pending verification on a device:** Pixel 8, Exit with and without live sessions, with the screen off and reopening; that no notification or active process is left.

### D-REL-7 · 2026-10-05 · 0.1.3
- At the user's request ("si todo funciona saca la release") `0.1.3` (versionCode 10399) is published with reliable closing: "Exit" in the menu and the fix for "Exit" in the notification (verified on an OPPO PGEM10 with `master`'s debug build). The menu's "Exit" entry has not been tested on a device yet; it is tested with this release on the Pixel 8.

### Sixteenth round (the user's Pixel 8, 2026-10-05, signed `0.1.3` installed on top of `0.1.2`)
- **Menu's "Exit" entry, with a live session:** the last one appears, in red and with the power icon; it asks for confirmation ("Exit UltimateTerminal? 1 session will be closed and its program stopped."); on confirming, the service and the notification disappear, the app returns to the home screen and reopening it is a normal start with one tab (Fedora) and a single tap. The process stays alive for a while (Android recycles it; D-EXIT-2, it is not killed by hand) but no task of the app is left in recents.
- **Not checked:** "Exit" with zero sessions (it exits directly, without an alert), with the screen off and with "keep awake" on; "Exit" from the menu in the tablet's side bar and strip.
- Fixed screen restored when finished.

### D-PASTE-1 · 2026-10-14 · Pasting with the finger
- **The user's finding:** "copy has been implemented in the terminal and not paste". It was true: pasting (`TerminalSessionHost.pasteFromClipboard`, with the emulator's bracketed paste mode) was only reached through physical keyboard shortcuts; on a touch screen there was no button.
- **Decision:** the selection bar (which appears with a long press, which creates a one-cell selection) shows "Copy" and "Paste". "Paste" (`TerminalViewModel.paste`) removes the selection and pastes into the active pane and into those of a broadcast. That bar was chosen because it is what Copy already did and as in Termux (long press → paste), without adding another permanent button. It remains untested on a device; a source-code guard (`PasteButtonGuardTest`) prevents the button from disappearing.

### D-SSH-2 · 2026-10-14 · The server key that is trusted is kept by the app
- **The user's report:** "even if I say yes when it asks whether I trust an SSH host, it never saves it and the following times I have to say yes". The question is OpenSSH's (`StrictHostKeyChecking=ask`, the app has none of its own); answering "yes" should write `~/.ssh/known_hosts`.
- **What was checked:** no part of the app touches `.ssh` or `known_hosts` (neither the cleanup of the temporary key in `/tmp`, nor the installer, nor the backup); a connection's command runs as root with `HOME=/root`; the extractor leaves `/root` with the owner's rwx (Fedora ships it as 0550), and `ssh` would write to `/root/.ssh`. **It could not be reproduced:** with no phone connected that day, and an attempt to build `proot` and to use Fedora 44 in a container was not feasible (Fedora 44 does not package `proot`, and the mirrors timed out). The real cause remains **unproven**.
- **Decision:** that the app owns the file: a `ssh-known-hosts` folder in private storage (outside any distro, created by the app, so it is always writable), bound in the guest as `/ut-ssh`, and `-o UserKnownHostsFile="/ut-ssh/known_hosts /root/.ssh/known_hosts"` (ssh adds to the first and reads both, so what someone trusted by hand in the distro is still valid). Additionally `-o UpdateHostKeys=no`: ssh only appends lines, without the temporary file + `link` + `rename` that `proot --link2symlink` has to emulate. The question is not disabled (`StrictHostKeyChecking` stays at its default): it is asked once and remembered.
- **Limits:** it only covers the connections the app launches from "SSH servers". An `ssh` typed by hand in a distro tab still uses its user's `~/.ssh`. **It has to be tested on a device:** connecting twice to a new host (the second must not ask) and looking at `known_hosts` in the app's folder. If the question keeps coming back, I need the output of `ssh -v` and `ls -la /root/.ssh`.

### D-REL-8 · 2026-10-14 · 0.1.4
- At the user's request `0.1.4` (versionCode 10499) is published with the Paste button and the SSH server key that the app remembers. Neither of the two is tested on a device yet (the second could not even be reproduced: D-SSH-2); the changelog says so.

### D-SEL-1 · 2026-10-14 · Selection: a compact bar, handles and word/line/all, drawn by the app
- **Report:** "copy and paste is so big it is useless and options are missing"; select all and selection handles were absent. Copy and Paste were two fixed 120 dp buttons in the top-right corner, over the text.
- **Why not the system's:** Android's native toolbar and handles belong to `TextView`/`EditText`; the terminal is a canvas the system knows nothing about, so it cannot supply them. Using a floating `ActionMode` for the toolbar was considered and left out: it needs a `View` of its own and its position and size are not ours to control.
- **Decision:** `SelectionToolbar` (a 44 dp pill, wrapping its content) sits above the selection's first row, or under its last one when there is no room above, and offers Copy, Paste, Select all and Select line. A long press selects the word under the finger (`WordBounds`: a run of non-blank characters, so paths and URLs come whole) and dragging stretches it. Two circular handles at the ends are dragged by `selectionHandles`, which runs first in the pointer chain and only takes a touch that lands within 28 dp of a handle. Select all covers the screen and the whole scrollback.
- **Limit:** word bounds treat one column as one character, which is approximate next to wide characters. Tab/drag details and handle feel are not validated on a device.

### D-CLOSE-1 · 2026-10-14 · A tab closes when its shell ends; the app stops with the last one
- **Request:** "I want the tab to close if I close the processes, and if it is the last tab the app should stop".
- **Decision:** `SessionController` closes a session when its shell ends, through the same `close` path as the X (so a pane leaves its sibling the space and the last session triggers `onEmpty`, which already stops the service and removes the task, D-NOTIF-3). The rule is `AutoClose.shouldClose(status, livedMillis)`: it closes unless the shell failed to start (`START_FAILED`) or ended with a non-zero status within 2 s of starting. That exception is on purpose: a `127`/`126` right after launch, or a proot that cannot start, would otherwise make the tab vanish with nobody to read why; those keep the "Session ended" card and its restart.
- **Consequence:** the "Session ended, tap to restart" card now only shows for failed starts. A shell that ends with an error after more than 2 s (an `exit 1`) closes like any other.
- **Not validated on a device:** closing with `exit` in a tab, in a pane of a split tab, and in the last tab.


### D-LANG-1 · 2026-10-14 · Choosing the app's language
- **Request:** make the app multilingual if it is not yet. **State found:** English (default) and Spanish already ship in full (`values` and `values-es` have the same strings; the four that differ are brand names marked `translatable=false`), and the build already generates `locales_config.xml` from the resources (`generateLocaleConfig`), so Android 13+ lists the app in its per-app language settings. What was missing was a way to choose from inside the app, and anything for Android 8–12 (`minSdk` 26).
- **Decision:** `AppLanguage` (System, English, Spanish; pure, with tests) and `AppLocale`. From Android 13 it uses `LocaleManager.applicationLocales`, so the system and the app agree. Before that the choice is kept in a small `SharedPreferences` file and applied in `MainActivity.attachBaseContext`; the activity is recreated on change. A new row group in Settings > Terminal lists the options with their own-language names.
- **Not in the backup:** the language is a per-device preference (the system's own per-app language is not restored by a backup either), so it is not part of `SettingsDto`; this also leaves `data.backup`'s 100 % untouched.
- **`bundle { language { enableSplit = false } }`:** Lint's `AppBundleLocaleChanges` fails on a runtime locale change unless a bundle does not split by language. The app is distributed as APKs, so this is configuration, not a suppression.
- **Adding a language** is a `values-xx/strings.xml` and one entry in `AppLanguage`; the per-app list is generated from the resources.
- **Not validated on a device:** the change on Android 13+ (and the system settings entry) and the recreation on Android 8–12.

### D-REL-9 · 2026-10-14 · 0.1.5
- At the user's request ("si hay que implementarlo saca release") `0.1.5` (versionCode 10599) is published with the compact selection bar and handles, tabs that close with their shell, and the language setting. None of the three is tested on a device yet; the changelog says so.


## D-PK · Passphrase-protected SSH keys, and hardware keys (feat/protected-ssh-keys)

**Request:** keys stored in the app must be protectable "with a PIN or a hardware key", so that a lost
phone does not hand over usable keys; first a passphrase, then research on FIDO2 / YubiKey.

### D-PK-1 · 2026-10-26 · A protected key is stored as OpenSSH's own encrypted `openssh-key-v1`
- **Decision:** a key with a passphrase is written as `ssh-keygen -N` writes it: cipher `aes256-ctr`, KDF
  `bcrypt` (16 rounds, 16-byte random salt), the public part in the clear. It is the same text the
  Keystore layer (D-T14-4) seals, so at rest there are two independent layers: the passphrase, and the
  Keystore key. Reading accepts `aes128/192/256-ctr` and `aes128/192/256-cbc`, so keys made elsewhere
  import without conversion. The AEAD ciphers (`*-gcm@openssh.com`, `chacha20-poly1305@openssh.com`)
  are refused as unsupported: `ssh-keygen` only uses them if asked with `-Z`. At most 1024 rounds are
  accepted, so a hostile file cannot hang the phone.
- **Reason:** the format is the standard, the user can export a protected key and use it anywhere,
  and an imported protected key does not need re-encoding by a format of ours. A key made by the app
  was checked to open with `ssh-keygen -y -P`, and the keys `ssh-keygen` makes open in the app.
- **A wrong passphrase** is told apart from a damaged file by the two check words of the format
  (they only match when the decryption worked): `WrongPassphrase` against `CorruptKey`.
- **A "PIN":** the minimum is 4 characters, so a four-digit PIN is allowed, but the dialogs say that a
  long phrase is much stronger. A short secret falls to an offline attack by whoever has the encrypted
  file *and* gets past the Keystore (see D-PK-6); the Keystore layer is what makes that hard.
- **Alternatives:** an own format with AES-GCM and PBKDF2 (non-standard, not exportable, and a weaker KDF
  than `bcrypt_pbkdf` for a short secret on a phone); Argon2 (not in the platform, a new dependency).

### D-PK-2 · 2026-10-26 · `bcrypt_pbkdf` written in Kotlin, no new dependency
- **Decision:** `BcryptPbkdf` (+ `BlowfishState`, `BlowfishTables`), about 200 lines, and `AES/CTR|CBC` from
  the platform. No BouncyCastle (the reason in D-T14-2 stands: 2 MB and another license).
- **The Blowfish constants** are the first 1042 words of the hexadecimal digits of pi. They were not
  typed from memory: a script computes pi with big integers (Machin's formula) and writes them as text,
  and the first, the 19th and the last word were checked against the published values.
- **Checked** against OpenBSD's reference vector (`password`/`salt`/4 rounds), against three keys written by
  OpenSSH 10.2 (`aes256-ctr`, `aes256-cbc`, a non-ASCII passphrase; they are in the tests as base64
  without the frame, with the passphrase in the open: throwaway keys made for this, worth nothing), and, when
  `ssh-keygen` is installed (CI and developer machines), live in both directions for every supported
  cipher and for RSA.
- **Cost:** the default 16 rounds run in a fraction of a second on a PC; on a phone it was **not timed**.
  It runs on the CPU dispatcher, never on the main thread.
- **Wiping:** the derived key, the decrypted section, the SHA-512 digests and the Blowfish state are zeroed
  after use, and the passphrase handed around the domain is a `ByteArray` the caller wipes. A Compose text field
  holds a `String`, which cannot be wiped: the UI copies it to a `CharArray` at the last moment and the
  ViewModels wipe that copy. This is "where practical", not a guarantee against a memory dump.

### D-PK-3 · 2026-10-26 · Where the passphrase can be set, changed or removed
- **Generate:** an optional passphrase and its confirmation. **Import:** a field "its passphrase (if any)";
  a protected OpenSSH key is checked with it and stays protected by the same one. An unprotected key
  ignores the field (protect it afterwards). **Existing keys:** the key's action sheet offers "Set",
  "Change" or "Remove the passphrase". Changing and removing need the current one; a wrong current one
  changes nothing. Keys stored before this change are unprotected and keep working as they were.
- **Rewriting a key** (`SshKeyService.changePassphrase`) reads the stored text, decrypts it, encodes it
  again with a fresh salt and replaces both files. The `.key` is written before the `.meta`; whether a key is
  protected is decided by the stored text itself, not by the flag in the `.meta`, so an interrupted
  rewrite cannot leave a key that is shown as unprotected and then fails.
- **Fields are not saved with the instance state** (`remember`, not `rememberSaveable`): a passphrase does not
  go into a `Bundle`. Rotating the screen with a dialog open clears them.

### D-PK-4 · 2026-10-26 · Connecting: ask, decrypt in the app, optionally keep unlocked for 15 minutes
- **Decision:** `SshConnector.prepare` fails with `PassphraseRequired` when the key is protected and no
  passphrase is remembered; the hosts screen then asks for it (a sheet with a secure field and a switch
  "Keep it unlocked for 15 minutes") and calls `prepare` again. A wrong one comes back as `WrongPassphrase`
  and the sheet shows it. The connector decrypts the key **in the app** and writes the *plain* key to the
  same 0600 temporary file as before (D-T14-5); `ssh` never sees a passphrase.
- **Keep unlocked:** `PassphraseCache`, in memory only, keyed by the key's alias, 15 minutes from when it was
  typed (not extended by use), zeroed when it expires, is forgotten, or when the key is changed or deleted, and lost
  when the process ends. A remembered passphrase that stops fitting is dropped at the first failure.
- **Why not let `ssh` ask in the terminal** (write the encrypted key and let OpenSSH prompt): it would keep
  the key encrypted on disk while the session lasts, which is better, but the passphrase would be typed in
  the terminal (the keyboard policy, the extra keys row, scrollback, shell history of other programs) and the
  app could not offer "keep unlocked". The cleaner fix for both is an in-app `ssh-agent` (D-PK-8), so
  this stays as the next step.
- **Biometrics (not done):** the app does not use `BiometricPrompt`, and the Keystore key here is not bound
  to user authentication (D-T14-4 chose one-tap connections). Binding a Keystore key to biometrics to
  wrap the passphrase needs `androidx.biometric` (Apache-2.0, no Play Services) and a device to try it on;
  it was left out rather than shipped untested. It would be an opt-in "unlock with fingerprint" next to
  "keep unlocked".

### D-PK-5 · 2026-10-26 · Backups, logs and Android's own backup
- A protected key goes into the configuration backup as the same encrypted text with `passphraseProtected:
  true`; a backup from before this change has no such field and reads as unprotected. A backup that includes SSH
  keys stays optionally encrypted with its own password (T15); a protected key is therefore covered by two
  secrets in an encrypted backup, and by one (its passphrase) in a plain one.
- `allowBackup` is `false` and `data_extraction_rules.xml` and `backup_rules.xml` exclude files, databases and
  shared preferences from cloud backup and device transfer; nothing was changed there. The passphrase is
  never written to disk, preferences, logs or the saved instance state. No error type carries it, and
  `KeyUnlock.toString` omits it.

### D-PK-6 · 2026-10-26 · Threat model with a passphrase
- **Now also protects against:** a lost or borrowed phone that is unlocked (or whose Keystore is used by
  a rooted attacker): the stored key is still encrypted by the passphrase, and the 15-minute window is the
  only time it is in memory. A stolen storage dump is covered twice.
- **Still does not protect against:** the 0600 temporary file with the *decrypted* key while a session is
  open (D-T14-5/6; the in-app agent is the way out); another program inside the distro reading it; a short
  PIN against someone who has both the encrypted file and the Keystore; malware with root reading the
  process memory while the key is unlocked; a keyboard that logs what is typed.
- **A brute-force limit** on wrong attempts is not implemented: each attempt costs one `bcrypt_pbkdf`, which
  slows a person at the screen but is not a lockout.

### D-PK-7 · 2026-10-26 · Hardware security keys: research and outcome
**Outcome: not implemented in this change. Documented here as the follow-up, with the design.**

- **What the app actually runs:** the app has no SSH library. It starts OpenSSH's `ssh` inside proot (D-T14-1).
  So "does our SSH library support `sk-ssh-ed25519@openssh.com`" becomes "can `ssh` in proot reach the key":
  OpenSSH (8.2 or later, on the server too) supports `sk-ssh-ed25519@openssh.com` and
  `sk-ecdsa-sha2-nistp256@openssh.com`, but its client talks to the key through a *security key provider*
  (the built-in USB HID one, or `libfido2`), which opens `/dev/hidraw*`. Inside proot on Android there is no
  such device, and Android gives USB devices to the *app* as a file descriptor through `UsbManager`, not to the
  processes proot starts. NFC is only available to the app. So `ssh` inside the distro cannot sign with a
  YubiKey by itself, and no setting fixes that.
- **Android's FIDO2 APIs cannot do it:** Credential Manager and Play Services' `Fido2ApiClient` are WebAuthn
  for web origins: the relying party id is tied to the caller's origin and the client data is a JSON
  structure the platform hashes; OpenSSH needs rp id `ssh:` and its own challenge hash. Play Services is
  proprietary and forbidden here anyway (the free-software rules, F-Droid). This is why apps that do this talk to the key
  themselves.
- **What can talk to the key: Yubico's `yubikit-android`** (checked 2026-10-26): Apache-2.0 (compatible with
  GPL-3.0-or-later), on Maven Central, no Play Services, actively maintained (3.x line; commits in
  October 2026), USB and NFC, API 21+. Its `fido` module exposes the raw CTAP2 session (`Ctap2Session.makeCredential`
  / `getAssertions` with an arbitrary rp id and client data hash, plus the PIN/UV protocols), which is what `ssh-sk`
  needs; it also has `piv` and `openpgp` modules. Its transitive dependencies and the F-Droid scanner result
  must be checked when it is added (`./gradlew dependencies`, `THIRD_PARTY_NOTICES.md`).
- **What `ssh` would need from the app:** an **ssh-agent** the app serves. The app listens on a Unix
  socket inside a folder bound into the guest (the same trick as `/ut-ssh` for `known_hosts`, D-T14-10), the
  host entry passes `-o IdentityAgent=...`, and the agent answers `REQUEST_IDENTITIES` and `SIGN_REQUEST` by
  talking to the key through yubikit. Every route below needs this agent; none can work without it.
- **Routes, in the order I would build them:**
  1. **FIDO2 `sk-ssh-ed25519` / `sk-ecdsa-sha2-nistp256`.** Registration is `makeCredential` (rp `ssh:`,
     Ed25519 where the key supports it, otherwise ES256; a non-resident credential whose handle goes in the stored key file,
     or a resident one). Signing is `getAssertion` with the SHA-256 of the data OpenSSH sends as the
     client data hash; the SSH signature is the raw signature plus the `flags` byte and the 4-byte counter from the authenticator
     data. A touch is needed for every signature, and the PIN if the credential asks. Needs OpenSSH 8.2+ on every server.
  2. **PIV** (yubikit `piv`): ECDSA P-256/P-384 or RSA keys in a slot, with a PIN (and touch) policy; the
     public key is a standard `ecdsa-sha2-nistp256` or `ssh-rsa` one, so **any server accepts it**, old or new.
     The agent hashes and signs through PIV. The app does not support ECDSA keys yet (D-T14-2), so the public-key
     line and the signature conversion (DER to SSH `r`,`s`) are new code.
  3. **OpenPGP** (yubikit `openpgp`, or the OpenKeychain route that ConnectBot uses): an authentication
     subkey signs; Ed25519 on recent keys. Similar to PIV, with the GnuPG key management on top.
- **Why it was left out of this change** (rather than shipped half-done): it cannot be tested without a real
  YubiKey and a phone, which this change did not have, and the code would be the sign-in path of a trip
  nobody could rehearse; it needs a long-lived agent service with a foreground USB/NFC flow (NFC needs a tap per
  signature while the app is in the foreground and `ssh` blocks waiting); and a feature this size
  should be reviewed on its own. The passphrase work above stands without it.
- **The agent also pays back on its own:** with an in-app agent, protected software keys could be signed
  without ever writing the decrypted key to the rootfs (the improvement D-T14-5 and D-PK-4 point at).
  A reasonable order is: agent for software keys first, then FIDO2 (or PIV if servers older than 8.2 matter).

### D-PK-8 · 2026-10-26 · What is still to do for hardware keys
Agent service and socket binding; `yubikit` dependencies and notices; registration screen (touch/PIN prompts,
NFC and USB permission); key type `sk-*` in `SshKeyType` and the stored key handle; the signature
assembly; a 100 % tested pure core (agent protocol, signature formatting) with the device flow tested by hand.

### D-PK: what has NOT been validated
- Anything on a device: the dialogs, the `bcrypt_pbkdf` time on a low-end phone (it is a pure-JVM loop;
  if it is slow, lower the default rounds or move to a native implementation), connecting with a protected
  key in proot, and the 15-minute window.
- The fields being cleared on rotation (by design), TalkBack on the new dialogs and the 48 dp targets.
- `ssh` with a key written by `SshConnector` after decrypting a protected one: it is the same plain
  `openssh-key-v1` text as before; the interop test checks that `ssh-keygen` reads protected files the app
  writes and the app reads protected files `ssh-keygen` writes.
- Hardware keys: nothing, see D-PK-7.

