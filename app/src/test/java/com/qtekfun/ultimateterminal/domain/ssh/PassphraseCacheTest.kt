// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PassphraseCacheTest {
    private var now = 1_000L
    private val cache = PassphraseCache(ttlMillis = 100) { now }
    private val secret = "hunter22".toByteArray()

    @Test
    fun aRememberedPassphraseComesBackUntilItExpires() {
        cache.remember("a", secret)
        now += 99
        assertArrayEquals(secret, cache.recall("a"))
        now += 1
        assertNull(cache.recall("a"))
    }

    @Test
    fun otherKeysAreNotAffected() {
        cache.remember("a", secret)
        assertNull(cache.recall("b"))
    }

    @Test
    fun whatIsHandedOutAndWhatIsKeptAreCopies() {
        val given = secret.copyOf()
        cache.remember("a", given)
        given.fill(0)
        val first = cache.recall("a")
        assertArrayEquals(secret, first)
        first?.fill(0)
        val second = cache.recall("a")
        assertArrayEquals(secret, second)
        assertNotSame(first, second)
    }

    @Test
    fun rememberingAgainReplacesAndRestartsTheClock() {
        cache.remember("a", secret)
        now += 90
        cache.remember("a", "other-pass".toByteArray())
        now += 90
        assertEquals("other-pass", String(cache.recall("a") ?: ByteArray(0)))
    }

    @Test
    fun forgettingOneOrAllEmptiesTheCache() {
        cache.remember("a", secret)
        cache.remember("b", secret)
        cache.forget("a")
        assertNull(cache.recall("a"))
        assertArrayEquals(secret, cache.recall("b"))
        cache.forgetAll()
        assertNull(cache.recall("b"))
    }
}
