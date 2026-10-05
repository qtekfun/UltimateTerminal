// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.backup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimateterminal.data.backup.BackupExporter
import com.qtekfun.ultimateterminal.data.backup.BackupRestorer
import com.qtekfun.ultimateterminal.domain.backup.BackupError
import com.qtekfun.ultimateterminal.domain.backup.BackupProgress
import com.qtekfun.ultimateterminal.domain.backup.BackupResult
import com.qtekfun.ultimateterminal.domain.backup.ExportRequest
import com.qtekfun.ultimateterminal.domain.backup.ExportSummary
import com.qtekfun.ultimateterminal.domain.backup.RestoreSummary
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.platform.ContentResolverBackupSink
import com.qtekfun.ultimateterminal.platform.ContentResolverBackupSource
import com.qtekfun.ultimateterminal.platform.documentName
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the user is told after a backup action; the screen turns it into text. */
sealed interface BackupMessage {
    data class Exported(val summary: ExportSummary, val fileName: String?) : BackupMessage

    data class Restored(val summary: RestoreSummary) : BackupMessage

    data class Failed(val error: BackupError) : BackupMessage
}

data class BackupUiState(
    /** The distros that can be exported on their own. */
    val distros: List<Distro> = emptyList(),
    val busy: Boolean = false,
    val progress: BackupProgress? = null,
    val message: BackupMessage? = null,
    /** A file that turned out to be encrypted, waiting for its password. */
    val passwordFor: Uri? = null
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    application: Application,
    private val exporter: BackupExporter,
    private val restorer: BackupRestorer,
    private val restoreActivity: RestoreActivity,
    distros: DistroRepository
) : AndroidViewModel(application) {
    private val state = MutableStateFlow(BackupUiState())
    private var job: Job? = null

    val uiState: StateFlow<BackupUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            distros.observeAll().collect { all ->
                state.update { it.copy(distros = all.filter { d -> d.state == DistroState.READY }) }
            }
        }
        // Work files an earlier run left behind; nothing runs yet, so they are safe to remove.
        viewModelScope.launch { exporter.recoverInterrupted() }
    }

    fun export(target: Uri, request: ExportRequest) = run {
        val resolver = getApplication<Application>().contentResolver
        val sink = ContentResolverBackupSink(resolver, target)
        when (val result = exporter.export(request, sink, ::onProgress)) {
            is BackupResult.Success ->
                BackupMessage.Exported(result.value, documentName(resolver, target))

            is BackupResult.Failure -> BackupMessage.Failed(result.error)
        }
    }

    /** Looks at the file first: an encrypted one needs its password before anything else. */
    fun startRestore(source: Uri) = run {
        val file =
            ContentResolverBackupSource(getApplication<Application>().contentResolver, source)
        when (val probe = restorer.probe(file)) {
            is BackupResult.Failure -> BackupMessage.Failed(probe.error)

            is BackupResult.Success -> if (probe.value.encrypted) {
                state.update { it.copy(passwordFor = source) }
                null
            } else {
                restore(file, null)
            }
        }
    }

    fun restoreEncrypted(source: Uri, password: String) {
        state.update { it.copy(passwordFor = null) }
        run {
            restore(
                ContentResolverBackupSource(getApplication<Application>().contentResolver, source),
                password
            )
        }
    }

    fun cancelPassword() = state.update { it.copy(passwordFor = null) }

    fun cancel() {
        job?.cancel()
    }

    fun dismissMessage() = state.update { it.copy(message = null) }

    private suspend fun restore(
        source: ContentResolverBackupSource,
        password: String?
    ): BackupMessage {
        restoreActivity.set(true)
        val result = try {
            restorer.restore(source, password, ::onProgress)
        } finally {
            restoreActivity.set(false)
        }
        return when (result) {
            is BackupResult.Success -> BackupMessage.Restored(result.value)
            is BackupResult.Failure -> BackupMessage.Failed(result.error)
        }
    }

    private fun onProgress(progress: BackupProgress) = state.update { it.copy(progress = progress) }

    /** Runs one action at a time; a null message means it only moved on to a next step. */
    private fun run(action: suspend () -> BackupMessage?) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, progress = null, message = null) }
        job = viewModelScope.launch {
            val message = try {
                action()
            } finally {
                state.update { it.copy(busy = false, progress = null) }
            }
            message?.let { done -> state.update { it.copy(message = done) } }
        }
    }
}
