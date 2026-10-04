// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.local

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** In-memory database on the host JVM, using the bundled SQLite build for JVM. */
fun inMemoryDatabase(): UltimateTerminalDatabase {
    val context = mockk<Context>(relaxed = true)
    every { context.applicationContext } returns context
    return Room.inMemoryDatabaseBuilder<UltimateTerminalDatabase>(context)
        .setDriver(BundledSQLiteDriver())
        .build()
}

/**
 * `runTest` for tests on a real database. Room opens SQLite on a real thread, and loading its
 * native library and touching the disk can take more than a minute on a busy machine, which is
 * how long `runTest` waits by default: some of these tests failed with `UncompletedCoroutinesError`
 * only when the machine was loaded. Five minutes is generous but still ends a test that is stuck.
 */
fun runDatabaseTest(testBody: suspend TestScope.() -> Unit): TestResult =
    runTest(timeout = DATABASE_TEST_TIMEOUT, testBody = testBody)

private val DATABASE_TEST_TIMEOUT = 5.minutes
