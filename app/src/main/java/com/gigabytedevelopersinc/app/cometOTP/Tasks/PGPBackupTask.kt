@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Tasks

import android.content.Context
import android.net.Uri
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
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
 * Time: 12:18 AM
 * Desc: PGPBackupTask
 **/
class PGPBackupTask(context: Context, private val payload: String?, uri: Uri?) : GenericBackupTask(context, uri) {

    override val backupType: Constants.BackupType
        get() = Constants.BackupType.OPEN_PGP

    override fun doBackup(): Boolean {
        return StorageAccessHelper.saveFile(applicationContext, uri!!, payload!!)
    }
}
