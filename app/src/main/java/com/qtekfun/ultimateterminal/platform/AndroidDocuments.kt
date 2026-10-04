// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

/** Reads and writes the files the user picks with the system document picker. */
class AndroidDocuments @Inject constructor(@ApplicationContext private val context: Context) {
    /**
     * The bytes of [uri], or null if it cannot be read or is longer than [maxBytes]. The read stops
     * one byte past the limit, so a huge file is never loaded into memory.
     */
    fun read(uri: Uri, maxBytes: Int): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { readUpTo(it, maxBytes) }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun readUpTo(input: InputStream, maxBytes: Int): ByteArray? {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(CHUNK)
        var total = 0
        while (total <= maxBytes) {
            val count = input.read(chunk, 0, minOf(chunk.size, maxBytes + 1 - total))
            if (count < 0) break
            buffer.write(chunk, 0, count)
            total += count
        }
        return if (total > maxBytes) null else buffer.toByteArray()
    }

    /** The name the user sees for [uri] (a file name), or an empty string. */
    fun displayName(uri: Uri): String = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else "" }
            .orEmpty()
    } catch (_: SecurityException) {
        ""
    }

    /** Replaces the content of [uri] with [text]; false if it could not be written. */
    fun write(uri: Uri, text: String): Boolean = try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } !=
            null
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private companion object {
        const val CHUNK = 8 * 1024
    }
}
