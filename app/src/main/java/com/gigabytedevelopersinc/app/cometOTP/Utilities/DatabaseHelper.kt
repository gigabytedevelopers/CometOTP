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
                copyFile(backup, original)
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

                FileHelper.writeBytesToFile(File(context.filesDir.toString() + "/" + Constants.FILENAME_DATABASE), data)
            }
        } catch (error: Exception) {
            error.printStackTrace()
            return false
        }

        val backupManager = BackupManager(context)
        backupManager.dataChanged()

        return true
    }

    @JvmStatic
    fun loadDatabase(context: Context, encryptionKey: SecretKey?): ArrayList<Entry> {
        var entries = ArrayList<Entry>()

        if (encryptionKey != null) {
            try {
                synchronized(DatabaseFileLock) {
                    var data = FileHelper.readFileToBytes(File(context.filesDir.toString() + "/" + Constants.FILENAME_DATABASE))
                    data = EncryptionHelper.decrypt(encryptionKey, data)

                    entries = stringToEntries(String(data, Charset.defaultCharset()))
                }
            } catch (error: Exception) {
                error.printStackTrace()
            }
        } else {
            Toast.makeText(context, R.string.toast_encryption_key_empty, Toast.LENGTH_LONG).show()
        }

        return entries
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

    @JvmStatic
    fun stringToEntries(data: String?): ArrayList<Entry> {
        val entries = ArrayList<Entry>()

        try {
            val json = JSONArray(data!!)

            for (i in 0 until json.length()) {
                val entry = Entry(json.getJSONObject(i))
                entries.add(entry)
            }
        } catch (error: Exception) {
            error.printStackTrace()
        }

        return entries
    }
}
