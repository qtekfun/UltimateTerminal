// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ssh

import android.content.ContentResolver
import android.net.Uri

/** Reads and writes the small key files the user picks with the system file chooser. */
internal object KeyFiles {
    // A key file is a few KiB; anything over the limit is not a key.
    const val MAX_BYTES = 64 * 1024

    /**
     * The text of [source], or null if it cannot be read or is too big. Read in a loop because
     * `InputStream.readNBytes` needs Android 13, and the app supports Android 8.
     */
    fun read(resolver: ContentResolver, source: Uri): String? = runCatching {
        resolver.openInputStream(source)?.use { stream ->
            val buffer = ByteArray(MAX_BYTES + 1)
            var size = 0
            while (size < buffer.size) {
                val count = stream.read(buffer, size, buffer.size - size)
                if (count < 0) break
                size += count
            }
            if (size <= MAX_BYTES) String(buffer, 0, size, Charsets.UTF_8) else null
        }
    }.getOrNull()

    /** Writes [text] to [destination], replacing its content; false if it could not be written. */
    fun write(resolver: ContentResolver, destination: Uri, text: String): Boolean = runCatching {
        resolver.openOutputStream(destination, "wt")?.use {
            it.write(text.toByteArray(Charsets.UTF_8))
        } != null
    }.getOrDefault(false)
}
