// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.rootfs

/** Excerpts of the real official indexes (fetched 2026-10-04), trimmed to what the parsers read. */
internal object Samples {
    const val ALPINE_SHA = "9bf70a7f18ea44094cbb5f70c58f9af129c8214745743db0e68e5502cc2ce773"

    val ALPINE_YAML = """
        ---
        -
          title: "Netboot"
          desc: |
            Kernel, initramfs and modloop for
            netboot.

          branch: v3.24
          arch: aarch64
        -
          title: "Mini root filesystem"
          desc: |
            Minimal root filesystem for containers.
          arch: aarch64
          version: 3.24.2
          flavor: alpine-minirootfs
          file: alpine-minirootfs-3.24.2-aarch64.tar.gz
          iso: alpine-minirootfs-3.24.2-aarch64.tar.gz
          date: 2026-09-17
          size: 4028030
          sha256: $ALPINE_SHA
          sha512: c717aa68d67a354c7f46488eacb459c7a80951bcd7ea255f04a574aba1a6c5144f2117866e674f27fc92df0f05dd7685c1e6824b88ddd41994803d6fb025395c
    """.trimIndent()

    val UBUNTU_SUMS = """
        6bc2cde3930ad088b3bb46fa45279e96d25bc3810f209850ecbe4722711874f9 *ubuntu-base-24.04.3-base-amd64.tar.gz
        7b2dced6dd56ad5e4a813fa25c8de307b655fdabc6ea9213175a92c48dabb048 *ubuntu-base-24.04.3-base-arm64.tar.gz
        04207713ece899c3740823d33690441ad3a7f0ded1101aca744e2b0f37ac7ff2 *ubuntu-base-24.04.4-base-arm64.tar.gz
        991520b47f6586f38a78505cf016e300b6191bb8ff86a0723481ec23a37ab7f4 *ubuntu-base-24.04.4-base-armhf.tar.gz
        1111111111111111111111111111111111111111111111111111111111111111 *ubuntu-base-24.04.10-base-arm64.tar.gz
        2222222222222222222222222222222222222222222222222222222222222222 *ubuntu-base-24.04.9-base-arm64.tar.gz
    """.trimIndent()

    const val DEBIAN_SHA = "bd36565c0fdebaf0f3af5c3b4ce610ca085ced32e9e9da850d95912f5f18f47b"

    val DEBIAN_MANIFEST = """
        {"schemaVersion":2,"mediaType":"application/vnd.oci.image.manifest.v1+json",
         "config":{"mediaType":"application/vnd.oci.image.config.v1+json","digest":"sha256:3fde68","size":466},
         "layers":[{"mediaType":"application/vnd.oci.image.layer.v1.tar+gzip",
                    "digest":"sha256:$DEBIAN_SHA","size":30189691}]}
    """.trimIndent()
}
