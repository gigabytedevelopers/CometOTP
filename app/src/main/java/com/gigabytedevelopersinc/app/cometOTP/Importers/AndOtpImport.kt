@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Reads andOTP's backups, which CometOTP grew out of and still writes itself.
 *
 * A plain-text backup is a JSON array of entries: `{"secret":..,"issuer":..,"label":..,
 * "digits":..,"type":..,"algorithm":..,"period":..|"counter":..,"tags":[..]}`. A
 * password-protected one (`.json.aes`) is AES-256-GCM: in the current format a 4-byte big-endian
 * iteration count, a 12-byte salt and a 12-byte IV come first and the key is PBKDF2-SHA1 of the
 * password; in the format before it only the IV comes first and the key is the SHA-256 of the
 * password. Both are tried, so the user does not have to know which andOTP wrote.
 */
object AndOtpImport : ImportFormat {

    private const val INT_LENGTH = 4
    private const val SALT_LENGTH = 12
    private const val IV_LENGTH = 12
    private const val KEY_LENGTH = 32
    private const val MAX_ITERATIONS = 10_000_000

    override fun read(data: ByteArray, password: String?): List<ImportedToken> {
        val entries = ImportCrypto.jsonArray(data)
                ?: if (OtpauthLinksImport.decodeText(data) != null)
                    throw ImportException.notThisFormat("Text that is not a JSON array")
                else
                    decrypt(data, password)

        val tokens = (0 until entries.length()).map { i ->
            readEntry(entries.optJSONObject(i) ?: throw ImportException.damaged("Entry $i is not an object"))
        }
        if (tokens.isEmpty())
            throw ImportException.empty()
        return tokens
    }

    private fun decrypt(data: ByteArray, password: String?): JSONArray {
        if (password == null)
            throw ImportException.passwordRequired()
        val passwordBytes = password.toByteArray(Charsets.UTF_8)

        // Current format: iterations, salt, IV, then the ciphertext and tag.
        val headerLength = INT_LENGTH + SALT_LENGTH + IV_LENGTH
        if (data.size > headerLength + ImportCrypto.GCM_TAG_BITS / 8) {
            val iterations = ByteBuffer.wrap(data, 0, INT_LENGTH).int
            if (iterations in 1..MAX_ITERATIONS) {
                val salt = data.copyOfRange(INT_LENGTH, INT_LENGTH + SALT_LENGTH)
                val iv = data.copyOfRange(INT_LENGTH + SALT_LENGTH, headerLength)
                val key = Pbkdf2.sha1(passwordBytes, salt, iterations, KEY_LENGTH)
                val plain = ImportCrypto.aesGcmDecrypt(key, iv, data.copyOfRange(headerLength, data.size))
                if (plain != null)
                    return ImportCrypto.jsonArray(plain) ?: throw ImportException.damaged("The decrypted backup is not a JSON array")
            }
        }

        // The format before it: IV, then the ciphertext and tag, under a key hashed from the password.
        if (data.size > IV_LENGTH + ImportCrypto.GCM_TAG_BITS / 8) {
            val key = MessageDigest.getInstance("SHA-256").digest(passwordBytes)
            val plain = ImportCrypto.aesGcmDecrypt(key, data.copyOfRange(0, IV_LENGTH), data.copyOfRange(IV_LENGTH, data.size))
            if (plain != null)
                return ImportCrypto.jsonArray(plain) ?: throw ImportException.damaged("The decrypted backup is not a JSON array")
        }

        throw ImportException.wrongPassword()
    }

    private fun readEntry(entry: JSONObject): ImportedToken {
        val issuer = entry.optString("issuer").trim()
        // Backups from before issuers were stored apart keep "Issuer:label" in the label.
        val (splitIssuer, label) = TokenImport.splitName(entry.optString("label"), issuer)

        val tags = ArrayList<String>()
        val tagsArray = entry.optJSONArray("tags")
        if (tagsArray != null)
            for (i in 0 until tagsArray.length())
                tags.add(tagsArray.optString(i))

        return ImportedToken(TokenImport.type(entry.optString("type")),
                TokenImport.decodeBase32(entry.optString("secret")),
                splitIssuer, label,
                TokenImport.algorithm(entry.optString("algorithm")),
                entry.optInt("digits", TokenCalculator.TOTP_DEFAULT_DIGITS),
                entry.optInt("period", TokenCalculator.TOTP_DEFAULT_PERIOD),
                entry.optLong("counter", 0),
                tags)
    }
}
