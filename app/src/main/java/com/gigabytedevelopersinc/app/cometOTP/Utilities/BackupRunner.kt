@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.content.Context
import android.util.Log
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupDestination.DestinationException
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupDestination.StoredBackup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.SecretKey

/**
 * Makes one automatic backup (scheduled, "Back up now" or Auto Sync): reads the database, writes
 * it as a password-encrypted backup to the chosen destination, then removes the automatic backups
 * beyond the number to keep. Runs on the calling thread, which must not be the main thread.
 */
object BackupRunner {
    private val TAG = BackupRunner::class.java.simpleName

    /** What the user has to do before a failed backup can succeed. */
    enum class Fix { NONE, OPEN_SCHEDULED_BACKUP }

    sealed class Outcome {
        class Success(val fileName: String) : Outcome()

        /** Failed, but may work if tried again later (no network, a busy provider). */
        class Retry(val messageId: Int) : Outcome()

        /** Failed and will keep failing until the user fixes [fix]. */
        class Failed(val messageId: Int, val fix: Fix) : Outcome()

        /** Nothing was written and nothing is wrong (there are no accounts yet). */
        class Skipped(val messageId: Int) : Outcome()
    }

    // The worker, "Back up now" and the backup after unlocking can overlap; one at a time keeps
    // two of them from pruning each other's files.
    private val runLock = Any()

    fun run(context: Context, encryptionKey: SecretKey?, now: Long = System.currentTimeMillis()): Outcome {
        val appContext = context.applicationContext

        synchronized(runLock) {
            val settings = Settings(appContext)
            settings.lastBackupAttempt = now

            val outcome = backUp(appContext, settings, encryptionKey, now)

            when (outcome) {
                is Outcome.Success -> {
                    settings.lastBackupSuccess = now
                    settings.lastBackupFile = outcome.fileName
                    settings.lastBackupError = ""
                }
                is Outcome.Retry -> settings.lastBackupError = appContext.getString(outcome.messageId)
                is Outcome.Failed -> settings.lastBackupError = appContext.getString(outcome.messageId)
                is Outcome.Skipped -> settings.lastBackupError = appContext.getString(outcome.messageId)
            }

            return outcome
        }
    }

    private fun backUp(context: Context, settings: Settings, encryptionKey: SecretKey?, now: Long): Outcome {
        val password = settings.backupPasswordEnc
        if (password.isEmpty())
            return Outcome.Failed(R.string.backup_error_no_password, Fix.OPEN_SCHEDULED_BACKUP)

        val destination = BackupDestination.forSettings(context, settings)
            ?: return Outcome.Failed(R.string.backup_error_no_destination, Fix.OPEN_SCHEDULED_BACKUP)

        if (encryptionKey == null)
            return Outcome.Failed(R.string.backup_error_database_unreadable, Fix.NONE)

        // An unreadable database must never replace a good backup with an empty one.
        val entries = DatabaseHelper.loadDatabase(context, encryptionKey)
            ?: return Outcome.Failed(R.string.backup_error_database_unreadable, Fix.NONE)

        // An empty backup is worth nothing, and writing one would prune real backups to make room.
        if (entries.isEmpty())
            return Outcome.Skipped(R.string.backup_error_nothing_to_back_up)

        val data = try {
            BackupHelper.encryptBackup(password, DatabaseHelper.entriesToString(entries))
        } catch (e: Exception) {
            Log.e(TAG, "Encrypting the backup failed", e)
            return Outcome.Failed(R.string.backup_toast_export_failed, Fix.NONE)
        }

        val keep = settings.scheduledBackupKeep
        val name = backupName(now, keep)

        try {
            destination.write(name, data)
        } catch (e: DestinationException) {
            Log.w(TAG, "Writing the backup failed", e)
            return if (e.retryable)
                Outcome.Retry(e.messageId)
            else
                Outcome.Failed(e.messageId, e.fix)
        }

        // The backup is safe at this point; failing to remove old ones is only logged and is
        // tried again after the next backup.
        try {
            for (old in backupsToPrune(destination.listAutoBackups(), name, keep))
                destination.delete(old)
        } catch (e: DestinationException) {
            Log.w(TAG, "Removing old backups failed", e)
        }

        return Outcome.Success(name)
    }

    /** One file overwritten when only one is kept, otherwise a new file named by the time. */
    fun backupName(now: Long, keep: Int): String {
        return if (keep <= 1)
            Constants.BACKUP_FILENAME_AUTO
        else
            String.format(Constants.BACKUP_FILENAME_AUTO_FORMAT, timestamp(now))
    }

    private fun timestamp(now: Long): String {
        return SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ENGLISH).format(Date(now))
    }

    /** True for the files [backupName] makes, which are the only ones ever removed. */
    fun isAutoBackupName(name: String?): Boolean {
        return name != null && name.startsWith(AUTO_BACKUP_PREFIX) && name.endsWith(AUTO_BACKUP_SUFFIX)
    }

    /**
     * The automatic backups to remove so that [keep] are left, counting [justWritten]. Newest
     * first by modification time, so switching between one file and dated files keeps the right
     * ones; [justWritten] itself is never removed.
     */
    fun backupsToPrune(stored: List<StoredBackup>, justWritten: String, keep: Int): List<StoredBackup> {
        return stored
            .filter { isAutoBackupName(it.name) && it.name != justWritten }
            .sortedWith(compareByDescending<StoredBackup> { it.modified }.thenByDescending { it.name })
            .drop((keep - 1).coerceAtLeast(0))
    }

    private const val AUTO_BACKUP_PREFIX = "otp_accounts_auto"
    private const val AUTO_BACKUP_SUFFIX = ".json.aes"
}
