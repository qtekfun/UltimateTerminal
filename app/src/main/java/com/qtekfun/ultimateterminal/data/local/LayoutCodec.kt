// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.data.local

import com.qtekfun.ultimateterminal.domain.model.LayoutNode
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The stored form of a pane tree: JSON with a `type` field ("pane" or "split"). Unknown keys are
 * ignored so a layout written by a newer version still opens.
 */
internal object LayoutCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(node: LayoutNode): String = json.encodeToString(LayoutNode.serializer(), node)

    /** The tree, or null if [text] is not a valid one (including a split whose ratio is not in 0..1). */
    fun decode(text: String): LayoutNode? = try {
        json.decodeFromString(LayoutNode.serializer(), text)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
