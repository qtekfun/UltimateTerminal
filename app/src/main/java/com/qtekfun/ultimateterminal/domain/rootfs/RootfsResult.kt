// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.rootfs

/** Why a root filesystem could not be resolved or downloaded. Never thrown towards the UI. */
sealed interface RootfsError {
    /** The official index could not be fetched or did not contain what was expected. */
    data class CatalogUnavailable(val reason: String) : RootfsError

    /** Only HTTPS sources are downloaded. */
    data class InsecureUrl(val url: String) : RootfsError

    /** Permanent HTTP failure (404...) or a transient one that kept happening. */
    data class HttpStatus(val code: Int) : RootfsError

    data class Network(val reason: String) : RootfsError

    data class SizeMismatch(val expected: Long, val actual: Long) : RootfsError

    data class HashMismatch(val expected: String, val actual: String) : RootfsError

    /** The expected checksum is not a 64-digit hexadecimal SHA-256. */
    data class MalformedChecksum(val value: String) : RootfsError

    data class Storage(val reason: String) : RootfsError
}

sealed interface RootfsResult<out T> {
    data class Success<T>(val value: T) : RootfsResult<T>

    data class Failure(val error: RootfsError) : RootfsResult<Nothing>
}
