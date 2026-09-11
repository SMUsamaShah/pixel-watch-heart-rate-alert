package com.usamashah.heartthreshold

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.health.services.client.data.SampleDataPoint
import kotlin.math.roundToInt

/** Receives the low-power, batched heart-rate stream from Wear OS Health Services. */
class PassiveHeartRateService : PassiveListenerService() {

    private val preferences by lazy {
        getSharedPreferences(MainActivity.PREFERENCES, Context.MODE_PRIVATE)
    }

    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val heartRatePoints = dataPoints.getData(DataType.HEART_RATE_BPM)
        for (point in heartRatePoints) {
            val sample = point as? SampleDataPoint<*> ?: continue
            val bpm = (sample.value as? Number)?.toDouble()?.roundToInt() ?: continue
            handleHeartRate(bpm)
        }
    }

    override fun onPermissionLost() {
        preferences.edit().putBoolean(MainActivity.PREF_ACTIVE, false).apply()
        notifyPermissionLost()
    }

    private fun handleHeartRate(bpm: Int) {
        val threshold = preferences.getInt(MainActivity.PREF_THRESHOLD, MainActivity.DEFAULT_THRESHOLD)
        val wasAbove = preferences.getBoolean(MainActivity.PREF_WAS_ABOVE, false)
        val isAbove = bpm >= threshold
        val episodeStillActive = if (wasAbove) {
            bpm > threshold - MainActivity.RESET_HYSTERESIS_BPM
        } else {
            isAbove
        }

        preferences.edit()
            .putInt(MainActivity.PREF_LAST_HEART_RATE, bpm)
            .putBoolean(MainActivity.PREF_WAS_ABOVE, episodeStillActive)
            .apply()

        if (isAbove && !wasAbove) {
            notifyHeartRateAlert(bpm, threshold)
        }
    }

    private fun notifyHeartRateAlert(bpm: Int, threshold: Int) {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_heart)
            .setContentTitle("Heart rate above limit")
            .setContentText("Detected $bpm BPM (limit $threshold)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        try {
            manager.notify(ALERT_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // The activity requests POST_NOTIFICATIONS before enabling monitoring.
        }

        val vibrator = getSystemService(Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0L, 350L, 150L, 350L),
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0L, 350L, 150L, 350L), -1)
        }
    }

    private fun notifyPermissionLost() {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_heart)
            .setContentTitle("Heart Threshold stopped")
            .setContentText("Background heart-rate permission is no longer available")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            getSystemService(NotificationManager::class.java)
                .notify(PERMISSION_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Notifications may have been revoked together with another permission.
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            ALERT_CHANNEL_ID,
            getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.alert_channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ALERT_CHANNEL_ID = "heart_rate_alerts"
        const val ALERT_NOTIFICATION_ID = 1001
        const val PERMISSION_NOTIFICATION_ID = 1002

        fun listenerConfig(): PassiveListenerConfig = PassiveListenerConfig.builder()
            .setDataTypes(setOf(DataType.HEART_RATE_BPM))
            .build()

        fun register(context: Context) = HealthServices.getClient(context)
            .passiveMonitoringClient
            .setPassiveListenerServiceAsync(
                PassiveHeartRateService::class.java,
                listenerConfig()
            )

        fun unregister(context: Context) = HealthServices.getClient(context)
            .passiveMonitoringClient
            .clearPassiveListenerServiceAsync()
    }
}
