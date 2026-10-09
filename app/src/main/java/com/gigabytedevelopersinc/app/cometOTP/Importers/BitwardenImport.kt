@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Reads a Bitwarden vault export (Settings > Vault > Export vault) and takes the accounts that
 * have an authenticator key.
 *
 * The `.json` export is `{"encrypted":false,"items":[..]}`, each login item carrying its key in
 * `login.totp`: an otpauth link, a bare base32 secret, or `steam://` and a secret. The `.csv`
 * export has the same in its `login_totp` column. The item's name and username stand in for an
 * issuer and label the key does not carry.
 *
 * The password-protected `.json` export is `{"encrypted":true,"passwordProtected":true,
 * "salt":..,"kdfType":..,"data":"2.iv|data|mac"}`. The password and the salt (the base64 text
 * itself, or its SHA-256 for Argon2id) derive a key by PBKDF2-SHA256 or Argon2id, which HKDF
 * stretches into an encryption key and a MAC key; `data` is AES-256-CBC with an HMAC-SHA256
 * over IV and ciphertext, and holds the plain export. An export encrypted with the account's
 * own key instead has no password to open it with.
 */
object BitwardenImport : ImportFormat {

    private const val KDF_PBKDF2 = 0
    private const val KDF_ARGON2ID = 1
    private const val ENC_TYPE_AES_CBC_HMAC = "2"

    override fun read(data: ByteArray, password: String?): List<ImportedToken> {
        val root = ImportCrypto.jsonObject(data)
        if (root == null) {
            val text = OtpauthLinksImport.decodeText(data) ?: throw ImportException.notThisFormat("Not text")
            return readCsv(text)
        }

        val export = if (root.optBoolean("encrypted", false)) decrypt(root, password) else root
        val items = export.optJSONArray("items") ?: throw ImportException.notThisFormat("No items")

        val tokens = ArrayList<ImportedToken>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val login = item.optJSONObject("login") ?: continue
            val totp = if (login.isNull("totp")) "" else login.optString("totp").trim()
            if (totp.isEmpty())
                continue
            tokens.add(TokenImport.fromKeyText(totp, item.optString("name").trim(), login.optString("username").trim()))
        }
        if (tokens.isEmpty())
            throw ImportException.empty()
        return tokens
    }

    /* ------------------------------------------------------------------------------------------
     * CSV
     * ------------------------------------------------------------------------------------------ */

    private fun readCsv(text: String): List<ImportedToken> {
        val rows = Csv.parse(text)
        if (rows.isEmpty())
            throw ImportException.notThisFormat("Empty text")
        val header = rows[0].map { it.trim().lowercase() }
        val totpColumn = header.indexOf("login_totp")
        if (totpColumn < 0)
            throw ImportException.notThisFormat("No login_totp column")
        val nameColumn = header.indexOf("name")
        val userColumn = header.indexOf("login_username")

        val tokens = ArrayList<ImportedToken>()
        for (row in rows.drop(1)) {
            val totp = row.getOrNull(totpColumn)?.trim() ?: ""
            if (totp.isEmpty())
                continue
            tokens.add(TokenImport.fromKeyText(totp, row.getOrNull(nameColumn)?.trim() ?: "", row.getOrNull(userColumn)?.trim() ?: ""))
        }
        if (tokens.isEmpty())
            throw ImportException.empty()
        return tokens
    }

    /* ------------------------------------------------------------------------------------------
     * Password-protected exports
     * ------------------------------------------------------------------------------------------ */

    private fun decrypt(root: JSONObject, password: String?): JSONObject {
        if (!root.optBoolean("passwordProtected", false))
            throw ImportException.encryptionUnsupported("Encrypted with the account key")
        if (password == null)
            throw ImportException.passwordRequired()

        val salt = root.optString("salt")
        if (salt.isEmpty())
            throw ImportException.damaged("No salt")
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        val saltBytes = salt.toByteArray(Charsets.UTF_8)

        val key = when (root.optInt("kdfType", KDF_PBKDF2)) {
            KDF_PBKDF2 -> {
                val iterations = root.optInt("kdfIterations", 0)
                if (iterations <= 0)
                    throw ImportException.damaged("Bad iteration count")
                Pbkdf2.sha256(passwordBytes, saltBytes, iterations, 32)
            }
            KDF_ARGON2ID -> {
                val iterations = root.optInt("kdfIterations", 0)
                val memoryMiB = root.optInt("kdfMemory", 0)
                val parallelism = root.optInt("kdfParallelism", 0)
                try {
                    Argon2.id(passwordBytes, MessageDigest.getInstance("SHA-256").digest(saltBytes),
                            iterations, memoryMiB * 1024, parallelism, 32)
                } catch (e: IllegalArgumentException) {
                    throw ImportException.damaged("Bad Argon2 parameters", e)
                }
            }
            else -> throw ImportException.encryptionUnsupported("Unknown KDF")
        }

        val encKey = hkdfExpand(key, "enc")
        val macKey = hkdfExpand(key, "mac")

        val plain = decryptString(root.optString("data"), encKey, macKey) ?: throw ImportException.wrongPassword()
        return ImportCrypto.jsonObject(plain) ?: throw ImportException.damaged("The decrypted export is not JSON")
    }

    /** HKDF-Expand with SHA-256 for one block, as Bitwarden stretches a 256-bit key. */
    private fun hkdfExpand(key: ByteArray, info: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        mac.update(info.toByteArray(Charsets.UTF_8))
        mac.update(1)
        return mac.doFinal()
    }

    /** @return the plaintext of an "2.iv|data|mac" string, or null when the MAC does not check out */
    private fun decryptString(encrypted: String, encKey: ByteArray, macKey: ByteArray): ByteArray? {
        val typeAndRest = encrypted.split('.', limit = 2)
        if (typeAndRest.size != 2 || typeAndRest[0] != ENC_TYPE_AES_CBC_HMAC)
            throw ImportException.damaged("Not an AES-CBC-HMAC string")
        val parts = typeAndRest[1].split('|')
        if (parts.size != 3)
            throw ImportException.damaged("Not iv|data|mac")
        val iv = ImportCrypto.base64(parts[0]) ?: throw ImportException.damaged("Bad IV")
        val data = ImportCrypto.base64(parts[1]) ?: throw ImportException.damaged("Bad data")
        val mac = ImportCrypto.base64(parts[2]) ?: throw ImportException.damaged("Bad MAC")

        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(macKey, "HmacSHA256"))
        hmac.update(iv)
        hmac.update(data)
        if (!MessageDigest.isEqual(hmac.doFinal(), mac))
            return null

        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
            cipher.doFinal(data)
        } catch (e: Exception) {
            null
        }
    }
}
