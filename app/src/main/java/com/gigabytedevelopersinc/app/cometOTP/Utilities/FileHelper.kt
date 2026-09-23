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
}
