@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.ConnectedBrowsers.Status
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.PairedBrowser.State
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Connected extensions says about each browser, and what a removed one had. */
class ConnectedBrowsersTest {

    private val browser = PairedBrowser("AAAAAAAAAAAAAAAAAAAAAA", "https://relay.example.com", "W".repeat(43), "V".repeat(43),
        "Chrome on macOS", 0, State.PAIRED)

    private fun totp(issuer: String, secret: String) =
        Entry(Entry.OTPType.TOTP, secret, 30, 6, issuer, "alice", HashAlgorithm.SHA1, mutableListOf())

    @Test
    fun saysTheMostPressingThingAboutABrowser() {
        assertEquals(Status.NOT_SYNCED, ConnectedBrowsers.status(browser, paused = false))
        assertEquals(Status.SYNCED, ConnectedBrowsers.status(browser.copy(uploadedAt = 1), paused = false))
        assertEquals(Status.PAUSED, ConnectedBrowsers.status(browser.copy(uploadedAt = 1), paused = true))
        assertEquals(Status.PENDING, ConnectedBrowsers.status(browser.copy(state = State.PENDING), paused = true))
        assertEquals(Status.BROKEN, ConnectedBrowsers.status(browser.copy(state = State.BROKEN), paused = true))
        assertEquals(Status.REMOVING, ConnectedBrowsers.status(browser.copy(state = State.REMOVING), paused = true))
        assertEquals(Status.DISCONNECTED, ConnectedBrowsers.status(browser.copy(state = State.DISCONNECTED, disconnectedAt = 1), paused = true))
    }

    @Test
    fun findsTheServicesARemovedBrowserHad() {
        val github = totp("GitHub", "JBSWY3DPEHPK3PXP")
        val steam = totp("Steam", "GEZDGNBVGY3TQOJQ")
        val other = totp("Other", "MFRGGZDFMZTWQ2LK")
        val ids = VaultPayload.services(listOf(steam, github)).map { it.id } + "deletedSinceAAAA"
        val (had, deleted) = ConnectedBrowsers.sharedServices(ids, listOf(github, other, steam))
        assertEquals(listOf("GitHub", "Steam"), had.map { it.issuer })
        assertEquals(1, deleted)
        assertEquals(Pair(emptyList<Entry>(), 0), ConnectedBrowsers.sharedServices(null, listOf(github)))
    }

    @Test
    fun cleansANewName() {
        assertEquals("Work laptop", ConnectedBrowsers.cleanName("  Work laptop "))
        assertNull(ConnectedBrowsers.cleanName("   "))
        assertEquals(64, SyncEncoding.utf8(ConnectedBrowsers.cleanName("x".repeat(100))!!).size)
    }

    @Test
    fun aVaultCountsAsDeletedUnlessTheRelayCouldNotBeReached() {
        assertTrue(BrowserSync.vaultDeleted(DeleteResult.DELETED))
        assertTrue(BrowserSync.vaultDeleted(DeleteResult.GONE))
        assertTrue(BrowserSync.vaultDeleted(DeleteResult.LOST))
        assertFalse(BrowserSync.vaultDeleted(DeleteResult.FAILED))
    }
}
