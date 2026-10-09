@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads the backup file 2FAS Auth writes under Settings > Backup > Export, a `.2fas` JSON file.
 *
 * Unprotected, the accounts are the `services` array. With a password, `services` is empty and
 * the same array is in `servicesEncrypted` as three base64 parts, `data:salt:iv`: AES-256-GCM
 * under a key PBKDF2-SHA256 derives from the password with 10,000 iterations. The tag follows
 * the ciphertext in `data`.
 *
 * Each service is `{"name":..,"secret":..,"otp":{"account":..,"issuer":..,"digits":..,
 * "period":..,"counter":..,"algorithm":..,"tokenType":..}}`; older backups name the account
 * `label` and have no `tokenType`. The name is what the user sees in 2FAS, which may differ
 * from the issuer the site set, so the issuer is preferred and the name used in its place.
 */
object TwoFasImport : ImportFormat {

    private const val PBKDF2_ITERATIONS = 10_000
    private const val KEY_LENGTH = 32

    override fun read(data: ByteArray, password: String?): List<ImportedToken> {
        val root = ImportCrypto.jsonObject(data) ?: throw ImportException.notThisFormat("Not JSON")
        if (!root.has("services") && !root.has("servicesEncrypted"))
            throw ImportException.notThisFormat("No services")

        val encrypted = root.optString("servicesEncrypted", "")
        val services = if (encrypted.isNotEmpty())
            decryptServices(encrypted, password)
        else
            root.optJSONArray("services") ?: throw ImportException.damaged("services is not a list")

        val tokens = (0 until services.length()).map { i ->
            readService(services.optJSONObject(i) ?: throw ImportException.damaged("Service $i is not an object"))
        }
        if (tokens.isEmpty())
            throw ImportException.empty()
        return tokens
    }

    private fun decryptServices(encrypted: String, password: String?): JSONArray {
        val parts = encrypted.split(':')
        if (parts.size != 3)
            throw ImportException.damaged("servicesEncrypted is not data:salt:iv")
        val ciphertext = ImportCrypto.base64(parts[0]) ?: throw ImportException.damaged("Bad ciphertext")
        val salt = ImportCrypto.base64(parts[1]) ?: throw ImportException.damaged("Bad salt")
        val iv = ImportCrypto.base64(parts[2]) ?: throw ImportException.damaged("Bad iv")

        if (password == null)
            throw ImportException.passwordRequired()

        val key = Pbkdf2.sha256(password.toByteArray(Charsets.UTF_8), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val plain = ImportCrypto.aesGcmDecrypt(key, iv, ciphertext) ?: throw ImportException.wrongPassword()

        return ImportCrypto.jsonArray(plain) ?: throw ImportException.damaged("The decrypted services are not a list")
    }

    private fun readService(service: JSONObject): ImportedToken {
        val name = service.optString("name").trim()
        val otp = service.optJSONObject("otp") ?: JSONObject()

        val issuer = otp.optString("issuer").trim().ifEmpty { name }
        val label = otp.optString("account").trim().ifEmpty { otp.optString("label").trim() }

        val type = TokenImport.type(otp.optString("tokenType"))
        val secret = TokenImport.decodeBase32(service.optString("secret"))
        val digits = if (otp.has("digits") && !otp.isNull("digits")) otp.optInt("digits")
                else if (type == Entry.OTPType.STEAM) TokenCalculator.STEAM_DEFAULT_DIGITS else TokenCalculator.TOTP_DEFAULT_DIGITS
        val period = if (otp.has("period") && !otp.isNull("period")) otp.optInt("period") else TokenCalculator.TOTP_DEFAULT_PERIOD

        return ImportedToken(type, secret, issuer, label,
                TokenImport.algorithm(otp.optString("algorithm")),
                digits, period, otp.optLong("counter", 0))
    }
}
