@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.content.Context
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset
import javax.crypto.SecretKey

/**
 * Accounts removed from the list, kept for [RETENTION_MS] so that a removal can be undone: losing
 * a secret can lock its owner out of the account.
 *
 * They live in a file of their own ([Constants.FILENAME_REMOVED]), encrypted with the database's
 * key, rather than flagged in the database. Everything that reads the database (backups, imports,
 * tags, the list) then goes on seeing only the accounts in use, and backups leave the bin out.
 * The file goes with the database: wiped with it ([DatabaseHelper.wipeDatabase]) and re-encrypted
 * with it ([EncryptionChangeHelper]).
 */
object RecycleBin {
    const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** An account in the bin, and when it was removed (milliseconds since the epoch). */
    class Removed(val entry: Entry, val removedAt: Long)

    /* Pure parts, tested on the JVM */

    internal fun toJson(items: List<Removed>): String {
        val json = JSONArray()
        for (item in items) {
            try {
                json.put(JSONObject().put("entry", item.entry.toJSON()).put("removedAt", item.removedAt))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return json.toString()
    }

    /** The items in [data]; null when it is not a JSON array. An item that cannot be read is left out. */
    internal fun fromJson(data: String): MutableList<Removed>? {
        val json = try {
            JSONArray(data)
        } catch (e: Exception) {
            return null
        }
        val items = ArrayList<Removed>()
        for (i in 0 until json.length()) {
            try {
                val item = json.getJSONObject(i)
                items.add(Removed(Entry(item.getJSONObject("entry")), item.getLong("removedAt")))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return items
    }

    /** [items] without those removed [RETENTION_MS] or longer before [now]. */
    internal fun withoutExpired(items: List<Removed>, now: Long): MutableList<Removed> {
        return items.filterTo(ArrayList()) { now - it.removedAt < RETENTION_MS }
    }

    /** Whole days, rounded up, until an account removed at [removedAt] is deleted; at least 1. */
    fun daysLeft(removedAt: Long, now: Long): Int {
        val left = removedAt + RETENTION_MS - now
        return ((left + DAY_MS - 1) / DAY_MS).toInt().coerceAtLeast(1)
    }

    /* The file */

    private fun file(context: Context): File {
        return File(context.filesDir, Constants.FILENAME_REMOVED)
    }

    /**
     * What is in the bin, newest first, after deleting what has been there [RETENTION_MS] or
     * longer. A bin that cannot be read (it was encrypted with a key since lost) counts as empty:
     * nothing in it could be restored anyway.
     */
    fun load(context: Context, key: SecretKey, now: Long = System.currentTimeMillis()): MutableList<Removed> {
        synchronized(DatabaseHelper.DatabaseFileLock) {
            val items = read(file(context), key) ?: return ArrayList()
            val kept = withoutExpired(items, now)
            if (kept.size != items.size)
                write(file(context), kept, key)
            kept.sortByDescending { it.removedAt }
            return kept
        }
    }

    fun save(context: Context, items: List<Removed>, key: SecretKey): Boolean {
        synchronized(DatabaseHelper.DatabaseFileLock) {
            return write(file(context), items, key)
        }
    }

    /** Puts [entries] in the bin, removed [now]; false if the bin could not be written. */
    fun add(context: Context, entries: List<Entry>, key: SecretKey, now: Long = System.currentTimeMillis()): Boolean {
        synchronized(DatabaseHelper.DatabaseFileLock) {
            val items = load(context, key, now)
            for (entry in entries)
                items.add(Removed(entry, now))
            return write(file(context), items, key)
        }
    }

    /**
     * Takes [entries] back out of the bin (one item for each, matched as equal entries), e.g.
     * when their removal is undone. False if the bin could not be written.
     */
    fun take(context: Context, entries: List<Entry>, key: SecretKey): Boolean {
        synchronized(DatabaseHelper.DatabaseFileLock) {
            val items = load(context, key)
            for (entry in entries) {
                val i = items.indexOfFirst { it.entry == entry }
                if (i >= 0)
                    items.removeAt(i)
            }
            return write(file(context), items, key)
        }
    }

    fun wipe(context: Context) {
        file(context).delete()
    }

    /**
     * Re-encrypts the bin from [oldKey] to [newKey], with the database. A bin that cannot be
     * read with [oldKey], or written with [newKey], is deleted: under the new key it could never
     * be read again. True when the bin came through.
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
        val items = if (oldKey != null) read(file, oldKey) else null
        if (items != null && write(file, items, newKey))
            return true
        file.delete()
        return false
    }

    internal fun read(file: File, key: SecretKey): MutableList<Removed>? {
        if (!file.exists())
            return ArrayList()
        return try {
            val data = EncryptionHelper.decrypt(key, FileHelper.readFileToBytes(file))
            fromJson(String(data, Charset.defaultCharset()))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    internal fun write(file: File, items: List<Removed>, key: SecretKey): Boolean {
        return try {
            if (items.isEmpty()) {
                file.delete() || !file.exists()
            } else {
                val data = EncryptionHelper.encrypt(key, toJson(items).toByteArray(Charset.defaultCharset()))
                FileHelper.writeBytesToFileAtomically(file, data)
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
