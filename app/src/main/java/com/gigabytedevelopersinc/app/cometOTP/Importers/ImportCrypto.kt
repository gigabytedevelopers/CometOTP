@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.binary.Hex
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** What the importers share for opening encrypted exports and reading their fields. */
internal object ImportCrypto {

    const val GCM_TAG_BITS = 128

    /**
     * AES-GCM with a 128-bit tag, the tag after the ciphertext as the Java API expects it.
     *
     * @return the plaintext, or null when the tag does not check out, which with a key derived
     *         from a password means the password was wrong
     */
    fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertextAndTag: ByteArray, associatedData: ByteArray? = null): ByteArray? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
            if (associatedData != null)
                cipher.updateAAD(associatedData)
            cipher.doFinal(ciphertextAndTag)
        } catch (e: AEADBadTagException) {
            null
        }
    }

    /** @return the bytes the hex spells, or null when it is not hex */
    fun hex(text: String?): ByteArray? {
        if (text == null || text.length % 2 != 0)
            return null
        return try {
            Hex.decodeHex(text.toCharArray())
        } catch (e: Exception) {
            null
        }
    }

    /** @return the bytes the base64 spells, standard or URL-safe alphabet, or null when it is neither */
    fun base64(text: String?): ByteArray? {
        if (text == null)
            return null
        val cleaned = text.replace(Regex("\\s"), "")
        if (cleaned.isEmpty() || !Base64.isBase64(cleaned))
            return null
        return Base64.decodeBase64(cleaned)
    }

    /**
     * @return the file as a JSON object, or null when it is not one. Export files are small, so
     *         the whole text is parsed; a byte order mark is tolerated.
     */
    fun jsonObject(data: ByteArray): JSONObject? = json(data) as? JSONObject

    fun jsonArray(data: ByteArray): JSONArray? = json(data) as? JSONArray

    private fun json(data: ByteArray): Any? {
        val text = OtpauthLinksImport.decodeText(data) ?: return null
        return try {
            JSONTokener(text.trim()).nextValue()
        } catch (e: Exception) {
            null
        }
    }
}
