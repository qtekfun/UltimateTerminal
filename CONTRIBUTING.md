<!--
  SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->

# Contributing

Thanks for helping. This file says how the project is run; [`SPEC.md`](SPEC.md) says what to build,
[`PLAN.md`](PLAN.md) in what order, and [`DECISIONS.md`](DECISIONS.md) what was decided and why. Read the
three before changing behaviour, and [`CLAUDE.md`](CLAUDE.md) for the working rules (it also guides the
AI assistant that does much of the development here).

## Work flow

- The main branch is **`master`**. Do not commit to it directly.
- Work on one task of `PLAN.md` at a time, in a branch named `feat/<task>` (`fix/<what>` for a fix).
- Commits follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `test:`,
  `docs:`, `ci:`, `chore:`…), small and atomic.
- Open a pull request against `master`. It must pass **every** check before merging:
  - `check`: the whole build, tests, detekt, ktlint, Android Lint and the coverage rules;
  - **GitGuardian Security Checks**: the secret scan. It can flag harmless things (a test password, a
    field named `password`); read what it flags, decide whether it is real, and say so in the pull
    request instead of ignoring it.
- **Do not merge `master` into your branch.** The secret scan re-reads whatever the merge brings in and
  has flagged other people's code as yours. Rebase onto `master` before the first push, or rebuild the
  branch as a single commit on top of it.
- Do not force-push, and do not rewrite history that others may have.
- Before opening the pull request run, locally:

  ```
  ./gradlew :app:detekt :app:ktlintCheck :app:lintDebug :app:testDebugUnitTest
  ./gradlew check assembleDebug
  ```

  Android Lint runs with warnings as errors in CI, and a rule that is silent in the editor can still
  fail the build (for example `UsableSpace`). Run `lintDebug`.

## Free software only

This is an F-Droid app and a GPL-3.0-or-later project. These rules are not negotiable:

- No Firebase, Google Play Services, Crashlytics, analytics, telemetry or any proprietary SDK. The build
  fails if one shows up (`checkForbiddenDependencies`), and so it does for a dependency whose license is
  not on the allowed list (`licensee`).
- Check the license of a new dependency before adding it: it must be compatible with the GPL-3.0, and you
  should ask first. Do not change dependency versions by hand: Dependabot does it.
- No precompiled third-party binaries in the repository or the APK. proot is built from its submodule,
  and Linux root filesystems are downloaded from official mirrors and verified, never bundled.
- Every source file has the SPDX header. Every string the user sees goes in `strings.xml` (`values/` and
  `values-es/`), never in code.
- **Credits.** Any third-party code, library, font, icon or asset you add, upgrade or remove must update
  [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) in the same change, keeping its original copyright
  notice and license text. Read the license from the project's official repository, not from memory.
  From `termux-app` only the `terminal-emulator` and `terminal-view` libraries (Apache-2.0) may be
  reused; the rest of it is GPL-3.0-only and must not be copied. Do not use the names or logos of Termux,
  Debian, Ubuntu, Alpine or Apple in a way that suggests affiliation, and do not use Apple's SF Pro,
  SF Symbols or any Apple asset.
- `targetSdk` stays at **28** (see the README). Do not raise it.

## Tests and the coverage rules

- Tests are JUnit 5 with MockK, Turbine, MockWebServer and an in-memory Room database. A test must be
  able to fail for a real reason: no empty or tautological tests written to raise a number.
- **Tests must be deterministic: no wall clock, no randomness, no ordering luck.** This has already cost
  us. Test archives gave directories the current time, so a restore test reached a different branch from
  one run to the next and the coverage rule failed two runs in six with the same code. Give every
  fixture a fixed time, a fixed seed and a fixed order. If a branch of the code depends on a compressed
  size, build the fixture so the size does not depend on the compressor (see `gzippedStored`).
- Coverage (Kover): at least 85 % over `domain` and `data`, and **100 % of lines and branches** in the
  critical packages `data.rootfs.verify` and `data.backup`. Generated code, `@Preview` and pure Compose
  UI are excluded. Do not relax a rule to make the build pass; add the test.
- When coverage looks different on your machine and in CI, run the tests cold, without the build cache:

  ```
  ./gradlew :app:cleanTestDebugUnitTest :app:koverVerifyCritical --no-build-cache
  ```

- Host tests cannot see everything: a call that works on the JVM can throw on Android (a real example is
  `Files.getFileStore`, which Android denies to apps). When something can only be checked on a device,
  say so in `DECISIONS.md` under "not validated" and in the pull request, and do not write that it works.

## Code

Kotlin, Jetpack Compose and Material 3, with MVVM in three layers (`ui`, `domain`, `data`) and Hilt.
Business logic lives in `domain`, not in composables or view models. Errors are sealed types, not
exceptions thrown at the UI. All file and network I/O runs off the main thread. Secrets are never
logged, and terminal contents never are. Warnings of Kotlin and Lint are errors, and detekt and ktlint
are not relaxed to pass.

## Decisions

When you choose something the spec did not decide, or you depart from it, add an entry to
`DECISIONS.md` (date, decision, reason, alternatives, impact) and list anything you could not verify.
If the spec is ambiguous or something is missing, ask rather than guess.

## Reporting a security problem

Please do not open a public issue for a vulnerability. Contact the maintainer privately, through the
contact details on their GitHub profile, and give them time to fix it before anything is published.
