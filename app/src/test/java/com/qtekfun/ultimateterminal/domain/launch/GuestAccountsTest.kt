// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.launch

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.getOrNull
import com.qtekfun.ultimateterminal.domain.model.FsPath
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import com.qtekfun.ultimateterminal.fakes.InMemoryFileSystemRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuestAccountsTest {
    private val passwd = """
        # a comment
        root:x:0:0:Super User:/root:/bin/bash
        daemon:x:1:1::/:/usr/sbin/nologin
        broken line
        nobody:x:65534:65534::/nonexistent:/bin/false
        dev:x:1000:1000::/home/dev:/bin/zsh
        odd:x:abc:1::/h:/bin/sh
    """.trimIndent() + "\n"

    @Test
    fun `finds a user with its ids home and shell, skipping comments and broken lines`() {
        assertEquals(
            GuestAccount("dev", 1000, 1000, "/home/dev", "/bin/zsh"),
            GuestAccounts.find(passwd, "dev")
        )
        assertNull(GuestAccounts.find(passwd, "odd"))
        assertNull(GuestAccounts.find(passwd, "ghost"))
        assertNull(GuestAccounts.find(passwd, "# a comment"))
    }

    @Test
    fun `a shell that only refuses logins or is not absolute becomes the plain shell`() {
        assertEquals("/bin/sh", GuestAccounts.find(passwd, "nobody")?.shell)
        assertEquals("/bin/sh", GuestAccounts.loginShell(""))
        assertEquals("/bin/sh", GuestAccounts.loginShell("bash"))
        assertEquals("/bin/sh", GuestAccounts.loginShell("/sbin/nologin"))
        assertEquals("/bin/fish", GuestAccounts.loginShell(" /bin/fish "))
    }

    @Test
    fun `an empty home falls back to the home under home`() {
        val account = GuestAccounts.find("svc:x:5:5:::/bin/sh\n", "svc")
        assertEquals("/home/svc", account?.home)
    }

    @Test
    fun `add takes the first free id from 1000 and a group of the same name`() {
        val files = checkNotNull(GuestAccounts.add(passwd, "root:x:0:\ndev:x:1000:\n", "ann"))

        assertEquals(GuestAccount("ann", 1001, 1001, "/home/ann", "/bin/sh"), files.account)
        assertTrue(files.passwd.endsWith("ann:x:1001:1001:ann:/home/ann:/bin/sh\n"))
        assertTrue(files.passwd.startsWith(passwd))
        assertEquals("root:x:0:\ndev:x:1000:\nann:x:1001:\n", files.group)
    }

    @Test
    fun `add reuses a group that already has the name and keeps the files ending in a newline`() {
        val files =
            checkNotNull(GuestAccounts.add("root:x:0:0::/root:/bin/sh", "ann:x:2000:", "ann"))

        assertEquals(2000, files.account.gid)
        assertEquals("ann:x:2000:", files.group)
        assertEquals(
            "root:x:0:0::/root:/bin/sh\nann:x:1000:2000:ann:/home/ann:/bin/sh\n",
            files.passwd
        )
    }

    @Test
    fun `add picks another gid when the uid is taken as a gid`() {
        val files = checkNotNull(GuestAccounts.add("", "users:x:1000:\n", "ann"))

        assertEquals(1001, files.account.gid)
        assertEquals(1000, files.account.uid)
    }

    @Test
    fun `add skips ids used by duplicate or out of order entries and uses the given shell`() {
        val odd = "a:x:1001:1001::/h:/bin/sh\na:x:1000:5::/h:/bin/sh\nb:x:1002:1003::/h:/bin/sh"

        val files = checkNotNull(GuestAccounts.add(odd, "g:x:1004:", "ann", "/bin/bash"))

        assertEquals(1003, files.account.uid)
        assertEquals(1000, files.account.gid)
        assertEquals("/bin/bash", files.account.shell)
    }

    @Test
    fun `a duplicate name resolves to the first entry and is never added again`() {
        val dup = "dev:x:1000:1000::/home/dev:/bin/sh\ndev:x:2000:2000::/x:/bin/sh\n"

        assertEquals(1000, GuestAccounts.find(dup, "dev")?.uid)
        assertNull(GuestAccounts.add(dup, "", "dev"))
    }

    @Test
    fun `the shell is bash when etc shells lists it and the plain shell otherwise`() {
        assertEquals("/bin/bash", GuestAccounts.preferredShell("# x\n/bin/sh\n/bin/bash\n"))
        assertEquals("/bin/sh", GuestAccounts.preferredShell("/bin/sh\n/usr/bin/bash\n"))
        assertEquals("/bin/sh", GuestAccounts.preferredShell(""))
    }

    @Test
    fun `add refuses root, an invalid name and a name that exists`() {
        assertNull(GuestAccounts.add(passwd, "", "root"))
        assertNull(GuestAccounts.add(passwd, "", "-l"))
        assertNull(GuestAccounts.add(passwd, "", "dev"))
    }

    @Test
    fun `add fails when every id is used`() {
        val full = (1000..59_999).joinToString("\n") { "u$it:x:$it:$it::/h:/bin/sh" }

        assertNull(GuestAccounts.add(full, "", "ann"))
        val groups = (1000..59_999).joinToString("\n") { "g$it:x:$it:" }
        assertNull(GuestAccounts.add("", groups, "ann"))
    }
}

class ExitHintTest {
    @Test
    fun `127 and 126 have a hint and other codes do not`() {
        assertEquals(ExitHint.COMMAND_NOT_FOUND, ExitHint.of(127))
        assertEquals(ExitHint.NOT_EXECUTABLE, ExitHint.of(126))
        assertNull(ExitHint.of(0))
        assertNull(ExitHint.of(1))
        assertNull(ExitHint.of(130))
    }
}

class GuestAccountResolverTest {
    private val fs = InMemoryFileSystemRepository()
    private val resolver = GuestAccountResolver(fs)
    private val rootfs = checkNotNull(FsPath.of("distros/f").getOrNull())

    private fun text(path: String) = fs.readFile(path)?.decodeToString()

    @Test
    fun `without a passwd file there is nobody to log in as`() = runTest {
        assertNull(resolver.resolve(rootfs, "dev"))
    }

    @Test
    fun `a missing group file is created along with the user`() = runTest {
        fs.putFile("distros/f/etc/passwd", "root:x:0:0::/root:/bin/sh\n".toByteArray())

        val login = checkNotNull(resolver.resolve(rootfs, "dev"))

        assertEquals("/home/dev", login.workingDirectory)
        assertEquals("dev:x:1000:\n", text("distros/f/etc/group"))
    }

    @Test
    fun `an unreadable group file stops the creation and leaves passwd as it was`() = runTest {
        val before = "root:x:0:0::/root:/bin/sh\n"
        fs.putFile("distros/f/etc/passwd", before.toByteArray())
        fs.putFile("distros/f/etc/group", ByteArray(2_000_000))

        assertNull(resolver.resolve(rootfs, "dev"))
        assertEquals(before, text("distros/f/etc/passwd"))
    }

    @Test
    fun `a failing write of passwd fails the launch`() = runTest {
        val failing = object : FileSystemRepository by fs {
            override suspend fun writeText(path: FsPath, text: String): Outcome<Unit> =
                if (path.value.endsWith("passwd")) {
                    Outcome.Failure(DomainError.Io("disk full"))
                } else {
                    fs.writeText(path, text)
                }
        }
        fs.putFile("distros/f/etc/passwd", "root:x:0:0::/root:/bin/sh\n".toByteArray())

        assertNull(GuestAccountResolver(failing).resolve(rootfs, "dev"))
    }

    @Test
    fun `a home outside home is not created and the session starts at the root`() = runTest {
        fs.putFile("distros/f/etc/passwd", "svc:x:5:5::/var/svc:/bin/sh\n".toByteArray())

        val login = checkNotNull(resolver.resolve(rootfs, "svc"))

        assertEquals("/", login.workingDirectory)
        assertNotNull(login.account)
    }

    @Test
    fun `a home that is the root directory starts at the root`() = runTest {
        fs.putFile("distros/f/etc/passwd", "svc:x:5:5::/:/bin/sh\n".toByteArray())

        assertEquals("/", resolver.resolve(rootfs, "svc")?.workingDirectory)
    }
}
