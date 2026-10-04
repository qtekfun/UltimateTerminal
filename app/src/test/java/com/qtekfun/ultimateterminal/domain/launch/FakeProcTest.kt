// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.launch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FakeProcTest {
    private val inputs = FakeProcInputs(
        cpuCount = 4,
        uptimeSeconds = 12_345,
        kernelRelease = "6.1.157-android14-11-gbd23337e42e7",
        bootEpochSeconds = 1_700_000_000
    )

    private fun lines(name: String) = FakeProc.render(inputs).getValue(name).lines().filter {
        it.isNotEmpty()
    }

    @Test
    fun `every file the programs read is there, in bind order`() {
        assertEquals(FakeProc.NAMES, FakeProc.render(inputs).keys.toList())
    }

    @Test
    fun `stat has an aggregate line and one per cpu with the ten fields`() {
        val cpuLines = lines("stat").filter { it.startsWith("cpu") }

        assertEquals(5, cpuLines.size)
        assertTrue(cpuLines[0].startsWith("cpu  "))
        cpuLines.forEach { line ->
            val fields = line.trim().split(Regex("\\s+"))
            assertEquals(11, fields.size, line)
            assertTrue(fields.drop(1).all { it.toLongOrNull() != null }, line)
        }
        assertEquals((0..3).map { "cpu$it" }, cpuLines.drop(1).map { it.substringBefore(' ') })
    }

    @Test
    fun `the aggregate cpu line is the sum of the cpus`() {
        val cpuLines = lines("stat").filter { it.startsWith("cpu") }
        fun fields(line: String) = line.trim().split(Regex("\\s+")).drop(1).map(String::toLong)
        val perCpu = cpuLines.drop(1).map(::fields)
        val summed = (0 until 10).map { column -> perCpu.sumOf { it[column] } }

        assertEquals(summed, fields(cpuLines[0]))
    }

    @Test
    fun `the cpu times add up to the uptime in jiffies, idle being the rest`() {
        val one = lines("stat")[1].trim().split(Regex("\\s+")).drop(1).map(String::toLong)

        assertEquals(12_345L * 100, one.sum())
        assertTrue(one[3] > one[0] + one[2])
    }

    @Test
    fun `stat ends with the counters vmstat and top read`() {
        val rest = lines("stat").filter { !it.startsWith("cpu") }.associate {
            it.substringBefore(' ') to it.substringAfter(' ')
        }

        assertEquals("1700000000", rest["btime"])
        assertEquals(
            listOf(
                "intr",
                "ctxt",
                "btime",
                "processes",
                "procs_running",
                "procs_blocked",
                "softirq"
            ),
            rest.keys.toList()
        )
        assertEquals(11, rest.getValue("softirq").split(' ').size)
    }

    @Test
    fun `uptime is two decimal numbers and idle counts every cpu`() {
        val (up, idle) = FakeProc.uptime(4, 12_345).trim().split(' ')

        assertEquals("12345.00", up)
        assertEquals("44935.80", idle)
        assertTrue(Regex("\\d+\\.\\d{2}").matches(idle))
    }

    @Test
    fun `uptime keeps the leading zero of the centiseconds`() {
        assertEquals("0.00 0.00\n", FakeProc.uptime(2, 0))
    }

    @Test
    fun `loadavg has five fields in the kernel format`() {
        assertTrue(
            Regex(
                "\\d+\\.\\d{2} \\d+\\.\\d{2} \\d+\\.\\d{2} \\d+/\\d+ \\d+\\n"
            ).matches(FakeProc.LOADAVG_TEXT)
        )
    }

    @Test
    fun `version shows the kernel release of the device`() {
        assertTrue(
            FakeProc.version("6.1.157-android14").startsWith("Linux version 6.1.157-android14 (")
        )
        assertTrue(FakeProc.version("x").endsWith("\n"))
    }

    @Test
    fun `a blank or hostile kernel release falls back and cannot inject lines`() {
        assertTrue(FakeProc.version("  ").startsWith("Linux version 5.10.0 ("))
        val hostile = FakeProc.version("6.1\nroot::0:0\u0000")

        assertEquals(1, hostile.lines().filter { it.isNotEmpty() }.size)
        assertTrue(hostile.startsWith("Linux version 6.1root::0:0 ("))
    }

    @Test
    fun `vmstat is name and number per line, with the keys procps needs`() {
        val entries = lines("vmstat").map { it.split(' ') }

        assertTrue(entries.all { it.size == 2 && it[1].toLongOrNull() != null })
        val names = entries.map { it[0] }
        assertTrue(
            names.containsAll(listOf("nr_free_pages", "pgpgin", "pgpgout", "pgfault", "pgmajfault"))
        )
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `counters grow with the uptime`() {
        assertTrue(FakeProc.vmstat(10) != FakeProc.vmstat(10_000))
    }

    @Test
    fun `a negative uptime is treated as zero`() {
        val files = FakeProc.render(inputs.copy(uptimeSeconds = -5))

        assertTrue(files.getValue("uptime").startsWith("0.00 "))
        assertTrue(files.getValue("stat").lines().first().startsWith("cpu  0 0 0 0 0 0 0 0 0 0"))
    }

    @Test
    fun `the number of cpus is kept within sane bounds`() {
        val none = FakeProc.render(inputs.copy(cpuCount = 0)).getValue("stat").lines().filter {
            it.startsWith("cpu")
        }
        val many = FakeProc.render(inputs.copy(cpuCount = 10_000)).getValue("stat").lines().filter {
            it.startsWith("cpu")
        }

        assertEquals(2, none.size)
        assertEquals(65, many.size)
    }
}
