// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimateterminal.R
import com.qtekfun.ultimateterminal.domain.profile.LayoutNotice
import com.qtekfun.ultimateterminal.domain.profile.PaneNotice
import com.qtekfun.ultimateterminal.domain.profile.ProfileProblem

/** Why a profile could not open, in the user's language. */
@Composable
internal fun problemText(problem: ProfileProblem): String = when (problem) {
    is ProfileProblem.UnknownDistro -> stringResource(R.string.problem_unknown_distro)

    is ProfileProblem.DistroNotReady ->
        stringResource(R.string.problem_distro_not_ready, problem.distroName)

    is ProfileProblem.InvalidUser -> stringResource(R.string.problem_invalid_user, problem.user)

    is ProfileProblem.InvalidStartupCommand -> stringResource(R.string.problem_invalid_command)
}

/** What had to change for a pane to open, in the user's language. */
@Composable
internal fun noticeText(notice: PaneNotice): String = when (notice) {
    is PaneNotice.ProfileMissing -> stringResource(R.string.notice_profile_missing)

    is PaneNotice.SchemeMissing -> stringResource(R.string.notice_scheme_missing)

    is PaneNotice.FontMissing -> stringResource(R.string.notice_font_missing)

    is PaneNotice.ValueAdjusted -> stringResource(R.string.notice_value_adjusted)

    PaneNotice.NoDistroReady -> stringResource(R.string.notice_no_distro_ready)

    is PaneNotice.Degraded ->
        stringResource(R.string.notice_degraded, problemText(notice.problem))
}

/** What a restored layout changed, in the user's language; panes and divisions count from 1. */
@Composable
internal fun layoutNoticeText(notice: LayoutNotice): String = when (notice) {
    is LayoutNotice.ForPane ->
        stringResource(R.string.notice_layout_pane, notice.paneIndex + 1, noticeText(notice.notice))

    is LayoutNotice.RatioAdjusted ->
        stringResource(R.string.notice_layout_ratio, notice.splitIndex + 1)
}
