// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.settings

/**
 * One entry of the credits. [title] is a paragraph, a list item or, for a table row, the first cell
 * (the component); [detail] holds the other cells of the row (license, notes), one per line, and is
 * null for plain text.
 */
data class NoticeItem(val title: String, val detail: String? = null)

/** One block of the credits: a heading and its entries, already free of Markdown marks. */
data class NoticeSection(val title: String, val items: List<NoticeItem>)

/**
 * Reads `THIRD_PARTY_NOTICES.md` into sections for the About page. It is not a Markdown renderer:
 * it splits at `##` headings, joins the hard-wrapped lines of a paragraph or list item into one
 * entry, turns each table row into one entry and drops links, code marks and emphasis, which is all
 * that file uses. Table header rows (the column names) and the sentence that tells maintainers to
 * keep the file up to date are the only things left out; every credit and license stays.
 */
object Notices {
    private val link = Regex("\\[([^\\]]+)]\\([^)]*\\)")
    private val tableRule = Regex("^\\|?[\\s:|-]+\\|[\\s:|-]*$")
    private val bullet = Regex("^[-*] +")
    private const val MAINTENANCE = "This file must be updated"

    fun parse(markdown: String): List<NoticeSection> =
        Reader(markdown.lines().map { it.trim() }).read()

    /** One pass over the lines: the open section and the paragraph being gathered. */
    private class Reader(private val lines: List<String>) {
        private val sections = mutableListOf<NoticeSection>()
        private var title = ""
        private var items = mutableListOf<NoticeItem>()
        private val text = StringBuilder()

        fun read(): List<NoticeSection> {
            lines.forEachIndexed { index, line -> accept(line, lines.getOrNull(index + 1)) }
            close()
            return sections.filter { it.items.isNotEmpty() }
        }

        private fun accept(line: String, next: String?) {
            when {
                line.startsWith("## ") || line.startsWith("### ") -> {
                    close()
                    title = clean(line.trimStart('#'))
                }

                line.startsWith("# ") || tableRule.matches(line) -> Unit

                line.isEmpty() -> flush()

                line.startsWith("|") -> {
                    flush()
                    // The row above a rule holds the column names, not a credit.
                    if (next == null || !tableRule.matches(next)) row(line)?.let { items += it }
                }

                else -> {
                    if (bullet.containsMatchIn(line)) flush()
                    if (text.isNotEmpty()) text.append(' ')
                    text.append(line.replace(bullet, ""))
                }
            }
        }

        private fun flush() {
            val paragraph = clean(text.toString())
            text.clear()
            if (paragraph.isNotEmpty() && !paragraph.startsWith(MAINTENANCE)) {
                items += NoticeItem(paragraph)
            }
        }

        private fun close() {
            flush()
            if (title.isNotEmpty() || items.isNotEmpty()) sections += NoticeSection(title, items)
            items = mutableListOf()
        }
    }

    private fun row(line: String): NoticeItem? {
        val cells = line.trim('|').split('|').map { clean(it) }.filter { it.isNotEmpty() }
        return cells.firstOrNull()?.let { name ->
            NoticeItem(name, cells.drop(1).joinToString("\n").ifEmpty { null })
        }
    }

    private fun clean(text: String): String = link.replace(text, "$1")
        .replace("`", "").replace("**", "").replace("__", "").trim()
}
