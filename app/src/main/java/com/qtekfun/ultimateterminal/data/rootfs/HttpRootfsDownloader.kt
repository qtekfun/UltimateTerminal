// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.rootfs

import com.qtekfun.ultimateterminal.data.rootfs.verify.Sha256Verifier
import com.qtekfun.ultimateterminal.data.rootfs.verify.Verification
import com.qtekfun.ultimateterminal.domain.rootfs.DownloadProgress
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsDownloader
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsError
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsResult
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsSource
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads to `<destination>.part`, resuming with an HTTP Range request after a failure, and only
 * renames it to the destination once its size and SHA-256 match. A wrong archive is deleted and
 * reported; it is never installed.
 *
 * Transient failures (network errors, 5xx, 408, 429) are retried with exponential backoff up to
 * [maxAttempts] times, keeping the partial file so the next attempt continues where it stopped.
 */
class HttpRootfsDownloader(
    private val client: OkHttpClient,
    private val verifier: Sha256Verifier = Sha256Verifier(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val backoff: suspend (attempt: Int) -> Unit = { attempt ->
        delay(BASE_BACKOFF_MS shl (attempt - 1))
    },
    private val requireHttps: Boolean = true,
    private val userAgent: String = "UltimateTerminal"
) : RootfsDownloader {

    override suspend fun download(
        source: RootfsSource,
        destinationPath: String,
        onProgress: (DownloadProgress) -> Unit
    ): RootfsResult<Unit> = if (requireHttps && !source.url.startsWith("https://")) {
        RootfsResult.Failure(RootfsError.InsecureUrl(source.url))
    } else {
        downloadWithRetries(
            source,
            File(destinationPath),
            File("$destinationPath.part"),
            onProgress
        )
    }

    private suspend fun downloadWithRetries(
        source: RootfsSource,
        destination: File,
        partial: File,
        onProgress: (DownloadProgress) -> Unit
    ): RootfsResult<Unit> {
        var last: Outcome = Outcome.Retry(RootfsError.Network("no attempt was made"))
        for (attempt in 1..maxAttempts) {
            last = withContext(io) { attemptOnce(source, destination, partial, onProgress) }
            if (last !is Outcome.Retry) break
            if (attempt < maxAttempts) backoff(attempt)
        }
        return when (val outcome = last) {
            Outcome.Done -> RootfsResult.Success(Unit)
            is Outcome.Fatal -> RootfsResult.Failure(outcome.error)
            is Outcome.Retry -> RootfsResult.Failure(outcome.error)
        }
    }

    private sealed interface Outcome {
        data object Done : Outcome

        data class Retry(val error: RootfsError) : Outcome

        data class Fatal(val error: RootfsError) : Outcome
    }

    private suspend fun attemptOnce(
        source: RootfsSource,
        destination: File,
        partial: File,
        onProgress: (DownloadProgress) -> Unit
    ): Outcome {
        val outcome: Outcome = try {
            partial.parentFile?.mkdirs()
            val offset = partial.length()
            val request = Request.Builder()
                .url(source.url)
                .header("User-Agent", userAgent)
                .apply { if (offset > 0) header("Range", "bytes=$offset-") }
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    response.code == HTTP_OK -> {
                        val total = response.body.contentLength().takeIf { it >= 0 }
                        copy(response.body.byteStream(), partial, append = false, total, onProgress)
                        finish(source, destination, partial)
                    }

                    response.code == HTTP_PARTIAL &&
                        startsAt(response.header("Content-Range"), offset) -> {
                        val total = response.body.contentLength().takeIf { it >= 0 }?.plus(offset)
                        copy(response.body.byteStream(), partial, append = true, total, onProgress)
                        finish(source, destination, partial)
                    }

                    response.code == HTTP_PARTIAL -> restart(
                        partial,
                        RootfsError.HttpStatus(response.code)
                    )

                    response.code == HTTP_RANGE_NOT_SATISFIABLE -> whenRangeRefused(
                        source,
                        destination,
                        partial
                    )

                    isTransient(
                        response.code
                    ) -> Outcome.Retry(RootfsError.HttpStatus(response.code))

                    else -> Outcome.Fatal(RootfsError.HttpStatus(response.code))
                }
            }
        } catch (e: IOException) {
            Outcome.Retry(RootfsError.Network(e.message ?: e.javaClass.simpleName))
        }
        return outcome
    }

    /** The partial file may already be the whole archive (the server has nothing past its end). */
    private fun whenRangeRefused(source: RootfsSource, destination: File, partial: File): Outcome {
        val complete = partial.length() > 0 &&
            verifier.verify(partial, source.sha256, source.sizeBytes) == Verification.Verified
        return if (complete) {
            finish(source, destination, partial)
        } else {
            restart(partial, RootfsError.HttpStatus(HTTP_RANGE_NOT_SATISFIABLE))
        }
    }

    private fun restart(partial: File, error: RootfsError): Outcome {
        partial.delete()
        return Outcome.Retry(error)
    }

    private suspend fun copy(
        input: InputStream,
        partial: File,
        append: Boolean,
        total: Long?,
        onProgress: (DownloadProgress) -> Unit
    ) {
        var done = if (append) partial.length() else 0L
        FileOutputStream(partial, append).use { output ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                done += read
                onProgress(DownloadProgress(done, total))
            }
        }
    }

    private fun finish(source: RootfsSource, destination: File, partial: File): Outcome =
        when (val check = verifier.verify(partial, source.sha256, source.sizeBytes)) {
            Verification.Verified -> install(partial, destination)

            is Verification.SizeMismatch -> rejected(
                partial,
                RootfsError.SizeMismatch(check.expected, check.actual)
            )

            is Verification.HashMismatch -> rejected(
                partial,
                RootfsError.HashMismatch(check.expected, check.actual)
            )

            is Verification.MalformedChecksum -> rejected(
                partial,
                RootfsError.MalformedChecksum(check.value)
            )
        }

    private fun rejected(partial: File, error: RootfsError): Outcome {
        partial.delete()
        return Outcome.Fatal(error)
    }

    /** Same directory, so the rename is atomic: the destination is either absent or complete. */
    private fun install(partial: File, destination: File): Outcome = try {
        Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        Outcome.Done
    } catch (e: IOException) {
        Outcome.Fatal(RootfsError.Storage(e.message ?: e.javaClass.simpleName))
    }

    private companion object {
        fun isTransient(code: Int) = code >= HTTP_SERVER_ERROR || code == HTTP_REQUEST_TIMEOUT ||
            code == HTTP_TOO_MANY_REQUESTS

        /** True when a `Content-Range: bytes start-end/total` header starts at [offset]. */
        fun startsAt(contentRange: String?, offset: Long): Boolean =
            contentRange?.let { CONTENT_RANGE.matchEntire(it.trim()) }
                ?.groupValues?.get(1)?.toLongOrNull() == offset

        const val DEFAULT_MAX_ATTEMPTS = 4
        const val BASE_BACKOFF_MS = 1_000L
        const val BUFFER_SIZE = 64 * 1024
        const val HTTP_OK = 200
        const val HTTP_PARTIAL = 206
        const val HTTP_REQUEST_TIMEOUT = 408
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVER_ERROR = 500
        val CONTENT_RANGE = Regex("""bytes (\d+)-\d+/(?:\d+|\*)""")
    }
}
