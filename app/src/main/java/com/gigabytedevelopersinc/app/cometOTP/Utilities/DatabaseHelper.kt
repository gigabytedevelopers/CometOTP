@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.app.backup.BackupManager
import android.content.Context
import android.widget.Toast
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.R
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.Charset
import javax.crypto.SecretKey

object DatabaseHelper {

    @JvmField
    internal val DatabaseFileLock = Any()

    @JvmStatic
    fun wipeDatabase(context: Context) {
        val db = File(context.filesDir.toString() + "/" + Constants.FILENAME_DATABASE)
        val dbBackup = File(context.filesDir.toString() + "/" + Constants.FILENAME_DATABASE_BACKUP)
        db.delete()
        dbBackup.delete()
    }

    /** Whether a database has been saved (it can exist and still be unreadable). */
    @JvmStatic
    fun databaseExists(context: Context): Boolean {
        return File(context.filesDir.toString() + "/" + Constants.FILENAME_DATABASE).exists()
    }

    @Throws(IOException::class)
    private fun copyFile(src: File, dst: File) {
        FileInputStream(src).use { `in` ->
            FileOutputStream(dst).use { out ->
                val buffer = ByteArray(1024)
                var len: Int
                while (`in`.read(buffer).also { len = it } > 0) {
                    out.write(buffer, 0, len)
                }
            }
        }
    }

    @JvmStatic
    fun backupDatabase(context: Context): Boolean {
        val original = File(context.filesDir.toString() + "/" + Constants.FILENAME_DATABASE)
        val backup = File(context.filesDir.toString() + "/" + Constants.FILENAME_DATABASE_BACKUP)

        if (original.exists()) {
            try {
                copyFile(original, backup)
            } catch (e: IOException) {
                return false
            }
        }

        return true
    }

    @JvmStatic
    fun restoreDatabaseBackup(context: Context): Boolean {
        val original = File(context.filesDir.toString() + "/" + Constants.FILENAME_DATABASE)
        val backup = File(context.filesDir.toString() + "/" + Constants.FILENAME_DATABASE_BACKUP)

        if (backup.exists()) {
            try {
                // Replaced in one step like saveDatabase() does, not copied over it in place.
                FileHelper.writeBytesToFileAtomically(original, FileHelper.readFileToBytes(backup))
            } catch (e: IOException) {
                return false
            }
        }

        return true
    }

    /* Database functions */
    @JvmStatic
    fun saveDatabase(context: Context, entries: ArrayList<Entry>, encryptionKey: SecretKey?): Boolean {
        if (encryptionKey == null) {
            Toast.makeText(context, R.string.toast_encryption_key_empty, Toast.LENGTH_LONG).show()
            return false
        }

        val jsonString = entriesToString(entries)

        try {
            synchronized(DatabaseFileLock) {
                val data = EncryptionHelper.encrypt(encryptionKey, jsonString.toByteArray(Charset.defaultCharset()))

                // Written to a temporary file and renamed over the database, so a crash mid-write
                // cannot leave a truncated (and then unreadable) database behind.
                FileHelper.writeBytesToFileAtomically(File(context.filesDir.toString() + "/" + Constants.FILENAME_DATABASE), data)
            }
        } catch (error: Exception) {
            error.printStackTrace()
            return false
        }

        val backupManager = BackupManager(context)
        backupManager.dataChanged()

        return true
    }

    /**
     * Loads the database. Returns null when it exists but cannot be read: no key, the wrong key,
     * or a file that cannot be read, decrypted or parsed. A failed load must never be saved or
     * backed up in place of the database, which is why it is not an empty list. A database that
     * does not exist yet (nothing saved so far) loads as an empty list.
     */
    @JvmStatic
    fun loadDatabase(context: Context, encryptionKey: SecretKey?): ArrayList<Entry>? {
        if (encryptionKey == null) {
            Toast.makeText(context, R.string.toast_encryption_key_empty, Toast.LENGTH_LONG).show()
            return null
        }

        synchronized(DatabaseFileLock) {
            return loadDatabaseFile(File(context.filesDir.toString() + "/" + Constants.FILENAME_DATABASE), encryptionKey)
        }
    }

    /** [loadDatabase] without the Context: null when [file] exists but cannot be read. */
    @JvmStatic
    internal fun loadDatabaseFile(file: File, encryptionKey: SecretKey): ArrayList<Entry>? {
        if (!file.exists())
            return ArrayList()

        return try {
            val data = EncryptionHelper.decrypt(encryptionKey, FileHelper.readFileToBytes(file))
            stringToEntriesOrNull(String(data, Charset.defaultCharset()))
        } catch (error: Exception) {
            error.printStackTrace()
            null
        }
    }

    /* Conversion functions */

    @JvmStatic
    fun entriesToString(entries: ArrayList<Entry>): String {
        val json = JSONArray()

        for (e in entries) {
            try {
                json.put(e.toJSON())
            } catch (error: Exception) {
                error.printStackTrace()
            }
        }

        return json.toString()
    }

    /**
     * Parses a JSON array of entries. An entry that cannot be read is skipped on its own; the
     * entries around it are still returned. (Callers save what they loaded, so anything left out
     * here is gone from the database after the next save.)
     */
    @JvmStatic
    fun stringToEntries(data: String?): ArrayList<Entry> {
        if (data == null)
            return ArrayList()
        return stringToEntriesOrNull(data) ?: ArrayList()
    }

    /** [stringToEntries], but null when [data] is not a JSON array at all. */
    @JvmStatic
    internal fun stringToEntriesOrNull(data: String): ArrayList<Entry>? {
        val entries = ArrayList<Entry>()

        val json: JSONArray
        try {
            json = JSONArray(data)
        } catch (error: Exception) {
            error.printStackTrace()
            return null
        }

        for (i in 0 until json.length()) {
            try {
                entries.add(Entry(json.getJSONObject(i)))
            } catch (error: Exception) {
                error.printStackTrace()
            }
        }

        return entries
    }
}
