@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Tasks

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings

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
 * Time: 12:08 AM
 * Desc: GenericRestoreTask
 **/
abstract class GenericRestoreTask(context: Context, uri: Uri?) :
    UiBasedBackgroundTask<GenericRestoreTask.RestoreTaskResult>(RestoreTaskResult.failure(R.string.backup_toast_import_failed)) {
    protected val applicationContext: Context = context.applicationContext
    protected val settings: Settings = Settings(applicationContext)
    protected var uri: Uri? = uri

    abstract override fun doInBackground(): RestoreTaskResult

    class RestoreTaskResult(
        @JvmField val success: Boolean,
        @JvmField val payload: String?,
        @JvmField val messageId: Int
    ) {
        @JvmField
        var isPGP = false
        @JvmField
        var decryptIntent: Intent? = null
        @JvmField
        var uri: Uri? = null

        constructor(success: Boolean, payload: String?, messageId: Int, isPGP: Boolean, decryptIntent: Intent?, uri: Uri?) :
            this(success, payload, messageId) {
            this.isPGP = isPGP
            this.decryptIntent = decryptIntent
            this.uri = uri
        }

        companion object {
            @JvmStatic
            fun success(payload: String?): RestoreTaskResult {
                return RestoreTaskResult(true, payload, 0)
            }

            @JvmStatic
            fun failure(messageId: Int): RestoreTaskResult {
                return RestoreTaskResult(false, null, messageId)
            }
        }
    }
}
