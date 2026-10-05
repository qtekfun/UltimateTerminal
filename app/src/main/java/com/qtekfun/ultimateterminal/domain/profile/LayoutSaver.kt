// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.profile

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.map
import com.qtekfun.ultimateterminal.domain.model.Layout
import com.qtekfun.ultimateterminal.domain.repository.LayoutRepository
import com.qtekfun.ultimateterminal.domain.session.PaneEditor
import com.qtekfun.ultimateterminal.domain.session.SessionId
import com.qtekfun.ultimateterminal.domain.session.tabOf
import com.qtekfun.ultimateterminal.domain.session.treeOf
import kotlinx.coroutines.flow.first

/** Saves the panes of the active tab as a named layout (SPEC RF-12). */
class LayoutSaver(private val editor: PaneEditor, private val layouts: LayoutRepository) {
    /**
     * The shape and proportions of the active tab, each pane with the profile and command it was
     * opened with, saved as [name]. A name that is taken fails with [DomainError.NameTaken] unless
     * [replace] is true, which overwrites that layout (ignoring case) instead.
     */
    suspend fun save(name: String, replace: Boolean = false): Outcome<Layout> {
        val sessions = editor.state.value
        val active = sessions.activeId ?: return Outcome.Failure(DomainError.NotFound)
        val existing = layouts.observeAll().first()
        val replacing = if (replace) {
            existing.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.id ?: 0L
        } else {
            0L
        }
        val describe = { id: SessionId ->
            editor.openingOf(id)?.let { PaneDescription(it.spec.profileId, it.command) }
                ?: PaneDescription()
        }
        val tree = sessions.treeOf(sessions.tabOf(active))
        return when (val built = LayoutSaving.build(name, tree, describe, existing, replacing)) {
            is Outcome.Failure -> built
            is Outcome.Success -> store(built.value)
        }
    }

    private suspend fun store(layout: Layout): Outcome<Layout> = if (layout.id == 0L) {
        layouts.add(layout)
    } else {
        layouts.update(layout).map { layout }
    }
}
