@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Tasks

import android.content.Context
import android.net.Uri
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.StorageAccessHelper

/**
 * Project - CometOTP
 * Created with Android Studio
 * Company: Gigabyte Developers
 * User: Emmanuel Nwokoma
 * Title: Founder and CEO
 * Day: Wednesday, 10
 * Month: February
 * Year: 2021
 * Date: 10 Feb, 2021
 * Time: 12:12 AM
 * Desc: PlainTextBackupTask
 **/
class PlainTextBackupTask(context: Context, private val entries: ArrayList<Entry>, uri: Uri?) :
    GenericBackupTask(context, uri) {

    override val backupType: Constants.BackupType
        get() = Constants.BackupType.PLAIN_TEXT

    override fun doBackup(): Boolean {
        val payload = DatabaseHelper.entriesToString(entries)
        return StorageAccessHelper.saveFile(applicationContext, uri!!, payload)
    }
}
