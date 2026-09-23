@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Tasks

import android.content.Context
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
 * Time: 12:14 AM
 * Desc: PlainTextRestoreTask
 **/
class PlainTextRestoreTask(context: Context, uri: Uri?) : GenericRestoreTask(context, uri) {

    override fun doInBackground(): RestoreTaskResult {
        val data = StorageAccessHelper.loadFileString(applicationContext, uri!!)
        return RestoreTaskResult.success(data)
    }
}
