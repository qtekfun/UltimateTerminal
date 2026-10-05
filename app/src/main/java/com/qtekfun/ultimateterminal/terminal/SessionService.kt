// SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimateterminal.terminal

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.qtekfun.ultimateterminal.domain.repository.SettingsRepository
import com.qtekfun.ultimateterminal.domain.session.ServiceCommand
import com.qtekfun.ultimateterminal.domain.session.wakeLockWanted
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps the process, and so the shells, alive while any of them runs (SPEC RF-07). It owns nothing
 * itself: the sessions belong to [SessionManager]. It shows the persistent notification, holds the
 * optional wake lock, and stops itself when no shell runs.
 *
 * The notification and wake lock are updated from a background dispatcher: both are thread-safe
 * system calls. The sessions are only ever touched on the main thread, where the commands arrive.
 */
@AndroidEntryPoint
class SessionService : Service() {
    @Inject lateinit var manager: SessionManager

    @Inject lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observer: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        SessionNotification.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promoted at once, whatever the command: Android kills a service started with
        // startForegroundService that does not call startForeground in time.
        promote(manager.state.value.runningCount)
        when (ServiceCommand.of(intent?.action)) {
            ServiceCommand.NEW_SESSION -> manager.newDefaultSession()
            ServiceCommand.EXIT -> manager.shutdownAll()
            ServiceCommand.NONE -> Unit
        }
        if (!manager.state.value.needsService) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        observe()
        // Not sticky: if the system kills the process the shells are gone, so there is nothing to resume.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun promote(running: Int) {
        // specialUse needs API 34; before that the type is not part of startForeground.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            SessionNotification.NOTIFICATION_ID,
            SessionNotification.build(this, running),
            type
        )
    }

    private fun observe() {
        if (observer?.isActive == true) return
        observer = scope.launch {
            combine(manager.state, settings.observe()) { sessions, appSettings ->
                sessions to appSettings
            }.collect { (sessions, appSettings) ->
                getSystemService(NotificationManager::class.java)?.notify(
                    SessionNotification.NOTIFICATION_ID,
                    SessionNotification.build(this@SessionService, sessions.runningCount)
                )
                if (wakeLockWanted(
                        appSettings.keepAwake,
                        sessions
                    )
                ) {
                    acquireWakeLock()
                } else {
                    releaseWakeLock()
                }
            }
        }
    }

    // Held for as long as shells run and the user asked for it (SPEC RF-07), so a timeout would
    // defeat its purpose. It is released when the last shell ends and when the service is destroyed.
    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            ?.apply { setReferenceCounted(false) }
            ?.also { wakeLock = it }
        if (lock?.isHeld == false) lock.acquire()
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    companion object {
        const val ACTION_NEW_SESSION = ServiceCommand.ACTION_NEW_SESSION
        const val ACTION_EXIT = ServiceCommand.ACTION_EXIT
        private const val WAKE_LOCK_TAG = "ultimateterminal:sessions"
    }
}
