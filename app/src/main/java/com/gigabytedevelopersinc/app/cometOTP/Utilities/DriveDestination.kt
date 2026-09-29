@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.content.Context
import android.util.Log
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupDestination.DestinationException
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupDestination.StoredBackup
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DriveClient.DriveException

/**
 * A folder in the user's Google Drive, created by this app under the name the user chose. If the
 * user deletes or trashes it, the next backup creates it again.
 *
 * Nothing here touches the network until a backup is written, so it can be created on the main
 * thread (e.g. to check that a destination is set up); every call below must run off it.
 */
class DriveDestination(private val context: Context, private val settings: Settings) : BackupDestination {

    private val email = settings.driveAccount
    private val folderName = settings.driveFolderName

    private val client = DriveClient(
        token = { DriveAuth.silentToken(context, email) },
        invalidateToken = { DriveAuth.clearToken(context, it) }
    )

    private fun folderId(): String {
        val stored = settings.driveFolderId
        if (stored.isNotEmpty() && client.isUsableFolder(stored))
            return stored

        val id = client.findFolder(folderName) ?: client.createFolder(folderName)
        settings.driveFolderId = id
        return id
    }

    override fun write(name: String, data: ByteArray) {
        drive {
            val folder = folderId()
            val existing = client.findFile(folder, name)
            if (existing != null)
                client.update(existing, data)
            else
                client.create(folder, name, data)
        }
    }

    override fun listAutoBackups(): List<StoredBackup> {
        return drive {
            client.listFolder(folderId())
                .filter { BackupRunner.isAutoBackupName(it.name) }
                .map { StoredBackup(it.name, it.modified, it.id) }
        }
    }

    override fun delete(backup: StoredBackup) {
        // Into the Drive trash rather than gone: the user can still get it back from there.
        drive { client.trash(backup.ref as String) }
    }

    /** Runs a Drive call, turning its failures into the ones backups report. */
    private fun <T> drive(call: () -> T): T {
        try {
            return call()
        } catch (e: DriveException) {
            Log.w(TAG, "Google Drive call failed", e)
            throw when {
                e.retryable -> DestinationException(R.string.backup_error_drive_unreachable, true, cause = e)
                e.status == 401 || e.status == 403 -> DestinationException(R.string.backup_error_drive_reconnect, false, cause = e)
                else -> DestinationException(R.string.backup_error_drive_failed, false, cause = e)
            }
        }
    }

    companion object {
        private val TAG = DriveDestination::class.java.simpleName
    }
}
