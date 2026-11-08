@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import android.content.Context
import android.os.Build
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EntryThumbnail
import org.apache.commons.codec.binary.Base32
import org.apache.commons.codec.binary.Hex
import java.util.Locale

/** What goes into a browser's vault: the services it may show, and the phone's name (protocol §4.3). */
object VaultPayload {

    private const val MAX_NAME_BYTES = 64

    /**
     * The services paired browsers get, in list order: those whose type a browser can show and
     * whose Browser extension switch is on, each under the id [ids] gives it.
     */
    fun services(entries: List<Entry>): List<SyncService> {
        val ids = ids(entries) { it.showsInBrowser }
        return entries.indices.mapNotNull { i -> ids[i]?.let { service(entries[i], it) } }
    }

    /**
     * The id each of [entries] reaches browsers under when those that pass [sent] are sent, or
     * null for one that does not (protocol §4.4). Going through them in list order, the first with
     * a given secret gets the secret's id, and each later one an alias id made from its issuer and
     * label too, so that services sharing a secret all reach browsers. An alias id is given only
     * once: a later service whose alias id an earlier one already got is the same service listed
     * twice, and is left out. So is one whose issuer or label is too long for an alias id.
     */
    internal fun ids(entries: List<Entry>, sent: (Entry) -> Boolean): List<String?> {
        val given = HashSet<String>()
        return entries.map { entry ->
            val secret = entry.secret?.takeIf { it.isNotEmpty() && sent(entry) } ?: return@map null
            val id = SyncVault.serviceId(secret).takeIf { it !in given }
                ?: SyncVault.serviceAliasId(secret, entry.issuer, entry.label ?: "")
            id?.takeIf { given.add(it) }
        }
    }

    /** One service, sent under [id]; null when it has no secret to share. */
    private fun service(entry: Entry, id: String): SyncService? {
        val secret = entry.secret?.takeIf { it.isNotEmpty() } ?: return null
        return SyncService(
            id = id,
            type = if (entry.type == Entry.OTPType.STEAM) "steam" else "totp",
            secret = String(Base32().encode(secret), Charsets.US_ASCII).trimEnd('='),
            algorithm = entry.algorithm.name,
            digits = entry.digits,
            period = entry.period,
            issuer = entry.issuer,
            label = entry.label ?: "",
            icon = entry.thumbnail.takeIf { it != EntryThumbnail.EntryThumbnails.Default }?.name,
            initials = entry.initials.takeIf { it.isNotEmpty() },
            badge = entry.badgeColor.takeIf { it != 0 }?.let { String.format(Locale.ROOT, "#%06X", it and 0xFFFFFF) },
            tags = entry.tags.takeIf { it.isNotEmpty() }?.toList(),
        )
    }

    /**
     * SHA-256 (hex) of the payload with its update time left out, so that an upload happens only
     * when something a browser shows has changed (protocol §7.1).
     */
    fun hash(phone: String, services: List<SyncService>): String =
        String(Hex.encodeHex(SyncCrypto.sha256(SyncEncoding.utf8(SyncVault.payloadJson(phone, 0, services)))))

    /**
     * The name a browser shows for this phone: the name it was given in the phone's settings
     * (Android 7.1 and later), else its model, cut to the protocol's 64 bytes.
     */
    fun phoneName(context: Context): String {
        val named = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1)
            android.provider.Settings.Global.getString(context.contentResolver, android.provider.Settings.Global.DEVICE_NAME)
        else null
        return fitName(named?.trim().takeUnless { it.isNullOrEmpty() } ?: Build.MODEL ?: "Android")
    }

    /** [name] cut to at most 64 bytes of UTF-8, at a character boundary; never empty. */
    internal fun fitName(name: String): String {
        if (name.isEmpty())
            return "Android"
        var end = name.length
        while (SyncEncoding.utf8(name.substring(0, end)).size > MAX_NAME_BYTES) {
            end--
            if (end > 0 && Character.isLowSurrogate(name[end]))
                end--
        }
        return name.substring(0, end)
    }
}
