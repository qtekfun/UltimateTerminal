// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.distro

import com.qtekfun.ultimateterminal.domain.model.FsPath

/**
 * Unpacks a downloaded root filesystem archive into a directory. It writes only inside
 * [destination] and never follows a link on the way to a file it writes (see `DECISIONS.md`, T07).
 *
 * On failure or cancellation it leaves whatever it had written: the caller owns the directory and
 * deletes it, which is what keeps an install transactional.
 */
interface RootfsExtractor {
    suspend fun extract(
        archive: FsPath,
        destination: FsPath,
        onProgress: (Float?) -> Unit = {}
    ): ExtractionResult
}

data class ExtractionStats(val entries: Long, val bytes: Long, val skippedSpecialFiles: Long)

sealed interface ExtractionError {
    /** A compression format that is not supported (only gzip and plain tar are). */
    data class UnsupportedFormat(val format: String) : ExtractionError

    /** The archive is damaged or truncated. */
    data class Corrupt(val reason: String) : ExtractionError

    /** An entry that would write outside the destination or through a link; the archive is refused. */
    data class UnsafeEntry(val name: String, val reason: String) : ExtractionError

    /** More entries or bytes than any real root filesystem has (a decompression bomb). */
    data class TooLarge(val reason: String) : ExtractionError

    data object NoSpace : ExtractionError

    data class Io(val message: String) : ExtractionError
}

sealed interface ExtractionResult {
    data class Success(val stats: ExtractionStats) : ExtractionResult

    data class Failure(val error: ExtractionError) : ExtractionResult
}
