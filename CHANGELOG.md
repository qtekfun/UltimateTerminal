<!--
SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Changelog

All notable changes are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/)
and the project uses [SemVer](https://semver.org/).

## [Unreleased]

### Added
- The tab sidebar on wide windows (landscape, tablets, split-screen) is now dynamic: it shrinks to a slim
  rail of tab initials when you tap the terminal, so the terminal gets more columns, and opens again when
  you tap the rail. Settings > Terminal > "Collapse the sidebar when you use the terminal" (on by default;
  off keeps it always expanded) is included in configuration backups.

### Fixed
- Wide layouts: after the sidebar collapsed, typed keys did not reach the terminal, and a space re-opened the
  sidebar and closed the soft keyboard. The sidebar controls no longer take keyboard focus, and opening or
  closing the sidebar never hides the keyboard.

## [0.1.0] - 2026-10-05

First release. A free (GPL-3.0-or-later) terminal for Android with Linux distributions through proot, no
graphical environment, no telemetry. It is early software: the README says, feature by feature, what has
been tried on a device (a Pixel 8 and, for window resizing, rotation and split-screen, a Huawei tablet) and
what is only tested on a computer.

### Highlights
- Terminal with a real PTY, tabs, split panes and profiles, saved layouts, typing in several panes at once,
  and editable keyboard shortcuts. It resizes with the window, the keyboard, rotation and split-screen
  (tried on a tablet).
- Debian, Ubuntu, Alpine and Fedora from their official mirrors with SHA-256 checks, users created
  automatically, a first-run setup when no distro is installed, and backups of a distro, of the settings or
  of everything (optionally encrypted).
- iOS-style interface, three extra-key styles, OLED mode, your own fonts and colour schemes, accessibility
  fixes, English and Spanish.
- Measured on a Pixel 8 with the signed release build: about 1.2 s from a cold start to the prompt (Alpine) and
  `seq 1 200000` in 1.09 s.

### Known limitations
- TalkBack and the layouts at font scale 2.0 have not been tried on a device.
- Restoring a backup from the first-run setup, and rotating mid-install, have not been tried on a device.
- A profile's colour scheme, font and size are stored but not applied per pane.
- Fedora is offered on a 32-bit ARM device and fails with a message there.
- Backups use gzip rather than `.tar.zst`.

The detailed notes of the pre-releases are below.

## [0.1.0-rc.3] - 2026-10-05

Third pre-release. Still early software: the README says what is verified on a device.

### Added
- First-run setup: with no distro installed, a welcome screen to install one (Alpine preselected) or restore a backup replaces the Android shell tab. Tried on a Pixel 8: install, an error without network with "Try again", and skipping.

### Changed
- The "keep sessions alive" prompt now appears after the first session starts, not over the welcome screen.

### Notes
- Measured on a Pixel 8 with the signed release build of 0.1.0-rc.2: about 1.2 s from a cold start to the prompt (Alpine) and `seq 1 200000` in 1.09 s.

## [0.1.0-rc.2] - 2026-10-05

First pre-release on the way to `0.1.0` (`0.1.0-rc.1` was only the version in the tree before anything
was published). Early software: it has been tried by hand on a Pixel 8 and, for window resizing only, on a
Huawei MRO-W09 tablet.
What is verified on a device and what is only tested on a computer is in the README.

### Added

- Terminal with a real PTY, Termux's emulator, scrollback, selection and pinch zoom, resizing with the
  window, the keyboard and rotation.
- Linux distributions through proot (built from source for arm64, armv7 and x86_64): install Debian,
  Ubuntu, Alpine or Fedora from their official mirrors with SHA-256 checks, open them in a tab, list, rename,
  duplicate and delete them, choose a default one that opens when the app starts.
- Profiles (distro, user, scrollback, start-up command), saved pane layouts that reopen as a tab, typing in
  several panes at once with a visible indicator, and keyboard shortcuts you can change; the shortcuts travel
  in the configuration backup.
- Tabs and split panes; a foreground service that keeps the sessions alive in the background.
- An extra-keys row (Esc, Tab, Ctrl, Alt, arrows and more) with sticky modifiers, hardware-keyboard
  shortcuts, copy and paste with bracketed paste.
- SSH: saved hosts that open a tab running `ssh`, and key generation, import and export, with private keys
  encrypted by the Android Keystore.
- Optional access to the device's shared storage from a distribution, under `~/storage`.
- Backups of one distribution, of the settings, or of everything, optionally encrypted with a password
  (AES-256-GCM), and restore. Gzip-compressed rather than `.tar.zst`.
- Appearance: colour schemes you can edit, import and export, light, dark and pure-black OLED themes, a
  bundled font (JetBrains Mono) or your own font file, cursor shape, margins and tab bar style.
- Fedora as a distribution (the official container image, a `tar.xz` read with `org.tukaani:xz`, 0BSD, with
  a memory limit); Fedora has no 32-bit ARM image. `dnf` works under proot on a Pixel 8.
- Non-root users: a user that does not exist in the distro is created (`/etc/passwd`, `/etc/group`, a home) and
  the shell runs as that user with proot's `-i uid:gid`, with no `su`. The session-ended card explains exit
  codes 127 and 126.
- From the Distros list: change a distro's default user, and set the default distro.
- An iOS-style interface: large titles, grouped lists, sheets, alerts, menus and a slider, applied to the
  tab bar, the extra keys, Distros, a nine-section Settings screen, SSH, Appearance, profiles and layouts.
- Three styles for the extra keys (flat, capsule, classic) with a live preview in Appearance.
- Accessibility: roles, headings and actions for every gesture (select, rename, close and move tabs, move the
  pane divider), 48 dp touch targets, and large-font layouts, from an audit of the code. The terminal itself
  does not expose its text to TalkBack.
- Tests of the key flows on the host (install and launch, backup round trip, resize) and instrumented tests
  for a real proot guest, a backup round trip and the activity (`docs/TESTING.md`).
- A throughput guard test for the emulator and a procedure to measure start-up and bulk output
  (`docs/PERFORMANCE.md`).
- English and Spanish.
- Fastlane metadata for F-Droid, `README.md`, `CONTRIBUTING.md` and `PRIVACY.md`.

### Changed

- A tab opened from a profile is named after the profile; other tabs are named after their distro.
- Opening a profile or a layout closes Settings so the new tab is visible at once.
- Each pane of a split tab has its own 48 dp header strip for the pane menu and the broadcast indicator,
  so they no longer cover the text. A pane created by splitting inherits the distro and user of the pane
  it came from, without the start-up command.
- The start-up command of a profile is typed once the shell has drawn its first prompt (or after 5 s at
  most), instead of after a fixed delay.
- The on-screen keyboard closes while Settings or another screen covers the terminal.
- The extra-keys labels follow the system font only up to 1.2x.
- "Credits and licenses" joins the hard-wrapped lines into paragraphs and shows one row per component.
- The extra-keys row is shown only while the on-screen keyboard is up (an option, on by default).
- The battery-optimisation prompt opens the system dialog directly instead of the general battery list.
- Inside a distribution, name resolution uses the network's DNS servers first and the public resolvers
  1.1.1.1 and 9.9.9.9 only as a last resort.

### Fixed

Found on a device:

- Installing a distribution from the app crashed every time: it asked Android for the storage's
  `FileStore`, which the system denies to apps. It now reads the free space with `StatFs`.
- What you typed on the soft keyboard did not reach the shell until the keyboard was hidden, because the
  keyboard holds a word while composing it. Text is now sent as it is typed. Verified on the Pixel 8.
- The extra-keys row stayed visible with the keyboard hidden; it now follows the keyboard. Verified on the Pixel 8.
- A new tab stayed blank until you switched tabs and came back; it is now drawn at once. Verified on the Pixel 8.
- The Back button and gesture did nothing in any screen (the activity did not declare the back callback
  that Android 13+ needs); it now goes back, and in the terminal it hides the keyboard without typing
  anything into the shell. Verified on the Pixel 8.
- Back on an Android 12 tablet: the terminal view no longer consumes the system keys (Back, Home, Recents,
  Menu, power, volume). Not verified on the tablet yet.
- Opening a profile for editing, or a shortcut, closed the app (a vertical scroll nested in a sheet);
  fixed and guarded by a test. Verified on the Pixel 8.

Found on a device and fixed, not looked at again on one:

- Text fields now take focus from a tap anywhere on the row, and the form no longer shifts when a warning
  appears.
- The active tab's menu button could be cut off by the "+" button with three or more tabs; the active
  tab is now scrolled fully into view. Not verified on a device yet.
- A non-root user failed to start (code 127) in a distro without `su`, such as Fedora; fixed by the
  non-root users above. Verified on the Pixel 8.

### Known limitations

- Not verified on a device: SSH keys with the real Keystore, backup export and restore with the file
  picker, the `~/storage` permission on Android 13 and later, fonts of your own, Debian and Ubuntu.
- On the tablet only window resizing was checked; split-screen mode, split panes and a distro were not.
- TalkBack and font scale 2.0 have not been tried on a device. Cold start to prompt is about 1.2 s median on the
  signed release build with Alpine (1.63 s on a debug build with Fedora), within the 1.5 s goal.
- Fedora is offered on a 32-bit ARM device and fails with a message there.
- A profile's colour scheme, font and size are stored but not applied per pane.
- The text of the initial command can be echoed twice if the shell prints slowly at start-up.
- Programs that read `/proc/stat` and a few other files may fail
  inside a distribution; some are replaced by approximate fake files.
- Programs of 32-bit architecture inside a 64-bit distribution do not run, and System V shared memory may fail.
- From Android 12 the system may kill the child processes of an app even with a foreground service (about 32
  at a time), cutting a long session with many programs short. There is no mitigation yet.
- Backups use gzip, not Zstandard: the Java Zstandard libraries ship precompiled binaries, which F-Droid
  does not accept.
