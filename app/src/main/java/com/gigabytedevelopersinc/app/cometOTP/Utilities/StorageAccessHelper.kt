@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Project - CometOTP
 * Created with Android Studio
 * Company: Gigabyte Developers
 * User: Emmanuel Nwokoma
 * Title: Founder and CEO
 * Day: Tuesday, 24
 * Month: December
 * Year: 2019
 * Date: 24 Dec, 2019
 * Time: 4:28 AM
 * Desc: StorageAccessHelper
 **/
object StorageAccessHelper {
    @JvmStatic
    fun saveFile(context: Context, file: Uri, data: ByteArray): Boolean {
        var success = true

        try {
            // Both are closed even when the write throws. A provider that returns no descriptor
            // is a failed save, not a crash.
            // "wt", not "w": some providers do not truncate for "w", so writing a shorter backup
            // over a longer one left the old tail behind and the file could not be restored.
            val pfd = context.contentResolver.openFileDescriptor(file, "wt") ?: return false
            pfd.use {
                FileOutputStream(it.fileDescriptor).use { fileOutputStream ->
                    fileOutputStream.write(data)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            success = false
        } catch (e: IllegalArgumentException) {
            // A provider that does not accept the "wt" mode.
            e.printStackTrace()
            success = false
        }

        return success
    }

    @JvmStatic
    fun saveFile(context: Context, file: Uri, data: String): Boolean {
        return saveFile(context, file, data.toByteArray(Charsets.UTF_8))
    }

    @JvmStatic
    @Throws(IOException::class)
    fun loadFile(context: Context, file: Uri): ByteArray {
        // openInputStream returns null when the provider has nothing to give; report that as the
        // IOException callers already handle instead of a NullPointerException they do not.
        val stream = context.contentResolver.openInputStream(file) ?: throw IOException("Cannot open $file")
        stream.use { inputStream ->
            val bytes = ByteArrayOutputStream()

            val buffer = ByteArray(1024)
            var count: Int

            while (inputStream.read(buffer).also { count = it } != -1) {
                bytes.write(buffer, 0, count)
            }

            return bytes.toByteArray()
        }
    }

    @JvmStatic
    fun loadFileString(context: Context, file: Uri): String {
        var result = ""

        try {
            val content = loadFile(context, file)
            result = String(content, Charsets.UTF_8)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return result
    }
}
