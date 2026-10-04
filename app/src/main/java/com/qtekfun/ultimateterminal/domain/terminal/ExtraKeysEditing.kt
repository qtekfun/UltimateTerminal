// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.terminal

/** Limits of the extra-keys row, so it stays usable on a phone (7 keys of 48 dp fit 360 dp). */
object ExtraKeysEditing {
    const val MAX_ROWS = 3
    const val MAX_KEYS_PER_ROW = 8
}

/** The keys of the catalog that no row uses yet, in catalog order. */
fun ExtraKeysConfig.unusedKeys(): List<ExtraKey> {
    val used = rows.flatten().toSet()
    return ExtraKeyCatalog.all.filter { it.id !in used }
}

/**
 * Adds the key [id] at the end of row [row]; a [row] equal to the number of rows starts a new one.
 * Nothing changes if the key is unknown or already used, the row is full or there are too many rows.
 */
fun ExtraKeysConfig.withKey(row: Int, id: String): ExtraKeysConfig {
    val known = ExtraKeyCatalog.find(id) != null && rows.none { id in it }
    return when {
        !known -> this

        row == rows.size && rows.size < ExtraKeysEditing.MAX_ROWS -> copy(
            rows =
                rows + listOf(listOf(id))
        )

        row in rows.indices && rows[row].size < ExtraKeysEditing.MAX_KEYS_PER_ROW ->
            copy(rows = rows.mapIndexed { index, keys -> if (index == row) keys + id else keys })

        else -> this
    }
}

/** Removes the key at [index] of row [row]; a row left empty goes away. */
fun ExtraKeysConfig.withoutKey(row: Int, index: Int): ExtraKeysConfig {
    if (row !in rows.indices || index !in rows[row].indices) return this
    val edited = rows.mapIndexed { r, keys ->
        if (r ==
            row
        ) {
            keys.filterIndexed { i, _ -> i != index }
        } else {
            keys
        }
    }
    return copy(rows = edited.filter { it.isNotEmpty() })
}

/** Moves a key within its row, to [to] (clamped); [from] out of range changes nothing. */
fun ExtraKeysConfig.withKeyMoved(row: Int, from: Int, to: Int): ExtraKeysConfig {
    if (row !in rows.indices || from !in rows[row].indices) return this
    val keys = rows[row].toMutableList()
    val key = keys.removeAt(from)
    keys.add(to.coerceIn(0, keys.size), key)
    return copy(rows = rows.mapIndexed { r, old -> if (r == row) keys else old })
}
