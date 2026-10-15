@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import android.content.Context
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionChangeHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.FileHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.crypto.SecretKey

/**
 * A browser paired with CometOTP: what the phone needs to keep its vault current (protocol §4.1)
 * and what Settings shows about it. The read token went to the browser and is not kept here.
 */
data class PairedBrowser(
    /** The vault id, base64url; identifies the pairing. */
    val vaultId: String,
    /** The relay's base URL. */
    val relay: String,
    /** The write token W, base64url. */
    val writeToken: String,
    /** The vault key V, base64url. */
    val vaultKey: String,
    /** The browser's name, as it gave it when pairing or as renamed since. */
    val name: String,
    /** When pairing started, in milliseconds since the epoch. */
    val pairedAt: Long,
    val state: State,
    /** The last version uploaded; 0 before the first. */
    val version: Long = 0,
    /** SHA-256 (hex) of the last uploaded payload without its update time, to skip unchanged uploads. */
    val payloadHash: String? = null,
    /** When the vault was last uploaded, in milliseconds since the epoch; 0 for never. */
    val uploadedAt: Long = 0,
    /** The day (UTC, YYYY-MM-DD) the relay says the browser last downloaded the vault. */
    val lastRead: String? = null,
    /** The ids ([SyncVault.serviceId]) of the services in the last vault uploaded; null before the first. */
    val sharedIds: List<String>? = null,
    /** When an upload found the vault gone ([State.DISCONNECTED]), in milliseconds since the epoch. */
    val disconnectedAt: Long = 0,
) {
    enum class State {
        /** Pairing has not finished: the browser has not confirmed it stored the vault's details. */
        PENDING,
        PAIRED,
        /** The relay no longer knows the vault (401 or 404): the browser has to be paired again. */
        BROKEN,
        /**
         * Removed on this phone, or a pairing that did not finish; kept only until the relay has
         * deleted its vault.
         */
        REMOVING,
        /**
         * The relay deleted the vault (410): the browser disconnected itself, or did not download
         * it for 180 days. Nothing is sent to it any more; it is kept only so that Connected
         * extensions can say so, until the person clears it.
         */
        DISCONNECTED,
    }
}

/**
 * The paired browsers, in a file of their own ([Constants.FILENAME_BROWSERS]) encrypted with the
 * database's key, like [com.gigabytedevelopersinc.app.cometOTP.Utilities.RecycleBin]. Their vault
 * keys give the same access as the secrets themselves, so they get the same protection.
 *
 * The file goes with the database: wiped with it ([DatabaseHelper.wipeDatabase], which the panic
 * response uses) and re-encrypted with it ([EncryptionChangeHelper]). Backups and device transfer
 * leave it out: a restored phone has to pair its browsers again.
 */
object BrowserStore {

    /**
     * How long a pairing may stay [PairedBrowser.State.PENDING]: after its channel's lifetime the
     * browser can no longer answer, so the pairing can never finish.
     */
    const val PENDING_LIFETIME_MS = Pairing.CHANNEL_LIFETIME * 1000L

    /* Pure parts, tested on the JVM */

    internal fun toJson(browsers: List<PairedBrowser>): String {
        val json = JSONArray()
        for (b in browsers) {
            val item = JSONObject()
                .put("vault", b.vaultId)
                .put("relay", b.relay)
                .put("write", b.writeToken)
                .put("key", b.vaultKey)
                .put("name", b.name)
                .put("paired", b.pairedAt)
                .put("state", b.state.name.lowercase())
                .put("version", b.version)
                .put("uploaded", b.uploadedAt)
            b.payloadHash?.let { item.put("hash", it) }
            b.lastRead?.let { item.put("read", it) }
            b.sharedIds?.let { item.put("services", JSONArray(it)) }
            if (b.disconnectedAt != 0L)
                item.put("disconnected", b.disconnectedAt)
            json.put(item)
        }
        return json.toString()
    }

    /** The browsers in [data]; null when it is not a JSON array. A browser that cannot be read is left out. */
    internal fun fromJson(data: String): MutableList<PairedBrowser>? {
        val json = try {
            JSONArray(data)
        } catch (e: Exception) {
            return null
        }
        val browsers = ArrayList<PairedBrowser>()
        for (i in 0 until json.length()) {
            try {
                val item = json.getJSONObject(i)
                val browser = PairedBrowser(
                    vaultId = item.getString("vault"),
                    relay = item.getString("relay"),
                    writeToken = item.getString("write"),
                    vaultKey = item.getString("key"),
                    name = item.getString("name"),
                    pairedAt = item.getLong("paired"),
                    state = PairedBrowser.State.valueOf(item.getString("state").uppercase()),
                    version = item.optLong("version", 0),
                    payloadHash = if (item.has("hash")) item.getString("hash") else null,
                    uploadedAt = item.optLong("uploaded", 0),
                    lastRead = if (item.has("read")) item.getString("read") else null,
                    sharedIds = item.optJSONArray("services")?.let { ids -> (0 until ids.length()).map(ids::getString) },
                    disconnectedAt = item.optLong("disconnected", 0),
                )
                // Credentials of the wrong size could never be used; leave the browser out.
                SyncEncoding.fromBase64Url(browser.vaultId, 16, "The vault id")
                SyncEncoding.fromBase64Url(browser.writeToken, 32, "The write token")
                SyncEncoding.fromBase64Url(browser.vaultKey, 32, "The vault key")
                if (browsers.none { it.vaultId == browser.vaultId })
                    browsers.add(browser)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return browsers
    }

    /**
     * Splits [browsers] into those to keep and the pending pairings that started
     * [PENDING_LIFETIME_MS] or longer before [now] and so can never finish.
     */
    internal fun splitStalePending(browsers: List<PairedBrowser>, now: Long): Pair<List<PairedBrowser>, List<PairedBrowser>> {
        return browsers.partition {
            it.state != PairedBrowser.State.PENDING || now - it.pairedAt < PENDING_LIFETIME_MS
        }
    }

    /** [browsers] with the one sharing [browser]'s vault id replaced by it; null when there is none. */
    internal fun withReplaced(browsers: List<PairedBrowser>, browser: PairedBrowser): MutableList<PairedBrowser>? {
        val i = browsers.indexOfFirst { it.vaultId == browser.vaultId }
        if (i < 0)
            return null
        return browsers.toMutableList().also { it[i] = browser }
    }

    /* The file */

    private fun file(context: Context): File = File(context.filesDir, Constants.FILENAME_BROWSERS)

    /**
     * The paired browsers, in the order they were paired. A file that cannot be read (encrypted
     * with a key since lost) counts as none: its vaults could not be updated anyway.
     */
    fun load(context: Context, key: SecretKey): MutableList<PairedBrowser> {
        synchronized(DatabaseHelper.DatabaseFileLock) {
            return read(file(context), key) ?: ArrayList()
        }
    }

    fun save(context: Context, browsers: List<PairedBrowser>, key: SecretKey): Boolean {
        synchronized(DatabaseHelper.DatabaseFileLock) {
            return write(file(context), browsers, key)
        }
    }

    /** Adds [browser], or replaces the one with the same vault id. False if it could not be written. */
    fun put(context: Context, browser: PairedBrowser, key: SecretKey): Boolean {
        synchronized(DatabaseHelper.DatabaseFileLock) {
            val browsers = load(context, key)
            val i = browsers.indexOfFirst { it.vaultId == browser.vaultId }
            if (i >= 0)
                browsers[i] = browser
            else
                browsers.add(browser)
            return write(file(context), browsers, key)
        }
    }

    /**
     * Replaces the browser with [browser]'s vault id, if it is still there: a browser removed
     * while its upload was under way is not brought back. False if nothing was replaced.
     */
    fun update(context: Context, browser: PairedBrowser, key: SecretKey): Boolean {
        synchronized(DatabaseHelper.DatabaseFileLock) {
            val browsers = withReplaced(load(context, key), browser) ?: return false
            return write(file(context), browsers, key)
        }
    }

    /** Removes the browser with [vaultId] and returns it, or null if there is none or it could not be written. */
    fun remove(context: Context, vaultId: String, key: SecretKey): PairedBrowser? {
        synchronized(DatabaseHelper.DatabaseFileLock) {
            val browsers = load(context, key)
            val i = browsers.indexOfFirst { it.vaultId == vaultId }
            if (i < 0)
                return null
            val removed = browsers.removeAt(i)
            return if (write(file(context), browsers, key)) removed else null
        }
    }

    /** This store, as a pairing under way keeps its browser in it ([PhonePairing]). */
    fun records(context: Context, key: SecretKey): PhonePairing.Records = object : PhonePairing.Records {
        override fun put(browser: PairedBrowser) = BrowserStore.put(context, browser, key)
        override fun update(browser: PairedBrowser) = BrowserStore.update(context, browser, key)
        override fun remove(vaultId: String) {
            BrowserStore.remove(context, vaultId, key)
        }
    }

    /**
     * Marks the pending pairings that can no longer finish (see [splitStalePending]) as being
     * removed, so that their vaults are deleted from the relay like a removed browser's: the
     * browser may have stored a vault's details before the phone heard back. Run when CometOTP starts.
     */
    fun removeStalePending(context: Context, key: SecretKey, now: Long = System.currentTimeMillis()) {
        synchronized(DatabaseHelper.DatabaseFileLock) {
            val browsers = read(file(context), key) ?: return
            val stale = splitStalePending(browsers, now).second.map { it.vaultId }.toSet()
            if (stale.isEmpty())
                return
            write(file(context), browsers.map {
                if (it.vaultId in stale) it.copy(state = PairedBrowser.State.REMOVING) else it
            }, key)
        }
    }

    fun wipe(context: Context) {
        file(context).delete()
    }

    /**
     * Re-encrypts the file from [oldKey] to [newKey], with the database. A file that cannot be read
     * with [oldKey], or written with [newKey], is deleted: under the new key it could never be read
     * again. True when the browsers came through.
     */
    fun reencrypt(context: Context, oldKey: SecretKey?, newKey: SecretKey): Boolean {
        synchronized(DatabaseHelper.DatabaseFileLock) {
            return reencryptFile(file(context), oldKey, newKey)
        }
    }

    /** [reencrypt] on [file], without the Context. */
    internal fun reencryptFile(file: File, oldKey: SecretKey?, newKey: SecretKey): Boolean {
        if (!file.exists())
            return true
        val browsers = if (oldKey != null) read(file, oldKey) else null
        if (browsers != null && write(file, browsers, newKey))
            return true
        file.delete()
        return false
    }

    internal fun read(file: File, key: SecretKey): MutableList<PairedBrowser>? {
        if (!file.exists())
            return ArrayList()
        return try {
            fromJson(String(EncryptionHelper.decrypt(key, FileHelper.readFileToBytes(file)), Charsets.UTF_8))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    internal fun write(file: File, browsers: List<PairedBrowser>, key: SecretKey): Boolean {
        return try {
            if (browsers.isEmpty()) {
                file.delete() || !file.exists()
            } else {
                FileHelper.writeBytesToFileAtomically(file, EncryptionHelper.encrypt(key, toJson(browsers).toByteArray(Charsets.UTF_8)))
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
