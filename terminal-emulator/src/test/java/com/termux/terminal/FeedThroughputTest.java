// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.termux.terminal;

import java.nio.charset.StandardCharsets;

/**
 * Host-side guard for the emulator feed path (T17, docs/PERFORMANCE.md): massive output must be
 * parsed correctly and in bounded time. The time limits are two orders of magnitude above what a
 * laptop needs, so they only catch a complexity regression (a quadratic scroll, say), never a slow
 * or busy machine. The numbers that matter, on the device, are measured by hand.
 */
public class FeedThroughputTest extends TerminalTestCase {

    private static final int COLUMNS = 80;
    private static final int ROWS = 24;
    private static final int TRANSCRIPT_ROWS = 2000;
    private static final int CHUNK = 4096;
    private static final long GENEROUS_LIMIT_MILLIS = 30_000;

    private TerminalEmulator newEmulator() {
        return new TerminalEmulator(new MockTerminalOutput(), COLUMNS, ROWS, INITIAL_CELL_WIDTH_PIXELS,
            INITIAL_CELL_HEIGHT_PIXELS, TRANSCRIPT_ROWS, null);
    }

    /** Feeds [data] the way the pty reader does: in chunks of at most [CHUNK] bytes. */
    private static void feed(TerminalEmulator emulator, byte[] data) {
        for (int offset = 0; offset < data.length; offset += CHUNK) {
            int length = Math.min(CHUNK, data.length - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(data, offset, chunk, 0, length);
            emulator.append(chunk, length);
        }
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /** What `seq 1 200000` writes to a terminal: one number per line, CR LF after the tty's onlcr. */
    public void testSeqOfTwoHundredThousandLinesEndsOnTheLastNumber() {
        StringBuilder out = new StringBuilder();
        for (int i = 1; i <= 200_000; i++) out.append(i).append("\r\n");
        byte[] data = out.toString().getBytes(StandardCharsets.US_ASCII);
        TerminalEmulator emulator = newEmulator();

        long start = System.nanoTime();
        feed(emulator, data);
        long millis = elapsedMillis(start);

        // The screen holds the last rows, in order, and the history is capped (the transcript size counts the screen rows too).
        String transcript = emulator.getScreen().getTranscriptText();
        assertTrue(transcript, transcript.endsWith("200000"));
        assertTrue(transcript, transcript.contains("199999"));
        assertEquals(TRANSCRIPT_ROWS - ROWS, emulator.getScreen().getActiveTranscriptRows());
        assertTrue("seq took " + millis + " ms", millis < GENEROUS_LIMIT_MILLIS);
    }

    /** Long colored lines that wrap: every cell takes a style change, the heaviest path of the parser. */
    public void testWrappedColoredOutputKeepsTheLastLine() {
        StringBuilder out = new StringBuilder();
        for (int line = 0; line < 20_000; line++) {
            for (int i = 0; i < 40; i++) out.append("\033[3").append(i % 8).append("mab");
            out.append("\033[0m\r\n");
        }
        out.append("done");
        TerminalEmulator emulator = newEmulator();

        long start = System.nanoTime();
        feed(emulator, out.toString().getBytes(StandardCharsets.US_ASCII));
        long millis = elapsedMillis(start);

        assertTrue(emulator.getScreen().getTranscriptText().endsWith("done"));
        assertTrue("colored output took " + millis + " ms", millis < GENEROUS_LIMIT_MILLIS);
    }

    /** Wide characters and combining marks: the width tables are consulted for every cell. */
    public void testUnicodeOutputIsParsedWithoutLosingTheTail() {
        StringBuilder out = new StringBuilder();
        for (int line = 0; line < 20_000; line++) out.append("日本語 é 😀 ok\r\n");
        out.append("fin");
        TerminalEmulator emulator = newEmulator();

        long start = System.nanoTime();
        feed(emulator, out.toString().getBytes(StandardCharsets.UTF_8));
        long millis = elapsedMillis(start);

        assertTrue(emulator.getScreen().getTranscriptText().endsWith("fin"));
        assertTrue("unicode output took " + millis + " ms", millis < GENEROUS_LIMIT_MILLIS);
    }
}
