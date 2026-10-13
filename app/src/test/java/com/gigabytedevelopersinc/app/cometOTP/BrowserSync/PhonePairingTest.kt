@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.PairedBrowser.State
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.PhonePairing.Failure
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.PhonePairing.Step
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/**
 * The phone's side of pairing (protocol §3.4) against a browser that does its side as the
 * extension does, through a relay kept in memory: the happy path, both orders of the two
 * confirmations, and every way it can stop, each leaving no vault behind.
 */
class PhonePairingTest {

    /** The browser: answers what reaches it, as told. */
    private class Browser {
        val own = SyncCrypto.generateKeyPair()
        val code = PairingCode(RELAY, SyncCrypto.randomBytes(16), SyncCrypto.encodePublicKey(own.public),
            SyncCrypto.randomBytes(32), "Chrome on macOS")
        lateinit var keys: PairingKeys
        val received = ArrayList<PairingMessage>()

        /** Its answer to the hello and to the vault message; null for none. */
        var answerHello: PairingMessage? = PairingMessage.Confirm
        var answerVault: PairingMessage? = PairingMessage.Done

        fun receive(index: Int, frame: ByteArray): PairingMessage? {
            if (index == 1) {
                val phoneKey = frame.copyOfRange(0, 65)
                keys = Pairing.deriveKeys(SyncCrypto.ecdh(own.private, phoneKey), code.secret, Pairing.transcript(code, phoneKey))
            }
            val message = Pairing.openFrame(keys, code.channel, Pairing.TO_BROWSER, index, frame)
            received.add(message)
            return when (message) {
                is PairingMessage.Hello -> answerHello
                is PairingMessage.Vault -> answerVault
                else -> null
            }
        }
    }

    private class StoredVault(val writeHash: ByteArray, val readHash: ByteArray) {
        var blob: ByteArray? = null
        var deleted = false
    }

    /** The relay, with the browser behind it. */
    private class FakeRelay(val browser: Browser) : PairingRelay, Relay {
        var claimAnswer: ClaimResult? = null
        var claimed = false
        var closed = false
        val toBrowser: MutableList<ByteArray> = Collections.synchronizedList(ArrayList())
        val toPhone: MutableList<ByteArray> = Collections.synchronizedList(ArrayList())
        /** Changes a byte of each frame to the phone, as if it had been tampered with. */
        var tamper = false
        val vaults = HashMap<String, StoredVault>()
        var uploadAnswer: UploadResult? = null
        var deleteAnswer = DeleteResult.DELETED
        /** Called on every poll, for tests that move the clock. */
        var onPoll: () -> Unit = {}

        private fun deliver(frame: ByteArray) {
            toBrowser.add(frame)
            val reply = browser.receive(toBrowser.size, frame) ?: return
            val sealed = Pairing.sealFrame(browser.keys, browser.code.channel, Pairing.TO_PHONE, toPhone.size + 1, reply)
            if (tamper)
                sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 1).toByte()
            toPhone.add(sealed)
        }

        override fun claim(relay: String, channel: String, frame: ByteArray): ClaimResult {
            assertEquals(SyncEncoding.toBase64Url(browser.code.channel), channel)
            claimAnswer?.let { return it }
            if (claimed)
                return ClaimResult.AlreadyClaimed
            claimed = true
            deliver(frame)
            return ClaimResult.Claimed(PHONE_TOKEN)
        }

        override fun post(relay: String, channel: String, token: String, frame: ByteArray): PostResult {
            assertEquals(PHONE_TOKEN, token)
            if (closed)
                return PostResult.ENDED
            deliver(frame)
            return PostResult.POSTED
        }

        override fun poll(relay: String, channel: String, token: String, after: Int): PollResult {
            assertEquals(PHONE_TOKEN, token)
            onPoll()
            if (closed)
                return PollResult.Ended
            val waiting = synchronized(toPhone) { toPhone.drop(after) }
            return PollResult.Messages(waiting.mapIndexed { i, frame -> ChannelMessage(after + i + 1, frame) })
        }

        override fun closeChannel(relay: String, channel: String, token: String) {
            closed = true
        }

        override fun createVault(relay: String, vaultId: String, writeHash: ByteArray, readHash: ByteArray): CreateResult {
            vaults[vaultId] = StoredVault(writeHash, readHash)
            return CreateResult.CREATED
        }

        override fun upload(relay: String, vaultId: String, writeToken: String, blob: ByteArray): UploadResult {
            uploadAnswer?.let { return it }
            val vault = vaults.getValue(vaultId)
            assertArrayEquals(vault.writeHash, SyncCrypto.sha256(SyncEncoding.fromBase64Url(writeToken)))
            vault.blob = blob
            return UploadResult.Stored(SyncVault.version(blob), null)
        }

        override fun delete(relay: String, vaultId: String, token: String): DeleteResult {
            if (deleteAnswer == DeleteResult.DELETED)
                vaults.getValue(vaultId).deleted = true
            return deleteAnswer
        }
    }

    private class MemoryRecords : PhonePairing.Records {
        val browsers = LinkedHashMap<String, PairedBrowser>()
        override fun put(browser: PairedBrowser): Boolean {
            browsers[browser.vaultId] = browser
            return true
        }
        override fun update(browser: PairedBrowser): Boolean {
            if (browser.vaultId !in browsers)
                return false
            browsers[browser.vaultId] = browser
            return true
        }
        override fun remove(vaultId: String) {
            browsers.remove(vaultId)
        }
    }

    private val browser = Browser()
    private val relay = FakeRelay(browser)
    private val records = MemoryRecords()
    private val steps: MutableList<Step> = Collections.synchronizedList(ArrayList())
    private var clock = 1_791_763_200_000L
    private val services = VaultPayload.services(listOf(
        Entry(Entry.OTPType.TOTP, "JBSWY3DPEHPK3PXP", 30, 6, "GitHub", "alice", HashAlgorithm.SHA1, mutableListOf())))

    /** What happens at each step, besides being recorded. */
    private var onStep: (PhonePairing, Step) -> Unit = { pairing, step -> if (step is Step.Compare) pairing.approve() }

    private fun pairing(code: PairingCode = browser.code): PhonePairing {
        lateinit var pairing: PhonePairing
        pairing = PhonePairing(code, "Pixel 7", records, relay, relay,
            listener = { step -> steps.add(step); onStep(pairing, step) },
            now = { clock },
            pause = { Thread.sleep(5) })
        return pairing
    }

    private fun run(code: PairingCode = browser.code) = pairing(code).run { services }

    private fun failure(): Failure = (steps.last() as Step.Failed).failure

    /** Nothing is left: no pairing on the phone, every vault deleted, the channel closed. */
    private fun assertNothingLeft() {
        assertTrue(records.browsers.isEmpty())
        assertTrue(relay.vaults.values.all { it.deleted })
        assertTrue(!relay.claimed || relay.closed)
    }

    @Test
    fun pairsAndGivesTheBrowserAVaultItCanOpen() {
        run()

        val paired = (steps.last() as Step.Paired).browser
        assertEquals(listOf(Step.Connecting, Step.Compare(browser.keys.sas), Step.WaitingForBrowser, Step.Sending, Step.Paired(paired)), steps)
        assertEquals(PairingMessage.Hello("Pixel 7"), browser.received.first())
        assertEquals(State.PAIRED, paired.state)
        assertEquals("Chrome on macOS", paired.name)
        assertEquals(RELAY, paired.relay)
        assertEquals(1, paired.version)
        assertEquals(services.map { it.id }, paired.sharedIds)
        assertEquals(listOf(paired), records.browsers.values.toList())
        assertTrue(relay.closed)

        // The browser holds what opens the vault and reads it; the phone keeps only the write token.
        val vault = browser.received.filterIsInstance<PairingMessage.Vault>().single()
        assertEquals(paired.vaultId, vault.id)
        assertEquals(paired.vaultKey, vault.key)
        val stored = relay.vaults.getValue(vault.id)
        assertArrayEquals(stored.readHash, SyncCrypto.sha256(SyncEncoding.fromBase64Url(vault.read)))
        val (version, json) = SyncVault.open(SyncEncoding.fromBase64Url(vault.key), SyncEncoding.fromBase64Url(vault.id), stored.blob!!)
        assertEquals(1L, version)
        val payload = JSONObject(json)
        assertEquals("Pixel 7", payload.getString("phone"))
        assertEquals("GitHub", payload.getJSONArray("services").getJSONObject(0).getString("issuer"))
    }

    @Test
    fun theBrowserMayConfirmBeforeThePersonTapsPair() {
        onStep = { pairing, step ->
            if (step is Step.Compare)
                Thread { Thread.sleep(300); pairing.approve() }.start()
        }
        run()
        assertEquals(listOf(Step.Connecting::class, Step.Compare::class, Step.Sending::class, Step.Paired::class), steps.map { it::class })
    }

    @Test
    fun aCodeAlreadyUsedExpiredOrUnreachableIsSaidSo() {
        for ((answer, expected) in listOf(
            ClaimResult.AlreadyClaimed to Failure.ALREADY_USED,
            ClaimResult.Expired to Failure.EXPIRED,
            ClaimResult.Failed to Failure.UNREACHABLE,
        )) {
            steps.clear()
            relay.claimAnswer = answer
            run()
            assertEquals(listOf(Step.Connecting, Step.Failed(expected)), steps)
            assertTrue(relay.vaults.isEmpty())
            assertTrue(records.browsers.isEmpty())
        }
    }

    @Test
    fun aCodeWhoseKeyIsNotOnTheCurveIsRefusedBeforeTheRelay() {
        val bad = PairingCode(RELAY, browser.code.channel, ByteArray(65).also { it[0] = 4 }, browser.code.secret, "Chrome")
        run(bad)
        assertEquals(Failure.INVALID_CODE, failure())
        assertFalse(relay.claimed)
    }

    @Test
    fun theBrowserCancellingFirstMeansNoVaultIsMade() {
        browser.answerHello = PairingMessage.Cancel
        run()
        assertEquals(Failure.CANCELLED_IN_BROWSER, failure())
        assertTrue(relay.vaults.isEmpty())
        assertEquals(1, relay.toBrowser.size)
        assertNothingLeft()
    }

    @Test
    fun theBrowserCancellingInsteadOfStoringTheVaultDeletesIt() {
        browser.answerVault = PairingMessage.Cancel
        run()
        assertEquals(Failure.CANCELLED_IN_BROWSER, failure())
        assertEquals(1, relay.vaults.size)
        assertNothingLeft()
    }

    @Test
    fun cancellingHereTellsTheBrowser() {
        onStep = { pairing, step -> if (step is Step.Compare) pairing.cancel() }
        run()
        assertEquals(Failure.CANCELLED, failure())
        assertEquals(listOf(PairingMessage.Hello("Pixel 7"), PairingMessage.Cancel), browser.received)
        assertNothingLeft()
    }

    @Test
    fun cancellingWhileTheBrowserStoresTheVaultDeletesIt() {
        browser.answerVault = null
        var canceller: Thread? = null
        var goneOnceCancelled = false
        onStep = { pairing, step ->
            when (step) {
                is Step.Compare -> pairing.approve()
                Step.Sending -> canceller = Thread {
                    Thread.sleep(200)
                    pairing.cancel()
                    // Already, not only once run() has noticed.
                    goneOnceCancelled = records.browsers.isEmpty() && relay.vaults.values.all { it.deleted }
                }.also { it.start() }
                else -> {}
            }
        }
        run()
        canceller!!.join()
        assertTrue(goneOnceCancelled)
        assertEquals(Failure.CANCELLED, failure())
        assertEquals(PairingMessage.Cancel, browser.received.last())
        assertEquals(1, relay.vaults.size)
        assertNothingLeft()
    }

    @Test
    fun cancellingOncePairedChangesNothing() {
        lateinit var pairing: PhonePairing
        onStep = { p, step ->
            pairing = p
            if (step is Step.Compare)
                p.approve()
        }
        run()
        val paired = (steps.last() as Step.Paired).browser
        pairing.cancel()
        assertEquals(listOf(paired), records.browsers.values.toList())
        assertFalse(relay.vaults.getValue(paired.vaultId).deleted)
        // Nothing more reached the browser: it has the hello and the vault message only.
        assertEquals(listOf(PairingMessage.Hello::class, PairingMessage.Vault::class), browser.received.map { it::class })
    }

    @Test
    fun aVaultTheRelayCouldNotDeleteIsKeptToBeDeletedLater() {
        browser.answerVault = PairingMessage.Cancel
        relay.deleteAnswer = DeleteResult.FAILED
        run()
        assertEquals(Failure.CANCELLED_IN_BROWSER, failure())
        assertEquals(listOf(State.REMOVING), records.browsers.values.map { it.state })
    }

    @Test
    fun aFrameThatDoesNotOpenStopsThePairing() {
        relay.tamper = true
        run()
        assertEquals(Failure.BROKEN, failure())
        assertEquals(PairingMessage.Cancel, browser.received.last())
        assertNothingLeft()
    }

    @Test
    fun anUploadThatFailsDeletesTheNewVault() {
        relay.uploadAnswer = UploadResult.Failed
        run()
        assertEquals(Failure.UNREACHABLE, failure())
        assertEquals(1, relay.vaults.size)
        assertNothingLeft()
    }

    @Test
    fun aBrowserThatNeverAnswersRunsOutOfTime() {
        browser.answerHello = null
        relay.onPoll = { clock += 60_000 }
        run()
        assertEquals(Failure.EXPIRED, failure())
        assertNothingLeft()
    }

    private companion object {
        const val RELAY = "https://relay.example.com"
        val PHONE_TOKEN = "P".repeat(43)
    }
}
