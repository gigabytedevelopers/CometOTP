@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Removed accounts stay in the bin for 30 days, so that a removal can be undone. */
class RecycleBinTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val oldKey = EncryptionHelper.generateSymmetricKey(ByteArray(16) { it.toByte() })
    private val newKey = EncryptionHelper.generateSymmetricKey(ByteArray(16) { (it + 100).toByte() })

    private val day = 24L * 60 * 60 * 1000

    private fun entry(issuer: String) = Entry(Entry.OTPType.TOTP, "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", 30, 6,
        issuer, "me@example.test", HashAlgorithm.SHA1, mutableListOf("Work"))

    @Test
    fun keepsTheWholeAccountAndWhenItWasRemoved() {
        val original = entry("ShareTest")
        val items = RecycleBin.fromJson(RecycleBin.toJson(listOf(RecycleBin.Removed(original, 1_234_567L))))!!

        assertEquals(1, items.size)
        val back = items[0].entry
        assertEquals(original, back)
        assertArrayEquals(original.secret, back.secret)
        assertEquals(listOf("Work"), back.tags)
        assertEquals(1_234_567L, items[0].removedAt)
    }

    @Test
    fun anUnreadableItemIsLeftOutAndAnythingElseIsNoBin() {
        val good = RecycleBin.toJson(listOf(RecycleBin.Removed(entry("A"), 5L)))
        val withBad = good.dropLast(1) + ",{\"entry\":{},\"removedAt\":1},{\"removedAt\":2}]"
        assertEquals(listOf("A"), RecycleBin.fromJson(withBad)!!.map { it.entry.issuer })
        assertNull(RecycleBin.fromJson("not json"))
        assertNull(RecycleBin.fromJson("{}"))
    }

    @Test
    fun deletesWhatHasBeenThere30Days() {
        val now = 100 * day
        val items = listOf(
            RecycleBin.Removed(entry("today"), now),
            RecycleBin.Removed(entry("29 days"), now - 29 * day),
            RecycleBin.Removed(entry("just under 30"), now - 30 * day + 1),
            RecycleBin.Removed(entry("30 days"), now - 30 * day),
            RecycleBin.Removed(entry("31 days"), now - 31 * day))

        assertEquals(listOf("today", "29 days", "just under 30"),
            RecycleBin.withoutExpired(items, now).map { it.entry.issuer })
    }

    @Test
    fun countsTheDaysLeftRoundedUp() {
        val now = 100 * day
        assertEquals(30, RecycleBin.daysLeft(now, now))
        assertEquals(30, RecycleBin.daysLeft(now - 1, now))
        assertEquals(29, RecycleBin.daysLeft(now - day, now))
        assertEquals(1, RecycleBin.daysLeft(now - 30 * day + 1, now))
        // Never "0 days" for something still in the bin.
        assertEquals(1, RecycleBin.daysLeft(now - 30 * day, now))
    }

    @Test
    fun theBinIsEncryptedAndAnEmptyOneIsNoFile() {
        val file = tmp.root.resolve("removed.dat")
        assertTrue(RecycleBin.write(file, listOf(RecycleBin.Removed(entry("A"), 7L)), oldKey))
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("GEZDGNBV"))
        assertEquals(listOf("A"), RecycleBin.read(file, oldKey)!!.map { it.entry.issuer })
        assertNull(RecycleBin.read(file, newKey))

        assertTrue(RecycleBin.write(file, emptyList(), oldKey))
        assertFalse(file.exists())
        assertEquals(0, RecycleBin.read(file, oldKey)!!.size)
    }

    @Test
    fun followsTheDatabaseToANewKey() {
        val file = tmp.root.resolve("removed.dat")
        RecycleBin.write(file, listOf(RecycleBin.Removed(entry("A"), 7L)), oldKey)

        assertTrue(RecycleBin.reencryptFile(file, oldKey, newKey))
        assertEquals(listOf("A"), RecycleBin.read(file, newKey)!!.map { it.entry.issuer })
        assertNull(RecycleBin.read(file, oldKey))
    }

    @Test
    fun aBinTheOldKeyCannotReadIsDeletedRatherThanLeftUnreadable() {
        val file = tmp.root.resolve("removed.dat")
        RecycleBin.write(file, listOf(RecycleBin.Removed(entry("A"), 7L)), newKey)

        assertFalse(RecycleBin.reencryptFile(file, oldKey, newKey))
        assertFalse(file.exists())
        // Nothing to follow: fine.
        assertTrue(RecycleBin.reencryptFile(file, oldKey, newKey))
    }

    companion object {
        // The app's AES-GCM takes an IvParameterSpec, as Android's provider does; the JVM's needs this.
        @BeforeClass
        @JvmStatic
        fun installAndroidGcm() = AndroidGcmProvider.install()
    }
}
