<!--
SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Releasing

## Versions

- The version lives in one place: `appVersion` in `gradle.properties`, as SemVer (`1.2.3`), or `1.2.3-rc.N` for a release candidate.
- The Android version code is derived from it, never set by hand: `(MAJOR*10000 + MINOR*100 + PATCH) * 100 + N`, with `N = 99` for a final release. So `0.1.0-rc.1` is `10001` and `0.1.0` is `10099`: a final version always sorts after its release candidates, and nothing depends on dates or the machine (reproducible builds).
- Before 1.0.0 the app is `0.x`.

## Signing (one time)

Releases are signed with the project's own key, and the builds are reproducible: F-Droid builds the same source, checks that its APK matches the one published here and then ships ours. That way the app can be updated from F-Droid or GitHub interchangeably.

**Never commit the key, its passwords or the base64 of the key.** `*.jks` and `*.keystore` are in `.gitignore`.

1. Create the key, and keep the file and passwords somewhere safe and **backed up**: if the key is lost, users would have to uninstall to update.
   ```sh
   keytool -genkeypair -v -keystore ~/keys/ultimateterminal-release.jks -alias ultimateterminal \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Add these secrets to the GitHub repository (Settings → Secrets and variables → Actions):
   - `UT_KEYSTORE_BASE64`: `base64 -w0 ~/keys/ultimateterminal-release.jks`
   - `UT_KEYSTORE_PASSWORD`, `UT_KEY_ALIAS` (`ultimateterminal`), `UT_KEY_PASSWORD`
3. For F-Droid, take the certificate fingerprint and put it in `AllowedAPKSigningKeys` of `fdroid/com.qtekfun.ultimateterminal.yml` (it holds a placeholder until then):
   ```sh
   keytool -list -v -keystore ~/keys/ultimateterminal-release.jks -alias ultimateterminal | grep SHA256
   ```

Locally, the same variables sign a release build: `UT_KEYSTORE_FILE` (path to the `.jks`), `UT_KEYSTORE_PASSWORD`, `UT_KEY_ALIAS` and `UT_KEY_PASSWORD`. Without them `./gradlew assembleRelease` builds an unsigned APK, which is what F-Droid does before comparing. The Release workflow refuses to publish if the key secret is missing, so a tag can never produce an unsigned release.

## Making a release

1. Move the `[Unreleased]` notes in `CHANGELOG.md` under `## [X.Y.Z] - YYYY-MM-DD`. The workflow fails if that version has no notes.
2. Set `appVersion=X.Y.Z` in `gradle.properties`.
3. Commit (`chore: release X.Y.Z`), merge to `master`, then tag and push the tag:
   ```sh
   git tag vX.Y.Z && git push origin vX.Y.Z
   ```
4. The **Release** workflow checks that the tag matches `appVersion`, runs `./gradlew check`, builds the signed APK (`UltimateTerminal-X.Y.Z.apk`) and publishes a GitHub Release with the notes of that version. Release candidates (`-rc.N`) are marked as pre-releases.
5. F-Droid picks the new tag up by itself (`UpdateCheckMode: Tags`, final versions only: release candidates are not offered there).

Every pull request and push to `master` also uploads the **debug APK** as a workflow artifact (`UltimateTerminal-debug-apk`, kept 14 days), so a build can be tried without releasing.

## Reproducible builds

What makes the APK reproducible, and what to keep that way:

- **Version:** derived from `appVersion` only (see above); no dates, no git hash (`vcsInfo.include = false`, no `git describe`).
- **Toolchain pinned:** JDK 21, Gradle 9.8 (wrapper with checksum), AGP and Kotlin in `libs.versions.toml`, NDK `28.2.13676358` and CMake `3.31.6` in `app/build.gradle.kts`, all dependencies verified by `gradle/verification-metadata.xml`.
- **Sources pinned:** proot is a git submodule at a fixed tag (`third_party/proot`), talloc is vendored with the SHA-256 of its tarball; nothing is downloaded during the build.
- **Native code:** compiled with `-ffile-prefix-map=<checkout>=.`, so the checkout path does not end up in the binaries; `PROOT_VERSION` is a constant, not `git describe`.
- **No Google blob:** `dependenciesInfo` is off.

Check it before the first release (and after touching the native build): build `assembleRelease` unsigned from two different checkout paths and compare the APK contents (`diffoscope`, or at least the `lib/*/*.so` hashes). Differences in the native libraries almost always come from an absolute path or a timestamp.

## F-Droid

`fdroid/com.qtekfun.ultimateterminal.yml` is the app's metadata as submitted to [fdroiddata](https://gitlab.com/fdroid/fdroiddata) (`metadata/com.qtekfun.ultimateterminal.yml`). It has no comments because fdroiddata's tools remove them. F-Droid builds each tagged version with JDK 21 like CI, **with `submodules: true`** (proot is built from the submodule) and the pinned NDK, checks that its APK matches ours (`Binaries`, `AllowedAPKSigningKeys`) and then publishes ours.

Before the first submission:

- Replace `AllowedAPKSigningKeys` with the certificate fingerprint (see Signing).
- Update `versionName`, `versionCode`, `commit` and `CurrentVersion*` to the first final release tag.
- Declare any anti-feature that applies and check F-Droid's inclusion policy. The app downloads Linux root filesystems chosen and started by the user and runs them; review whether that needs a note.
- Metadata and screenshots (`fastlane/`) belong to T20; third-party licenses are in `THIRD_PARTY_NOTICES.md`.
