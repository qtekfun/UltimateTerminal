# UltimateTerminal — Specification (SPEC)

Document version: 0.1 · Status: draft for the MVP implementation

Points marked **(DEFAULT)** are default decisions not confirmed by the user: review them before implementing.

## 1. Goal

A terminal for Android designed for **server maintainers** and the spiritual successor of Termux: a modern UI, **truly adaptive on tablets** (it fills the whole screen and resizes with multi-window, the keyboard and foldables) and complete **WSL-style** Linux environments through `proot`. Terminal only: no graphical environment. It must be useful to other people and be published on F-Droid.

### Problems it solves (Termux)
1. The UI is outdated.
2. On tablets it does not resize well and does not show full screen.
3. Managing environments, backups and changing device is manual and fragile.

## 2. Scope and assumptions

| Topic | Decision |
|---|---|
| Platform | Android, `minSdk` 26. **Tablet and phone on a par**; adaptive design from the MVP |
| `targetSdk` | **28**, like Termux. Android 10+ forbids running binaries from the app's storage with `targetSdk` ≥ 29. Consequence: **not suitable for Google Play** (already ruled out by policy) |
| Distribution | F-Droid and GitHub Releases |
| Terminal emulator | Reuse Termux's `terminal-emulator` (Apache-2.0, PTY through JNI), with our own view and UI in Compose |
| Userland | **proot distros only**, WSL-style. No own bootstrap/packages like `pkg` and no `com.termux` prefix |
| MVP distros | Debian, Ubuntu and Alpine; **Fedora is added as T24** (user request: it is their usual distro). Official rootfs images downloaded at install time, verified with SHA-256. Arch, in the backlog |
| proot | **Built from source** in the build (NDK; proot and talloc sources pinned by hash), packaged as `.so`. Reproducible and F-Droid compatible |
| Graphical environment | None. No X11, no Wayland, no VNC |
| License | GPL-3.0-or-later |
| Languages | English and Spanish; architecture ready for more |
| Accounts/servers | Everything local. There is no backend and no account of our own |
| `applicationId` | `com.qtekfun.ultimateterminal` |

## 3. Functional requirements (MVP)

### RF-01 Terminal and emulation
- Full xterm emulation (256/truecolor colors, unicode and emoji, mouse, bracketed paste, alternate screen).
- **Criteria:** `vim`, `tmux`, `htop`, `less`, `nano` and `mc` look and respond correctly. The scrollback is configurable (10 000 lines by default).

### RF-02 Multitab and panes
- Several tabs, renamable and reorderable; each one is an independent session of a distro.
- On wide screens, **split panes** (several terminals at once), in addition to tabs.
- **Criteria:** creating, closing, moving and switching tabs does not interrupt the other processes. Closing a session with running processes asks for confirmation.

### RF-03 Adaptive screen (main requirement)
- Edge-to-edge. The terminal area **resizes** when the window changes (multi-window, split screen, floating window, foldables, rotation) and when the keyboard is shown/hidden, and the PTY receives the new size (`SIGWINCH`).
- Layout by window size classes (`WindowSizeClass`): a side or top tab bar depending on the width.
- **Criteria:**
  - On a tablet the terminal takes 100 % of the available area, with no bands or gaps.
  - After resizing, `stty size` matches what is seen.
  - Rotating or entering multi-window does not close or restart the sessions.

### RF-04 Distros (WSL-style)
- Install, list, **rename, duplicate** and delete distros from the app. Several installed at once.
- Download of the official rootfs with **SHA-256 verification**, progress, resume and retry.
- Configurable default user (root or a normal user) and default distro.
- Inside the distro `apt`/`apk`, `ssh`, `nmap`, `python`, etc. work.
- **Criteria:** installing Debian from scratch leads to a working prompt with no manual steps; a network failure half way does not leave a corrupt distro.

### RF-05 Access to the device's files
- Bind-mount of the shared storage (`/sdcard`, Downloads) inside the distro, e.g. at `~/storage`, to use `cp`/`mv` and pull logs.
- The storage permission is requested only when the user turns the feature on.
- **Criteria:** `cp /var/log/syslog ~/storage/downloads/` shows up in the device's Downloads folder.
- A SAF provider (the distro visible in the Files app) is out of the MVP (see section 4).

### RF-06 Backups and migration
- Export **one distro** (the complete rootfs), **only the configuration** or **everything** to a `.tar.zst` file.
- The **app configuration** includes every setting: theme and OLED mode, fonts, profiles, color schemes, extra keys, shortcuts, saved layouts, SSH hosts and keys, default distro, etc. It is exported as a small file independent of the rootfs images, to leave a new device like the previous one at a given moment.
- Device-specific paths and values (e.g. the storage permission) are requested again on restore, not copied.
- **Optional encryption** AES-256-GCM with a key derived (PBKDF2) from a user password, as in UltimateDeck: the Keystore key cannot leave the device.
- Restore on a new device, also from the welcome screen.
- **Criteria:**
  - Exporting only the configuration on one device and restoring it on another leaves the app with the same look, shortcuts, profiles and layouts, with no manual steps.
  - Exporting and restoring keeps the rootfs's permissions, owners, symbolic links and executable bits.
  - An encrypted backup with the wrong password fails clearly and writes nothing.

### RF-07 Session persistence
- A **foreground service** with a persistent notification that keeps sessions alive with the app in the background.
- Optional wake lock. Guided notice about battery optimization.
- **Criteria:** an `ssh` or a long task survives switching apps and turning the screen off.
- Restoring tabs after a real close (not processes): **(DEFAULT) backlog**.

### RF-16 Exit the app
- Last entry of the "⋯"/"+" menu (top bar, sidebar and rail): **Exit**, with a power icon. With live sessions it asks to confirm ("Exit UltimateTerminal?", with the number of sessions that will be closed); with none, it exits directly.
- Exit closes every session (kills their processes), stops the foreground service (removes the notification and releases the wake lock) and closes the task (`finishAndRemoveTask`).
- **Criteria:** after Exit no service or notification remains; opening the app afterwards is a normal start (one new tab).

### RF-08 Input
- **Extra-keys row** (Esc, Tab, Ctrl, Alt, arrows, etc.), configurable, with sticky Ctrl/Alt. **It is only shown while the on-screen keyboard is visible**: when the keyboard is hidden it disappears and gives its space back to the terminal (a "hide with the keyboard" option, on by default, for those who prefer to always see it).
- **Full hardware keyboard:** F1–F12, Ctrl/Alt combinations, app shortcuts (**(DEFAULT)** `Ctrl+Shift+T` new tab, `Alt+n` switch tab), mouse and wheel.
- **On-screen keyboard type:** a setting in Settings > Keyboard with three options: **Normal** (**(DEFAULT)**, a text field with no suggestions and no password variation, so each phone shows its usual keyboard and not the "secure keyboard" of some manufacturers), **Compatible** (a visible-password field, which some keyboards need so they do not autocorrect in a terminal) and **Raw** (no input type, like Termux's `input-type` option: only key presses). It is saved and goes in the configuration backup; changing it restarts the keyboard input. (Still to be checked on a device.)
- Copy/paste, touch selection with handles, pinch zoom.

### RF-09 SSH host manager
- A list of saved servers (name, host, port, user, key) that opens a tab with `ssh` already started in the chosen distro.
- Key management: generate, import and export. **Private keys are encrypted at rest with the Android Keystore** and never go to logs.
- **Criteria:** connecting to a saved host takes one tap; the private key does not appear in plain text outside the distro.

### RF-10 Themes, OLED mode and fonts
- Light, dark, system theme and **OLED mode**: pure black (#000000) in the terminal background and the interface to save battery on OLED screens. It is turned on separately from the dark theme.
- Terminal color schemes (including Solarized, Dracula, etc.), editable and with import/export.
- Bundled monospaced fonts (**(DEFAULT)** a free Nerd Font) and an adjustable size.

### RF-12 Terminator-style customization
- **Profiles** (**(DEFAULT)**): a named set of color scheme, font, size, scrollback, distro, user and startup command. Each tab or pane can use a different one.
- **Saved layouts:** an arrangement of tabs and panes (horizontal/vertical splits with proportions) saved with a name and reopened with one tap, each pane with its profile and its startup command (e.g. 3 panes opened to three servers).
- Split a pane horizontally or vertically, resize by dragging the dividers and reorder panes.
- **Fully configurable shortcuts** (split, close, switch pane, full screen of a pane, etc.).
- **Broadcast to several panes** (**(DEFAULT)**): what is typed in one pane is sent to all those in the group, useful to administer several servers.
- *Status (T12b):* of the profile, the distro, the user, the scrollback and the startup command are applied per pane today; the scheme, the font and the size are saved but not yet applied per pane (D-T12b-9). The tab numbers (`Alt+1` to `9`) cannot be reassigned yet.
- **Criteria:** a saved layout is restored identical (structure, profiles and commands); shortcuts and profiles are included in the configuration backup (RF-06).

### RF-11 Settings and internationalization
- English and Spanish; follows the system language.
- **A Settings screen that is always reachable:** a permanent icon (⚙) in the tab bar and an entry in the "+" menu. That menu opens with a visible button (⋯, "More options") next to the "+" in the top bar, the sidebar and the rail; a long press on "+" is kept as a shortcut. It cannot depend on opening another screen first.
- Sections: **Appearance** (theme, OLED mode, scheme, font, size, margins, cursor; RF-10 and T12c), **Terminal** (scrollback, bell), **Keyboard** (extra-key rows, hide with the keyboard, shortcuts), **Sessions** (wake lock, background permission), **Distros** (install, default, compatibility mode), **Storage** (`~/storage`), **Network** (fallback DNS), **Backups** (RF-06) and **About** (version, licenses and credits).
- **Criteria:** all the MVP's settings are changed from that screen, without editing files or using adb; they are kept across restarts and travel in the configuration backup (RF-06).

### RF-13 Start and first run
- When the app opens, the first tab opens the **default distro** (proot) if it is ready; with the app fully closed and reopened the same happens.
- If no distro is installed, the **initial setup** (RF-15) is shown instead of the Android shell.
- Tabs are named after their distro (e.g. "Alpine"), not "Shell N", unless the user renames them.
- **Criterion:** with a distro installed, closing the app and opening it again leaves a prompt of that distro in the first tab. (Checked on a Pixel 8; the tab name, pending.)

### RF-13b With no sessions, the app closes
- When the number of sessions reaches **zero by any path** (the "Exit" action of the notification, the last tab closed with its X, the exit menu), the app closes completely: the service is stopped and the notification goes away, and the task is closed and removed even if the activity is stopped or in recents. A session whose process ended stays listed until it is closed, and does not count as zero.
- Opening the app (icon, recents, notification) is always a normal start: a tab of the default distro (RF-13) or the initial setup (RF-15). There is never an open app with zero tabs, and an old screen that comes back without sessions closes itself without opening tabs.

### RF-14 iOS-style design
The user tried the app on a Pixel 8 and found it ugly ("it has to be redesigned in the iOS style"). The app's own interface (everything but the terminal area) moves to an **iOS-inspired** design, on a common component system (`ui/ios`):
- **Large titles that collapse** on scroll, with a small navigation bar that takes them in.
- **Translucent bars with blur** of the content passing underneath (from Android 12; before that, an almost solid bar with the same look).
- **Grouped lists** with rounded corners on a grey background, thin indented separators, chevrons, values and rows with a switch or a destructive action.
- **Switches, segmented controls, search field and buttons** (filled, tinted and plain) in the iOS style.
- **Modal sheets with a handle** (half and full height, draggable) instead of Material dialogs; alerts and action sheets; context menus.
- **iOS typography and spacing** (Large Title 34 to Caption 12 scale, 8 pt grid), **spring animations** and **haptic response**; no floating buttons (FAB).
- **Light, dark and OLED consistent with the T12 color schemes**: the tint comes from the chosen scheme's blue and is adjusted to be legible.
- **Accessibility:** TalkBack with roles and states, touch targets of at least 48 dp, the system font scale and a text contrast of at least 4.5:1 in all themes and schemes.

**What is NOT done:** **SF Pro, SF Symbols or any Apple asset** are not used (their license does not allow it). The typography is **Inter** (SIL OFL-1.1) and the icons are **Lucide** (ISC); both are credited in `THIRD_PARTY_NOTICES.md`. It is an inspired design, not a branded copy, and no affiliation with Apple is claimed. The terminal area (monospaced text, scheme colors) does not change its look.

**Criteria:** the component catalog (only in debug builds) shows each one in light, dark and OLED; the redesigned screens (T22b and T22c) only use `ui/ios` components; no text is fixed inside a component.

### RF-15 Initial setup (first run)
User request: "if there is no distro, show an initial setup straight away to install one, so the experience is consistent". Before, a new install opened an Android shell with no distro and the user had to discover Settings > Distributions > Manage > +.
- When the app opens **with no ready distro and no open tabs**, instead of the Android shell tab a full-screen welcome screen is shown, in iOS style (`ui/ios`): a short explanation, a choice of distribution (Alpine —the smallest download, preselected and recommended—, Debian, Ubuntu and Fedora, those of the catalog), a name and default user (`root`), a note on network and size, and the main **Install** button.
- **Restore from a backup** reuses the Settings > Backups flow (system picker, password if encrypted, progress and cancellation).
- The installation is **the same** as Distributions' (same ViewModel and installer, with progress, cancellation and errors). A failure is shown with its reason and the form stays there to **retry or pick another distro**: there is no dead end.
- When it ends, the new distro is the **default**, the setup closes and the first tab opens it (RF-13).
- "Skip, use the Android shell for now" (a text button, not the default path) opens the Android shell; it is only remembered during the session: with no distro, the setup is shown again on the next start.
- The "Keep sessions alive" notice is not shown over the setup: it appears when the first session starts.
- **Criterion:** on a new install the welcome is seen, not an Android shell; installing Alpine from it ends at an Alpine prompt in the first tab. (Still to be checked on a device.)

### RF-16 Dynamic tab sidebar
User request (0.1.1): "I want the sidebar in landscape to shrink when I tap the terminal, that is, to have a dynamic size". On wide windows (RF-03, from 600 dp) the tab column took a fixed width and, e.g. in half screen, left the terminal with 49 columns. Now it has two states:
- **Expanded** (192 dp): the usual look (pills with a name, "+", ⚙) plus a button with a chevron to collapse it.
- **Collapsed** (a 56 dp strip, with 48 dp touch targets): the initial of each tab (the active one highlighted), "+" and ⚙, and a chevron on top to expand it. Tapping the strip (or its chevron) expands it; a long press on a tab asks "Close <name>?", the same as in the full bar (that is where the rename/close menu and the drag to reorder are).
- **Automatic:** tapping a terminal pane collapses the bar; the state only changes on a tap, it never collapses by itself on a timer. It is shown expanded when the app opens. In the top bar (narrow windows) nothing changes.
- **Setting** in Settings > Terminal: "Collapse the sidebar when using the terminal" (on by default); off = always expanded, and no control to collapse it is shown. It travels in the configuration backup (RF-06) with a versioned field.
- **The PTY** is resized **once** per change, to the final width (the terminal area is computed with the target width and only its drawing slides during the animation), without losing the scrollback or the keyboard focus. With the system's "Remove animations" the change is instant.
- **Accessibility:** the strip and its tabs say name, position ("tab 2 of 3"), state and selected; the expand/collapse control has a label, and each tab offers the custom action "Expand/Collapse the sidebar".
- **Criteria:** tapping the terminal in landscape on a tablet or a wide phone collapses the bar and the terminal gains columns (`stty size`); tapping the strip expands it and the terminal loses them; the keyboard does not close while animating; in portrait on the Pixel 8 everything stays the same. (Still to be checked on a device.)

### RF-17 Long press on a tab
User request (0.1.2): "if I long-press a tab in the bar, show the close confirmation". A long press on a tab (full bar or strip) released without moving shows "Close <name>?" with Close/Cancel, even if its process has already ended; if a process is alive, the notice that it will be stopped is added. A long press followed by a drag still reorders (and does not ask). Tapping selects and a double tap renames, as before. TalkBack keeps its actions (rename, close, move). (Still to be checked on a device.)

## 4. Out of scope (MVP)
- Any graphical environment (X11, Wayland, VNC).
- Own bootstrap and package manager like `pkg`; Termux plugins or add-ons.
- A SAF documents provider (the distro in the Files app). Backlog.
- Automatic backups to a destination (SAF/SFTP). Backlog.
- Restoring tabs and scrollback after a close. Backlog.
- More distros (Arch, Kali...). Backlog.
- Device root access, `chroot` or real containers.
- Cloud sync, an own account, telemetry or analytics.
- Any Google service.

## 5. Data model and storage

- **Profile:** `{ id, name, scheme, font, size, scrollback, distroId, user, startup command }`. **Layout:** a tree of panes with proportions, profile and command per pane. The complete configuration is serialized to a versioned format (**(DEFAULT)** JSON) for the backup.
- **Distro:** `{ id, name, type (debian/ubuntu/alpine), version, rootfs path, default user, size, date }`. The rootfs lives in the app's private storage.
- **SSH host:** `{ id, name, host, port, user, keyId, distroId }`. **Tabs/panes:** UI state in memory; the service is the source of truth for live sessions.
- Metadata in Room; rootfs images are file-system directories. A versioned schema with tested migrations.
- Backups: tar with `zstd` preserving POSIX metadata (see risks).

## 6. Non-functional requirements
- **Performance:** cold start to a prompt < 1.5 s with a distro already installed; smooth text output (e.g. `cat` of a large file without blocking the UI).
- **Privacy:** no telemetry or third-party services. The app only uses the network when the user asks (rootfs download, whatever they run in the terminal).
- **Security:** keys encrypted with the Keystore; `allowBackup` disabled (the own backup is used); no logs of terminal content or of secrets. SHA-256 verification of everything downloaded.
- **Accessibility:** TalkBack in the app's UI (the terminal itself exposes text), touch targets ≥ 48 dp, font scaling, enough contrast.
- **Robustness:** no loss of distro data on closes, lack of space or network failures; long operations resumable or transactional.

## 7. Quality and CI
- **GitHub Actions:** on every PR, build (including proot's NDK build) + detekt + ktlint + Android Lint + unit tests + Kover.
- **Coverage (Kover):** ≥85 % overall in `domain`/`data`; **100 % in rootfs/download verification and in the backup format (encryption, restore)**; generated code, `@Preview` and pure Compose UI excluded.
- **Integration tests on a device/emulator:** install a distro, run a command, resize and check `stty size`, export and restore.
- A weekly grouped **Dependabot**; a **license check** that fails on non-free dependencies or Play Services; Gradle dependency verification.
- **SemVer** versioning, Conventional Commits. Reproducible builds and own signing as in UltimateDeck (see `RELEASING.md` when it exists).

## 8. Known risks
1. **Building proot in the build and having it work on modern Android** (`seccomp`, `ptrace`, `/proc`, `link2symlink`). It is the highest-risk task; prototype first (Phase 0).
2. **Android 12+ and the "phantom process killer":** the system may kill child processes (a limit of ~32) even with a foreground service. Document the developer setting and mitigate as far as possible. Android 14+ requires declaring the foreground service type.
3. **`targetSdk` 28 and its obsolescence:** Google may tighten the installation of apps with a low `targetSdk`. Watch it; it is the reason for not publishing on Play.
4. **Own terminal view in Compose:** drawing performance (Canvas), text selection, IME and mouse. Prototype before building the rest.
5. **Resizing on tablets:** validate `SIGWINCH`/`TIOCSWINSZ` with multi-window and keyboard (it is the problem that motivates the project).
6. **Rootfs backup:** preserve owners, permissions, symlinks and special files without root; size and times of large rootfs images.
7. **Distribution of third-party rootfs images:** they are only downloaded from official sources and with a hash; they are not redistributed in the APK.

## 9. Open decisions (to confirm during implementation)
- The concrete font/Nerd Font and its license.
- Default keyboard shortcuts.
- URL and exact verification scheme of each rootfs (hash vs signature).
- The effective minimum Android version after testing proot on API 26–28.
- Whether "restore tabs" enters the MVP (by default no).
