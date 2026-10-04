# Third-party notices

UltimateTerminal is free software under the **GPL-3.0-or-later** (see `LICENSE`). It is an
independent project: **it is not affiliated with, endorsed by or sponsored by** Termux, Debian,
Ubuntu, Alpine Linux or any other project named here. All names and trademarks belong to their
respective owners and are used only to say what is compatible or what is used.

This file must be updated in the same change that adds, upgrades or removes any third-party code,
library, font or asset (see `CLAUDE.md`). Original copyright notices and license texts of
third-party code are kept untouched.

## Used now

| Component | License | Notes |
|---|---|---|
| Kotlin and kotlinx libraries, © JetBrains s.r.o. and contributors | Apache-2.0 | Language runtime and standard library |
| AndroidX, Jetpack Compose and Material 3, © The Android Open Source Project | Apache-2.0 | UI toolkit |
| Dagger / Hilt, © The Dagger Authors | Apache-2.0 | Dependency injection |
| JUnit 5, © the JUnit team | EPL-2.0 | Tests only; not shipped in the APK |

## Planned (listed here so the credit is not forgotten; moved to "Used now" when integrated)

| Component | License | Task | Notes |
|---|---|---|---|
| `terminal-emulator` and `terminal-view` from [termux-app](https://github.com/termux/termux-app), © Termux developers, derived from [Android Terminal Emulator](https://github.com/jackpal/Android-Terminal-Emulator) © Jack Palevich | Apache-2.0 | T03 | Only these two libraries. The rest of `termux-app` is **GPL-3.0-only** and must not be copied. Per-file headers are checked when the code is imported |
| [PRoot](https://github.com/proot-me/proot), © Cédric Vincent, STMicroelectronics and contributors | GPL-2.0-or-later | T02 | Built from source in this project's build; the source is available from this repository and upstream |
| [talloc](https://talloc.samba.org/), © Samba Team | LGPL-3.0-or-later | T02 | PRoot dependency, built from source |
| Debian, Ubuntu and Alpine Linux root filesystems | Per-package free licenses | T06 | Downloaded by the user's device from the official mirrors at install time; **not redistributed** in the APK |
| Monospace font (to be chosen) | To be checked (SIL OFL-1.1 expected) | T12 | License text shipped with the font |
