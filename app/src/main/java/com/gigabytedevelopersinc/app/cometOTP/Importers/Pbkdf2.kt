@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * PBKDF2 (RFC 8018) over any HMAC the platform has.
 *
 * SecretKeyFactory only offers PBKDF2WithHmacSHA256 and the other SHA-2 variants from Android 8,
 * and CometOTP runs on Android 7; the HMACs themselves are there on every version, and PBKDF2 on
 * top of one is a dozen lines. Other apps' exports use SHA-256 almost without exception.
 */
object Pbkdf2 {

    fun sha256(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray =
        derive("HmacSHA256", password, salt, iterations, length)

    fun sha1(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray =
        derive("HmacSHA1", password, salt, iterations, length)

    fun sha512(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray =
        derive("HmacSHA512", password, salt, iterations, length)

    fun derive(hmac: String, password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        require(iterations > 0) { "Iterations must be positive" }
        require(length > 0) { "Length must be positive" }

        val mac = Mac.getInstance(hmac)
        // An empty password is a valid PBKDF2 input but not a valid SecretKeySpec; HMAC pads a
        // key out with zeros, so one zero byte keys it the same way an empty key would.
        mac.init(SecretKeySpec(if (password.isEmpty()) ByteArray(1) else password, hmac))

        val hashLength = mac.macLength
        val blocks = (length + hashLength - 1) / hashLength
        val out = ByteArray(blocks * hashLength)

        for (block in 1..blocks) {
            mac.update(salt)
            mac.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
            var u = mac.doFinal()
            val t = u.copyOf()
            for (i in 1 until iterations) {
                u = mac.doFinal(u)
                for (k in t.indices)
                    t[k] = (t[k].toInt() xor u[k].toInt()).toByte()
            }
            System.arraycopy(t, 0, out, (block - 1) * hashLength, hashLength)
        }

        return if (out.size == length) out else out.copyOf(length)
    }
}
