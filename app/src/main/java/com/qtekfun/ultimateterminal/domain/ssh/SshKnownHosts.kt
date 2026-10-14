// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

/**
 * Where the host keys the user trusted are kept (D-SSH-2). OpenSSH asks "are you sure you want to
 * continue connecting?" the first time and writes the answer to a `known_hosts` file; if that file
 * does not survive, the question comes back at every connection. The app therefore owns the file:
 * it lives in the app's private storage, outside any distro, and is bound into the guest.
 */
object SshKnownHosts {
    /** A folder of the storage root; it is created before a connection starts. */
    const val STORAGE_DIRECTORY = "ssh-known-hosts"

    /** Where the connection sees that folder. */
    const val GUEST_DIRECTORY = "/ut-ssh"

    /** The file that new host keys are added to (the first one of `UserKnownHostsFile`). */
    const val GUEST_FILE = "$GUEST_DIRECTORY/known_hosts"

    /** The distro's own file, read as well so what someone trusted there by hand still counts. */
    private const val GUEST_OWN_FILE = "/root/.ssh/known_hosts"

    /** The value of `-o UserKnownHostsFile=`: ssh adds to the first file and reads all of them. */
    const val OPTION_VALUE = "$GUEST_FILE $GUEST_OWN_FILE"
}
