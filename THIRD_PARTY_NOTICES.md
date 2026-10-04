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
| Kotlin and kotlinx libraries, © JetBrains s.r.o. and contributors | Apache-2.0 | Language runtime and standard library; includes `kotlinx-coroutines-test` (tests only, not shipped in the APK) |
| AndroidX, Jetpack Compose and Material 3, © The Android Open Source Project | Apache-2.0 | UI toolkit |
| Dagger / Hilt, © The Dagger Authors | Apache-2.0 | Dependency injection |
| AndroidX Room 3 and AndroidX SQLite, © The Android Open Source Project | Apache-2.0 | Local metadata database. The app uses the system SQLite |
| MockK, Turbine and the bundled SQLite build for the JVM (`sqlite-bundled-jvm`; SQLite itself is in the public domain) | Apache-2.0 | Tests only; not shipped in the APK |
| [OkHttp](https://square.github.io/okhttp/) 5.5.0 and [Okio](https://square.github.io/okio/), © Square, Inc. | Apache-2.0 | HTTP client used to find and download the root filesystems (T06). `MockWebServer` (same project) is used in tests only and is not shipped in the APK |
| AndroidX Startup, © The Android Open Source Project | Apache-2.0 | Transitive dependency of OkHttp on Android |
| [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/) 1.28.0, with Commons IO 2.20.0, Commons Codec 1.19.0 and Commons Lang 3.18.0, © The Apache Software Foundation | Apache-2.0 | Reads the `.tar.gz` root filesystems when a distro is installed (T07). Only gzip and plain tar are read; the optional xz, zstd and brotli codecs are not shipped. Android's packaging drops the libraries' own `NOTICE` and `LICENSE` files, so their texts are copied unchanged into `app/src/main/res/raw/third_party_apache_commons.txt`, which ships in the APK (checked in the release build) |
| `kotlinx-serialization-json` 1.11.0, © JetBrains s.r.o. and contributors | Apache-2.0 | Reads the OCI manifest that locates the Debian root filesystem (T06) |
| JUnit 5, © the JUnit team | EPL-2.0 | Tests only; not shipped in the APK |
| JUnit 4, © the JUnit team | EPL-1.0 | Tests only; not shipped in the APK. Runs the emulator's upstream tests |
| `terminal-emulator` from [termux-app](https://github.com/termux/termux-app), © Termux developers, derived from [Android Terminal Emulator](https://github.com/jackpal/Android-Terminal-Emulator) © Jack Palevich | Apache-2.0 (see the note below) | Vendored **unmodified** in `terminal-emulator/` at tag `v0.118.3` (commit `5b657c6adf4304e5198951ce815fe0205dcac29c`): the Java sources, the JNI `termux.c` and upstream's unit tests. Only this library is used: the rest of `termux-app` is **GPL-3.0-only** and is not copied, and `terminal-view` is not used (the view is our own, in Compose). License text: `terminal-emulator/LICENSE` |
| `WcWidth.java` (inside the library above) derives from [jquast/wcwidth](https://github.com/jquast/wcwidth), © 2014 Jeff Quast, and Markus Kuhn's wcwidth | MIT, and Kuhn's permission notice | Notice reproduced in `terminal-emulator/NOTICE-wcwidth.txt` |
| [PRoot](https://github.com/proot-me/proot), © STMicroelectronics (Cédric Vincent and contributors), through the [Termux fork](https://github.com/termux/proot) (Android patches), pinned at tag `v5.1.107.96` as the git submodule `third_party/proot` | GPL-2.0-or-later | Built from source in this project's build and shipped as `libproot.so` and `libproot-loader.so`. Its full source is this repository's submodule and the upstream repositories; its license is `third_party/proot/COPYING` |
| [talloc](https://talloc.samba.org/) 2.5.0, © Andrew Tridgell, Stefan Metzmacher and the Samba Team | LGPL-3.0-or-later | Vendored unmodified in `third_party/talloc` (license in `COPYING`) and statically linked into `libproot.so`. A hand-written `replace.h` replaces Samba's generated one; the library can be relinked from the vendored sources |
| Debian, Ubuntu and Alpine Linux root filesystems (see the note below) | Per-package free licenses | Downloaded by the user's device from the official sources at install time (T06); **not redistributed** in the APK or in this repository. The app only reads each project's own public index to learn the current file name, size and SHA-256 |

### Note on the license of `terminal-emulator`

None of the files of upstream's `terminal-emulator/` carries a license header, and the directory has no
license file. The only statement is in termux-app's `LICENSE.md`: the repository is GPL-3.0-only
**except** that the code derived from Android Terminal Emulator, in the `terminal-view` and
`terminal-emulator` libraries, is Apache-2.0. It is not stated file by file, and the library has grown
since (for instance the sixel and bitmap support). We use it under that statement, unmodified and
credited; it is an open point in `DECISIONS.md` (D-T03-2) that the maintainers should confirm before a
public release.

## Planned (listed here so the credit is not forgotten; moved to "Used now" when integrated)

| Component | License | Task | Notes |
|---|---|---|---|
| Monospace font (to be chosen) | To be checked (SIL OFL-1.1 expected) | T12 | License text shipped with the font |

### Note on the root filesystem sources

The app finds the current archive in each project's own index and verifies its SHA-256 before using it:

- **Alpine Linux** `alpine-minirootfs`, from `dl-cdn.alpinelinux.org` (`latest-releases.yaml`).
- **Ubuntu Base**, from `cdimage.ubuntu.com` (`SHA256SUMS`).
- **Debian** `slim` root filesystem, built with [debuerreotype](https://github.com/debuerreotype/debuerreotype) and published as an OCI image in
  [debuerreotype/docker-debian-artifacts](https://github.com/debuerreotype/docker-debian-artifacts) (Apache-2.0 repository).

Their contents are free software under each package's own license; the license texts are inside the
archives once installed. Names such as Debian, Ubuntu and Alpine Linux belong to their owners (Debian
is a registered trademark of Software in the Public Interest, Inc.; Ubuntu of Canonical Ltd.).
