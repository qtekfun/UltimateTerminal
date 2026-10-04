// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.settings

/** One block of the credits: a heading and its lines, already free of Markdown marks. */
data class NoticeSection(val title: String, val lines: List<String>)

/**
 * Reads `THIRD_PARTY_NOTICES.md` into sections for the About page. It is not a Markdown renderer:
 * it splits at `##` headings, turns table rows into one line of cells, and drops links, code marks
 * and emphasis, which is all that file uses.
 */
object Notices {
    private val link = Regex("\\[([^\\]]+)]\\([^)]*\\)")
    private val tableRule = Regex("^\\|?[\\s:|-]+\\|[\\s:|-]*$")

    fun parse(markdown: String): List<NoticeSection> {
        val sections = mutableListOf<NoticeSection>()
        var title = ""
        var lines = mutableListOf<String>()
        fun close() {
            if (title.isNotEmpty() || lines.isNotEmpty()) sections += NoticeSection(title, lines)
            lines = mutableListOf()
        }
        markdown.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("## ") || line.startsWith("### ") -> {
                    close()
                    title = clean(line.trimStart('#'))
                }

                line.startsWith("# ") -> Unit

                line.isEmpty() || tableRule.matches(line) -> Unit

                line.startsWith("|") -> lines += cells(line)

                else -> lines += clean(line.removePrefix("- ").removePrefix("* "))
            }
        }
        close()
        return sections.filter { it.lines.isNotEmpty() }
    }

    private fun cells(row: String): String = row.trim('|').split('|')
        .map { clean(it) }.filter { it.isNotEmpty() }.joinToString(" · ")

    private fun clean(text: String): String = link.replace(text, "$1")
        .replace("`", "").replace("**", "").replace("__", "").trim()
}
