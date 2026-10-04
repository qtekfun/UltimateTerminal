# F-Droid metadata (fastlane layout)

`metadata/android/<locale>/` is read by F-Droid and by `fdroid` tooling:

| File | Limit | Notes |
|---|---|---|
| `title.txt` | 30 characters | |
| `short_description.txt` | 80 characters | |
| `full_description.txt` | 4000 characters | Only a few HTML tags are allowed (`<b>`, `<i>`, `<u>`); bullets are plain `•`. |
| `changelogs/<versionCode>.txt` | 500 characters | The file name is the `versionCode` of the build it describes. |
| `images/icon.png` | 512 × 512 | Same image in every locale. |
| `images/phoneScreenshots/`, `images/sevenInchScreenshots/` | | **Missing, see below.** |

Locales: `en-US` (default) and `es-ES`.

## `versionCode`

It comes from `appVersion` in `gradle.properties` (see `app/build.gradle.kts`):
`(MAJOR*10000 + MINOR*100 + PATCH) * 100 + N` for `MAJOR.MINOR.PATCH-rc.N`, and `99` in place of `N` for a
final release. `0.1.0-rc.1` is `10001` (its changelog is here); the first final release, `0.1.0`, is
`10099`, and its `changelogs/10099.txt` must be written when that release is cut (see `RELEASING.md`).

## Screenshots are still missing on purpose

Screenshots must be real captures from a device, and none have been taken for the current build, so none
are committed. When they are, name them `1.png`, `2.png`… in `images/phoneScreenshots/` (and
`images/sevenInchScreenshots/` for a tablet, which is the case the app is for) in each locale. Useful ones:

1. A tab running a Linux distribution (a shell, a few commands, colour output).
2. Two split panes, on a tablet.
3. The distributions screen with one installed.
4. The appearance screen with its live preview.
5. The extra-keys row above the keyboard.
