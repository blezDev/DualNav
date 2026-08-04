package com.blez.dualnav.feature.companion.presentation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.blez.dualnav.MainActivity
import com.blez.dualnav.R
import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.ConnectionType
import com.blez.dualnav.core.domain.model.DeviceInfo
import com.blez.dualnav.core.domain.model.NavigationCommand
import com.blez.dualnav.core.domain.repository.DeviceRepository
import com.blez.dualnav.core.domain.repository.MessageRepository
import com.blez.dualnav.core.domain.repository.PreferencesRepository
import com.blez.dualnav.feature.companion.domain.MapsIntentHandler
import com.blez.dualnav.feature.companion.domain.NavigationSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Keeps listening for [NavigationCommand]s while this app is backgrounded (the Companion phone's
 * screen is normally showing Google Maps, not DualNav). Navigate/Stop/Resume/AddStop all act on
 * the Maps app directly: Navigate/Resume/AddStop via a launched Intent, Stop via the accessibility
 * service (there's no public Android API to pause another app's live navigation any other way —
 * see [MapsControlAccessibilityService]). If accessibility isn't enabled, Stop/AddStop fall back
 * to a notification asking the person holding the phone to act manually.
 */
class CommandReceiverService : Service() {

    private val messageRepository: MessageRepository by inject()
    private val preferencesRepository: PreferencesRepository by inject()
    private val deviceRepository: DeviceRepository by inject()
    private val mapsIntentHandler: MapsIntentHandler by inject()
    private val navigationSessionState: NavigationSessionState by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        serviceScope.launch {
            messageRepository.receiveCommand().collect { command -> handleCommand(command) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun handleCommand(command: NavigationCommand) {
        when (command) {
            is NavigationCommand.Navigate -> {
                navigationSessionState.setDestination(command.destination)
                navigationSessionState.setTravelMode(command.travelMode)
                mapsIntentHandler.openNavigation(command.destination, command.travelMode)
                notifyAlert(NAVIGATE_ALERT_ID, getString(R.string.companion_alert_navigate, command.destination.address))
            }

            is NavigationCommand.AddStop -> handleAddStop(command)
            NavigationCommand.Stop -> handleStop()
            NavigationCommand.Resume -> handleResume()
            is NavigationCommand.StatusCheck -> replyWithStatus()
        }
    }

    private fun handleStop() {
        val stopped = mapsIntentHandler.stopNavigation()
        val message = if (stopped) {
            getString(R.string.companion_alert_stop_auto)
        } else {
            getString(R.string.companion_alert_stop)
        }
        notifyAlert(STOP_ALERT_ID, message)
    }

    private fun handleResume() {
        val destination = navigationSessionState.getDestination()
        if (destination != null) {
            mapsIntentHandler.openNavigationWithStops(
                destination,
                navigationSessionState.getStops(),
                navigationSessionState.getTravelMode()
            )
            notifyAlert(RESUME_ALERT_ID, getString(R.string.companion_alert_resume_auto))
        } else {
            notifyAlert(RESUME_ALERT_ID, getString(R.string.companion_alert_resume))
        }
    }

    private fun handleAddStop(command: NavigationCommand.AddStop) {
        mapsIntentHandler.stopNavigation()
        navigationSessionState.setTravelMode(command.travelMode)
        navigationSessionState.addStop(command.destination)

        val destination = navigationSessionState.getDestination()
        if (destination != null) {
            mapsIntentHandler.openNavigationWithStops(
                destination,
                navigationSessionState.getStops(),
                navigationSessionState.getTravelMode()
            )
        } else {
            mapsIntentHandler.openNavigation(command.destination, command.travelMode)
        }
        notifyAlert(
            ADD_STOP_ALERT_ID,
            getString(R.string.companion_alert_add_stop, command.destination.address)
        )
    }

    private suspend fun replyWithStatus() {
        val role = deviceRepository.getDeviceRole() ?: AppRole.COMPANION
        val connectionType = preferencesRepository.getConnectionPreferences().first() ?: ConnectionType.BLUETOOTH
        val selfInfo = preferencesRepository.getDeviceInfo().first() ?: DeviceInfo(
            deviceId = "",
            deviceName = Build.MODEL,
            role = role,
            connectionType = connectionType,
            lastSeen = System.currentTimeMillis(),
            isConnected = true
        )
        messageRepository.sendStatusUpdate(selfInfo.copy(isConnected = true, lastSeen = System.currentTimeMillis()))
    }

    private fun notifyAlert(notificationId: Int, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(getString(R.string.companion_home_title))
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(
                NotificationChannel(ONGOING_CHANNEL_ID, getString(R.string.companion_ongoing_channel), NotificationManager.IMPORTANCE_LOW)
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(ALERT_CHANNEL_ID, getString(R.string.companion_alert_channel), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, ONGOING_CHANNEL_ID)
            .setContentTitle(getString(R.string.companion_ongoing_notification))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
        startForeground(ONGOING_NOTIFICATION_ID, notification)
    }

    private companion object {
        const val ONGOING_CHANNEL_ID = "companion_ongoing"
        const val ALERT_CHANNEL_ID = "companion_alerts"
        const val ONGOING_NOTIFICATION_ID = 100
        const val NAVIGATE_ALERT_ID = 101
        const val STOP_ALERT_ID = 102
        const val RESUME_ALERT_ID = 103
        const val ADD_STOP_ALERT_ID = 104
    }
}
