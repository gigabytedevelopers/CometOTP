@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Reads the vault Aegis exports under Settings > Import & export > Export, as "Aegis (JSON)".
 *
 * The file is `{"version":1,"header":{...},"db":...}`. Exported without encryption, `header`
 * has null slots and params and `db` is the vault itself. Encrypted, `db` is base64 ciphertext
 * and the header carries the key slots: each password slot holds the vault's master key wrapped
 * with a key derived from the password by scrypt, and the vault is AES-GCM under the master key.
 * The tags travel separately from the ciphertext, in `key_params` and `params`.
 *
 * The vault's entries are `{"type":"totp","name":..,"issuer":..,"info":{"secret":..,"algo":..,
 * "digits":..,"period":..|"counter":..}}`; the type also names steam, motp and yandex.
 */
object AegisImport : ImportFormat {

    private const val SLOT_PASSWORD = 1
    private const val MASTER_KEY_LENGTH = 32

    override fun read(data: ByteArray, password: String?): List<ImportedToken> {
        val root = ImportCrypto.jsonObject(data) ?: throw ImportException.notThisFormat("Not JSON")
        val header = root.optJSONObject("header")
        if (header == null || !root.has("db"))
            throw ImportException.notThisFormat("No header or db")

        val db = root.optJSONObject("db") ?: decryptVault(header, root.optString("db"), password)

        val entries = db.optJSONArray("entries") ?: throw ImportException.damaged("No entries in the vault")
        val tokens = readEntries(entries)
        if (tokens.isEmpty())
            throw ImportException.empty()
        return tokens
    }

    private fun decryptVault(header: JSONObject, db: String, password: String?): JSONObject {
        val slots = header.optJSONArray("slots") ?: throw ImportException.damaged("Encrypted vault without slots")
        val params = header.optJSONObject("params") ?: throw ImportException.damaged("Encrypted vault without params")

        val passwordSlots = (0 until slots.length()).mapNotNull { slots.optJSONObject(it) }
                .filter { it.optInt("type", -1) == SLOT_PASSWORD }
        if (passwordSlots.isEmpty())
            throw ImportException.encryptionUnsupported("No password slot: only a biometric key can open this vault")
        if (password == null)
            throw ImportException.passwordRequired()

        val masterKey = passwordSlots.firstNotNullOfOrNull { unwrapMasterKey(it, password) }
                ?: throw ImportException.wrongPassword()

        val ciphertext = ImportCrypto.base64(db) ?: throw ImportException.damaged("The vault is not base64")
        val nonce = ImportCrypto.hex(params.optString("nonce")) ?: throw ImportException.damaged("Bad vault nonce")
        val tag = ImportCrypto.hex(params.optString("tag")) ?: throw ImportException.damaged("Bad vault tag")

        val plain = ImportCrypto.aesGcmDecrypt(masterKey, nonce, ciphertext + tag)
                ?: throw ImportException.damaged("The vault does not decrypt with its own master key")

        return ImportCrypto.jsonObject(plain) ?: throw ImportException.damaged("The decrypted vault is not JSON")
    }

    /** @return the master key the slot wraps, or null when the password does not open the slot */
    private fun unwrapMasterKey(slot: JSONObject, password: String): ByteArray? {
        val salt = ImportCrypto.hex(slot.optString("salt")) ?: throw ImportException.damaged("Bad slot salt")
        val n = slot.optInt("n", 0)
        val r = slot.optInt("r", 0)
        val p = slot.optInt("p", 0)
        val wrapped = ImportCrypto.hex(slot.optString("key")) ?: throw ImportException.damaged("Bad slot key")
        val keyParams = slot.optJSONObject("key_params") ?: throw ImportException.damaged("Slot without key params")
        val nonce = ImportCrypto.hex(keyParams.optString("nonce")) ?: throw ImportException.damaged("Bad slot nonce")
        val tag = ImportCrypto.hex(keyParams.optString("tag")) ?: throw ImportException.damaged("Bad slot tag")

        val derived = try {
            Scrypt.derive(password.toByteArray(Charsets.UTF_8), salt, n, r, p, MASTER_KEY_LENGTH)
        } catch (e: IllegalArgumentException) {
            throw ImportException.damaged("Bad scrypt parameters", e)
        }

        return ImportCrypto.aesGcmDecrypt(derived, nonce, wrapped + tag)
    }

    private fun readEntries(entries: JSONArray): List<ImportedToken> {
        val tokens = ArrayList<ImportedToken>()
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: throw ImportException.damaged("Entry $i is not an object")
            tokens.add(readEntry(entry))
        }
        return tokens
    }

    private fun readEntry(entry: JSONObject): ImportedToken {
        val info = entry.optJSONObject("info") ?: throw ImportException.damaged("Entry without info")
        val typeName = entry.optString("type")
        val type = TokenImport.type(typeName)
        val issuer = entry.optString("issuer").trim()
        val label = entry.optString("name").trim()

        if (type == Entry.OTPType.MOTP)
            return ImportedToken(type, info.optString("secret").toByteArray(Charsets.UTF_8), issuer, label)

        val secret = if (info.has("secret")) TokenImport.decodeBase32(info.optString("secret")) else ByteArray(0)
        val digits = optInt(info, "digits") ?: if (type == Entry.OTPType.STEAM) TokenCalculator.STEAM_DEFAULT_DIGITS else TokenCalculator.TOTP_DEFAULT_DIGITS

        return ImportedToken(type, secret, issuer, label,
                TokenImport.algorithm(info.optString("algo")),
                digits,
                optInt(info, "period") ?: TokenCalculator.TOTP_DEFAULT_PERIOD,
                optLong(info, "counter") ?: 0L)
    }

    private fun optInt(o: JSONObject, name: String): Int? = try {
        if (o.has(name) && !o.isNull(name)) o.getInt(name) else null
    } catch (e: JSONException) {
        null
    }

    private fun optLong(o: JSONObject, name: String): Long? = try {
        if (o.has(name) && !o.isNull(name)) o.getLong(name) else null
    } catch (e: JSONException) {
        null
    }
}
