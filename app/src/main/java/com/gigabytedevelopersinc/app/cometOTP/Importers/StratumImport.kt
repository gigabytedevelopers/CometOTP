@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Reads Stratum's backups (the app was Authenticator Pro until 2024).
 *
 * "Backup" writes a `.stratum` (before that `.authpro`) file: the ASCII header `AUTHENTICATORPRO`,
 * a 16-byte salt, a 12-byte IV, then AES-256-GCM over the JSON with the tag last, under a key
 * Argon2id derives from the password (3 passes, 64 MiB, 4 lanes). Files from before 2022 have
 * the header `AuthenticatorPro`, a 20-byte salt, a 16-byte IV and AES-256-CBC under PBKDF2-SHA1
 * with 64,000 iterations. "Export > Unencrypted (JSON)" writes the JSON as it is.
 *
 * The JSON is `{"Authenticators":[{"Type":..,"Issuer":..,"Username":..,"Secret":..,"Pin":..,
 * "Algorithm":..,"Digits":..,"Period":..,"Counter":..}],"Categories":[{"Id":..,"Name":..}],
 * "AuthenticatorCategories":[{"CategoryId":..,"AuthenticatorSecret":..}]}`. Type is 1 HOTP,
 * 2 TOTP, 3 mOTP, 4 Steam, 5 Yandex; algorithm 0 SHA1, 1 SHA256, 2 SHA512. Categories come
 * across as tags.
 */
object StratumImport : ImportFormat {

    private const val HEADER_STRONG = "AUTHENTICATORPRO"
    private const val HEADER_LEGACY = "AuthenticatorPro"
    private const val HEADER_LENGTH = 16

    private const val STRONG_SALT_LENGTH = 16
    private const val STRONG_IV_LENGTH = 12
    private const val ARGON2_PASSES = 3
    private const val ARGON2_MEMORY_KIB = 65536
    private const val ARGON2_LANES = 4

    private const val LEGACY_SALT_LENGTH = 20
    private const val LEGACY_IV_LENGTH = 16
    private const val LEGACY_ITERATIONS = 64_000

    private const val KEY_LENGTH = 32

    override fun read(data: ByteArray, password: String?): List<ImportedToken> {
        val json = when (header(data)) {
            HEADER_STRONG -> decryptStrong(data, password)
            HEADER_LEGACY -> decryptLegacy(data, password)
            else -> ImportCrypto.jsonObject(data) ?: throw ImportException.notThisFormat("Neither a backup nor JSON")
        }

        val authenticators = json.optJSONArray("Authenticators") ?: throw ImportException.notThisFormat("No Authenticators")

        val categoryNames = HashMap<String, String>()
        json.optJSONArray("Categories")?.let { categories ->
            for (i in 0 until categories.length()) {
                val category = categories.optJSONObject(i) ?: continue
                categoryNames[category.optString("Id")] = category.optString("Name").trim()
            }
        }
        val tagsBySecret = HashMap<String, ArrayList<String>>()
        json.optJSONArray("AuthenticatorCategories")?.let { links ->
            for (i in 0 until links.length()) {
                val link = links.optJSONObject(i) ?: continue
                val name = categoryNames[link.optString("CategoryId")] ?: continue
                tagsBySecret.getOrPut(link.optString("AuthenticatorSecret")) { ArrayList() }.add(name)
            }
        }

        val tokens = (0 until authenticators.length()).map { i ->
            val authenticator = authenticators.optJSONObject(i) ?: throw ImportException.damaged("Authenticator $i is not an object")
            readAuthenticator(authenticator, tagsBySecret[authenticator.optString("Secret")] ?: emptyList())
        }
        if (tokens.isEmpty())
            throw ImportException.empty()
        return tokens
    }

    private fun header(data: ByteArray): String? {
        if (data.size < HEADER_LENGTH)
            return null
        return String(data, 0, HEADER_LENGTH, Charsets.US_ASCII)
    }

    private fun decryptStrong(data: ByteArray, password: String?): JSONObject {
        val payloadStart = HEADER_LENGTH + STRONG_SALT_LENGTH + STRONG_IV_LENGTH
        if (data.size <= payloadStart + ImportCrypto.GCM_TAG_BITS / 8)
            throw ImportException.damaged("Backup too short")
        if (password == null)
            throw ImportException.passwordRequired()

        val salt = data.copyOfRange(HEADER_LENGTH, HEADER_LENGTH + STRONG_SALT_LENGTH)
        val iv = data.copyOfRange(HEADER_LENGTH + STRONG_SALT_LENGTH, payloadStart)
        val key = Argon2.id(password.toByteArray(Charsets.UTF_8), salt, ARGON2_PASSES, ARGON2_MEMORY_KIB, ARGON2_LANES, KEY_LENGTH)

        val plain = ImportCrypto.aesGcmDecrypt(key, iv, data.copyOfRange(payloadStart, data.size))
                ?: throw ImportException.wrongPassword()
        return ImportCrypto.jsonObject(plain) ?: throw ImportException.damaged("The decrypted backup is not JSON")
    }

    private fun decryptLegacy(data: ByteArray, password: String?): JSONObject {
        val payloadStart = HEADER_LENGTH + LEGACY_SALT_LENGTH + LEGACY_IV_LENGTH
        if (data.size <= payloadStart || (data.size - payloadStart) % 16 != 0)
            throw ImportException.damaged("Backup too short")
        if (password == null)
            throw ImportException.passwordRequired()

        val salt = data.copyOfRange(HEADER_LENGTH, HEADER_LENGTH + LEGACY_SALT_LENGTH)
        val iv = data.copyOfRange(HEADER_LENGTH + LEGACY_SALT_LENGTH, payloadStart)
        val key = Pbkdf2.sha1(password.toByteArray(Charsets.UTF_8), salt, LEGACY_ITERATIONS, KEY_LENGTH)

        val plain = try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            cipher.doFinal(data.copyOfRange(payloadStart, data.size))
        } catch (e: Exception) {
            throw ImportException.wrongPassword(e)
        }
        // CBC has no tag: a wrong password that happens to unpad cleanly is caught here instead.
        return ImportCrypto.jsonObject(plain) ?: throw ImportException.wrongPassword()
    }

    private fun readAuthenticator(a: JSONObject, tags: List<String>): ImportedToken {
        val type = when (a.optInt("Type", -1)) {
            1 -> Entry.OTPType.HOTP
            2 -> Entry.OTPType.TOTP
            3 -> Entry.OTPType.MOTP
            4 -> Entry.OTPType.STEAM
            else -> null
        }
        val algorithm = when (a.optInt("Algorithm", 0)) {
            0 -> TokenCalculator.HashAlgorithm.SHA1
            1 -> TokenCalculator.HashAlgorithm.SHA256
            2 -> TokenCalculator.HashAlgorithm.SHA512
            else -> null
        }
        val issuer = a.optString("Issuer").trim()
        val label = if (a.isNull("Username")) "" else a.optString("Username").trim()

        if (type == Entry.OTPType.MOTP)
            return ImportedToken(type, a.optString("Secret").toByteArray(Charsets.UTF_8), issuer, label, tags = tags)

        return ImportedToken(type, TokenImport.decodeBase32(a.optString("Secret")), issuer, label, algorithm,
                a.optInt("Digits", TokenCalculator.TOTP_DEFAULT_DIGITS),
                a.optInt("Period", TokenCalculator.TOTP_DEFAULT_PERIOD),
                a.optLong("Counter", 0),
                tags)
    }
}
