@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.content.Context
import java.io.IOException

/** Somewhere scheduled backups and Auto Sync write to: a folder on the device or in Google Drive. */
interface BackupDestination {

    /** A backup already in the destination; [ref] is whatever [delete] needs to find it again. */
    class StoredBackup(val name: String, val modified: Long, val ref: Any)

    /**
     * A write, list or delete that failed. [messageId] says why, for the user. A [retryable]
     * failure (no network, a busy provider) may work on the next attempt; any other failure needs
     * the user to fix something first, so [fix] says what.
     */
    class DestinationException(
        val messageId: Int,
        val retryable: Boolean,
        val fix: BackupRunner.Fix = BackupRunner.Fix.OPEN_SCHEDULED_BACKUP,
        cause: Throwable? = null
    ) : IOException(cause)

    /** Writes [data] as [name], replacing a file of that name. */
    @Throws(DestinationException::class)
    fun write(name: String, data: ByteArray)

    /** The automatic backups in the destination (see [BackupRunner.isAutoBackupName]). */
    @Throws(DestinationException::class)
    fun listAutoBackups(): List<StoredBackup>

    @Throws(DestinationException::class)
    fun delete(backup: StoredBackup)

    companion object {
        /** The destination chosen in [settings], or null when it has not been set up. */
        fun forSettings(context: Context, settings: Settings): BackupDestination? {
            return when (settings.backupDestination) {
                Constants.BackupDestinationType.LOCAL ->
                    if (settings.isBackupLocationSet) LocalFolderDestination(context, settings.backupLocation) else null
                Constants.BackupDestinationType.DRIVE -> null
            }
        }

        fun isConfigured(context: Context, settings: Settings): Boolean {
            return forSettings(context, settings) != null
        }
    }
}
