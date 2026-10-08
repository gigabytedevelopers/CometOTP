@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupDestination.DestinationException
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupDestination.StoredBackup

/**
 * A folder the user picked with the system folder picker (the default backup location). Access
 * to it lasts until the user revokes it or the folder goes away, which is a failure the user has
 * to fix by picking the folder again.
 */
class LocalFolderDestination(private val context: Context, private val treeUri: Uri) : BackupDestination {

    private fun folder(): DocumentFile {
        // Without the persisted grant every call below fails with a SecurityException, e.g. after
        // the settings were restored onto another device, or the user revoked it.
        val granted = context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isWritePermission
        }
        if (!granted)
            throw DestinationException(R.string.backup_error_folder_access_lost, false)

        val folder = try {
            DocumentFile.fromTreeUri(context, treeUri)
        } catch (e: IllegalArgumentException) {
            null
        }
        if (folder == null || !folder.isDirectory)
            throw DestinationException(R.string.backup_error_folder_access_lost, false)

        return folder
    }

    override fun write(name: String, data: ByteArray) {
        val file = try {
            val location = folder()
            location.findFile(name) ?: location.createFile(Constants.BACKUP_MIMETYPE_CRYPT, name)
        } catch (e: SecurityException) {
            throw DestinationException(R.string.backup_error_folder_access_lost, false, cause = e)
        } ?: throw DestinationException(R.string.backup_toast_file_creation_failed, true)

        // saveFile truncates ("wt"), so a shorter backup never keeps the tail of an older one.
        if (!StorageAccessHelper.saveFile(context, file.uri, data))
            throw DestinationException(R.string.backup_toast_export_failed, true)
    }

    override fun listAutoBackups(): List<StoredBackup> {
        return try {
            folder().listFiles()
                .filter { it.isFile && BackupRunner.isAutoBackupName(it.name) }
                .map { StoredBackup(it.name!!, it.lastModified(), it) }
        } catch (e: SecurityException) {
            throw DestinationException(R.string.backup_error_folder_access_lost, false, cause = e)
        }
    }

    override fun delete(backup: StoredBackup) {
        val file = backup.ref as DocumentFile
        val deleted = try {
            file.delete()
        } catch (e: SecurityException) {
            false
        }
        if (!deleted)
            throw DestinationException(R.string.backup_error_prune_failed, true)
    }

    companion object {
        /** The folder's display name, or null when it cannot be read. */
        fun folderName(context: Context, treeUri: Uri): String? {
            return try {
                DocumentFile.fromTreeUri(context, treeUri)?.name
            } catch (e: Exception) {
                null
            }
        }
    }
}
