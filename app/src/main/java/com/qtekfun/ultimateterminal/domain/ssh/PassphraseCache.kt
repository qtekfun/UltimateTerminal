// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

/**
 * Passphrases the user chose to keep unlocked for a while, so that reconnecting does not ask again.
 * They live in memory only, for [ttlMillis] after they were typed, and are lost when the process
 * ends. Reading hands out a copy that the caller wipes; an expired or forgotten entry is zeroed.
 */
class PassphraseCache(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val now: () -> Long = { System.nanoTime() / NANOS_PER_MILLI }
) {
    private class Entry(val passphrase: ByteArray, val expiresAt: Long)

    private val entries = HashMap<String, Entry>()

    @Synchronized
    fun remember(alias: String, passphrase: ByteArray) {
        entries.remove(alias)?.passphrase?.fill(0)
        entries[alias] = Entry(passphrase.copyOf(), now() + ttlMillis)
    }

    /** A copy of the passphrase for [alias], or null if none is kept or it has expired. */
    @Synchronized
    fun recall(alias: String): ByteArray? {
        val entry = entries[alias] ?: return null
        return if (now() >= entry.expiresAt) {
            forget(alias)
            null
        } else {
            entry.passphrase.copyOf()
        }
    }

    @Synchronized
    fun forget(alias: String) {
        entries.remove(alias)?.passphrase?.fill(0)
    }

    @Synchronized
    fun forgetAll() {
        entries.values.forEach { it.passphrase.fill(0) }
        entries.clear()
    }

    companion object {
        /** Fifteen minutes. */
        const val DEFAULT_TTL_MILLIS = 15L * 60 * 1000
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
