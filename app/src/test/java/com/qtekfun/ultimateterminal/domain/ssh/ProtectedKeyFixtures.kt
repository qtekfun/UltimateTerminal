// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

/**
 * Passphrase-protected Ed25519 keys written by OpenSSH 10.2's `ssh-keygen -N <passphrase>`. They
 * were made for these tests only, protect nothing and are worth nothing; the passphrases are in
 * the open. Only the base64 body is kept here: [pem] puts the frame back.
 */
internal object ProtectedKeyFixtures {
    class Vector(
        val body: String,
        val passphrase: String,
        val publicBlob: String,
        val fingerprint: String
    ) {
        fun pem(): String = "-----BEGIN OPENSSH PRIVATE KEY-----\n" +
            body.chunked(70).joinToString("\n") + "\n-----END OPENSSH PRIVATE KEY-----\n"
    }

    /** `ssh-keygen -t ed25519 -N 'correct horse' -a 4`: aes256-ctr. */
    val ctr = Vector(
        body = "b3BlbnNzaC1rZXktdjEAAAAACmFlczI1Ni1jdHIAAAAGYmNyeXB0AAAA" +
            "GAAAABCWMMT7OymgPi5mBhSXNV5fAAAABAAAAAEAAAAzAAAAC3NzaC1l" +
            "ZDI1NTE5AAAAIPxsqfPNGuRCzYhUivHJSyO+4WLFEV3hPGpzKzJTjqQ7" +
            "AAAAkAT4caXQxF7eRB42rnxY9rtQXkmSTWirWx83GxN2AdvUNAFhM5Fr" +
            "iyUARjvIKN90qIyPEpu9ojiKjoLq45RCNAFS+lEgT4eUTj7nDLbSMm8k" +
            "wwvXTEeS+i+TGJfG9indh1E9Cxub1b61blywLsPQyN+PI/RQrO6PHmaf" +
            "jT/16+VeuLk319c8thO2PkE1+RScXA==",
        passphrase = "correct horse",
        publicBlob = "AAAAC3NzaC1lZDI1NTE5AAAAIPxsqfPNGuRCzYhUivHJSyO+4WLFEV3hPGpzKzJTjqQ7",
        fingerprint = "SHA256:m4Kvt2V5iLMsEgCY5uJIIWMKakAIsXa6payq+VYbtc8"
    )

    /** `ssh-keygen -t ed25519 -N 'correct horse' -a 2 -Z aes256-cbc`. */
    val cbc = Vector(
        body = "b3BlbnNzaC1rZXktdjEAAAAACmFlczI1Ni1jYmMAAAAGYmNyeXB0AAAA" +
            "GAAAABBOdysi7Rogm6VCPERt6D2kAAAAAgAAAAEAAAAzAAAAC3NzaC1l" +
            "ZDI1NTE5AAAAIFl2UUHeMFXSzJ0oBU8ZLQ18JDBa4ZqzhyjODCNQUB6S" +
            "AAAAkN5rbLxyHxPhYSILNNtPiD+k+EwYfYxTqCoViU/UrkIZ0FnOBk+0" +
            "le7mmwN1hZp4vC8gVMWqNoFdIir3ETNA87522xJhZuUHnfKtUJ7bFtRI" +
            "uUtUolnqLjUzfcMI1QZDAbnl8k5VYdDhb9cJWJl4Qb9ZgE5Lm8wpxFJK" +
            "94YvJS28gxIIVJbFPysFXXYfUGuDNw==",
        passphrase = "correct horse",
        publicBlob = "AAAAC3NzaC1lZDI1NTE5AAAAIFl2UUHeMFXSzJ0oBU8ZLQ18JDBa4ZqzhyjODCNQUB6S",
        fingerprint = "SHA256:c8EOBbkp8eKqF67d/O4gaKQAMTP2YnGlL10Mb0sGZV0"
    )

    /** `ssh-keygen -t ed25519 -N 'ünï çödé' -a 2`: a passphrase that is not ASCII. */
    val unicode = Vector(
        body = "b3BlbnNzaC1rZXktdjEAAAAACmFlczI1Ni1jdHIAAAAGYmNyeXB0AAAA" +
            "GAAAABCkCdyf/JapAKoymXf/kseiAAAAAgAAAAEAAAAzAAAAC3NzaC1l" +
            "ZDI1NTE5AAAAIGIXhBtF6sU5b7DG2p8G1SVE51fKtvcOC6uR+CYrtzAn" +
            "AAAAkJV8roB6dVezuWmjewQGWqCzjNMNKK2hasetnCGw6JgfWFgJcdgw" +
            "mHPJRaZWfThj4veF1IT1f3LahmOFM+aX5kfeYPR5gOMA9x+vqAbAaWFv" +
            "AVaZJfWyM3wx473S5hBk/YOfxeZDfqowqI/jKzNCI1ctH010VpoQXBwV" +
            "Cqwqh4N5+ZoxziT9Cca+QYERv/evHw==",
        passphrase = "ünï çödé",
        publicBlob = "AAAAC3NzaC1lZDI1NTE5AAAAIGIXhBtF6sU5b7DG2p8G1SVE51fKtvcOC6uR+CYrtzAn",
        fingerprint = "SHA256:dfrKPo3+vrlTNDI6Dhx0/UaLrKoGLWwSlYFy5OZYp9Q"
    )
}
