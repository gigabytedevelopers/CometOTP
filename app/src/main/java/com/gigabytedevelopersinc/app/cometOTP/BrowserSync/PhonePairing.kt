@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.BrowserSyncException.Reason

/**
 * The phone's side of pairing with a browser (protocol §3.4), apart from Android. [run] takes a
 * scanned code to a paired browser on a background thread and tells [listener] each step; the
 * person's go-ahead comes through [approve], and [cancel] stops it from any other thread. Whatever
 * stops it, a vault already created is deleted again, and the browser is told when it can be.
 * Each of those steps may run twice, from [cancel] and from [run], and the second does nothing.
 */
class PhonePairing(
    private val code: PairingCode,
    /** The phone's name, as the browser shows it. */
    private val phone: String,
    private val records: Records,
    private val channels: PairingRelay,
    private val vaults: Relay,
    private val listener: Listener,
    private val now: () -> Long = System::currentTimeMillis,
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
) {

    /** Where the pairing is kept on the phone: [BrowserStore], or memory in tests. */
    interface Records {
        fun put(browser: PairedBrowser): Boolean
        fun update(browser: PairedBrowser): Boolean
        fun remove(vaultId: String)
    }

    sealed class Step {
        /** Sending the hello. */
        object Connecting : Step()

        /** The person compares [sas] with the one the browser shows, then taps Pair or Cancel. */
        data class Compare(val sas: String) : Step()

        /** The person tapped Pair; the browser has yet to confirm. */
        object WaitingForBrowser : Step()

        /** Both sides confirmed: creating the vault and sending it. */
        object Sending : Step()

        data class Paired(val browser: PairedBrowser) : Step()
        data class Failed(val failure: Failure) : Step()
    }

    enum class Failure {
        /** The code's key is not a P-256 point. */
        INVALID_CODE,
        /** Another phone answered the code first. */
        ALREADY_USED,
        /** The code, or the pairing, ran out of time. */
        EXPIRED,
        /** The person cancelled in the browser. */
        CANCELLED_IN_BROWSER,
        /** The relay could not be reached. */
        UNREACHABLE,
        /** A message did not open or made no sense, or the pairing could not be stored. */
        BROKEN,
        /** Cancelled here. */
        CANCELLED,
    }

    fun interface Listener {
        fun onStep(step: Step)
    }

    private class Stopped(val failure: Failure) : Exception()

    private val channel = SyncEncoding.toBase64Url(code.channel)
    private lateinit var keys: PairingKeys
    private var deadline = 0L

    @Volatile
    private var cancelled = false

    /** Guards the person's go-ahead and the browser's confirm. */
    private val approval = Object()
    private var approved = false
    private var confirmed = false

    /** Guards the channel: the posts to the browser, their count, and closing it. */
    private val sendLock = Any()
    private var token: String? = null
    private var channelOpen = false
    private var sent = 0

    /** Guards [pending]. */
    private val vaultLock = Any()

    /** The pending pairing, once its vault exists, until it is paired or its vault deleted. */
    private var pending: PairedBrowser? = null

    private fun setPending(browser: PairedBrowser) {
        synchronized(vaultLock) { pending = browser }
    }

    /** Runs the pairing to its end. [services] is asked for what to share once both sides agree. */
    fun run(services: () -> List<SyncService>) {
        val failure = try {
            listener.onStep(Step.Paired(pair(services)))
            return
        } catch (e: Stopped) {
            e.failure
        } catch (e: BrowserSyncException) {
            if (e.reason == Reason.BAD_PUBLIC_KEY) Failure.INVALID_CODE else Failure.BROKEN
        }
        val reported = if (cancelled) Failure.CANCELLED else failure
        cleanUp(tellBrowser = reported != Failure.CANCELLED_IN_BROWSER && reported != Failure.EXPIRED)
        listener.onStep(Step.Failed(reported))
    }

    /** The person tapped Pair. */
    fun approve() {
        synchronized(approval) {
            approved = true
            approval.notifyAll()
            // Said while holding the lock, so that it always comes before Sending.
            if (!confirmed && !cancelled)
                listener.onStep(Step.WaitingForBrowser)
        }
    }

    /**
     * Stops the pairing unless it has finished: the browser is told, the channel closed so that a
     * poll under way ends at once, and any vault deleted. Blocks on the network, so not on the
     * main thread.
     */
    fun cancel() {
        cancelled = true
        synchronized(approval) { approval.notifyAll() }
        closeChannel(tellBrowser = true)
        deleteVault()
    }

    private fun pair(services: () -> List<SyncService>): PairedBrowser {
        listener.onStep(Step.Connecting)
        val own = SyncCrypto.generateKeyPair()
        val phoneKey = SyncCrypto.encodePublicKey(own.public)
        keys = Pairing.deriveKeys(SyncCrypto.ecdh(own.private, code.browserPublicKey), code.secret, Pairing.transcript(code, phoneKey))
        claim(Pairing.sealFrame(keys, code.channel, Pairing.TO_BROWSER, 1, PairingMessage.Hello(phone), phoneKey))
        listener.onStep(Step.Compare(keys.sas))

        // The browser's confirm and the person's Pair, in either order.
        when (receive(1)) {
            PairingMessage.Confirm -> {}
            PairingMessage.Cancel -> throw Stopped(Failure.CANCELLED_IN_BROWSER)
            else -> throw Stopped(Failure.BROKEN)
        }
        awaitApproval()

        listener.onStep(Step.Sending)
        val (browser, read) = createVault(services())
        val vault = PairingMessage.Vault(browser.vaultId, read, browser.vaultKey)
        when (send(vault)) {
            PostResult.POSTED -> {}
            PostResult.ENDED -> throw Stopped(Failure.EXPIRED)
            PostResult.FAILED -> throw Stopped(Failure.UNREACHABLE)
        }

        when (receive(2)) {
            PairingMessage.Done -> {}
            PairingMessage.Cancel -> throw Stopped(Failure.CANCELLED_IN_BROWSER)
            else -> throw Stopped(Failure.BROKEN)
        }
        val paired = browser.copy(state = PairedBrowser.State.PAIRED)
        synchronized(vaultLock) {
            // A cancel that came first has deleted the vault and the pairing with it.
            if (pending == null || !records.update(paired))
                throw Stopped(Failure.BROKEN)
            pending = paired
        }
        closeChannel(tellBrowser = false)
        return paired
    }

    /** Posts the hello, which claims the channel (protocol §6.2). */
    private fun claim(hello: ByteArray) {
        synchronized(sendLock) {
            if (cancelled)
                throw Stopped(Failure.CANCELLED)
            when (val result = channels.claim(code.relay, channel, hello)) {
                is ClaimResult.Claimed -> token = result.token
                ClaimResult.AlreadyClaimed -> throw Stopped(Failure.ALREADY_USED)
                ClaimResult.Expired -> throw Stopped(Failure.EXPIRED)
                ClaimResult.Failed -> throw Stopped(Failure.UNREACHABLE)
            }
            channelOpen = true
            sent = 1
        }
        // The relay ends the channel first, as it was created before the hello; this is a backstop.
        deadline = now() + Pairing.CHANNEL_LIFETIME * 1000L
    }

    /** Posts the next message to the browser, trying again briefly when the relay cannot be reached. */
    private fun send(message: PairingMessage): PostResult {
        synchronized(sendLock) {
            if (!channelOpen)
                return PostResult.ENDED
            val frame = Pairing.sealFrame(keys, code.channel, Pairing.TO_BROWSER, sent + 1, message)
            var result = PostResult.FAILED
            for (attempt in 1..POST_ATTEMPTS) {
                result = channels.post(code.relay, channel, token!!, frame)
                if (result != PostResult.FAILED || cancelled)
                    break
                pause(RETRY_MS)
            }
            if (result == PostResult.POSTED)
                sent++
            return result
        }
    }

    /** Waits for the browser's message [index], polling the relay until the channel ends. */
    private fun receive(index: Int): PairingMessage {
        var lastFailed = false
        while (true) {
            if (cancelled)
                throw Stopped(Failure.CANCELLED)
            if (now() > deadline)
                throw Stopped(if (lastFailed) Failure.UNREACHABLE else Failure.EXPIRED)
            val started = now()
            when (val result = channels.poll(code.relay, channel, token!!, index - 1)) {
                is PollResult.Messages -> {
                    lastFailed = false
                    val message = result.messages.firstOrNull()
                    if (message == null) {
                        // A relay that answers at once rather than waiting is not polled flat out.
                        if (now() - started < MIN_POLL_MS)
                            pause(MIN_POLL_MS)
                        continue
                    }
                    if (message.seq != index)
                        throw Stopped(Failure.BROKEN)
                    return Pairing.openFrame(keys, code.channel, Pairing.TO_PHONE, index, message.frame)
                }
                PollResult.Ended -> throw Stopped(if (cancelled) Failure.CANCELLED else Failure.EXPIRED)
                PollResult.Failed -> {
                    lastFailed = true
                    pause(RETRY_MS)
                }
            }
        }
    }

    private fun awaitApproval() {
        synchronized(approval) {
            confirmed = true
            while (!approved && !cancelled) {
                if (now() > deadline)
                    throw Stopped(Failure.EXPIRED)
                approval.wait(APPROVAL_CHECK_MS)
            }
        }
        if (cancelled)
            throw Stopped(Failure.CANCELLED)
    }

    /**
     * Creates the vault with fresh credentials (protocol §4.1), records the pairing as pending and
     * uploads version 1. Returns the pairing and the read token, which only the browser keeps.
     */
    private fun createVault(services: List<SyncService>): Pair<PairedBrowser, String> {
        repeat(CREATE_ATTEMPTS) {
            val id = SyncCrypto.randomBytes(16)
            val write = SyncCrypto.randomBytes(32)
            val read = SyncCrypto.randomBytes(32)
            when (vaults.createVault(code.relay, SyncEncoding.toBase64Url(id), SyncCrypto.sha256(write), SyncCrypto.sha256(read))) {
                CreateResult.CREATED -> {}
                // Sixteen random bytes taken already: a relay that misbehaves, but try a new id.
                CreateResult.EXISTS -> return@repeat
                CreateResult.FAILED -> throw Stopped(Failure.UNREACHABLE)
            }
            val browser = PairedBrowser(
                vaultId = SyncEncoding.toBase64Url(id),
                relay = code.relay,
                writeToken = SyncEncoding.toBase64Url(write),
                vaultKey = SyncEncoding.toBase64Url(SyncCrypto.randomBytes(32)),
                name = code.browserName,
                pairedAt = now(),
                state = PairedBrowser.State.PENDING,
            )
            if (!records.put(browser)) {
                vaults.delete(browser.relay, browser.vaultId, browser.writeToken)
                throw Stopped(Failure.BROKEN)
            }
            setPending(browser)
            if (cancelled)
                throw Stopped(Failure.CANCELLED)

            val outcome = VaultUploader(vaults, now).upload(browser, phone, services, pairing = true)
            val uploaded = outcome.browser
            if (uploaded == null || uploaded.version < 1 || uploaded.state != PairedBrowser.State.PENDING)
                throw Stopped(if (outcome.retry) Failure.UNREACHABLE else Failure.BROKEN)
            if (!records.update(uploaded))
                throw Stopped(Failure.BROKEN)
            setPending(uploaded)
            return Pair(uploaded, SyncEncoding.toBase64Url(read))
        }
        throw Stopped(Failure.BROKEN)
    }

    /** Ends the channel, first telling the browser the pairing is off when [tellBrowser]. */
    private fun closeChannel(tellBrowser: Boolean) {
        synchronized(sendLock) {
            if (!channelOpen)
                return
            if (tellBrowser)
                channels.post(code.relay, channel, token!!,
                    Pairing.sealFrame(keys, code.channel, Pairing.TO_BROWSER, sent + 1, PairingMessage.Cancel))
            channelOpen = false
            channels.closeChannel(code.relay, channel, token!!)
        }
    }

    /** After a failure: closes the channel and deletes the vault, if there is one. */
    private fun cleanUp(tellBrowser: Boolean) {
        closeChannel(tellBrowser)
        deleteVault()
    }

    /**
     * Deletes the vault of a pairing that did not finish, and the pairing. A vault the relay could
     * not delete is kept as being removed, so that the next sync deletes it: the browser may have
     * stored its details already.
     */
    private fun deleteVault() {
        synchronized(vaultLock) {
            val browser = pending ?: return
            if (browser.state == PairedBrowser.State.PAIRED)
                return
            if (BrowserSync.vaultDeleted(vaults.delete(browser.relay, browser.vaultId, browser.writeToken)))
                records.remove(browser.vaultId)
            else
                records.update(browser.copy(state = PairedBrowser.State.REMOVING))
            pending = null
        }
    }

    companion object {
        private const val POST_ATTEMPTS = 3
        private const val CREATE_ATTEMPTS = 3
        private const val RETRY_MS = 2_000L
        private const val MIN_POLL_MS = 1_000L
        private const val APPROVAL_CHECK_MS = 1_000L
    }
}
