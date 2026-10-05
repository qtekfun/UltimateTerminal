// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.domain.distro

import com.qtekfun.ultimateterminal.domain.DomainError
import com.qtekfun.ultimateterminal.domain.Outcome
import com.qtekfun.ultimateterminal.domain.flatMap
import com.qtekfun.ultimateterminal.domain.launch.GuestUser
import com.qtekfun.ultimateterminal.domain.model.Distro
import com.qtekfun.ultimateterminal.domain.model.DistroState
import com.qtekfun.ultimateterminal.domain.model.NewDistro
import com.qtekfun.ultimateterminal.domain.repository.DistroRepository
import com.qtekfun.ultimateterminal.domain.repository.FileSystemRepository
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Everything the user does to an installed distro besides installing it (SPEC RF-04): list,
 * rename, duplicate, delete, choose the default distro and its user. Names are unique and the
 * checks live in [DistroRepository]; this class adds the file side and keeps both consistent.
 */
class DistroManager(
    private val distros: DistroRepository,
    private val fileSystem: FileSystemRepository,
    private val newToken: () -> String = { UUID.randomUUID().toString() }
) {
    fun observe(): Flow<List<Distro>> = distros.observeAll()

    suspend fun rename(id: Long, name: String): Outcome<Unit> = distros.rename(id, name)

    /**
     * Changes the user new sessions open as. The name must be one `useradd` accepts
     * ([GuestUser.isValid]); a user that does not exist yet is created when the next session starts.
     */
    suspend fun setDefaultUser(id: Long, user: String): Outcome<Unit> {
        val name = user.trim()
        return if (GuestUser.isValid(name)) {
            distros.setDefaultUser(id, name)
        } else {
            Outcome.Failure(DomainError.InvalidValue("user"))
        }
    }

    /** Only a finished distro can be the default: a new tab has to be able to open it. */
    suspend fun setDefault(id: Long): Outcome<Unit> =
        ready(id).flatMap { distros.setDefault(it.id) }

    /**
     * Copies a finished distro under a new name. The copy is built next to its destination and
     * renamed into place by the file system, and its row only becomes `READY` after that, so a
     * failure or a cancellation leaves neither a partial copy nor a row.
     */
    suspend fun duplicate(id: Long, newName: String): Outcome<Distro> =
        ready(id).flatMap { source ->
            val directory = DistroPaths.distroDirectory(newToken())
            distros.add(
                NewDistro(newName, source.type, source.release, directory, source.defaultUser)
            ).flatMap { copy -> copyInto(source, copy) }
        }

    private suspend fun copyInto(source: Distro, copy: Distro): Outcome<Distro> {
        var finished = false
        try {
            val result = fileSystem.copyRecursively(source.directory, copy.directory).flatMap {
                val size = (fileSystem.sizeOf(copy.directory) as? Outcome.Success)?.value ?: 0L
                distros.updateState(copy.id, DistroState.READY, size)
            }.flatMap {
                distros.get(copy.id)?.let { Outcome.Success(it) }
                    ?: Outcome.Failure(DomainError.NotFound)
            }
            finished = result is Outcome.Success
            return result
        } finally {
            if (!finished) {
                withContext(NonCancellable) {
                    fileSystem.deleteRecursively(copy.directory)
                    distros.remove(copy.id)
                }
            }
        }
    }

    /**
     * Deletes the files and then the row. The row is marked `FAILED` first, so if the deletion is
     * interrupted the distro shows as broken (and can be deleted again) instead of looking intact.
     * A distro that is still installing cannot be deleted from here.
     */
    suspend fun delete(id: Long): Outcome<Unit> {
        val distro = distros.get(id)
        return when {
            distro == null -> Outcome.Failure(DomainError.NotFound)

            distro.state == DistroState.INSTALLING ->
                Outcome.Failure(DomainError.InvalidValue("state"))

            else -> distros.updateState(id, DistroState.FAILED)
                .flatMap { fileSystem.deleteRecursively(distro.directory) }
                .flatMap { distros.remove(id) }
        }
    }

    /** The distro [id] if it exists and is finished; the only kind that can be copied or defaulted. */
    private suspend fun ready(id: Long): Outcome<Distro> {
        val distro = distros.get(id)
        return when {
            distro == null -> Outcome.Failure(DomainError.NotFound)
            distro.state != DistroState.READY -> Outcome.Failure(DomainError.InvalidValue("state"))
            else -> Outcome.Success(distro)
        }
    }

    /**
     * Removes what an install, a copy or a delete left behind when the process died: every row that
     * is not `READY`, its files, and the staging area. Call it once at start, before any install
     * can run. Returns how many distros were cleaned up.
     */
    suspend fun recoverInterrupted(): Int {
        val leftovers = distros.observeAll().first().filter { it.state != DistroState.READY }
        leftovers.forEach {
            fileSystem.deleteRecursively(it.directory)
            distros.remove(it.id)
        }
        fileSystem.deleteRecursively(DistroPaths.stagingRoot())
        return leftovers.size
    }
}
