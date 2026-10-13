@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.PairedBrowser.State
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the phone uploads a vault, which version, and what each answer means (protocol §7.1). */
class VaultUploaderTest {

    /** A relay that answers uploads from a script and keeps what it was sent. */
    private class ScriptedRelay(vararg answers: UploadResult) : Relay {
        val answers = ArrayDeque(answers.toList())
        val blobs = ArrayList<ByteArray>()
        override fun createVault(relay: String, vaultId: String, writeHash: ByteArray, readHash: ByteArray) = CreateResult.CREATED
        override fun upload(relay: String, vaultId: String, writeToken: String, blob: ByteArray): UploadResult {
            blobs.add(blob)
            return answers.removeFirst()
        }
        override fun delete(relay: String, vaultId: String, token: String) = DeleteResult.DELETED
    }

    private val key = SyncCrypto.randomBytes(32)
    private val id = SyncCrypto.randomBytes(16)
    private val browser = PairedBrowser(
        vaultId = SyncEncoding.toBase64Url(id),
        relay = "https://relay.example.com",
        writeToken = SyncEncoding.toBase64Url(SyncCrypto.randomBytes(32)),
        vaultKey = SyncEncoding.toBase64Url(key),
        name = "Chrome on macOS",
        pairedAt = 0,
        state = State.PAIRED,
        version = 4,
    )
    private val services = VaultPayload.services(listOf(
        Entry(Entry.OTPType.TOTP, "JBSWY3DPEHPK3PXP", 30, 6, "GitHub", "alice", HashAlgorithm.SHA1, mutableListOf())))
    private val now = 1_791_763_200_000L

    private fun uploader(relay: Relay) = VaultUploader(relay) { now }

    @Test
    fun uploadsTheNextVersionAndRemembersWhatItSent() {
        val relay = ScriptedRelay(UploadResult.Stored(5, "2026-10-12"))
        val outcome = uploader(relay).upload(browser, "Pixel 7", services)
        assertFalse(outcome.retry)
        val updated = outcome.browser!!
        assertEquals(5, updated.version)
        assertEquals(VaultPayload.hash("Pixel 7", services), updated.payloadHash)
        assertEquals(now, updated.uploadedAt)
        assertEquals("2026-10-12", updated.lastRead)
        assertEquals(services.map { it.id }, updated.sharedIds)

        // What was sent opens with the browser's key, as version 5, with the services in it.
        val (version, json) = SyncVault.open(key, id, relay.blobs.single())
        assertEquals(5L, version)
        assertEquals(SyncVault.payloadJson("Pixel 7", now / 1000, services), json)
    }

    @Test
    fun uploadsNothingWhenNothingShownHasChanged() {
        val upToDate = browser.copy(payloadHash = VaultPayload.hash("Pixel 7", services))
        val relay = ScriptedRelay()
        val outcome = uploader(relay).upload(upToDate, "Pixel 7", services)
        assertSame(upToDate, outcome.browser)
        assertFalse(outcome.retry)
        assertTrue(relay.blobs.isEmpty())
    }

    @Test
    fun leavesPendingBrokenAndRemovedPairingsAlone() {
        for (state in listOf(State.PENDING, State.BROKEN, State.REMOVING)) {
            val relay = ScriptedRelay()
            val other = browser.copy(state = state)
            assertSame(other, uploader(relay).upload(other, "Pixel 7", services).browser)
            assertTrue(relay.blobs.isEmpty())
        }
    }

    @Test
    fun uploadsVersionOneForAPairingUnderWay() {
        val pending = browser.copy(state = State.PENDING, version = 0)
        val relay = ScriptedRelay(UploadResult.Stored(1, null))
        val outcome = uploader(relay).upload(pending, "Pixel 7", services, pairing = true)
        assertEquals(State.PENDING, outcome.browser!!.state)
        assertEquals(1, outcome.browser!!.version)
        assertEquals(listOf(1L), relay.blobs.map { SyncVault.version(it) })

        // Only a pending pairing: a paired browser is not uploaded to as part of a pairing.
        val other = ScriptedRelay()
        assertSame(browser, uploader(other).upload(browser, "Pixel 7", services, pairing = true).browser)
        assertTrue(other.blobs.isEmpty())
    }

    @Test
    fun catchesUpOnceWhenTheRelayIsAhead() {
        val relay = ScriptedRelay(UploadResult.Stale(9), UploadResult.Stored(10, null))
        val outcome = uploader(relay).upload(browser, "Pixel 7", services)
        assertEquals(10, outcome.browser!!.version)
        assertEquals(listOf(5L, 10L), relay.blobs.map { SyncVault.version(it) })

        val stillStale = ScriptedRelay(UploadResult.Stale(9), UploadResult.Stale(12))
        val again = uploader(stillStale).upload(browser, "Pixel 7", services)
        assertSame(browser, again.browser)
        assertTrue(again.retry)
    }

    @Test
    fun aDeletedVaultRemovesThePairingAndALostOneBreaksIt() {
        assertNull(uploader(ScriptedRelay(UploadResult.Gone)).upload(browser, "Pixel 7", services).browser)
        val lost = uploader(ScriptedRelay(UploadResult.Lost)).upload(browser, "Pixel 7", services)
        assertEquals(State.BROKEN, lost.browser!!.state)
        assertEquals(browser.version, lost.browser!!.version)
        assertFalse(lost.retry)
    }

    @Test
    fun aFailureChangesNothingAndAsksForAnotherTry() {
        val outcome = uploader(ScriptedRelay(UploadResult.Failed)).upload(browser, "Pixel 7", services)
        assertSame(browser, outcome.browser)
        assertTrue(outcome.retry)
    }
}
