# terminal-emulator (vendored)

Terminal emulator library of Termux (`terminal-emulator/` in termux/termux-app), tag **v0.118.3**
(commit `5b657c6adf4304e5198951ce815fe0205dcac29c`), copied **unmodified**:

- `src/main/java/com/termux/terminal/*.java`
- `src/main/jni/termux.c`
- `src/test/java/com/termux/terminal/*.java` (upstream's unit tests)

Ours: `build.gradle.kts` and `src/main/jni/CMakeLists.txt` (upstream builds the JNI with ndk-build).

## License

The `LICENSE.md` of termux-app states that the repository is GPL-3.0-only **except** that the code
derived from Terminal Emulator for Android (Jack Palevich), in the `terminal-view` and
`terminal-emulator` libraries, is under the **Apache License 2.0**. None of the files here carries its
own license header, and there is no license file inside upstream's `terminal-emulator/` directory,
so that exception is the only license statement. See `DECISIONS.md` (T03) and
`THIRD_PARTY_NOTICES.md`. `WcWidth.java` also derives from jquast/wcwidth (MIT).

The Apache-2.0 text is in `LICENSE`.
