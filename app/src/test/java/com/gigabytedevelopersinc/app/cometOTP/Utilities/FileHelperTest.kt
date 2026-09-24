@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The database is replaced through a temporary file, never truncated and rewritten in place.
 * Android's rename() replaces an existing file; File.renameTo() does not on Windows, so the tests
 * that replace a file pass a rename that does (REPLACE_EXISTING).
 */
class FileHelperTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val replace: (File, File) -> Boolean = { from, to ->
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        true
    }

    private fun tempOf(file: File) = File(file.parentFile, file.name + ".tmp")

    @Test
    fun createsTheFile() {
        val file = File(tmp.root, "secrets.dat")
        FileHelper.writeBytesToFileAtomically(file, byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
        assertFalse(tempOf(file).exists())
    }

    @Test
    fun replacesALongerFileCompletely() {
        val file = File(tmp.root, "secrets.dat")
        file.writeBytes(ByteArray(100) { 7 })
        FileHelper.writeBytesToFileAtomically(file, byteArrayOf(1, 2, 3), replace)
        assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
        assertFalse(tempOf(file).exists())
    }

    /** A leftover temporary file (from a crash) is overwritten, not appended to. */
    @Test
    fun overwritesALeftoverTemporaryFile() {
        val file = File(tmp.root, "secrets.dat")
        tempOf(file).writeBytes(ByteArray(100) { 9 })
        FileHelper.writeBytesToFileAtomically(file, byteArrayOf(4, 5), replace)
        assertArrayEquals(byteArrayOf(4, 5), file.readBytes())
        assertFalse(tempOf(file).exists())
    }

    /** When the new content cannot be put in place the old file is left exactly as it was. */
    @Test
    fun aFailedReplaceKeepsTheOldFile() {
        val file = File(tmp.root, "secrets.dat")
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        assertThrows(IOException::class.java) {
            FileHelper.writeBytesToFileAtomically(file, byteArrayOf(9), { _, _ -> false })
        }
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), file.readBytes())
        assertFalse(tempOf(file).exists())
    }

    @Test
    fun aFailedWriteKeepsTheOldFile() {
        val file = File(tmp.root, "secrets.dat")
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        // The temporary file cannot be opened for writing when a directory is in its place.
        tempOf(file).mkdir()
        assertThrows(IOException::class.java) {
            FileHelper.writeBytesToFileAtomically(file, byteArrayOf(9), replace)
        }
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), file.readBytes())
    }
}
