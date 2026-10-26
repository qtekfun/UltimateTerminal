// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

import java.security.MessageDigest

/**
 * `bcrypt_pbkdf`, the key derivation function OpenSSH uses to protect private keys (the `bcrypt`
 * KDF of `openssh-key-v1`). It is not the password hash `bcrypt`: it runs the expensive Blowfish key
 * schedule of bcrypt over SHA-512 digests of the password and the salt, and mixes [rounds] of them.
 *
 * Written from the algorithm as OpenBSD specifies it, and checked against keys that `ssh-keygen`
 * wrote (see the tests): a wrong step here would fail to open them. The Blowfish rounds are in
 * [BlowfishState].
 */
object BcryptPbkdf {
    private const val HASH_BYTES = 32
    private const val WORDS = 8
    private const val ENCRYPT_ROUNDS = 64
    private val MAGIC = "OxychromaticBlowfishSwatDynamite".toByteArray(Charsets.US_ASCII)

    /**
     * Derives [outLength] bytes from [password] and [salt]. Every temporary buffer holding
     * key-dependent data is wiped before returning; the caller wipes the result when done.
     */
    fun derive(password: ByteArray, salt: ByteArray, rounds: Int, outLength: Int): ByteArray {
        require(rounds >= 1 && outLength >= 1 && salt.isNotEmpty() && password.isNotEmpty())
        val sha = MessageDigest.getInstance("SHA-512")
        val passHash = sha.digest(password)
        val key = ByteArray(outLength)
        val stride = (outLength + HASH_BYTES - 1) / HASH_BYTES
        var remaining = outLength
        var count = 1
        val mixed = ByteArray(HASH_BYTES)
        while (remaining > 0) {
            val saltHash = sha.digest(salt + intBytes(count))
            var block = hash(passHash, saltHash)
            block.copyInto(mixed)
            repeat(rounds - 1) {
                val next = hash(passHash, sha.digest(block))
                block.fill(0)
                block = next
                for (index in 0 until HASH_BYTES) {
                    mixed[index] = (mixed[index].toInt() xor block[index].toInt()).toByte()
                }
            }
            block.fill(0)
            saltHash.fill(0)
            val amount = minOf((outLength + stride - 1) / stride, remaining)
            var written = 0
            while (written < amount) {
                val destination = written * stride + (count - 1)
                if (destination >= outLength) break
                key[destination] = mixed[written]
                written++
            }
            remaining -= written
            count++
        }
        mixed.fill(0)
        passHash.fill(0)
        return key
    }

    private fun intBytes(value: Int) = byteArrayOf(
        (value ushr TOP_SHIFT).toByte(),
        (value ushr (TOP_SHIFT - BYTE_BITS)).toByte(),
        (value ushr BYTE_BITS).toByte(),
        value.toByte()
    )

    // bcrypt_hash: the key schedule keyed by the two SHA-512 digests, then 64 encryptions of a
    // fixed text. The result is written little-endian, unlike bcrypt's own output.
    private fun hash(passHash: ByteArray, saltHash: ByteArray): ByteArray {
        val state = BlowfishState()
        state.expandWithSalt(saltHash, passHash)
        repeat(ENCRYPT_ROUNDS) {
            state.expand(saltHash)
            state.expand(passHash)
        }
        val words = IntArray(WORDS) { index -> bigEndianWord(MAGIC, index * INT_BYTES) }
        repeat(ENCRYPT_ROUNDS) { state.encryptBlocks(words) }
        val out = ByteArray(HASH_BYTES)
        for (index in 0 until WORDS) {
            for (byte in 0 until INT_BYTES) {
                out[index * INT_BYTES + byte] = (words[index] ushr (BYTE_BITS * byte)).toByte()
            }
        }
        state.wipe()
        words.fill(0)
        return out
    }

    private fun bigEndianWord(data: ByteArray, offset: Int): Int {
        var value = 0
        for (index in 0 until INT_BYTES) {
            value = (value shl BYTE_BITS) or (data[offset + index].toInt() and BYTE_MASK)
        }
        return value
    }
}

private const val TOP_SHIFT = 24

/** Blowfish with the expensive key schedule of bcrypt (`eksblowfish`). */
internal class BlowfishState {
    private val p = BlowfishTables.P.copyOf()
    private val s = BlowfishTables.S0 + BlowfishTables.S1 + BlowfishTables.S2 + BlowfishTables.S3
    private var left = 0
    private var right = 0

    /** One expansion with [key] only (`Blowfish_expand0state`). */
    fun expand(key: ByteArray) = schedule(null, key)

    /** One expansion with [key] and [salt] (`Blowfish_expandstate`). */
    fun expandWithSalt(salt: ByteArray, key: ByteArray) = schedule(salt, key)

    /** Encrypts pairs of words in place, in ECB, as `blf_enc` does. */
    fun encryptBlocks(words: IntArray) {
        for (index in words.indices step 2) {
            encipher(words[index], words[index + 1])
            words[index] = left
            words[index + 1] = right
        }
    }

    fun wipe() {
        p.fill(0)
        s.fill(0)
        left = 0
        right = 0
    }

    private fun schedule(salt: ByteArray?, key: ByteArray) {
        var keyPosition = 0
        for (index in p.indices) {
            p[index] = p[index] xor streamWord(key, keyPosition)
            keyPosition = (keyPosition + INT_BYTES) % key.size
        }
        var saltPosition = 0
        left = 0
        right = 0
        // P and then the S-boxes are overwritten with the running output of the cipher; the salt
        // is mixed into the input of each step, cycling through it.
        for (index in 0 until p.size + s.size step 2) {
            if (salt != null) {
                left = left xor streamWord(salt, saltPosition)
                saltPosition = (saltPosition + INT_BYTES) % salt.size
                right = right xor streamWord(salt, saltPosition)
                saltPosition = (saltPosition + INT_BYTES) % salt.size
            }
            encipher(left, right)
            if (index < p.size) {
                p[index] = left
                p[index + 1] = right
            } else {
                s[index - p.size] = left
                s[index - p.size + 1] = right
            }
        }
    }

    // Four bytes of [data] from [start] as a big-endian word, wrapping around at the end.
    private fun streamWord(data: ByteArray, start: Int): Int {
        var value = 0
        for (offset in 0 until INT_BYTES) {
            value =
                (value shl BYTE_BITS) or (data[(start + offset) % data.size].toInt() and BYTE_MASK)
        }
        return value
    }

    // 16 rounds of the Feistel network; the result is left in [left] and [right].
    private fun encipher(leftIn: Int, rightIn: Int) {
        var l = leftIn xor p[0]
        var r = rightIn
        for (round in 1..ROUNDS step 2) {
            r = r xor (f(l) xor p[round])
            l = l xor (f(r) xor p[round + 1])
        }
        left = r xor p[ROUNDS + 1]
        right = l
    }

    private fun f(x: Int): Int {
        val a = s[x ushr TOP_SHIFT]
        val b = s[SBOX + ((x ushr (TOP_SHIFT - BYTE_BITS)) and BYTE_MASK)]
        val c = s[S2 + ((x ushr BYTE_BITS) and BYTE_MASK)]
        val d = s[S3 + (x and BYTE_MASK)]
        return ((a + b) xor c) + d
    }

    private companion object {
        const val ROUNDS = 16
        const val SBOX = 256
        const val S2 = SBOX + SBOX
        const val S3 = S2 + SBOX
    }
}
