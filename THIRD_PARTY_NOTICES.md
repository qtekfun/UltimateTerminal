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
| [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/) 1.28.0, with Commons IO 2.20.0, Commons Codec 1.19.0 and Commons Lang 3.18.0, © The Apache Software Foundation | Apache-2.0 | Reads the `.tar.gz` root filesystems when a distro is installed (T07). Only gzip and plain tar are read; the optional xz, zstd and brotli codecs are not shipped. Android's packaging drops the libraries' own `NOTICE` and `LICENSE` files, so their texts are copied unchanged into `app/src/main/res/raw/third_party_notices_apache.txt`, which ships in the APK (checked in the release build) |
| [XZ for Java](https://tukaani.org/xz/java.html) (`org.tukaani:xz`) 1.12, © The XZ for Java authors and contributors | 0BSD | Decompresses the `.tar.xz` root filesystem of Fedora (T24). 0BSD asks for no notice to be kept; it is credited all the same |
| [Jakarta Injection API](https://github.com/eclipse-ee4j/injection-api) 2.0.1 (`jakarta.inject-api`), © Eclipse Foundation and contributors | Apache-2.0 | Dagger/Hilt annotations (`@Inject`). Transitive dependency of Hilt. Its `NOTICE.md` and license are bundled with the app (see T23). |
| `kotlinx-serialization-json` 1.11.0, © JetBrains s.r.o. and contributors | Apache-2.0 | Reads the OCI manifest that locates the Debian root filesystem (T06) |
| [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) 2.304, © 2020 The JetBrains Mono Project Authors | SIL OFL-1.1 | Terminal font, bundled unmodified (Regular, Bold, Italic, Bold Italic; checked identical to the official release). License text in the APK, `assets/licenses/JetBrainsMono-OFL-1.1.txt` |
| [Inter](https://github.com/rsms/inter) 4.1, © 2016 The Inter Project Authors | SIL OFL-1.1 | Typeface of the iOS-style screens (T22a): Regular, Medium, SemiBold and Bold, bundled unmodified from the official release (`extras/ttf`). License text in the APK, `assets/licenses/Inter-OFL-1.1.txt` |
| [Lucide](https://lucide.dev) icons 1.52.0, © 2026 Lucide Icons and Contributors; the icons derived from Feather are © 2013-present Cole Bemis | ISC (Lucide) and MIT (Feather-derived icons) | 16 icons converted to Android vector drawables with their paths unchanged (`res/drawable/ic_ios_*.xml`). License texts in the APK, `assets/licenses/Lucide-ISC-MIT.txt` |
| Color scheme **Solarized**, © 2011 Ethan Schoonover ([altercation/solarized](https://github.com/altercation/solarized)) | MIT | Palette values, with the small readability changes listed in `BuiltInSchemes.kt` |
| Color scheme **Dracula**, © 2023 Dracula Theme ([dracula/dracula-theme](https://github.com/dracula/dracula-theme)) | MIT | Palette values |
| Color scheme **Gruvbox**, © Pavel Pertsev ([morhetz/gruvbox](https://github.com/morhetz/gruvbox)) | MIT/X11 | Palette values. Upstream states the license in its README and `package.json`; the repository has no `LICENSE` file |
| Color scheme **Nord**, © 2016-present Sven Greb ([nordtheme/nord](https://github.com/nordtheme/nord)) | MIT | Palette values |
| JUnit 5, © the JUnit team | EPL-2.0 | Tests only; not shipped in the APK |
| JUnit 4, © the JUnit team | EPL-1.0 | Tests only; not shipped in the APK. Runs the emulator's upstream tests |
| `terminal-emulator` from [termux-app](https://github.com/termux/termux-app), © Termux developers, derived from [Android Terminal Emulator](https://github.com/jackpal/Android-Terminal-Emulator) © Jack Palevich | Apache-2.0 (see the note below) | Vendored **unmodified** in `terminal-emulator/` at tag `v0.118.3` (commit `5b657c6adf4304e5198951ce815fe0205dcac29c`): the Java sources, the JNI `termux.c` and upstream's unit tests. Only this library is used: the rest of `termux-app` is **GPL-3.0-only** and is not copied, and `terminal-view` is not used (the view is our own, in Compose). License text: `terminal-emulator/LICENSE` |
| `WcWidth.java` (inside the library above) derives from [jquast/wcwidth](https://github.com/jquast/wcwidth), © 2014 Jeff Quast, and Markus Kuhn's wcwidth | MIT, and Kuhn's permission notice | Notice reproduced in `terminal-emulator/NOTICE-wcwidth.txt` |
| [PRoot](https://github.com/proot-me/proot), © STMicroelectronics (Cédric Vincent and contributors), through the [Termux fork](https://github.com/termux/proot) (Android patches), pinned at tag `v5.1.107.96` as the git submodule `third_party/proot` | GPL-2.0-or-later | Built from source in this project's build and shipped as `libproot.so` and `libproot-loader.so`. Its full source is this repository's submodule and the upstream repositories; its license is `third_party/proot/COPYING` |
| [talloc](https://talloc.samba.org/) 2.5.0, © Andrew Tridgell, Stefan Metzmacher and the Samba Team | LGPL-3.0-or-later | Vendored unmodified in `third_party/talloc` (license in `COPYING`) and statically linked into `libproot.so`. A hand-written `replace.h` replaces Samba's generated one; the library can be relinked from the vendored sources |
| Debian, Ubuntu, Alpine Linux and Fedora root filesystems (see the note below) | Per-package free licenses | Downloaded by the user's device from the official sources at install time (T06); **not redistributed** in the APK or in this repository. The app only reads each project's own public index to learn the current file name, size and SHA-256 |

### Note on the color schemes

The four MIT-licensed schemes are credited with their copyright lines and the MIT permission notice
in `assets/licenses/ColorSchemes-MIT.txt`, which ships inside the APK. The "OLED" scheme (pure black
background) and the way schemes are stored and imported are this project's own. A scheme the user
imports is theirs; the app does not check where it comes from.

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

### Note on the root filesystem sources

The app finds the current archive in each project's own index and verifies its SHA-256 before using it:

- **Alpine Linux** `alpine-minirootfs`, from `dl-cdn.alpinelinux.org` (`latest-releases.yaml`).
- **Ubuntu Base**, from `cdimage.ubuntu.com` (`SHA256SUMS`).
- **Fedora** Container Base image, from `dl.fedoraproject.org` (the `Container/<arch>/images/` directory of the newest release, with the SHA-256 of its signed `CHECKSUM` file). It is an OCI image archive compressed with xz; its single layer is the root filesystem.
- **Debian** `slim` root filesystem, built with [debuerreotype](https://github.com/debuerreotype/debuerreotype) and published as an OCI image in
  [debuerreotype/docker-debian-artifacts](https://github.com/debuerreotype/docker-debian-artifacts) (Apache-2.0 repository).

Their contents are free software under each package's own license; the license texts are inside the
archives once installed. Names such as Debian, Ubuntu, Alpine Linux and Fedora belong to their owners (Debian
is a registered trademark of Software in the Public Interest, Inc.; Ubuntu of Canonical Ltd.; Fedora and the Fedora logo of Red Hat, Inc.).

### Note on the iOS-style design

The iOS-style screens (RF-14) are **inspired by** iOS, not copied from it. They use no Apple font, icon or other asset: the typeface is Inter and the icons are Lucide, both free (see above). "iOS", "SF Pro" and "SF Symbols" are names or marks of Apple Inc.; this project is not affiliated with, endorsed by or sponsored by Apple.

## Where the notices are in the app

The notices that licenses ask to travel with the app are inside the APK: `res/raw/third_party_notices_apache`
(Apache Commons and Jakarta Injection, unmodified) and `assets/licenses/` (fonts, icons and color schemes).
The app reads them through `LicenseTexts` (`domain/license`) for its "About" screen. Android's packaging drops the
`META-INF/NOTICE` and `LICENSE` files of libraries, which is why they are bundled by hand; D-T23-2 in `DECISIONS.md`
records how it was measured that these are the only dependencies that publish a notice.
