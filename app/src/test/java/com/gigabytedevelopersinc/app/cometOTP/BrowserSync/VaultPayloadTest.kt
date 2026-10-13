@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EntryThumbnail
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** What paired browsers get from the app's services (protocol §4.3). */
class VaultPayloadTest {

    private val vectors = JSONObject(javaClass.getResourceAsStream("/browser-sync/v1.json")!!.bufferedReader().use { it.readText() })

    private fun totp(issuer: String, secret: String = "JBSWY3DPEHPK3PXP", label: String = "alice@example.com") =
        Entry(Entry.OTPType.TOTP, secret, 30, 6, issuer, label, HashAlgorithm.SHA1, mutableListOf())

    /**
     * The vector payload's services, built the way the app builds them from its entries. The
     * vectors' sample icon is "Github"; CometOTP's icon of that name is "GitHub".
     */
    @Test
    fun buildsTheVectorPayloadFromEntries() {
        val github = totp("GitHub").apply {
            thumbnail = EntryThumbnail.EntryThumbnails.GitHub
            tags = mutableListOf("Work")
        }
        val steamSecret = JSONObject(vectors.getJSONObject("vault").getString("payload"))
            .getJSONArray("services").getJSONObject(1).getString("secret")
        val steam = Entry(Entry.OTPType.STEAM, steamSecret, 30, 5, "Steam", "alice", HashAlgorithm.SHA1, mutableListOf()).apply {
            // The initials tile, not the Steam icon its issuer brings.
            thumbnail = EntryThumbnail.EntryThumbnails.Default
            initials = "ST"
            badgeColor = 0xFF3F51B5.toInt()
        }
        val json = SyncVault.payloadJson("Galaxy S25 Ultra", 1791763200, VaultPayload.services(listOf(github, steam)))
        assertEquals(vectors.getJSONObject("vault").getString("payload").replace("\"icon\":\"Github\"", "\"icon\":\"GitHub\""), json)
    }

    @Test
    fun leavesOutWhatABrowserMayNotHave() {
        val shown = totp("Shown")
        val switchedOff = totp("Off", secret = "GEZDGNBVGY3TQOJQ").apply { browserExtension = false }
        val hotp = Entry(Entry.OTPType.HOTP, "MFRGGZDFMZTWQ2LK", 1L, 6, "Counter", "bob", HashAlgorithm.SHA1, mutableListOf())
        val sameSecret = totp("Copy", label = "other")
        val services = VaultPayload.services(listOf(shown, switchedOff, hotp, sameSecret))
        assertEquals(listOf("Shown"), services.map { it.issuer })
    }

    @Test
    fun writesSecretsAndOptionalMembersAsTheProtocolSays() {
        val entry = totp("Example", secret = "MFRGG", label = "").apply {
            thumbnail = EntryThumbnail.EntryThumbnails.Default
            badgeColor = 0xFF00A0FF.toInt()
        }
        val service = VaultPayload.service(entry)!!
        assertEquals("MFRGG", service.secret)
        assertEquals("", service.label)
        assertEquals("#00A0FF", service.badge)
        assertEquals(null, service.icon)
        assertEquals(null, service.initials)
        assertEquals(null, service.tags)
        assertEquals("totp", service.type)
    }

    @Test
    fun theHashIgnoresTheUpdateTimeButNothingElse() {
        val services = VaultPayload.services(listOf(totp("A")))
        val hash = VaultPayload.hash("Pixel 7", services)
        assertEquals(64, hash.length)
        assertEquals(hash, VaultPayload.hash("Pixel 7", VaultPayload.services(listOf(totp("A")))))
        assertNotEquals(hash, VaultPayload.hash("Pixel 8", services))
        assertNotEquals(hash, VaultPayload.hash("Pixel 7", VaultPayload.services(listOf(totp("B")))))
    }

    @Test
    fun cutsThePhoneNameTo64BytesAtACharacterBoundary() {
        assertEquals("Galaxy S25 Ultra", VaultPayload.fitName("Galaxy S25 Ultra"))
        assertEquals("Android", VaultPayload.fitName(""))
        val long = "é".repeat(40)
        assertEquals(32, VaultPayload.fitName(long).length)
        val emoji = "a" + "😀".repeat(20)
        val fitted = VaultPayload.fitName(emoji)
        assertEquals(1 + 15 * 2, fitted.length)
        assertEquals(61, SyncEncoding.utf8(fitted).size)
    }
}
