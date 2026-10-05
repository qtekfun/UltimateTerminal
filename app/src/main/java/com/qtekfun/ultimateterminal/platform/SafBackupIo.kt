// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.platform

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.qtekfun.ultimateterminal.domain.backup.BackupSink
import com.qtekfun.ultimateterminal.domain.backup.BackupSource
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Reads a backup from a document the user picked with the system file picker. */
class ContentResolverBackupSource(private val resolver: ContentResolver, private val uri: Uri) :
    BackupSource {
    override suspend fun open(): InputStream =
        resolver.openInputStream(uri) ?: throw IOException("cannot open the backup file")
}

/** Writes a backup to a document the user created with the system file picker. */
class ContentResolverBackupSink(private val resolver: ContentResolver, private val uri: Uri) :
    BackupSink {
    // "wt" truncates: a document that already existed must not keep the tail of an older file.
    override suspend fun open(): OutputStream =
        resolver.openOutputStream(uri, "wt") ?: throw IOException("cannot write the backup file")

    override suspend fun discard() {
        // Best effort: not every provider lets the app delete the document it was given.
        runCatching { DocumentsContract.deleteDocument(resolver, uri) }
    }
}

/** The name the user gave the document in the picker, or null when the provider does not say. */
fun documentName(resolver: ContentResolver, uri: Uri): String? = runCatching {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()
