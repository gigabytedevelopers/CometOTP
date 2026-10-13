@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry

/** What the Connected extensions screen shows about paired browsers, apart from the Android parts. */
object ConnectedBrowsers {

    /** The one thing a browser's row says about its state, most pressing first. */
    enum class Status {
        /** Removed here; the relay has yet to delete its copy. */
        REMOVING,
        /** Pairing did not finish. */
        PENDING,
        /** The relay lost its vault: pair it again. */
        BROKEN,
        /** CometOTP has no lock, so nothing is sent. */
        PAUSED,
        /** Paired, nothing uploaded yet. */
        NOT_SYNCED,
        SYNCED,
    }

    fun status(browser: PairedBrowser, paused: Boolean): Status = when (browser.state) {
        PairedBrowser.State.REMOVING -> Status.REMOVING
        PairedBrowser.State.PENDING -> Status.PENDING
        PairedBrowser.State.BROKEN -> Status.BROKEN
        PairedBrowser.State.PAIRED -> when {
            paused -> Status.PAUSED
            browser.uploadedAt == 0L -> Status.NOT_SYNCED
            else -> Status.SYNCED
        }
    }

    /**
     * The services among [entries] that were in the last vault a browser got ([PairedBrowser.sharedIds]),
     * in list order, and how many of its ids no longer match any (services deleted since).
     */
    fun sharedServices(sharedIds: List<String>?, entries: List<Entry>): Pair<List<Entry>, Int> {
        if (sharedIds.isNullOrEmpty())
            return Pair(emptyList(), 0)
        val wanted = sharedIds.toSet()
        val found = LinkedHashMap<String, Entry>()
        for (entry in entries) {
            val secret = entry.secret ?: continue
            val id = SyncVault.serviceId(secret)
            if (id in wanted && id !in found)
                found[id] = entry
        }
        return Pair(found.values.toList(), wanted.size - found.size)
    }

    /** A new name for a browser: trimmed, at most 64 bytes; null when nothing is left. */
    fun cleanName(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty())
            return null
        return VaultPayload.fitName(trimmed)
    }
}
