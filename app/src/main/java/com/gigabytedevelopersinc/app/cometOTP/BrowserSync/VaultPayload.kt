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
     * whose Browser extension switch is on. A second service with the same secret is left out.
     */
    fun services(entries: List<Entry>): List<SyncService> {
        val services = ArrayList<SyncService>()
        val ids = HashSet<String>()
        for (entry in entries) {
            if (!entry.showsInBrowser)
                continue
            val service = service(entry) ?: continue
            if (ids.add(service.id))
                services.add(service)
        }
        return services
    }

    /** One service, or null when it has no secret to share. */
    internal fun service(entry: Entry): SyncService? {
        val secret = entry.secret ?: return null
        if (secret.isEmpty())
            return null
        return SyncService(
            id = SyncVault.serviceId(secret),
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
