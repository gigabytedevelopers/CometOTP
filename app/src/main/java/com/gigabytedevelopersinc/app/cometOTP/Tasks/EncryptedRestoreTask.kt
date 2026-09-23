@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Tasks

import android.content.Context
import android.net.Uri
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.StorageAccessHelper
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Arrays

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
 * Time: 12:02 AM
 * Desc: EncryptedRestoreTask
 **/
class EncryptedRestoreTask(
    context: Context,
    uri: Uri?,
    private val password: String?,
    private val oldFormat: Boolean
) : GenericRestoreTask(context, uri) {

    override fun doInBackground(): RestoreTaskResult {
        var success = true
        var decryptedString = ""

        try {
            val data = StorageAccessHelper.loadFile(applicationContext, uri!!)

            if (oldFormat) {
                val key = EncryptionHelper.generateSymmetricKeyFromPassword(password!!)
                val decrypted = EncryptionHelper.decrypt(key, data)

                decryptedString = String(decrypted, StandardCharsets.UTF_8)
            } else {
                // java.util.Arrays.copyOfRange on purpose: unlike Kotlin's copyOfRange it zero-pads
                // a range that runs past the end of a short file instead of throwing.
                val iterBytes = Arrays.copyOfRange(data, 0, Constants.INT_LENGTH)
                val salt = Arrays.copyOfRange(data, Constants.INT_LENGTH, Constants.INT_LENGTH + Constants.ENCRYPTION_IV_LENGTH)
                val encrypted = Arrays.copyOfRange(data, Constants.INT_LENGTH + Constants.ENCRYPTION_IV_LENGTH, data.size)

                val iter = ByteBuffer.wrap(iterBytes).int

                val key = EncryptionHelper.generateSymmetricKeyPBKDF2(password!!, iter, salt)

                val decrypted = EncryptionHelper.decrypt(key, encrypted)
                decryptedString = String(decrypted, StandardCharsets.UTF_8)
            }
        } catch (e: Exception) {
            success = false
            e.printStackTrace()
        }

        return if (success) {
            RestoreTaskResult.success(decryptedString)
        } else {
            RestoreTaskResult.failure(R.string.backup_toast_import_decryption_failed)
        }
    }
}
