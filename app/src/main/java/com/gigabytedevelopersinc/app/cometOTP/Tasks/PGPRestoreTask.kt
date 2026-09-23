@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Tasks

import android.content.Context
import android.content.Intent
import android.net.Uri
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
 * Time: 12:20 AM
 * Desc: PGPRestoreTask
 **/
class PGPRestoreTask(context: Context, uri: Uri?, private val decryptIntent: Intent?) : GenericRestoreTask(context, uri) {

    override fun doInBackground(): RestoreTaskResult {
        val data = StorageAccessHelper.loadFileString(applicationContext, uri!!)

        return RestoreTaskResult(true, data, 0, true, decryptIntent, uri)
    }
}
