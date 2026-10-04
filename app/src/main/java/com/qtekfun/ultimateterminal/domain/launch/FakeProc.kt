// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.launch

/** What the fake `/proc` files are derived from. */
data class FakeProcInputs(
    val cpuCount: Int,
    /** Seconds since the device booted. */
    val uptimeSeconds: Long,
    /** The kernel release the device reports (`os.version`), shown in `/proc/version`. */
    val kernelRelease: String,
    /** When the device booted, in seconds since the epoch. */
    val bootEpochSeconds: Long
)

/**
 * Plausible replacements for the `/proc` files that Android denies to apps (SELinux), so that `top`,
 * `uptime`, `free`, `vmstat` and `htop` work inside a distro. proot binds each one over the real
 * file; `/proc/meminfo` and the per-process directories stay the real ones, because they are readable.
 *
 * The numbers are **approximate by design** (see DECISIONS.md, D-T08c-4): only the structure is
 * exact, so that the programs of `procps` and `busybox` parse it. The CPU times are a fixed
 * proportion of the uptime, the load average is a constant, the counters grow with the uptime.
 */
object FakeProc {
    const val STAT = "stat"
    const val UPTIME = "uptime"
    const val LOADAVG = "loadavg"
    const val VERSION = "version"
    const val VMSTAT = "vmstat"

    /** The files, in the order they are bound. */
    val NAMES = listOf(LOADAVG, STAT, UPTIME, VERSION, VMSTAT)

    private const val USER_HZ = 100L
    private const val CENTI = 100L
    private const val PERCENT = 100L
    private const val MAX_CPUS = 64
    private const val MAX_RELEASE_LENGTH = 100
    private const val DEFAULT_RELEASE = "5.10.0"

    // How the CPU time is split, in percent of the total: the rest is idle.
    private const val USER_PERCENT = 3L
    private const val SYSTEM_PERCENT = 4L
    private const val IOWAIT_PERCENT = 1L
    private const val IRQ_PERCENT = 0L
    private const val SOFTIRQ_PERCENT = 1L
    private const val IDLE_PERCENT = PERCENT - USER_PERCENT - SYSTEM_PERCENT - IOWAIT_PERCENT -
        IRQ_PERCENT - SOFTIRQ_PERCENT

    private const val CONTEXT_SWITCHES_PER_SECOND = 400L
    private const val PROCESSES_PER_SECOND = 2L
    private const val PROCESSES_BASE = 800L
    private const val PAGE_FAULTS_PER_SECOND = 900L
    private const val MAJOR_FAULTS_DIVISOR = 20L
    private const val PAGES_IN_PER_SECOND = 40L
    private const val PAGES_OUT_PER_SECOND = 25L
    private const val FREE_PAGES = 120_000L
    private const val SOFTIRQ_COLUMNS = 10

    /** Every file's content, by its name under `/proc`. */
    fun render(inputs: FakeProcInputs): Map<String, String> {
        val cpus = inputs.cpuCount.coerceIn(1, MAX_CPUS)
        val uptime = inputs.uptimeSeconds.coerceAtLeast(0L)
        return linkedMapOf(
            LOADAVG to LOADAVG_TEXT,
            STAT to stat(cpus, uptime, inputs.bootEpochSeconds),
            UPTIME to uptime(cpus, uptime),
            VERSION to version(inputs.kernelRelease),
            VMSTAT to vmstat(uptime)
        )
    }

    /** `0.12 0.07 0.02 2/165 765`: three averages, running/total tasks, the last pid. */
    const val LOADAVG_TEXT = "0.12 0.07 0.02 2/165 765\n"

    /** `up idle`: the idle time is that of all the CPUs together, as the kernel reports it. */
    fun uptime(cpuCount: Int, uptimeSeconds: Long): String {
        val up = uptimeSeconds * CENTI
        val idle = up * cpuCount * IDLE_PERCENT / PERCENT
        return "${centis(up)} ${centis(idle)}\n"
    }

    fun version(kernelRelease: String): String {
        val release = kernelRelease.filter { it.code in PRINTABLE }.trim()
            .take(MAX_RELEASE_LENGTH).ifBlank { DEFAULT_RELEASE }
        return "Linux version $release (android@localhost) (clang) #1 SMP PREEMPT\n"
    }

    fun stat(cpuCount: Int, uptimeSeconds: Long, bootEpochSeconds: Long): String {
        val perCpu = uptimeSeconds * USER_HZ
        val times = cpuTimes(perCpu)
        val total = cpuTimes(perCpu * cpuCount)
        return buildString {
            appendLine("cpu  $total")
            for (cpu in 0 until cpuCount) appendLine("cpu$cpu $times")
            appendLine("intr 0")
            appendLine("ctxt ${uptimeSeconds * CONTEXT_SWITCHES_PER_SECOND}")
            appendLine("btime $bootEpochSeconds")
            appendLine("processes ${PROCESSES_BASE + uptimeSeconds * PROCESSES_PER_SECOND}")
            appendLine("procs_running 1")
            appendLine("procs_blocked 0")
            appendLine("softirq 0${" 0".repeat(SOFTIRQ_COLUMNS)}")
        }
    }

    fun vmstat(uptimeSeconds: Long): String = buildString {
        appendLine("nr_free_pages $FREE_PAGES")
        for (name in ZERO_COUNTERS) appendLine("$name 0")
        appendLine("pgpgin ${uptimeSeconds * PAGES_IN_PER_SECOND}")
        appendLine("pgpgout ${uptimeSeconds * PAGES_OUT_PER_SECOND}")
        appendLine("pswpin 0")
        appendLine("pswpout 0")
        appendLine("pgfault ${uptimeSeconds * PAGE_FAULTS_PER_SECOND}")
        appendLine("pgmajfault ${uptimeSeconds * PAGE_FAULTS_PER_SECOND / MAJOR_FAULTS_DIVISOR}")
        appendLine("oom_kill 0")
    }

    /** The ten fields of a `cpu` line: user nice system idle iowait irq softirq steal guest guest_nice. */
    private fun cpuTimes(jiffies: Long): String {
        val user = jiffies * USER_PERCENT / PERCENT
        val system = jiffies * SYSTEM_PERCENT / PERCENT
        val iowait = jiffies * IOWAIT_PERCENT / PERCENT
        val irq = jiffies * IRQ_PERCENT / PERCENT
        val softirq = jiffies * SOFTIRQ_PERCENT / PERCENT
        val idle = jiffies - user - system - iowait - irq - softirq
        return "$user 0 $system $idle $iowait $irq $softirq 0 0 0"
    }

    /** 12345 centiseconds -> `123.45`. */
    private fun centis(value: Long): String =
        "${value / CENTI}.${(value % CENTI).toString().padStart(2, '0')}"

    private const val FIRST_PRINTABLE = 0x20
    private const val LAST_PRINTABLE = 0x7e
    private val PRINTABLE = FIRST_PRINTABLE..LAST_PRINTABLE
    private val ZERO_COUNTERS = listOf(
        "nr_zone_inactive_anon",
        "nr_zone_active_anon",
        "nr_zone_inactive_file",
        "nr_zone_active_file",
        "nr_zone_unevictable",
        "nr_dirty",
        "nr_writeback"
    )
}
