// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.license

/**
 * One notice a third party's license asks to travel with the app. [id] is stable (it is what the
 * screen links to), [title] names the work and [license] its license in SPDX form. The text itself
 * is read on demand with [LicenseTexts.read], since some are long.
 */
data class LicenseEntry(val id: String, val title: String, val license: String)

/** Where the license texts live: bundled with the app, unmodified, so any source can be faked. */
interface LicenseSource {
    /** The text bundled under [name], or null if the app does not have it. */
    fun read(name: String): String?
}

/**
 * The third-party license texts bundled with the app. Android's packaging drops the `NOTICE` and
 * `LICENSE` files of libraries from the APK (measured: only five dependencies publish one, and
 * none of them survived but one by chance), so the texts are shipped by hand and read from here by
 * the "About" screen. See D-T23-2 in `DECISIONS.md` for how the list was measured.
 */
class LicenseTexts(private val source: LicenseSource) {
    /** The entries to list, in the order shown. */
    val entries: List<LicenseEntry> = CATALOG.map { it.entry }

    /** The text of the entry [id], or null if the id is unknown or the text is missing. */
    fun read(id: String): String? = CATALOG.firstOrNull { it.entry.id == id }
        ?.let { source.read(it.resource) }

    /** The entries whose text is not bundled: should be none, and a test says so. */
    fun missing(): List<LicenseEntry> = CATALOG
        .filter { source.read(it.resource).isNullOrBlank() }
        .map { it.entry }

    private data class Bundled(val entry: LicenseEntry, val resource: String)

    companion object {
        /** Where each notice is bundled: a `raw` resource or a file under `assets/licenses`. */
        const val APACHE_RESOURCE = "raw/third_party_notices_apache"

        private fun asset(id: String, title: String, license: String, file: String) =
            Bundled(LicenseEntry(id, title, license), "assets/licenses/$file")

        private val CATALOG = listOf(
            Bundled(
                LicenseEntry(
                    "apache-notices",
                    "Apache Commons (Compress, IO, Codec, Lang) and Jakarta Injection",
                    "Apache-2.0"
                ),
                APACHE_RESOURCE
            ),
            asset("jetbrains-mono-ofl", "JetBrains Mono", "OFL-1.1", "JetBrainsMono-OFL-1.1.txt"),
            asset(
                "jetbrains-mono-authors",
                "JetBrains Mono (authors)",
                "OFL-1.1",
                "JetBrainsMono-AUTHORS.txt"
            ),
            asset("inter-ofl", "Inter", "OFL-1.1", "Inter-OFL-1.1.txt"),
            asset("lucide", "Lucide icons", "ISC", "Lucide-ISC-MIT.txt"),
            asset(
                "color-schemes",
                "Color schemes (Solarized, Dracula, Nord, Gruvbox)",
                "MIT",
                "ColorSchemes-MIT.txt"
            )
        )
    }
}
