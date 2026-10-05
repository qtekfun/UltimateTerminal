// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.di

import com.qtekfun.ultimateterminal.domain.broadcast.BroadcastController
import com.qtekfun.ultimateterminal.domain.profile.LayoutSaver
import com.qtekfun.ultimateterminal.domain.profile.PaneOpener
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.domain.repository.LayoutRepository
import com.qtekfun.ultimateterminal.domain.repository.ProfileRepository
import com.qtekfun.ultimateterminal.domain.repository.SettingsRepository
import com.qtekfun.ultimateterminal.terminal.SessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The classes of T12b that act on the sessions the [SessionManager] owns: opening profiles and
 * layouts, saving a tab as a layout and the broadcast of each tab. They are single so that the
 * terminal screen and the screens of profiles and layouts share the broadcast state.
 */
@Module
@InstallIn(SingletonComponent::class)
object ProfileModule {
    @Provides
    @Singleton
    fun paneOpener(
        sessions: SessionManager,
        profiles: ProfileRepository,
        distros: DistroRepository,
        settings: SettingsRepository
    ) = PaneOpener(sessions.editor, profiles, distros, settings)

    @Provides
    @Singleton
    fun layoutSaver(sessions: SessionManager, layouts: LayoutRepository) =
        LayoutSaver(sessions.editor, layouts)

    @Provides
    @Singleton
    fun broadcastController(sessions: SessionManager) = BroadcastController(sessions.editor)
}
