// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.ssh

/** Why an SSH operation failed. Never carries key material, hosts or paths of secrets. */
sealed interface SshError {
    /** A field (host, user, port, name, key path) outside what is safe to pass to `ssh`. */
    data class Invalid(val field: String) : SshError

    /** The text is not a private key the app can read (only OpenSSH and PKCS#8 are supported). */
    data object UnsupportedKeyFormat : SshError

    /** The key is protected by a passphrase; remove it first (`ssh-keygen -p`). */
    data object EncryptedKey : SshError

    /** A stored or imported key that is damaged or fails its integrity check. */
    data object CorruptKey : SshError

    /** This device cannot generate keys of the requested type. */
    data class UnsupportedKeyType(val type: SshKeyType) : SshError

    data class NameTaken(val name: String) : SshError

    data object KeyNotFound : SshError

    /** The key is still used by [hostCount] saved hosts. */
    data class KeyInUse(val hostCount: Int) : SshError

    data object HostNotFound : SshError

    /** There is no installed, ready distro to run `ssh` in. */
    data object NoDistro : SshError

    /** The disk or the key store failed; [message] is safe for logs. */
    data class Storage(val message: String) : SshError
}

/** Result of an SSH operation; never an exception to the UI. */
sealed interface SshResult<out T> {
    data class Success<out T>(val value: T) : SshResult<T>

    data class Failure(val error: SshError) : SshResult<Nothing>
}

fun <T> SshResult<T>.getOrNull(): T? = (this as? SshResult.Success)?.value

inline fun <T, R> SshResult<T>.map(transform: (T) -> R): SshResult<R> = when (this) {
    is SshResult.Success -> SshResult.Success(transform(value))
    is SshResult.Failure -> this
}

inline fun <T, R> SshResult<T>.flatMap(transform: (T) -> SshResult<R>): SshResult<R> = when (this) {
    is SshResult.Success -> transform(value)
    is SshResult.Failure -> this
}
