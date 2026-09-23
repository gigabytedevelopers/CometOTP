@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.app.backup.BackupAgentHelper
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FileBackupHelper
import android.app.backup.SharedPreferencesBackupHelper
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.IOException

class BackupAgent : BackupAgentHelper() {

    // PreferenceManager.getDefaultSharedPreferencesName is only available in API > 24, this is its implementation
    internal val defaultSharedPreferencesName: String
        get() = packageName + "_preferences"

    @Throws(IOException::class)
    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) {
        val settings = Settings(this)

        val stringBuilder = StringBuilder("onBackup called with the backup service set to ")
        stringBuilder.append(if (settings.androidBackupServiceEnabled) "enabled" else "disabled")

        if (settings.androidBackupServiceEnabled) {
            synchronized(DatabaseHelper.DatabaseFileLock) {
                stringBuilder.append(" calling parent onBackup")
                super.onBackup(oldState, data, newState)
            }
        }
        Log.d(BackupAgent::class.java.simpleName, stringBuilder.toString())
    }

    @Throws(IOException::class)
    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) {
        val settings = Settings(this)
        val stringBuilder = StringBuilder("onRestore called with the backup service set to ")
        stringBuilder.append(if (settings.androidBackupServiceEnabled) "enabled" else "disabled")

        synchronized(DatabaseHelper.DatabaseFileLock) {
            stringBuilder.append(" but restore happens regardless, calling parent onRestore")
            super.onRestore(data, appVersionCode, newState)
        }
        Log.d(BackupAgent::class.java.simpleName, stringBuilder.toString())
    }

    override fun onCreate() {
        val prefs = defaultSharedPreferencesName

        val sharedPreferencesBackupHelper = SharedPreferencesBackupHelper(this, prefs)
        addHelper(PREFS_BACKUP_KEY, sharedPreferencesBackupHelper)

        val fileBackupHelper = FileBackupHelper(this, Constants.FILENAME_DATABASE, Constants.FILENAME_DATABASE_BACKUP)
        addHelper(FILES_BACKUP_KEY, fileBackupHelper)
    }

    companion object {
        internal const val PREFS_BACKUP_KEY = "prefs"
        internal const val FILES_BACKUP_KEY = "files"
    }
}
