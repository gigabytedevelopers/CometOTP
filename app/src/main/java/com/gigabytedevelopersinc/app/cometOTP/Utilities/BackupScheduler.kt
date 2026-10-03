@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.gigabytedevelopersinc.app.cometOTP.Tasks.ScheduledBackupWorker
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Keeps the WorkManager jobs in line with the scheduled backup settings. WorkManager survives
 * reboots and app updates by itself; [reconcile] only has to run when the settings change and on
 * app start (to put the job back after the settings were restored onto another device).
 */
object BackupScheduler {
    const val WORK_SCHEDULED = "scheduled-backup"
    const val WORK_AUTO_SYNC = "auto-sync-backup"

    /** WorkManager's shortest period. Only offered in debug builds, for testing. */
    const val MIN_INTERVAL_MINUTES = 15
    const val DAY_MINUTES = 24 * 60

    /** Auto Sync waits this long after an edit, so a burst of edits makes one backup. */
    private const val AUTO_SYNC_DELAY_SECONDS = 10L

    fun reconcile(context: Context) {
        val settings = Settings(context)
        val workManager = WorkManager.getInstance(context)

        if (!settings.scheduledBackupEnabled) {
            workManager.cancelUniqueWork(WORK_SCHEDULED)
            settings.scheduledBackupSignature = ""
            return
        }

        // Re-enqueueing an unchanged schedule would move its next run, so an unchanged one is
        // only put back if it is missing. A changed one replaces the old job and starts afresh.
        val signature = signature(settings)
        val policy = if (signature == settings.scheduledBackupSignature)
            ExistingPeriodicWorkPolicy.KEEP
        else
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE

        val interval = settings.scheduledBackupInterval.coerceAtLeast(MIN_INTERVAL_MINUTES)
        val delay = initialDelay(System.currentTimeMillis(), TimeZone.getDefault(), settings.scheduledBackupTime, interval)

        val request = PeriodicWorkRequestBuilder<ScheduledBackupWorker>(interval.toLong(), TimeUnit.MINUTES)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints(settings, scheduled = true))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .setInputData(workDataOf(ScheduledBackupWorker.KEY_TRIGGER to ScheduledBackupWorker.TRIGGER_SCHEDULE))
            .build()

        workManager.enqueueUniquePeriodicWork(WORK_SCHEDULED, policy, request)
        settings.scheduledBackupSignature = signature
    }

    /** Backs up shortly after an edit, from the background (KeyStore encryption only). */
    fun autoSync(context: Context) {
        val settings = Settings(context)

        val request = OneTimeWorkRequestBuilder<ScheduledBackupWorker>()
            .setInitialDelay(AUTO_SYNC_DELAY_SECONDS, TimeUnit.SECONDS)
            .setConstraints(constraints(settings, scheduled = false))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .setInputData(workDataOf(ScheduledBackupWorker.KEY_TRIGGER to ScheduledBackupWorker.TRIGGER_AUTO_SYNC))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(WORK_AUTO_SYNC, ExistingWorkPolicy.REPLACE, request)
    }

    fun scheduledWorkInfo(context: Context): LiveData<List<WorkInfo>> {
        return WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(WORK_SCHEDULED)
    }

    private fun constraints(settings: Settings, scheduled: Boolean): Constraints {
        val builder = Constraints.Builder()
            .setRequiredNetworkType(networkType(settings))

        // An edit is backed up straight away; only the routine backup waits for a good moment.
        if (scheduled) {
            builder.setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .setRequiresCharging(settings.scheduledBackupOnlyCharging)
        }

        return builder.build()
    }

    private fun networkType(settings: Settings): NetworkType {
        return when (settings.backupDestination) {
            Constants.BackupDestinationType.LOCAL -> NetworkType.NOT_REQUIRED
            Constants.BackupDestinationType.DRIVE -> NetworkType.CONNECTED
        }
    }

    /** Everything that goes into the periodic job; a change means it has to be replaced. */
    internal fun signature(settings: Settings): String {
        val interval = settings.scheduledBackupInterval
        val time = if (interval >= DAY_MINUTES) settings.scheduledBackupTime else -1
        return listOf(interval, time, settings.backupDestination.name, settings.scheduledBackupOnlyCharging,
            networkType(settings).name).joinToString("|")
    }

    /**
     * Milliseconds from [now] until the first backup. Daily and weekly backups start at the next
     * [minuteOfDay] in [zone]; shorter intervals start right away.
     */
    fun initialDelay(now: Long, zone: TimeZone, minuteOfDay: Int, intervalMinutes: Int): Long {
        if (intervalMinutes < DAY_MINUTES)
            return 0

        val next = Calendar.getInstance(zone)
        next.timeInMillis = now
        next.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        next.set(Calendar.MINUTE, minuteOfDay % 60)
        next.set(Calendar.SECOND, 0)
        next.set(Calendar.MILLISECOND, 0)

        // A time that has passed (or is about to) is tomorrow's. Adding a calendar day rather
        // than 24 hours keeps the wall-clock time across a daylight saving change.
        if (next.timeInMillis <= now + TimeUnit.MINUTES.toMillis(1))
            next.add(Calendar.DAY_OF_MONTH, 1)

        return next.timeInMillis - now
    }

    /**
     * True when the last successful backup is at least an interval old (or there is none). A
     * last success in the future means the clock was turned back; it counts as overdue.
     */
    fun isOverdue(lastSuccess: Long, intervalMinutes: Int, now: Long): Boolean {
        if (lastSuccess <= 0 || lastSuccess > now)
            return true
        return now - lastSuccess >= TimeUnit.MINUTES.toMillis(intervalMinutes.toLong())
    }
}
