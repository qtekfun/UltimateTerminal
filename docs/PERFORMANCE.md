# Performance: what is measured and how

SPEC section 6 sets two targets: **cold start to a shell prompt below 1.5 s** with a distribution
already installed, and **smooth output** (a large `cat` must not block the interface). This page says
what the automated tests cover, and gives the procedure for the numbers that only a device can give.
Do the on-device part on a **release-like** build when possible (a debug build is slower and its
numbers are only a ceiling).

## On the computer (runs in `./gradlew check`)

`terminal-emulator/src/test/java/com/termux/terminal/FeedThroughputTest.java` feeds the emulator,
in 4 KiB chunks like the pty reader, with: the output of `seq 1 200000` (about 1.3 MB), 20 000 lines
of colored text that wraps, and 20 000 lines of wide and combining Unicode. It checks that the
screen ends on the right text, that the scrollback is capped, and that each run finishes within
**30 s**. A laptop needs well under a second, so the limit only fails on a complexity regression
(for example a scroll that copies the whole history), never on a slow or busy machine. It says nothing
about drawing or about the device: that is the next section.

Not covered on the computer: the Compose drawing of the terminal, the time to start the process, proot.

## On a device

Needs: `adb`, a device with one distribution installed and chosen as the default, the app installed.
Use a debug build for `run-as` (step 2), or read the file by another means on a release build. Close
other apps, plug in the charger, set the screen to stay on, and let the phone cool down between runs.
Repeat each measurement **5 times**, discard the first (cold caches) and report the median and the
range, with device, Android version, build (`git rev-parse --short HEAD`) and distribution.

### A. Cold start until the prompt

1. Tell the shell to record the moment its prompt is drawn. In the distribution, once:

   ```sh
   echo "PS1='\$(date +%s%3N >> /root/.prompt_times)\\\$ '" >> /root/.profile   # or ~/.bashrc for bash
   ```

   (`PS1` runs the `date` command every time the prompt is shown. Remove the line when done.)
   If the shell does not read `.profile`, run the line by hand once per tab instead.
2. For each run, from the computer:

   ```sh
   adb shell am force-stop com.qtekfun.ultimateterminal
   adb shell 'echo $(date +%s%3N)'                       # T0, device clock, in ms
   adb shell am start -W -n com.qtekfun.ultimateterminal/.MainActivity
   sleep 5
   adb shell run-as com.qtekfun.ultimateterminal \
     cat files/distros/<id>/rootfs/root/.prompt_times | tail -1   # T1: the prompt
   ```

   Both times come from the device clock, so they can be subtracted. The distribution folder is
   `files/distros/<id>/` (see `DistroPaths`; `<id>` is the one folder in `files/distros/`); the
   `rootfs/` subfolder is the unpacked image (`UNPACKED_NAME`). If the layout changed, find it with
   `adb shell run-as com.qtekfun.ultimateterminal find files/distros -name .prompt_times`.
3. **Cold start to prompt = T1 - T0** (target: below 1500 ms). `am start -W` also prints
   `TotalTime`, the time to the first frame of the activity: record it as the "to first frame" number
   and the difference T1 - T0 - TotalTime as the cost of starting the shell.
4. Truncate the file between series: `adb shell run-as com.qtekfun.ultimateterminal sh -c ': > files/distros/<id>/rootfs/root/.prompt_times'`.

A **warm** start (app in the background, `am start` again without `force-stop`) is worth one extra
line in the report: it should be well under the cold one.

### B. Throughput of `seq 1 200000`

In a tab of the installed distribution (not the empty "Shell" tab, to include proot):

```sh
time seq 1 200000
```

`time` reports the time the shell spent writing; it ends when the last byte was accepted by the pty,
which blocks when the terminal cannot keep up, so it is a fair measure of the whole path (pty, reader,
emulator, redraw). Also record:

- **Smoothness while it runs:** `adb shell dumpsys gfxinfo com.qtekfun.ultimateterminal reset`
  before, and `adb shell dumpsys gfxinfo com.qtekfun.ultimateterminal` right after: report
  *Total frames rendered*, *Janky frames* (and percent) and the 90th and 99th percentile frame times.
  The target is a high refresh rate with few janky frames; a freeze of the interface while the output
  scrolls is a failure even if `time` is short.
- **The interface stays alive:** while `seq` runs, tap another tab or open Settings. It must respond.
- **Same test with the screen split in two panes**, one running `seq`, the other idle, and with a
  large font (pinch to zoom in): the cost grows with the visible cells.
- Optionally `time cat <a 50 MB file>` for sustained output.

A reference to compare with: Termux on the same phone, `time seq 1 200000`, same procedure.

### Report

Paste the table (run, T1 - T0, TotalTime, `time` real, janky %) in the pull request or in
`DECISIONS.md`, with the numbers *and* the conditions. Do not claim a target as met without it.
