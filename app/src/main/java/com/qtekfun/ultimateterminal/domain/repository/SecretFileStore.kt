// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.repository

import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.model.FsPath

/**
 * Small files the app writes and reads whole (key blobs, a materialized key). It sits next to
 * [FileSystemRepository], with the same root and the same rule: no `java.io.File` outside the data
 * layer. A write replaces the file atomically, so a crash never leaves a half-written secret.
 */
interface SecretFileStore {
    /** Writes [bytes] to [path] (creating directories); [ownerOnly] makes it readable by the app only. */
    suspend fun write(path: FsPath, bytes: ByteArray, ownerOnly: Boolean): Outcome<Unit>

    suspend fun read(path: FsPath): Outcome<ByteArray>

    /** The names (not paths) of the files directly under [directory]; empty if it does not exist. */
    suspend fun listNames(directory: FsPath): Outcome<List<String>>

    /** Deletes a file; fine when it is already gone. */
    suspend fun delete(path: FsPath): Outcome<Unit>
}
