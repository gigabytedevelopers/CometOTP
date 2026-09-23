@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.R
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import javax.crypto.SecretKey

/**
 * Project - CometOTP
 * Created with Android Studio
 * Company: Gigabyte Developers
 * User: Emmanuel Nwokoma
 * Title: Founder and CEO
 * Day: Tuesday, 24
 * Month: December
 * Year: 2019
 * Date: 24 Dec, 2019
 * Time: 4:22 AM
 * Desc: BackupHelper
 **/
object BackupHelper {
    class BackupFile {
        @JvmField
        var file: DocumentFile? = null
        @JvmField
        var errorMessage = 0
    }

    private fun backupMimeType(type: Constants.BackupType): String {
        var mimeType = Constants.BACKUP_MIMETYPE_PLAIN

        when (type) {
            Constants.BackupType.PLAIN_TEXT -> mimeType = Constants.BACKUP_MIMETYPE_PLAIN
            Constants.BackupType.ENCRYPTED -> mimeType = Constants.BACKUP_MIMETYPE_CRYPT
            Constants.BackupType.OPEN_PGP -> mimeType = Constants.BACKUP_MIMETYPE_PGP
            else -> {}
        }

        return mimeType
    }

    @JvmStatic
    fun backupFile(context: Context, backupLocationUri: Uri, type: Constants.BackupType): BackupFile {
        val backupFile = BackupFile()
        val backupLocation = DocumentFile.fromTreeUri(context, backupLocationUri)

        if (backupLocation != null) {
            // Try to find an existing file to overwrite
            backupFile.file = backupLocation.findFile(backupFilename(context, type))

            // Try to create a new file
            if (backupFile.file == null) {
                backupFile.file = backupLocation.createFile(backupMimeType(type), backupFilename(context, type))
            }

            // Both failed
            if (backupFile.file == null)
                backupFile.errorMessage = R.string.backup_toast_file_creation_failed
        } else {
            backupFile.errorMessage = R.string.backup_toast_location_access_failed
        }

        return backupFile
    }

    @JvmStatic
    fun backupFilename(context: Context, type: Constants.BackupType): String {
        val settings = Settings(context)
        when (type) {
            Constants.BackupType.PLAIN_TEXT -> {
                return if (settings.isAppendingDateTimeToBackups) {
                    String.format(Constants.BACKUP_FILENAME_PLAIN_FORMAT, Tools.getDateTimeString())
                } else {
                    Constants.BACKUP_FILENAME_PLAIN
                }
            }
            Constants.BackupType.ENCRYPTED -> {
                return if (settings.isAppendingDateTimeToBackups) {
                    String.format(Constants.BACKUP_FILENAME_CRYPT_FORMAT, Tools.getDateTimeString())
                } else {
                    Constants.BACKUP_FILENAME_CRYPT
                }
            }
            Constants.BackupType.OPEN_PGP -> {
                return if (settings.isAppendingDateTimeToBackups) {
                    String.format(Constants.BACKUP_FILENAME_PGP_FORMAT, Tools.getDateTimeString())
                } else {
                    Constants.BACKUP_FILENAME_PGP
                }
            }
            else -> {}
        }

        return Constants.BACKUP_FILENAME_PLAIN
    }

    @JvmStatic
    fun autoBackupType(context: Context): Constants.BackupType {
        val settings = Settings(context)

        if (!settings.isBackupLocationSet) {
            return Constants.BackupType.UNAVAILABLE
        }

        if (settings.backupPasswordEnc.isNotEmpty()) {
            return Constants.BackupType.ENCRYPTED
        }

        return Constants.BackupType.UNAVAILABLE
    }

    @JvmStatic
    fun backupToFile(context: Context, uri: Uri?, password: String?, encryptionKey: SecretKey?): Boolean {
        val entries: ArrayList<Entry> = DatabaseHelper.loadDatabase(context, encryptionKey)
        val plain = DatabaseHelper.entriesToString(entries)

        return backupToFile(context, uri, password, plain)
    }

    @JvmStatic
    fun backupToFile(context: Context, uri: Uri?, password: String?, plain: String?): Boolean {
        var success = true

        try {
            val iter = EncryptionHelper.generateRandomIterations()
            val salt = EncryptionHelper.generateRandom(Constants.ENCRYPTION_IV_LENGTH)

            val key = EncryptionHelper.generateSymmetricKeyPBKDF2(password!!, iter, salt)
            val encrypted = EncryptionHelper.encrypt(key, plain!!.toByteArray(StandardCharsets.UTF_8))

            val iterBytes = ByteBuffer.allocate(Constants.INT_LENGTH).putInt(iter).array()
            val data = ByteArray(Constants.INT_LENGTH + Constants.ENCRYPTION_IV_LENGTH + encrypted.size)

            System.arraycopy(iterBytes, 0, data, 0, Constants.INT_LENGTH)
            System.arraycopy(salt, 0, data, Constants.INT_LENGTH, Constants.ENCRYPTION_IV_LENGTH)
            System.arraycopy(encrypted, 0, data, Constants.INT_LENGTH + Constants.ENCRYPTION_IV_LENGTH, encrypted.size)

            success = StorageAccessHelper.saveFile(context, uri!!, data)
        } catch (e: Exception) {
            e.printStackTrace()
            success = false
        }

        return success
    }
}
