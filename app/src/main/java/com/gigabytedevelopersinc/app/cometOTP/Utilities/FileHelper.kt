@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

internal object FileHelper {
    @JvmStatic
    @Throws(IOException::class)
    fun readFileToBytes(file: File): ByteArray {
        FileInputStream(file).use { `in` ->
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var count: Int
            while (`in`.read(buffer).also { count = it } != -1) {
                bytes.write(buffer, 0, count)
            }
            return bytes.toByteArray()
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeBytesToFile(file: File, data: ByteArray) {
        FileOutputStream(file).use { out ->
            out.write(data)
        }
    }

    /**
     * Replaces [file] with [data] so that a crash, a full disk or a failed write leaves either the
     * old or the new content, never a truncated file. The data is written to a temporary file in
     * the same directory and synced to disk, which is then renamed over [file]. On Android (Linux)
     * rename() replaces an existing target atomically within one file system; the old file is not
     * removed first, so there is no moment without a database.
     *
     * [rename] is only there for the unit tests: on Windows File.renameTo() refuses to replace an
     * existing file.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun writeBytesToFileAtomically(file: File, data: ByteArray,
                                   rename: (File, File) -> Boolean = { from, to -> from.renameTo(to) }) {
        val temp = File(file.parentFile, file.name + ".tmp")
        try {
            FileOutputStream(temp).use { out ->
                out.write(data)
                out.flush()
                out.fd.sync()
            }
            if (!rename(temp, file))
                throw IOException("Cannot replace $file")
        } catch (e: IOException) {
            temp.delete()
            throw e
        }
    }
}
