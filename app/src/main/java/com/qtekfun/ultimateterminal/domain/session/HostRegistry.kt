// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * What belongs to each session while it runs (its terminal host), and a signal for whoever looks
 * one up. The sessions state names a new tab *before* its host exists (the host is created when
 * the session starts), so a lookup made at that moment finds nothing and, if it is never made
 * again, the screen follows nothing: a new tab stayed blank until something else changed. Looking
 * up through [follow] repeats the lookup whenever the registry changes. Used on the main thread.
 */
class HostRegistry<T : Any> {
    private val items = mutableMapOf<SessionId, T>()
    private val version = MutableStateFlow(0)

    /** A counter that goes up every time a host is registered or removed. */
    val changes: StateFlow<Int> = version.asStateFlow()

    operator fun get(id: SessionId?): T? = id?.let(items::get)

    /** The registered hosts at this moment. */
    val values: List<T> get() = items.values.toList()

    fun put(id: SessionId, item: T) {
        items[id] = item
        version.value++
    }

    fun remove(id: SessionId) {
        if (items.remove(id) != null) version.value++
    }

    /**
     * The host of the session in [activeId], emitted again when that session's host appears or goes,
     * and only when it is not the one already emitted.
     */
    fun follow(activeId: Flow<SessionId?>): Flow<T?> =
        combine(activeId.distinctUntilChanged(), changes) { id, _ -> get(id) }
            .distinctUntilChanged()
}
