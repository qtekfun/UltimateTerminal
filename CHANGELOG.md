<!--
SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Changelog

All notable changes are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/)
and the project uses [SemVer](https://semver.org/).

## [Unreleased]

Work towards the first release (`0.1.0`). Nothing has been released yet; the version in the tree is
`0.1.0-rc.1`. Early software: it has been tried by hand on one phone (a Pixel 8) and not on a tablet.
What is verified on a device and what is only tested on a computer is in the README.

### Added

- Terminal with a real PTY, Termux's emulator, scrollback, selection and pinch zoom, resizing with the
  window, the keyboard and rotation.
- Linux distributions through proot (built from source for arm64, armv7 and x86_64): install Debian,
  Ubuntu or Alpine from their official mirrors with SHA-256 checks, open them in a tab, list, rename,
  duplicate and delete them, choose a default one that opens when the app starts.
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
- A set of iOS-style interface components for the redesign that is coming (not applied to the screens yet).
- English and Spanish.
- Fastlane metadata for F-Droid, `README.md`, `CONTRIBUTING.md` and `PRIVACY.md`.

### Changed

- The extra-keys row is shown only while the on-screen keyboard is up (an option, on by default).
- The battery-optimisation prompt opens the system dialog directly instead of the general battery list.
- Inside a distribution, name resolution uses the network's DNS servers first and the public resolvers
  1.1.1.1 and 9.9.9.9 only as a last resort.

### Fixed

Found on a device:

- Installing a distribution from the app crashed every time: it asked Android for the storage's
  `FileStore`, which the system denies to apps. It now reads the free space with `StatFs`.
- What you typed on the soft keyboard did not reach the shell until the keyboard was hidden, because the
  keyboard holds a word while composing it. Text is now sent as it is typed.

### Known limitations

- Not yet verified on a tablet, nor on a device: split panes, themes and fonts, SSH keys with the real
  Keystore, backup export and restore, and the `~/storage` permission on Android 13 and later.
- A tab created after the first one can stay blank until you switch tabs and come back.
- There is no app-wide settings screen yet. Programs that read `/proc/stat` and a few other files may fail
  inside a distribution; some are replaced by approximate fake files.
- Programs of 32-bit architecture inside a 64-bit distribution do not run, and System V shared memory may fail.
- From Android 12 the system may kill the child processes of an app even with a foreground service (about 32
  at a time), cutting a long session with many programs short. There is no mitigation yet.
- Backups use gzip, not Zstandard: the Java Zstandard libraries ship precompiled binaries, which F-Droid
  does not accept.
