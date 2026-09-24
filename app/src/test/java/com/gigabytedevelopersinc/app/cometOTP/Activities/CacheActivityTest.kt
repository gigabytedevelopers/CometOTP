@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CacheActivityTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun deleteDirRemovesNestedFolders() {
        val root = tmp.newFolder("cache")
        File(root, "a.txt").writeText("a")
        val sub = File(root, "sub")
        assertTrue(sub.mkdir())
        File(sub, "b.txt").writeText("b")

        assertTrue(CacheActivity.deleteDir(root))
        assertFalse(root.exists())
    }

    /** File.list() returns null when a folder cannot be read (I/O error, no permission). */
    @Test
    fun deleteDirReportsFailureForAFolderThatCannotBeListed() {
        val unreadable = object : File(tmp.root, "unreadable") {
            override fun isDirectory(): Boolean = true
            override fun list(): Array<String>? = null
        }

        assertFalse(CacheActivity.deleteDir(unreadable))
    }
}
