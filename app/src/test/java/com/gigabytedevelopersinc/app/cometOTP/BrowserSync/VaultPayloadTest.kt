@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EntryThumbnail
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** What paired browsers get from the app's services (protocol §4.3). */
class VaultPayloadTest {

    private val vectors = JSONObject(javaClass.getResourceAsStream("/browser-sync/v1.json")!!.bufferedReader().use { it.readText() })

    private fun totp(issuer: String, secret: String = "JBSWY3DPEHPK3PXP", label: String = "alice@example.com") =
        Entry(Entry.OTPType.TOTP, secret, 30, 6, issuer, label, HashAlgorithm.SHA1, mutableListOf())

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    /** The vectors' id for [secret] (Base32), and their alias ids for services with GitHub's secret. */
    private fun secretId(secret: String) =
        vectors.getJSONArray("service_ids").objects().single { it.getString("secret") == secret }.getString("id")
    private fun aliasId(issuer: String) =
        vectors.getJSONArray("service_alias_ids").objects().single { it.getString("issuer") == issuer }.getString("id")

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
        val services = VaultPayload.services(listOf(shown, switchedOff, hotp))
        assertEquals(listOf("Shown"), services.map { it.issuer })
    }

    /** WanPay and Miden on the owner's phone: one QR code added twice, under two names. */
    @Test
    fun sendsEachServiceSharingASecretUnderAnIdOfItsOwn() {
        val wanPay = totp("WanPay", label = "emmanuel@example.com")
        val miden = totp("Miden", label = "emmanuel@example.com")
        val services = VaultPayload.services(listOf(wanPay, miden))
        assertEquals(listOf("WanPay", "Miden"), services.map { it.issuer })
        assertEquals(listOf(secretId("JBSWY3DPEHPK3PXP"), aliasId("Miden")), services.map { it.id })

        // Only services that are sent take ids: with WanPay switched off, Miden gets the secret's.
        val wanPayOff = totp("WanPay", label = "emmanuel@example.com").apply { browserExtension = false }
        assertEquals(listOf(secretId("JBSWY3DPEHPK3PXP")), VaultPayload.services(listOf(wanPayOff, miden)).map { it.id })
    }

    @Test
    fun leavesOutTheSameServiceListedTwice() {
        val github = totp("GitHub")
        val miden = totp("Miden", label = "emmanuel@example.com")
        val midenAgain = totp("Miden", label = "emmanuel@example.com")
        val wanPay = totp("WanPay", label = "emmanuel@example.com")
        val services = VaultPayload.services(listOf(github, miden, midenAgain, wanPay))
        assertEquals(listOf(secretId("JBSWY3DPEHPK3PXP"), aliasId("Miden"), aliasId("WanPay")), services.map { it.id })
    }

    /** `lp` cannot carry the length of an issuer or label of 65,536 bytes; the secret's own id needs neither. */
    @Test
    fun leavesOutALaterServiceWhoseNameIsTooLongForAnAliasId() {
        val long = totp("x".repeat(65536))
        val miden = totp("Miden", label = "emmanuel@example.com")
        assertEquals(listOf("GitHub", "Miden"), VaultPayload.services(listOf(totp("GitHub"), long, miden)).map { it.issuer })
        assertEquals(listOf(secretId("JBSWY3DPEHPK3PXP"), aliasId("Miden")), VaultPayload.services(listOf(long, miden)).map { it.id })
    }

    @Test
    fun servicesWithSecretsOfTheirOwnKeepTheSecretsIds() {
        val steamSecret = vectors.getJSONArray("service_ids").getJSONObject(1).getString("secret")
        val services = VaultPayload.services(listOf(totp("GitHub"), totp("Steam", secret = steamSecret)))
        assertEquals(listOf(secretId("JBSWY3DPEHPK3PXP"), secretId(steamSecret)), services.map { it.id })
    }

    @Test
    fun writesSecretsAndOptionalMembersAsTheProtocolSays() {
        val entry = totp("Example", secret = "MFRGG", label = "").apply {
            thumbnail = EntryThumbnail.EntryThumbnails.Default
            badgeColor = 0xFF00A0FF.toInt()
        }
        val service = VaultPayload.services(listOf(entry)).single()
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
