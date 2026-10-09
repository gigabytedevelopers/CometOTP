@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import org.json.JSONObject

/**
 * Reads the JSON Proton Authenticator exports under Settings > Export.
 *
 * The file is `{"version":1,"entries":[{"content":{"uri":"otpauth://..","entry_type":"Totp",
 * "name":..},"note":..}]}`: each account is an otpauth link, with the name the user gave it
 * alongside. Steam accounts are `entry_type` "Steam", their link an otpauth steam link or a bare
 * `steam://` secret. An export made with a password has `salt` and `content` in place of the
 * entries and is encrypted in a way CometOTP cannot open; Proton exports without one too.
 */
object ProtonImport : ImportFormat {

    override fun read(data: ByteArray, password: String?): List<ImportedToken> {
        val root = ImportCrypto.jsonObject(data) ?: throw ImportException.notThisFormat("Not JSON")
        val entries = root.optJSONArray("entries")
        if (entries == null) {
            if (root.has("salt") && root.has("content"))
                throw ImportException.encryptionUnsupported("Password-protected Proton export")
            throw ImportException.notThisFormat("No entries")
        }

        val tokens = (0 until entries.length()).map { i ->
            readEntry(entries.optJSONObject(i) ?: throw ImportException.damaged("Entry $i is not an object"))
        }
        if (tokens.isEmpty())
            throw ImportException.empty()
        return tokens
    }

    private fun readEntry(entry: JSONObject): ImportedToken {
        val content = entry.optJSONObject("content") ?: throw ImportException.damaged("Entry without content")
        val uri = content.optString("uri").trim()
        val name = content.optString("name").trim()
        val steam = content.optString("entry_type").equals("steam", ignoreCase = true)

        if (OtpauthUri.isOtpauthUri(uri)) {
            val token = OtpauthUri.parse(uri)!!
            val type = if (steam) Entry.OTPType.STEAM else token.type
            val digits = if (steam && !uri.contains("digits=", ignoreCase = true)) 5 else token.digits
            // The link names the account; the name the user gave it fills in an issuer it lacks.
            return ImportedToken(type, token.secret, token.issuer.ifEmpty { name }, token.label,
                    token.algorithm, digits, token.period, token.counter)
        }

        if (uri.startsWith("steam://", ignoreCase = true) || steam)
            return ImportedToken(Entry.OTPType.STEAM, TokenImport.decodeBase32(uri.removePrefix("steam://").removePrefix("STEAM://")),
                    name.ifEmpty { "Steam" }, "", digits = 5)

        throw ImportException.damaged("Entry with no link")
    }
}
