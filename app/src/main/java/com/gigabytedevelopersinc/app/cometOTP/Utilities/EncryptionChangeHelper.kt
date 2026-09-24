@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.content.Context
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import javax.crypto.SecretKey

/**
 * Re-encrypts the database with a new key, restoring the internal backup if anything fails.
 * Shared by the settings screen and the security screen.
 */
object EncryptionChangeHelper {

    /**
     * SAVE_FAILED also covers a current database that cannot be read: nothing is saved under the
     * new key then either. (The names are pinned by PersistedNamesTest.)
     */
    enum class Status { SUCCESS, BACKUP_FAILED, NO_KEY, SAVE_FAILED }

    class Result internal constructor(
        @JvmField val status: Status,
        @JvmField val newKey: SecretKey?
    )

    /**
     * @param currentKey  key the database is currently encrypted with (may be null when there is no
     *                    database yet)
     * @param newType     encryption type to switch to
     * @param newKeyBytes key material for [Constants.EncryptionType.PASSWORD]; ignored for the KeyStore
     */
    @JvmStatic
    fun changeEncryption(context: Context, currentKey: SecretKey?,
                         newType: Constants.EncryptionType, newKeyBytes: ByteArray?): Result {
        if (!DatabaseHelper.backupDatabase(context))
            return Result(Status.BACKUP_FAILED, null)

        // A database that cannot be read with the current key (or without one) is left alone:
        // saving what was not loaded under the new key would replace every account with nothing.
        val entries: ArrayList<Entry>? = if (currentKey != null)
            DatabaseHelper.loadDatabase(context, currentKey)
        else if (!DatabaseHelper.databaseExists(context))
            ArrayList()
        else
            null

        if (entries == null)
            return Result(Status.SAVE_FAILED, null)

        val newEncryptionKey: SecretKey?
        if (newType == Constants.EncryptionType.KEYSTORE) {
            newEncryptionKey = KeyStoreHelper.loadEncryptionKeyFromKeyStore(context, true)
        } else if (newKeyBytes != null && newKeyBytes.isNotEmpty()) {
            newEncryptionKey = EncryptionHelper.generateSymmetricKey(newKeyBytes)
        } else {
            DatabaseHelper.restoreDatabaseBackup(context)
            return Result(Status.NO_KEY, null)
        }

        // The KeyStore key can fail to load. saveDatabase() would report that with a Toast, which
        // crashes on the background threads this runs on.
        if (newEncryptionKey == null) {
            DatabaseHelper.restoreDatabaseBackup(context)
            return Result(Status.NO_KEY, null)
        }

        if (DatabaseHelper.saveDatabase(context, entries, newEncryptionKey))
            return Result(Status.SUCCESS, newEncryptionKey)

        DatabaseHelper.restoreDatabaseBackup(context)
        return Result(Status.SAVE_FAILED, null)
    }
}
