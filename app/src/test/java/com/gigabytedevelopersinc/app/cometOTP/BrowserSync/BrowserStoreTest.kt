@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.PairedBrowser.State
import com.gigabytedevelopersinc.app.cometOTP.Utilities.AndroidGcmProvider
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The paired browsers' file: encrypted like the database, following it, never backed up. */
class BrowserStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val oldKey = EncryptionHelper.generateSymmetricKey(ByteArray(16) { it.toByte() })
    private val newKey = EncryptionHelper.generateSymmetricKey(ByteArray(16) { (it + 100).toByte() })

    private fun b64u(length: Int, fill: Int) = SyncEncoding.toBase64Url(ByteArray(length) { fill.toByte() })

    private fun browser(id: Int, state: State = State.PAIRED, pairedAt: Long = 1_000L) = PairedBrowser(
        vaultId = b64u(16, id),
        relay = "https://relay.example.com",
        writeToken = b64u(32, 0x57),
        vaultKey = b64u(32, 0x56),
        name = "Chrome on macOS — ünïcode",
        pairedAt = pairedAt,
        state = state,
    )

    @Test
    fun keepsEverythingAboutABrowser() {
        val full = browser(1).copy(version = 7, payloadHash = "ab".repeat(32), uploadedAt = 5_000L, lastRead = "2026-10-13")
        val minimal = browser(2, State.PENDING)
        assertEquals(listOf(full, minimal), BrowserStore.fromJson(BrowserStore.toJson(listOf(full, minimal))))
    }

    @Test
    fun anUnreadableBrowserIsLeftOutAndAnythingElseIsNone() {
        val good = BrowserStore.toJson(listOf(browser(1)))
        val shortKey = BrowserStore.toJson(listOf(browser(2).copy(vaultKey = "AAAA")))
        val unknownState = BrowserStore.toJson(listOf(browser(3))).replace("\"state\":\"paired\"", "\"state\":\"lost\"")
        val duplicate = good
        val all = listOf(good, shortKey, unknownState, duplicate, "[{}]").joinToString(",") { it.removePrefix("[").removeSuffix("]") }
        assertEquals(listOf(browser(1)), BrowserStore.fromJson("[$all]"))
        assertNull(BrowserStore.fromJson("not json"))
        assertNull(BrowserStore.fromJson("{}"))
    }

    @Test
    fun aPendingPairingIsDroppedOnceItsChannelHasExpired() {
        val now = 10_000_000L
        val lifetime = BrowserStore.PENDING_LIFETIME_MS
        val paired = browser(1, State.PAIRED, pairedAt = 0)
        val broken = browser(2, State.BROKEN, pairedAt = 0)
        val fresh = browser(3, State.PENDING, pairedAt = now - lifetime + 1)
        val expired = browser(4, State.PENDING, pairedAt = now - lifetime)
        val (kept, stale) = BrowserStore.splitStalePending(listOf(paired, broken, fresh, expired), now)
        assertEquals(listOf(paired, broken, fresh), kept)
        assertEquals(listOf(expired), stale)
        assertEquals(600_000L, lifetime)
    }

    @Test
    fun theFileIsEncryptedAndNoBrowsersIsNoFile() {
        val file = tmp.root.resolve(Constants.FILENAME_BROWSERS)
        assertTrue(BrowserStore.write(file, listOf(browser(1)), oldKey))
        val raw = String(file.readBytes(), Charsets.ISO_8859_1)
        assertFalse(raw.contains("relay.example.com"))
        assertFalse(raw.contains(browser(1).vaultKey))
        assertEquals(listOf(browser(1)), BrowserStore.read(file, oldKey))
        assertNull(BrowserStore.read(file, newKey))

        assertTrue(BrowserStore.write(file, emptyList(), oldKey))
        assertFalse(file.exists())
        assertEquals(0, BrowserStore.read(file, oldKey)!!.size)
    }

    @Test
    fun followsTheDatabaseToANewKey() {
        val file = tmp.root.resolve(Constants.FILENAME_BROWSERS)
        BrowserStore.write(file, listOf(browser(1), browser(2, State.PENDING)), oldKey)

        assertTrue(BrowserStore.reencryptFile(file, oldKey, newKey))
        assertEquals(listOf(browser(1), browser(2, State.PENDING)), BrowserStore.read(file, newKey))
        assertNull(BrowserStore.read(file, oldKey))
    }

    @Test
    fun aFileTheOldKeyCannotReadIsDeletedRatherThanLeftUnreadable() {
        val file = tmp.root.resolve(Constants.FILENAME_BROWSERS)
        BrowserStore.write(file, listOf(browser(1)), newKey)

        assertFalse(BrowserStore.reencryptFile(file, oldKey, newKey))
        assertFalse(file.exists())
        assertTrue(BrowserStore.reencryptFile(file, oldKey, newKey))
        // No old key at all (the database was not encrypted before): nothing can be carried over.
        BrowserStore.write(file, listOf(browser(1)), oldKey)
        assertFalse(BrowserStore.reencryptFile(file, null, newKey))
        assertFalse(file.exists())
    }

    @Test
    fun anUpdateOnlyReplacesABrowserThatIsStillThere() {
        val both = listOf(browser(1), browser(2))
        assertEquals(listOf(browser(1), browser(2).copy(version = 3)), BrowserStore.withReplaced(both, browser(2).copy(version = 3)))
        // Removed while its upload was under way: the update must not bring it back.
        assertNull(BrowserStore.withReplaced(listOf(browser(2)), browser(1).copy(version = 9)))
        assertEquals(listOf(browser(1), browser(2)), both)
    }

    /** A restored or transferred phone must pair its browsers again, so neither may carry the file. */
    @Test
    fun backupsAndDeviceTransfersLeaveTheFileOut() {
        fun excluded(path: String, section: String?): List<String> {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
            val root = if (section == null) document.documentElement
                else document.getElementsByTagName(section).item(0) as Element
            val excludes = root.getElementsByTagName("exclude")
            return (0 until excludes.length).map { (excludes.item(it) as Element).getAttribute("path") }
        }
        for ((path, section) in listOf(
            "src/main/res/xml/backup_rules.xml" to null,
            "src/main/res/xml/data_extraction_rules.xml" to "cloud-backup",
            "src/main/res/xml/data_extraction_rules.xml" to "device-transfer",
        )) {
            val paths = excluded(path, section)
            assertTrue("$path $section", Constants.FILENAME_BROWSERS in paths)
            assertTrue("$path $section", Constants.FILENAME_REMOVED in paths)
        }
    }

    companion object {
        // The app's AES-GCM takes an IvParameterSpec, as Android's provider does; the JVM's needs this.
        @BeforeClass
        @JvmStatic
        fun installAndroidGcm() = AndroidGcmProvider.install()
    }
}
