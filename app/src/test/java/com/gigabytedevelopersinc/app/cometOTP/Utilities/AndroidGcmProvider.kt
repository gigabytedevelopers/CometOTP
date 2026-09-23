@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import java.security.AlgorithmParameters
import java.security.Key
import java.security.NoSuchAlgorithmException
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.CipherSpi
import javax.crypto.NoSuchPaddingException
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Makes the desktop JVM's AES/GCM behave like Android's (Conscrypt) for the unit tests.
 *
 * The app initialises "AES/GCM/NoPadding" with a plain [IvParameterSpec] and, for backups, with
 * the raw PBKDF2 key straight out of SecretKeyFactory. Android accepts both: an IvParameterSpec
 * means a 128-bit tag, and any RAW-encoded key of a valid AES length is used as an AES key. The
 * JDK's SunJCE rejects both. [AndroidGcmCipherSpi] applies exactly those two Android rules and
 * then delegates to SunJCE, so the ciphertext produced here is byte-for-byte what a device
 * produces. No other algorithm is touched.
 *
 * Oracle's JDK only accepts a javax.crypto provider from a signed jar, so this provider does not
 * offer the cipher under its own name: it hands out a service that belongs to SunJCE (already
 * trusted) whose [Provider.Service.newInstance] returns the adapter.
 */
@Suppress("DEPRECATION")
class AndroidGcmProvider private constructor() : Provider(NAME, 1.0, "AES/GCM with Android's parameter handling") {
    private val gcm: Service by lazy {
        val sunJce = Security.getProvider("SunJCE")
        object : Service(sunJce, "Cipher", TRANSFORMATION, AndroidGcmCipherSpi::class.java.name, null, null) {
            override fun newInstance(constructorParameter: Any?): Any = AndroidGcmCipherSpi()
        }
    }

    override fun getService(type: String, algorithm: String): Service? =
        if (type == "Cipher" && algorithm.equals(TRANSFORMATION, ignoreCase = true)) gcm else null

    override fun getServices(): Set<Service> = setOf(gcm)

    companion object {
        const val NAME = "AndroidGcmForTests"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        @JvmStatic
        @Synchronized
        fun install() {
            if (Security.getProvider(NAME) == null)
                Security.insertProviderAt(AndroidGcmProvider(), 1)
        }
    }
}

class AndroidGcmCipherSpi : CipherSpi() {
    private val delegate: Cipher = Cipher.getInstance("AES/GCM/NoPadding", "SunJCE")

    private fun aes(key: Key): Key =
        if (key.algorithm.equals("AES", ignoreCase = true)) key else SecretKeySpec(key.encoded, "AES")

    override fun engineSetMode(mode: String) {
        if (!mode.equals("GCM", ignoreCase = true)) throw NoSuchAlgorithmException(mode)
    }

    override fun engineSetPadding(padding: String) {
        if (!padding.equals("NoPadding", ignoreCase = true)) throw NoSuchPaddingException(padding)
    }

    override fun engineGetBlockSize(): Int = delegate.blockSize
    override fun engineGetOutputSize(inputLen: Int): Int = delegate.getOutputSize(inputLen)
    override fun engineGetIV(): ByteArray? = delegate.iv
    override fun engineGetParameters(): AlgorithmParameters? = delegate.parameters

    override fun engineInit(opmode: Int, key: Key, random: SecureRandom?) =
        delegate.init(opmode, aes(key), random)

    override fun engineInit(opmode: Int, key: Key, params: AlgorithmParameterSpec?, random: SecureRandom?) {
        val spec = if (params is IvParameterSpec) GCMParameterSpec(128, params.iv) else params
        delegate.init(opmode, aes(key), spec, random)
    }

    override fun engineInit(opmode: Int, key: Key, params: AlgorithmParameters?, random: SecureRandom?) =
        delegate.init(opmode, aes(key), params, random)

    override fun engineUpdate(input: ByteArray?, inputOffset: Int, inputLen: Int): ByteArray? =
        delegate.update(input, inputOffset, inputLen)

    override fun engineUpdate(input: ByteArray?, inputOffset: Int, inputLen: Int, output: ByteArray?, outputOffset: Int): Int =
        delegate.update(input, inputOffset, inputLen, output, outputOffset)

    override fun engineDoFinal(input: ByteArray?, inputOffset: Int, inputLen: Int): ByteArray =
        delegate.doFinal(input, inputOffset, inputLen)

    override fun engineDoFinal(input: ByteArray?, inputOffset: Int, inputLen: Int, output: ByteArray?, outputOffset: Int): Int =
        delegate.doFinal(input, inputOffset, inputLen, output, outputOffset)
}
