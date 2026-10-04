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
| [PRoot](https://github.com/proot-me/proot), © STMicroelectronics (Cédric Vincent and contributors), through the [Termux fork](https://github.com/termux/proot) (Android patches), pinned at tag `v5.1.107.96` as the git submodule `third_party/proot` | GPL-2.0-or-later | Built from source in this project's build and shipped as `libproot.so` and `libproot-loader.so`. Its full source is this repository's submodule and the upstream repositories; its license is `third_party/proot/COPYING` |
| [talloc](https://talloc.samba.org/) 2.5.0, © Andrew Tridgell, Stefan Metzmacher and the Samba Team | LGPL-3.0-or-later | Vendored unmodified in `third_party/talloc` (license in `COPYING`) and statically linked into `libproot.so`. A hand-written `replace.h` replaces Samba's generated one; the library can be relinked from the vendored sources |

## Planned (listed here so the credit is not forgotten; moved to "Used now" when integrated)

| Component | License | Task | Notes |
|---|---|---|---|
| `terminal-emulator` and `terminal-view` from [termux-app](https://github.com/termux/termux-app), © Termux developers, derived from [Android Terminal Emulator](https://github.com/jackpal/Android-Terminal-Emulator) © Jack Palevich | Apache-2.0 | T03 | Only these two libraries. The rest of `termux-app` is **GPL-3.0-only** and must not be copied. Per-file headers are checked when the code is imported |
| Debian, Ubuntu and Alpine Linux root filesystems | Per-package free licenses | T06 | Downloaded by the user's device from the official mirrors at install time; **not redistributed** in the APK |
| Monospace font (to be chosen) | To be checked (SIL OFL-1.1 expected) | T12 | License text shipped with the font |
