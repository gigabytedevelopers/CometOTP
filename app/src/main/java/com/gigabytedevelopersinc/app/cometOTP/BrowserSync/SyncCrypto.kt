@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.BrowserSyncException.Reason
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The browser sync protocol's cryptography: SHA-256, HKDF, AES-256-GCM and P-256 ECDH. Everything
 * here is in the platform from Android 7, so nothing is added to the app; X25519 would have needed
 * a library below Android 13.
 */
object SyncCrypto {

    private val random = SecureRandom()

    fun randomBytes(length: Int): ByteArray = ByteArray(length).also { random.nextBytes(it) }

    fun sha256(vararg parts: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        for (part in parts)
            digest.update(part)
        return digest.digest()
    }

    private fun hmacSha256(key: ByteArray, vararg parts: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        // HMAC pads its key with zeros, so an empty salt is the same as RFC 5869's HashLen zeros,
        // which SecretKeySpec accepts where it would refuse an empty key.
        mac.init(SecretKeySpec(if (key.isEmpty()) ByteArray(32) else key, "HmacSHA256"))
        for (part in parts)
            mac.update(part)
        return mac.doFinal()
    }

    /** HKDF with SHA-256 (RFC 5869). */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..255 * 32) { "HKDF output length out of range" }
        val prk = hmacSha256(salt, ikm)
        val out = ByteArrayOutputStream(length)
        var block = ByteArray(0)
        var counter = 1
        while (out.size() < length) {
            block = hmacSha256(prk, block, info, byteArrayOf(counter++.toByte()))
            out.write(block, 0, block.size)
        }
        return out.toByteArray().copyOf(length)
    }

    private fun gcm(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher {
        require(key.size == 32) { "AES-256 needs a 32-byte key" }
        require(nonce.size == 12) { "AES-GCM needs a 12-byte nonce" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        return cipher
    }

    /** AES-256-GCM: the ciphertext followed by the 16-byte tag. */
    fun seal(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray =
        gcm(Cipher.ENCRYPT_MODE, key, nonce, aad).doFinal(plaintext)

    /** Opens what [seal] made; any change to the key, nonce, data or ciphertext throws. */
    fun open(key: ByteArray, nonce: ByteArray, aad: ByteArray, sealed: ByteArray): ByteArray = try {
        gcm(Cipher.DECRYPT_MODE, key, nonce, aad).doFinal(sealed)
    } catch (e: AEADBadTagException) {
        throw BrowserSyncException(Reason.AUTH_FAILED, "Could not open: wrong key, or the data was changed")
    }

    // P-256 (SEC 2, secp256r1): y² = x³ − 3x + b over the field of P.
    private val P = BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16)
    private val B = BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16)

    /**
     * The curve's parameters. AlgorithmParameters("EC") only exists from Android 8, so they are
     * taken from a key generated for the curve, which works on every version.
     */
    val p256: ECParameterSpec by lazy {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        (generator.generateKeyPair().public as ECPublicKey).params
    }

    /** Whether [raw] is an uncompressed P-256 point (0x04 ‖ X ‖ Y) on the curve. */
    fun isP256Point(raw: ByteArray): Boolean {
        if (raw.size != 65 || raw[0] != 0x04.toByte())
            return false
        val x = BigInteger(1, raw.copyOfRange(1, 33))
        val y = BigInteger(1, raw.copyOfRange(33, 65))
        if (x >= P || y >= P)
            return false
        val three = BigInteger.valueOf(3)
        return y.pow(2).mod(P) == x.pow(3).subtract(three.multiply(x)).add(B).mod(P)
    }

    /**
     * A received public key. The point is checked here, before KeyFactory, because the protocol
     * requires it and not every security provider does.
     */
    fun publicKey(raw: ByteArray): PublicKey {
        if (!isP256Point(raw))
            throw BrowserSyncException(Reason.BAD_PUBLIC_KEY, "Not a P-256 public key")
        val point = ECPoint(BigInteger(1, raw.copyOfRange(1, 33)), BigInteger(1, raw.copyOfRange(33, 65)))
        return try {
            KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, p256))
        } catch (e: GeneralSecurityException) {
            throw BrowserSyncException(Reason.BAD_PUBLIC_KEY, "Not a P-256 public key")
        }
    }

    /** A new ephemeral P-256 key pair, in memory only. */
    fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), random)
        return generator.generateKeyPair()
    }

    /** A public key as uncompressed SEC1, 65 bytes. */
    fun encodePublicKey(key: PublicKey): ByteArray {
        val point = (key as ECPublicKey).w
        return SyncEncoding.concat(byteArrayOf(0x04), fixed32(point.affineX), fixed32(point.affineY))
    }

    private fun fixed32(n: BigInteger): ByteArray {
        val raw = n.toByteArray()
        val out = ByteArray(32)
        val copy = minOf(raw.size, 32)
        System.arraycopy(raw, raw.size - copy, out, 32 - copy, copy)
        return out
    }

    /** P-256 Diffie–Hellman: the shared x-coordinate, exactly 32 bytes with its leading zeros. */
    fun ecdh(own: PrivateKey, peerPublicKey: ByteArray): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(own)
        agreement.doPhase(publicKey(peerPublicKey), true)
        val secret = agreement.generateSecret()
        // Every provider seen returns 32 bytes; pad in case one ever drops leading zeros.
        return if (secret.size == 32) secret else fixed32(BigInteger(1, secret))
    }
}
