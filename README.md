# UltimateTerminal

A modern terminal for Android that runs full Linux distributions (Debian, Ubuntu, Alpine) through
[PRoot](https://github.com/proot-me/proot), a bit like WSL on Windows. It is inspired by
[Termux](https://github.com/termux/termux-app) and aims at a newer interface that fills the screen on
tablets and resizes with split screen, rotation and the keyboard. Terminal only: there is no graphical
environment. Free software ([GPL-3.0-or-later](LICENSE)), no Google services, no tracking.

> **Status: early pre-release (`0.1.0-rc.1`).** Most of the code is tested only on a computer. It has
> been tried on **one phone** (a Pixel 8 on Android 17) and **not yet on a tablet**, which is the case
> the project exists for. Read [What is verified](#what-is-verified) before relying on anything.

## What it does

Everything in this list is in the code and merged; see the next section for how far each part has
been checked.

- **Distributions:** install Debian, Ubuntu or Alpine from their official mirrors, with the download
  checked by SHA-256, and open it in a tab with a root shell (emulated by proot). List, rename,
  duplicate and delete them; pick a default one, which opens when the app starts.
- **Terminal:** a real PTY, 256-colour and true-colour emulation (Termux's emulator), scrollback,
  selection, pinch zoom, and a terminal that resizes with the window, the keyboard and rotation.
- **Tabs and panes:** several tabs, and split panes inside a tab.
- **Profiles, layouts and broadcast (Terminator-style):** named profiles (distro, user, scrollback,
  start-up command), saved pane layouts that reopen as a tab, typing in several panes at once with a
  red indicator, and shortcuts you can change. Only tested on a computer so far.
- **Input:** an extra-keys row (Esc, Tab, Ctrl, Alt, arrows and more) that shows only while the on-screen
  keyboard is up, sticky Ctrl/Alt, hardware-keyboard shortcuts.
- **SSH:** saved hosts that open a tab already running `ssh`, and SSH keys you can generate, import and
  export. Private keys are encrypted at rest with the Android Keystore.
- **Device files:** optional access to the shared storage from the distribution, under `~/storage`,
  for example to copy a log to Downloads. The storage permission is requested only when you turn it on.
- **Backups:** export one distribution, the settings, or everything to a file, optionally encrypted
  (AES-256-GCM, key derived from a password), and restore it on another device. Restoring adds the
  distributions without overwriting any you already have; the settings are replaced.
- **Appearance:** colour schemes (Solarized, Dracula, Gruvbox, Nord and more) that you can edit and
  import, light, dark and pure-black OLED themes, a bundled monospace font or a font file of your own,
  cursor shape, margins and tab bar style.
- **Languages:** English and Spanish.

## What is verified

"Host" means automated tests on a computer (JVM unit tests and CI). "Device" means it was run by hand
on a Pixel 8.

| | Device | Host only |
|---|---|---|
| App starts; a tab with a real shell and PTY | yes | |
| Typing, and the terminal shrinking above the keyboard (`stty size` matches what is drawn) | yes | |
| Foreground service keeping sessions alive | yes (it runs) | |
| Install Alpine from the app (download, SHA-256, extraction) and open it in a tab through proot | yes | |
| `apk add` over the network; `ssh` and `python3` running in that tab; `nmap` installed | yes | |
| Starting the app opens the default distribution | yes | |
| Install Debian or Ubuntu | | yes (index and download logic; never run on a device) |
| Split panes, tab reordering, shortcuts, pinch zoom, selection gestures | | yes |
| Profiles, saved layouts, typing in several panes, editing shortcuts | | yes |
| Theme, OLED mode, schemes, custom fonts | | yes |
| SSH hosts and keys with the real Keystore, running `ssh` to a real server | | yes (OpenSSH key format checked against the real `ssh-keygen` on a computer) |
| Backups: export and restore with the system file picker | | yes |
| `~/storage` mount and its permission on Android 13 and later | | yes (an untested hypothesis, see `DECISIONS.md` D-T13-3) |
| Behaviour on a **tablet**: resizing, split screen, multi-window | | yes |
| TalkBack, performance, battery | | yes (accessibility fixes and an emulator throughput test exist; measure with [`docs/PERFORMANCE.md`](docs/PERFORMANCE.md)) |

Some fixes are in the code but have not been looked at on a device since: the extra-keys row hiding
with the keyboard, text reaching the shell as it is typed (a soft keyboard used to hold it back until
the keyboard was hidden), the direct battery-optimisation dialog, and the fake `/proc` files that let
`top` and `uptime` work.

Every decision, and everything still to be validated, is written in [`DECISIONS.md`](DECISIONS.md).

## What is not there yet

The iOS-style redesign, accessibility and performance work, UI
tests on devices, and publication on F-Droid. Backups are gzip-compressed rather than `.tar.zst` as
the spec first asked, because the Zstandard libraries for Java ship precompiled binaries that F-Droid
does not accept. The full list and the order are in [`PLAN.md`](PLAN.md) and [`SPEC.md`](SPEC.md).

Known limits of running on Android: from Android 12 the system may kill the child processes of an app
even when it keeps a foreground service (the "phantom process killer", which caps them at about 32), so a
long session with many programs can be cut short. There is no mitigation in the app yet; see
`DECISIONS.md` (D-T08-5) for the developer-options workaround and the details.

## Why not Google Play

To run programs from its own storage the app targets Android 9 (API 28, `targetSdk 28`): from API 29
Android forbids executing binaries from an app's private storage, and proot needs it. Google Play
no longer accepts apps that target an API that old, so the app is meant for F-Droid and GitHub
Releases. `minSdk` is 26.

## Building

You need JDK 21, the Android SDK (`compileSdk` 37), the NDK `28.2.13676358` and CMake `3.31.6`. proot is
compiled from source as part of the build, so clone with the submodules:

```
git clone --recurse-submodules https://github.com/qtekfun/UltimateTerminal.git
cd UltimateTerminal
./gradlew assembleDebug     # debug APK in app/build/outputs/apk/debug/
./gradlew check             # everything CI runs: tests, detekt, ktlint, Lint, coverage rules
```

Releases, signing and the F-Droid recipe are described in [`RELEASING.md`](RELEASING.md). To contribute,
read [`CONTRIBUTING.md`](CONTRIBUTING.md). What the app does with your data and why it asks for each
permission is in [`PRIVACY.md`](PRIVACY.md).

## Credits and license

UltimateTerminal is licensed under the **GPL-3.0-or-later**. It builds on the work of others, each with
its own license, listed in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md). Among them:

- the terminal emulator library of [Termux](https://github.com/termux/termux-app) (Apache-2.0 for that
  library, itself derived from Android Terminal Emulator by Jack Palevich), included unmodified;
- [PRoot](https://github.com/proot-me/proot) (GPL-2.0-or-later) and [talloc](https://talloc.samba.org/)
  (LGPL-3.0-or-later), built from source;
- the fonts JetBrains Mono and Inter (SIL OFL 1.1), the Lucide icons (ISC) and the colour schemes
  Solarized, Dracula, Gruvbox and Nord (MIT).

The Linux distributions are downloaded by your device from their official mirrors and are not part of
this repository or of the APK. This is an independent project: it is **not affiliated with, endorsed by
or sponsored by** Termux, Debian, Ubuntu, Alpine Linux, Apple or any other project named here.
