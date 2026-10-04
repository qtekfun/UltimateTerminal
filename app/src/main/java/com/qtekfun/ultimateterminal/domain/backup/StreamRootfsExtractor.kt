// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.backup

import com.qtekfun.ultimateterminal.domain.distro.ExtractionResult
import com.qtekfun.ultimateterminal.domain.model.FsPath
import java.io.InputStream

/**
 * Unpacks a root filesystem from a stream, with the same safety checks as the installer's
 * extractor (no path traversal, no writing through links, size limits). A backup holds the archive
 * inside another file, so it cannot be handed over as a path.
 */
interface StreamRootfsExtractor {
    /** [totalBytes] is only used to report progress; the stream is not closed. */
    suspend fun extract(
        input: InputStream,
        totalBytes: Long,
        destination: FsPath,
        onProgress: (Float?) -> Unit = {}
    ): ExtractionResult
}
