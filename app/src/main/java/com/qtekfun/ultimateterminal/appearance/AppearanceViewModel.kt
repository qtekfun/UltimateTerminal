// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.appearance

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimateterminal.di.IoDispatcher
import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.appearance.AppearanceManager
import com.qtekfun.ultimateterminal.domain.appearance.FontImporter
import com.qtekfun.ultimateterminal.domain.model.AppSettings
import com.qtekfun.ultimateterminal.domain.repository.SettingsRepository
import com.qtekfun.ultimateterminal.domain.theme.SchemeCodec
import com.qtekfun.ultimateterminal.domain.theme.TerminalColorScheme
import com.qtekfun.ultimateterminal.platform.AndroidDocuments
import com.qtekfun.ultimateterminal.terminal.TerminalFontLoader
import com.qtekfun.ultimateterminal.terminal.TerminalTypefaces
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the user is told after an action; the screen turns it into text. */
sealed interface AppearanceMessage {
    data class FontImported(val name: String) : AppearanceMessage
    data class FontFailed(val error: DomainError) : AppearanceMessage
    data class SchemeImported(val name: String) : AppearanceMessage
    data class SchemeFailed(val error: DomainError) : AppearanceMessage
    data object SchemeExported : AppearanceMessage
    data object FileFailed : AppearanceMessage
}

data class AppearanceUiState(
    /** Null until the stored settings arrive. */
    val settings: AppSettings? = null,
    val message: AppearanceMessage? = null
)

/** Drives the appearance screen: the theme, the colors, the font and the design of the terminal. */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val manager: AppearanceManager,
    private val documents: AndroidDocuments,
    private val fontLoader: TerminalFontLoader,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    private val message = MutableStateFlow<AppearanceMessage?>(null)

    val state: StateFlow<AppearanceUiState> =
        combine(settings.observe(), message) { stored, current ->
            AppearanceUiState(stored, current)
        }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                AppearanceUiState()
            )

    /** The faces of the font [current] selects, for the preview. */
    fun typefaces(current: AppSettings): TerminalTypefaces =
        fontLoader.load(current.appearance.fontId, current.customFonts)

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    fun importFont(uri: Uri) {
        viewModelScope.launch {
            val picked = withContext(ioDispatcher) {
                documents.read(uri, FontImporter.MAX_BYTES)?.let {
                    it to documents.displayName(uri)
                }
            }
            message.value = when (picked) {
                null -> AppearanceMessage.FileFailed

                else -> when (val result = manager.importFont(picked.first, picked.second)) {
                    is Outcome.Success -> AppearanceMessage.FontImported(result.value.name)
                    is Outcome.Failure -> AppearanceMessage.FontFailed(result.error)
                }
            }
        }
    }

    fun deleteFont(id: String) {
        viewModelScope.launch { manager.deleteFont(id) }
    }

    /** Saves [scheme]; [onDone] gets null on success, or why it was refused (the editor stays open). */
    fun saveScheme(
        previousId: String?,
        scheme: TerminalColorScheme,
        onDone: (DomainError?) -> Unit
    ) {
        viewModelScope.launch {
            val saved = manager.saveScheme(previousId, scheme)
            onDone((saved as? Outcome.Failure)?.error)
        }
    }

    fun deleteScheme(id: String) {
        viewModelScope.launch { manager.deleteScheme(id) }
    }

    fun importScheme(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(ioDispatcher) {
                documents.read(uri, MAX_SCHEME_BYTES)?.toString(Charsets.UTF_8)
            }
            message.value = when (text) {
                null -> AppearanceMessage.FileFailed

                else -> when (val result = manager.importScheme(text)) {
                    is Outcome.Success -> AppearanceMessage.SchemeImported(result.value.name)
                    is Outcome.Failure -> AppearanceMessage.SchemeFailed(result.error)
                }
            }
        }
    }

    fun exportScheme(scheme: TerminalColorScheme, uri: Uri) {
        viewModelScope.launch {
            val written =
                withContext(ioDispatcher) { documents.write(uri, SchemeCodec.encode(scheme)) }
            message.value =
                if (written) AppearanceMessage.SchemeExported else AppearanceMessage.FileFailed
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** A scheme file is a few hundred bytes; the cap keeps a wrong file out of memory. */
        const val MAX_SCHEME_BYTES = 64 * 1024
    }
}
