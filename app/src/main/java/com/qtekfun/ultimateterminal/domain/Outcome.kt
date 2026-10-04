// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain

/** Result of an operation that can fail for a reason the UI explains; never an exception. */
sealed interface Outcome<out T> {
    data class Success<out T>(val value: T) : Outcome<T>

    data class Failure(val error: DomainError) : Outcome<Nothing>
}

/** Why an operation failed. Messages are for logs and never carry secrets. */
sealed interface DomainError {
    /** A name that is empty, too long or has control characters. */
    data class InvalidName(val name: String) : DomainError

    /** A field with a value outside what the app supports (e.g. a font size or a port). */
    data class InvalidValue(val field: String) : DomainError

    /** Another item already uses this name (names are unique, ignoring case). */
    data class NameTaken(val name: String) : DomainError

    data object NotFound : DomainError

    /** A path that is not a safe relative path inside the storage root. */
    data class InvalidPath(val path: String) : DomainError

    data class Io(val message: String) : DomainError
}

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}
