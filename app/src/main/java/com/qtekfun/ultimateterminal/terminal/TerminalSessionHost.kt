// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.qtekfun.ultimateterminal.domain.terminal.GridSize
import com.qtekfun.ultimateterminal.domain.terminal.ShellEnvironment
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns one [TerminalSession] (a pty and the shell on it) and turns the emulator library's callbacks
 * into state the UI can observe. Must be created and used on the main thread: the library delivers
 * its callbacks there.
 *
 * It runs Android's own `/system/bin/sh` for now; the proot distributions arrive with T07. The
 * foreground service (T08) owns these objects through the session manager, so a shell outlives the
 * activity. It needs a real pty and a device to be exercised, so it is not covered by unit tests.
 */
// The callback interface is imposed by the emulator library and has many methods.
@Suppress("TooManyFunctions")
class TerminalSessionHost(
    private val context: Context,
    private val onFinished: (Int) -> Unit = {}
) : TerminalSessionClient,
    TerminalOutput {
    private val frameState = MutableStateFlow(0)
    private val titleState = MutableStateFlow<String?>(null)
    private val exitState = MutableStateFlow<Int?>(null)

    /** Increments every time the screen content changes. */
    val frame: StateFlow<Int> = frameState.asStateFlow()
    val title: StateFlow<String?> = titleState.asStateFlow()

    /** The shell's exit status once it has ended, null while it runs. */
    val exitStatus: StateFlow<Int?> = exitState.asStateFlow()

    private var session: TerminalSession? = null

    override val emulator: TerminalEmulator? get() = session?.emulator

    /** Starts the shell at [grid] size, or resizes the running one (this also resizes the pty). */
    fun resize(grid: GridSize, cellWidthPx: Int, cellHeightPx: Int) {
        val current = session
        if (current == null) {
            exitState.value = null
            val home = context.filesDir.absolutePath
            val created = TerminalSession(
                SHELL,
                home,
                arrayOf(SHELL_NAME),
                ShellEnvironment.build(home, context.cacheDir.absolutePath, System.getenv()),
                TRANSCRIPT_ROWS,
                this
            )
            session = created
            created.updateSize(grid.columns, grid.rows, cellWidthPx, cellHeightPx)
        } else if (exitState.value == null &&
            (grid.columns != current.emulator.mColumns || grid.rows != current.emulator.mRows)
        ) {
            current.updateSize(grid.columns, grid.rows, cellWidthPx, cellHeightPx)
        }
        frameState.value++
    }

    /** Ends the shell (if running) and forgets the session, so the next [resize] starts a new one. */
    fun stop() {
        session?.finishIfRunning()
        session = null
    }

    override fun write(text: String) {
        session?.write(text)
    }

    override fun writeCodePoint(escapePrefix: Boolean, codePoint: Int) {
        session?.writeCodePoint(escapePrefix, codePoint)
    }

    fun paste(text: String) {
        emulator?.paste(text)
    }

    override fun onTextChanged(changedSession: TerminalSession) {
        frameState.value++
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
        titleState.value = changedSession.title
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        exitState.value = finishedSession.exitStatus
        frameState.value++
        onFinished(finishedSession.exitStatus)
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        copyToClipboard(text)
    }

    override fun onPasteTextFromClipboard(session: TerminalSession) {
        val manager = context.getSystemService(ClipboardManager::class.java)
        val text = manager?.primaryClip?.takeIf {
            it.itemCount > 0
        }?.getItemAt(0)?.coerceToText(context)
        if (!text.isNullOrEmpty()) paste(text.toString())
    }

    /** Pastes the clipboard into the shell (bracketed when the program asked for it). */
    fun pasteFromClipboard() {
        session?.let(::onPasteTextFromClipboard)
    }

    fun copyToClipboard(text: String) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
    }

    override fun onBell(session: TerminalSession) = Unit

    override fun onColorsChanged(session: TerminalSession) {
        frameState.value++
    }

    override fun onTerminalCursorStateChange(state: Boolean) {
        frameState.value++
    }

    override fun getTerminalCursorStyle(): Int = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE

    // The library's log messages can contain terminal content (SPEC §6: never log it), so they are
    // dropped instead of going to logcat.
    override fun logError(tag: String?, message: String?) = Unit

    override fun logWarn(tag: String?, message: String?) = Unit

    override fun logInfo(tag: String?, message: String?) = Unit

    override fun logDebug(tag: String?, message: String?) = Unit

    override fun logVerbose(tag: String?, message: String?) = Unit

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) = Unit

    override fun logStackTrace(tag: String?, e: Exception?) = Unit

    private companion object {
        const val SHELL = "/system/bin/sh"
        const val SHELL_NAME = "sh"
        const val CLIP_LABEL = "terminal"

        /** SPEC RF-01: the default scrollback is 10 000 lines. */
        const val TRANSCRIPT_ROWS = 10_000
    }
}
