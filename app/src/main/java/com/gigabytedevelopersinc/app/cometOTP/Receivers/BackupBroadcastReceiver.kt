@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Receivers

import android.content.BroadcastReceiver
import android.content.Context
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings

abstract class BackupBroadcastReceiver : BroadcastReceiver() {
    protected fun canSaveBackup(context: Context): Boolean {
        val settings = Settings(context)
        return settings.isBackupLocationSet
    }
}
