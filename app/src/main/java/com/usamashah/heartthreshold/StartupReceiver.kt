package com.usamashah.heartthreshold

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Re-registers passive Health Services data after the watch reboots. */
class StartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val preferences = context.getSharedPreferences(MainActivity.PREFERENCES, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(MainActivity.PREF_ACTIVE, false)) return

        WorkManager.getInstance(context).enqueueUniqueWork(
            RESTORE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RestorePassiveMonitoringWorker>().build()
        )
    }

    companion object {
        const val RESTORE_WORK_NAME = "restore_passive_monitoring"
    }
}

class RestorePassiveMonitoringWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val preferences = applicationContext.getSharedPreferences(
            MainActivity.PREFERENCES,
            Context.MODE_PRIVATE
        )
        if (!preferences.getBoolean(MainActivity.PREF_ACTIVE, false)) return Result.success()

        return try {
            PassiveHeartRateService.register(applicationContext).get(60, TimeUnit.SECONDS)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
