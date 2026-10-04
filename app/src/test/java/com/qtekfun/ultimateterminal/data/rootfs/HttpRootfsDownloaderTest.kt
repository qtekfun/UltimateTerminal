// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.rootfs

import com.qtekfun.ultimateterminal.data.rootfs.verify.Sha256Verifier
import com.qtekfun.ultimateterminal.domain.rootfs.Architecture
import com.qtekfun.ultimateterminal.domain.rootfs.DistroFamily
import com.qtekfun.ultimateterminal.domain.rootfs.DownloadProgress
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsError
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsResult
import com.qtekfun.ultimateterminal.domain.rootfs.RootfsSource
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class HttpRootfsDownloaderTest {
    @TempDir
    lateinit var dir: File

    private val server = MockWebServer()
    private val backoffs = mutableListOf<Int>()

    private val content = ByteArray(200_000) { (it * 7 % 251).toByte() }
    private val destination get() = File(dir, "rootfs.tar.gz")
    private val partial get() = File(dir, "rootfs.tar.gz.part")

    @BeforeEach
    fun start() = server.start()

    @AfterEach
    fun stop() = runCatching { server.close() }.let { }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun source(
        bytes: ByteArray = content,
        sha: String = sha256(bytes),
        size: Long? = bytes.size.toLong()
    ) = RootfsSource(
        DistroFamily.ALPINE,
        Architecture.ARM64,
        "1",
        server.url("/rootfs.tar.gz").toString(),
        sha,
        size
    )

    private fun downloader(maxAttempts: Int = 4, requireHttps: Boolean = false) =
        HttpRootfsDownloader(
            client = OkHttpClient(),
            verifier = Sha256Verifier(),
            io = Dispatchers.Unconfined,
            maxAttempts = maxAttempts,
            backoff = { backoffs += it },
            requireHttps = requireHttps
        )

    private fun body(bytes: ByteArray) = Buffer().write(bytes)

    private fun ok(bytes: ByteArray = content) =
        MockResponse.Builder().code(200).body(body(bytes)).build()

    private suspend fun fetch(
        src: RootfsSource = source(),
        downloader: HttpRootfsDownloader = downloader(),
        onProgress: (DownloadProgress) -> Unit = {}
    ) = downloader.download(src, destination.path, onProgress)

    private fun assertFailure(expected: RootfsError, result: RootfsResult<Unit>) =
        assertEquals(RootfsResult.Failure(expected), result)

    @Test
    fun aCompleteDownloadIsInstalledOnlyAfterItVerifies() = runTest {
        server.enqueue(ok())
        val progress = mutableListOf<DownloadProgress>()

        assertEquals(RootfsResult.Success(Unit), fetch(onProgress = { progress += it }))

        assertTrue(content.contentEquals(destination.readBytes()))
        assertFalse(partial.exists())
        assertEquals(
            DownloadProgress(content.size.toLong(), content.size.toLong()),
            progress.last()
        )
        assertTrue(progress.zipWithNext().all { (a, b) -> a.bytesDone < b.bytesDone })
    }

    @Test
    fun aSizeThatTheServerDoesNotAnnounceStillDownloads() = runTest {
        server.enqueue(MockResponse.Builder().code(200).chunkedBody(body(content), 4096).build())
        val progress = mutableListOf<DownloadProgress>()

        assertEquals(RootfsResult.Success(Unit), fetch(onProgress = { progress += it }))

        assertEquals(null, progress.first().totalBytes)
    }

    @Test
    fun anExistingDestinationIsReplacedByTheNewVerifiedArchive() = runTest {
        destination.writeText("old rootfs")
        server.enqueue(ok())

        assertEquals(RootfsResult.Success(Unit), fetch())

        assertTrue(content.contentEquals(destination.readBytes()))
    }

    @Test
    fun insecureUrlsAreRefusedBeforeAnyRequest() = runTest {
        val http = source()
        assertFailure(
            RootfsError.InsecureUrl(http.url),
            fetch(http, downloader(requireHttps = true))
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun anInterruptedDownloadResumesWithARangeRequest() = runTest {
        val half = content.size / 2
        server.enqueue(
            MockResponse.Builder().code(200).body(body(content.copyOfRange(0, half)))
                .setHeader("Content-Length", content.size)
                .onResponseEnd(SocketEffect.CloseSocket())
                .build()
        )
        server.enqueue(
            MockResponse.Builder().code(206)
                .setHeader("Content-Range", "bytes $half-${content.size - 1}/${content.size}")
                .body(body(content.copyOfRange(half, content.size))).build()
        )

        assertEquals(RootfsResult.Success(Unit), fetch())

        assertEquals(null, server.takeRequest().headers["Range"])
        assertEquals("bytes=$half-", server.takeRequest().headers["Range"])
        assertEquals(listOf(1), backoffs)
        assertTrue(content.contentEquals(destination.readBytes()))
    }

    @Test
    fun aServerThatIgnoresRangeStartsOverInsteadOfCorruptingTheFile() = runTest {
        partial.writeBytes(ByteArray(1000) { 9 })
        server.enqueue(ok())

        assertEquals(RootfsResult.Success(Unit), fetch())

        assertTrue(content.contentEquals(destination.readBytes()))
        assertEquals("bytes=1000-", server.takeRequest().headers["Range"])
    }

    @Test
    fun aPartialResponseThatStartsElsewhereDiscardsThePartialAndRetries() = runTest {
        partial.writeBytes(content.copyOfRange(0, 1000))
        server.enqueue(
            MockResponse.Builder().code(
                206
            ).setHeader("Content-Range", "bytes 5-9/10").body("12345").build()
        )
        server.enqueue(ok())

        assertEquals(RootfsResult.Success(Unit), fetch())

        assertEquals(listOf(1), backoffs)
        assertEquals("bytes=1000-", server.takeRequest().headers["Range"])
        assertEquals(null, server.takeRequest().headers["Range"])
        assertTrue(content.contentEquals(destination.readBytes()))
    }

    @Test
    fun aPartialResponseWithoutContentRangeIsNotTrusted() = runTest {
        partial.writeBytes(content.copyOfRange(0, 1000))
        server.enqueue(MockResponse.Builder().code(206).body("xyz").build())
        server.enqueue(ok())

        assertEquals(RootfsResult.Success(Unit), fetch())

        assertTrue(content.contentEquals(destination.readBytes()))
    }

    @Test
    fun anAlreadyCompletePartialIsVerifiedWhenTheServerRefusesTheRange() = runTest {
        partial.writeBytes(content)
        server.enqueue(MockResponse.Builder().code(416).build())

        assertEquals(RootfsResult.Success(Unit), fetch())

        assertEquals(1, server.requestCount)
        assertTrue(content.contentEquals(destination.readBytes()))
        assertFalse(partial.exists())
    }

    @Test
    fun aBadPartialThatTheServerRefusesIsDiscardedAndDownloadedAgain() = runTest {
        partial.writeBytes(ByteArray(content.size) { 1 })
        server.enqueue(MockResponse.Builder().code(416).build())
        server.enqueue(ok())

        assertEquals(RootfsResult.Success(Unit), fetch())

        assertEquals(2, server.requestCount)
        assertTrue(content.contentEquals(destination.readBytes()))
    }

    @Test
    fun aRefusedRangeWithNothingToResumeStartsFromScratch() = runTest {
        server.enqueue(MockResponse.Builder().code(416).build())
        server.enqueue(ok())

        assertEquals(RootfsResult.Success(Unit), fetch())

        assertTrue(content.contentEquals(destination.readBytes()))
    }

    @Test
    fun aMissingArchiveFailsAtOnceWithoutRetrying() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())

        assertFailure(RootfsError.HttpStatus(404), fetch())

        assertEquals(1, server.requestCount)
        assertTrue(backoffs.isEmpty())
        assertFalse(destination.exists())
    }

    @ParameterizedTest
    @ValueSource(ints = [500, 503, 429])
    fun transientHttpErrorsAreRetriedAfterBackingOff(code: Int) = runTest {
        server.enqueue(MockResponse.Builder().code(code).build())
        server.enqueue(ok())

        assertEquals(RootfsResult.Success(Unit), fetch())

        assertEquals(listOf(1), backoffs)
    }

    @Test
    fun aRequestTimeoutIsRetriedAfterBackingOff() = runTest {
        // OkHttp itself retries a 408 once, so two in a row are what reach the downloader.
        repeat(2) { server.enqueue(MockResponse.Builder().code(408).build()) }
        server.enqueue(ok())

        assertEquals(RootfsResult.Success(Unit), fetch())

        assertEquals(listOf(1), backoffs)
    }

    @Test
    fun retriesStopAfterTheMaximumAttemptsWithGrowingBackoff() = runTest {
        repeat(4) { server.enqueue(MockResponse.Builder().code(503).build()) }

        assertFailure(RootfsError.HttpStatus(503), fetch())

        assertEquals(4, server.requestCount)
        assertEquals(listOf(1, 2, 3), backoffs)
        assertFalse(destination.exists())
    }

    @Test
    fun aServerThatIsDownIsANetworkError() = runTest {
        val src = source()
        server.close()

        val result = fetch(src, downloader(maxAttempts = 2))

        assertTrue(result is RootfsResult.Failure && result.error is RootfsError.Network)
        assertEquals(listOf(1), backoffs)
    }

    @Test
    fun aWrongHashIsRejectedAndNothingIsLeftBehind() = runTest {
        server.enqueue(ok(content.reversedArray()))
        val expected = sha256(content)

        val result = fetch(source(sha = expected))

        assertTrue(result is RootfsResult.Failure)
        val error = (result as RootfsResult.Failure).error as RootfsError.HashMismatch
        assertEquals(expected, error.expected)
        assertFalse(destination.exists())
        assertFalse(partial.exists())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aWrongSizeIsRejectedAndNothingIsLeftBehind() = runTest {
        server.enqueue(ok())

        assertFailure(
            RootfsError.SizeMismatch(expected = 5, actual = content.size.toLong()),
            fetch(source(size = 5))
        )

        assertFalse(destination.exists())
        assertFalse(partial.exists())
    }

    @Test
    fun aMalformedExpectedChecksumIsRejectedAndNothingIsLeftBehind() = runTest {
        server.enqueue(ok())

        assertFailure(RootfsError.MalformedChecksum("nope"), fetch(source(sha = "nope")))

        assertFalse(destination.exists())
        assertFalse(partial.exists())
    }

    @Test
    fun aDestinationThatCannotBeWrittenIsAStorageError() = runTest {
        File(destination, "occupied").mkdirs()
        server.enqueue(ok())

        val result = fetch()

        assertTrue(result is RootfsResult.Failure && result.error is RootfsError.Storage)
    }

    @Test
    fun cancellingKeepsThePartialFileForALaterResumeAndInstallsNothing() = runTest {
        server.enqueue(ok())
        val job = launch {
            val self = currentCoroutineContext().job
            fetch(onProgress = { self.cancel() })
        }

        job.join()

        assertTrue(job.isCancelled)
        assertFalse(destination.exists())
        assertTrue(partial.exists() && partial.length() > 0)
    }
}
