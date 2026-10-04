// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.ssh

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimateterminal.di.IoDispatcher
import com.qtekfun.ultimateterminal.domain.ssh.SshError
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyInfo
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyService
import com.qtekfun.ultimateterminal.domain.ssh.SshKeyType
import com.qtekfun.ultimateterminal.domain.ssh.SshResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SshKeysUiState(
    val keys: List<SshKeyInfo> = emptyList(),
    val keyTypes: List<SshKeyType> = emptyList(),
    /** A key is being generated or imported; the screen shows progress. */
    val busy: Boolean = false,
    val message: SshMessage? = null
)

/**
 * Drives the key screen: generate, import, copy the public key, save the private key, delete.
 * Key material never reaches the UI state; only public parts do. The private key goes straight from
 * the service to a file the user chose, after the screen warned about it.
 */
@HiltViewModel
class SshKeysViewModel @Inject constructor(
    private val keyService: SshKeyService,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {
    private val local = MutableStateFlow(SshKeysUiState(keyTypes = keyService.supportedTypes))

    val uiState: StateFlow<SshKeysUiState> = combine(local, keyService.observe()) { state, keys ->
        state.copy(keys = keys)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, local.value)

    fun generateKey(name: String, type: SshKeyType) = addKey { keyService.generate(name, type) }

    fun importKey(name: String, text: String) = addKey { keyService.import(name, text) }

    fun removeKey(alias: String) {
        viewModelScope.launch {
            val result = keyService.remove(alias)
            if (result is SshResult.Failure) report(SshMessage.Failed(result.error))
        }
    }

    /** The public key line, for the clipboard. */
    fun publicKey(alias: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = keyService.exportPublic(alias)) {
                is SshResult.Success -> {
                    onReady(result.value)
                    local.update { it.copy(message = SshMessage.PublicKeyCopied) }
                }

                is SshResult.Failure -> report(SshMessage.Failed(result.error))
            }
        }
    }

    /** Writes the private key to a file the user chose. The screen has warned about it first. */
    fun savePrivateKey(alias: String, resolver: ContentResolver, destination: Uri) {
        viewModelScope.launch {
            when (val key = keyService.exportPrivate(alias)) {
                is SshResult.Failure -> report(SshMessage.Failed(key.error))

                is SshResult.Success -> {
                    val written = withContext(io) {
                        KeyFiles.write(resolver, destination, key.value)
                    }
                    val message = if (written) SshMessage.PrivateKeySaved else FILE_FAILED
                    local.update { it.copy(message = message) }
                }
            }
        }
    }

    /** Reads a key file the user picked, to fill the import form; null if it cannot be read. */
    fun readKeyFile(resolver: ContentResolver, source: Uri, onRead: (String?) -> Unit) {
        viewModelScope.launch { onRead(withContext(io) { KeyFiles.read(resolver, source) }) }
    }

    fun dismissMessage() = local.update { it.copy(message = null) }

    private fun addKey(create: suspend () -> SshResult<SshKeyInfo>) {
        viewModelScope.launch {
            local.update { it.copy(busy = true, message = null) }
            when (val result = create()) {
                is SshResult.Success -> local.update {
                    it.copy(busy = false, message = SshMessage.KeyAdded(result.value.name))
                }

                is SshResult.Failure -> report(SshMessage.Failed(result.error))
            }
        }
    }

    private fun report(message: SshMessage) =
        local.update { it.copy(busy = false, message = message) }

    private companion object {
        val FILE_FAILED = SshMessage.Failed(SshError.Storage("cannot save the file"))
    }
}
